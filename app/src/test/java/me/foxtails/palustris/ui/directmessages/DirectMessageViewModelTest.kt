package me.foxtails.palustris.ui.directmessages

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import me.foxtails.palustris.data.directmessages.DirectMessageWriteAuthority
import me.foxtails.palustris.data.directmessages.InMemoryDirectMessageStore
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.Audience
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.ConversationId
import me.foxtails.palustris.domain.DirectConversation
import me.foxtails.palustris.domain.DirectMessageRequest
import me.foxtails.palustris.domain.DirectThreadRequest
import me.foxtails.palustris.domain.EntityId
import me.foxtails.palustris.domain.Page
import me.foxtails.palustris.domain.Post
import me.foxtails.palustris.domain.Protocol
import me.foxtails.palustris.domain.ServerCapabilities
import me.foxtails.palustris.domain.SocialSource
import me.foxtails.palustris.domain.Timeline
import me.foxtails.palustris.ui.directmessages.DirectMessageViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DirectMessageViewModelTest {
    private val connection = Connection("https://example.org", Protocol.MASTODON)
    private val accountId = AccountId(connection, "owner")
    private val owner = Account(accountId, "Owner", "@owner@example.org")
    private val recipientA = Account(AccountId(connection, "alice"), "Alice", "@alice@example.org")
    private val recipientB = Account(AccountId(connection, "bob"), "Bob", "@bob@example.org")

    private fun post(id: String, author: Account = recipientA) = Post(
        id = EntityId(connection.origin, id),
        author = author,
        text = id,
        publishedAtEpochMillis = 0L,
        audience = Audience.Direct,
    )

    private fun conversation(id: String, lastId: String, author: Account = recipientA) = DirectConversation(
        id = ConversationId(connection.origin, id),
        participants = listOf(owner, author),
        lastPost = post(lastId, author),
        unread = false,
    )

    /** A source whose DM requests wait for an explicit release that survives cancellation. */
    private class GatedDirectSource : SocialSource, me.foxtails.palustris.domain.DirectMessageSource {
        override val capabilities = ServerCapabilities()
        val inboxRequests = mutableListOf<String?>()
        val threadRequests = mutableListOf<DirectThreadRequest>()
        val sendRequests = mutableListOf<DirectMessageRequest>()
        private val inboxPending = ArrayDeque<CompletableDeferred<Page<DirectConversation>>>()
        private val threadPending = ArrayDeque<CompletableDeferred<List<Post>>>()
        private val sendPending = ArrayDeque<CompletableDeferred<Post>>()

        override suspend fun timeline(timeline: Timeline, cursor: String?): Page<Post> = Page(emptyList())
        override suspend fun searchHashtag(tag: String, cursor: String?): Page<Post> = Page(emptyList())

        override suspend fun conversations(cursor: String?): Page<DirectConversation> {
            inboxRequests += cursor
            val gate = CompletableDeferred<Page<DirectConversation>>()
            inboxPending += gate
            return withContext(NonCancellable) { gate.await() }
        }

        override suspend fun conversationThread(request: DirectThreadRequest): List<Post> {
            threadRequests += request
            val gate = CompletableDeferred<List<Post>>()
            threadPending += gate
            return withContext(NonCancellable) { gate.await() }
        }

        override suspend fun sendDirectMessage(request: DirectMessageRequest): Post {
            sendRequests += request
            val gate = CompletableDeferred<Post>()
            sendPending += gate
            return withContext(NonCancellable) { gate.await() }
        }

        override suspend fun markConversationRead(id: ConversationId) = Unit

        fun completeInbox(index: Int, page: Page<DirectConversation>) { inboxPending[index].complete(page) }
        fun failInbox(index: Int, error: Exception) { inboxPending[index].completeExceptionally(error) }
        fun completeThread(index: Int, thread: List<Post>) { threadPending[index].complete(thread) }
        fun failThread(index: Int, error: Exception) { threadPending[index].completeExceptionally(error) }
        fun completeSend(index: Int, post: Post) { sendPending[index].complete(post) }
        fun failSend(index: Int, error: Exception) { sendPending[index].completeExceptionally(error) }
    }

    private suspend fun setup(
        source: GatedDirectSource,
        dispatcher: kotlinx.coroutines.CoroutineDispatcher,
    ): DirectMessageViewModel {
        val authority = DirectMessageWriteAuthority()
        val generation = authority.activate(accountId)
        return DirectMessageViewModel(
            accountId = accountId,
            source = source,
            writeGeneration = generation,
            store = InMemoryDirectMessageStore(),
            ioDispatcher = dispatcher,
            writeAuthority = authority,
        )
    }

    @Test
    fun lateThreadResultCannotMoveSelection() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val source = GatedDirectSource()
            val model = setup(source, StandardTestDispatcher(testScheduler))
            advanceUntilIdle()
            val conversationA = conversation("a", "a-last", recipientA)
            val conversationB = conversation("b", "b-last", recipientB)
            source.completeInbox(0, Page(listOf(conversationA, conversationB)))
            advanceUntilIdle()

            model.openConversation(conversationA)
            advanceUntilIdle()
            model.openConversation(conversationB)
            advanceUntilIdle()
            source.completeThread(0, listOf(post("a-1", recipientA)))
            advanceUntilIdle()
            source.completeThread(1, listOf(post("b-1", recipientB)))
            advanceUntilIdle()

            assertEquals(conversationB.id, model.state.value.selectedConversationId)
            assertEquals(listOf("b-1"), model.state.value.thread.map { it.id.value })
            assertNull(model.state.value.error)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun lateThreadFailureCannotAppearInReplacementConversation() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val source = GatedDirectSource()
            val model = setup(source, StandardTestDispatcher(testScheduler))
            advanceUntilIdle()
            val conversationA = conversation("a", "a-last", recipientA)
            val conversationB = conversation("b", "b-last", recipientB)
            source.completeInbox(0, Page(listOf(conversationA, conversationB)))
            advanceUntilIdle()

            model.openConversation(conversationA)
            advanceUntilIdle()
            model.openConversation(conversationB)
            advanceUntilIdle()
            source.failThread(0, java.io.IOException("old thread failed"))
            advanceUntilIdle()
            source.completeThread(1, listOf(post("b-1", recipientB)))
            advanceUntilIdle()

            assertEquals(conversationB.id, model.state.value.selectedConversationId)
            assertEquals(listOf("b-1"), model.state.value.thread.map { it.id.value })
            assertNull(model.state.value.error)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun closedConversationIgnoresLateThread() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val source = GatedDirectSource()
            val model = setup(source, StandardTestDispatcher(testScheduler))
            advanceUntilIdle()
            val conversationA = conversation("a", "a-last", recipientA)
            source.completeInbox(0, Page(listOf(conversationA)))
            advanceUntilIdle()

            model.openConversation(conversationA)
            advanceUntilIdle()
            model.closeConversation()
            advanceUntilIdle()
            source.completeThread(0, listOf(post("a-1", recipientA)))
            advanceUntilIdle()

            assertNull(model.state.value.selectedConversationId)
            assertTrue(model.state.value.thread.isEmpty())
            assertNull(model.state.value.error)
            assertFalse(model.state.value.loadingThread)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun newRecipientDraftsStayIsolated() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val source = GatedDirectSource()
            val model = setup(source, StandardTestDispatcher(testScheduler))
            advanceUntilIdle()
            source.completeInbox(0, Page(emptyList()))
            advanceUntilIdle()

            model.startConversation(recipientA)
            advanceUntilIdle()
            model.startConversation(recipientB)
            advanceUntilIdle()

            assertEquals(recipientB.id, model.state.value.recipient?.id)
            assertNull(model.state.value.selectedConversationId)
            assertTrue(model.state.value.thread.isEmpty())

            model.updateEditor("hello bob")
            model.send()
            advanceUntilIdle()
            model.startConversation(recipientA)
            advanceUntilIdle()
            source.completeSend(0, post("sent-bob", owner))
            advanceUntilIdle()

            // The stale send for Bob cannot move the current Alice draft.
            assertEquals(recipientA.id, model.state.value.recipient?.id)
            assertTrue(model.state.value.thread.isEmpty())
            // The Alice editor starts empty. Bob's text stayed with Bob's target.
            assertEquals("", model.state.value.editorText)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun staleSendCannotChangeReplacementConversation() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val source = GatedDirectSource()
            val model = setup(source, StandardTestDispatcher(testScheduler))
            advanceUntilIdle()
            val conversationA = conversation("a", "a-last", recipientA)
            val conversationB = conversation("b", "b-last", recipientB)
            source.completeInbox(0, Page(listOf(conversationA, conversationB)))
            advanceUntilIdle()

            model.openConversation(conversationA)
            advanceUntilIdle()
            source.completeThread(0, listOf(post("a-1", recipientA)))
            advanceUntilIdle()
            model.updateEditor("reply a")
            model.send()
            advanceUntilIdle()
            model.openConversation(conversationB)
            advanceUntilIdle()
            model.updateEditor("for b")
            source.completeSend(0, post("sent-a", owner))
            advanceUntilIdle()
            source.completeThread(1, listOf(post("b-1", recipientB)))
            advanceUntilIdle()

            assertEquals(conversationB.id, model.state.value.selectedConversationId)
            assertEquals(listOf("b-1"), model.state.value.thread.map { it.id.value })
            // The accepted send for Alice cannot clear the newer Bob draft.
            assertEquals("for b", model.state.value.editorText)
            assertNull(model.state.value.error)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun staleInboxPageCannotReplaceNewerRefresh() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val source = GatedDirectSource()
            val model = setup(source, StandardTestDispatcher(testScheduler))
            advanceUntilIdle()
            source.completeInbox(0, Page(listOf(conversation("a", "a-last")), nextCursor = "c1"))
            advanceUntilIdle()

            model.loadMore()
            advanceUntilIdle()
            model.refresh()
            advanceUntilIdle()
            // Paging cannot start behind an active refresh.
            val requestsBefore = source.inboxRequests.size
            model.loadMore()
            advanceUntilIdle()
            assertEquals(requestsBefore, source.inboxRequests.size)
            // Old paging failure arrives after the newer refresh started. A failed page
            // writes nothing, so this verifies inbox-epoch rejection without durable
            // write authority (owned by Slice 02-C).
            source.failInbox(1, java.io.IOException("stale page failed"))
            advanceUntilIdle()
            source.completeInbox(2, Page(listOf(conversation("new", "new-last")), nextCursor = "c2"))
            advanceUntilIdle()

            assertEquals(
                listOf("a-last", "new-last"),
                model.state.value.conversations.map { it.lastPost.id.value }.sorted(),
            )
            assertEquals("c2", model.state.value.nextCursor)
            assertNull(model.state.value.error)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun selectedConversationSurvivesRefreshAbsence() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val source = GatedDirectSource()
            val model = setup(source, StandardTestDispatcher(testScheduler))
            advanceUntilIdle()
            val conversationA = conversation("a", "a-last", recipientA)
            source.completeInbox(0, Page(listOf(conversationA)))
            advanceUntilIdle()

            model.openConversation(conversationA)
            advanceUntilIdle()
            source.completeThread(0, listOf(post("a-1", recipientA)))
            advanceUntilIdle()

            model.refresh()
            advanceUntilIdle()
            source.completeInbox(1, Page(emptyList()))
            advanceUntilIdle()

            assertEquals(conversationA.id, model.state.value.selectedConversationId)
            assertEquals(conversationA.id, model.state.value.selectedConversation?.id)
            assertEquals(recipientA.id, model.state.value.recipient?.id)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun unsupportedSourceExposesErrorWithoutStuckThread() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val plain = object : SocialSource {
                override val capabilities = ServerCapabilities()
                override suspend fun timeline(timeline: Timeline, cursor: String?): Page<Post> = Page(emptyList())
                override suspend fun searchHashtag(tag: String, cursor: String?): Page<Post> = Page(emptyList())
            }
            val authority = DirectMessageWriteAuthority()
            val generation = authority.activate(accountId)
            val model = DirectMessageViewModel(
                accountId = accountId,
                source = plain,
                writeGeneration = generation,
                store = InMemoryDirectMessageStore(),
                ioDispatcher = StandardTestDispatcher(testScheduler),
                writeAuthority = authority,
            )
            advanceUntilIdle()

            model.openConversation(conversation("a", "a-last"))
            advanceUntilIdle()
            model.updateEditor("hello")
            model.send()
            advanceUntilIdle()

            assertFalse(model.state.value.loadingThread)
            assertTrue(model.state.value.error != null)
            assertFalse(model.state.value.sending)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun stopRejectsLateThreadAndSend() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val source = GatedDirectSource()
            val model = setup(source, StandardTestDispatcher(testScheduler))
            advanceUntilIdle()
            val conversationA = conversation("a", "a-last", recipientA)
            source.completeInbox(0, Page(listOf(conversationA)))
            advanceUntilIdle()

            model.openConversation(conversationA)
            advanceUntilIdle()
            model.stop()
            source.completeThread(0, listOf(post("late", recipientA)))
            advanceUntilIdle()

            assertTrue(model.state.value.thread.isEmpty())
            assertNull(model.state.value.error)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun failedSendPreservesEditorTextForRecovery() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val source = GatedDirectSource()
            val model = setup(source, StandardTestDispatcher(testScheduler))
            advanceUntilIdle()
            val conversationA = conversation("a", "a-last", recipientA)
            source.completeInbox(0, Page(listOf(conversationA)))
            advanceUntilIdle()
            model.openConversation(conversationA)
            advanceUntilIdle()
            source.completeThread(0, listOf(post("a-1", recipientA)))
            advanceUntilIdle()

            model.updateEditor("unsent text")
            model.send()
            advanceUntilIdle()
            source.failSend(0, java.io.IOException("send failed"))
            advanceUntilIdle()

            assertEquals("unsent text", model.state.value.editorText)
            assertFalse(model.state.value.sending)
            assertTrue(model.state.value.error != null)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun newerTextTypedDuringSendSurvivesAcceptedCompletion() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val source = GatedDirectSource()
            val model = setup(source, StandardTestDispatcher(testScheduler))
            advanceUntilIdle()
            val conversationA = conversation("a", "a-last", recipientA)
            source.completeInbox(0, Page(listOf(conversationA)))
            advanceUntilIdle()
            model.openConversation(conversationA)
            advanceUntilIdle()
            source.completeThread(0, listOf(post("a-1", recipientA)))
            advanceUntilIdle()

            model.updateEditor("first")
            model.send()
            advanceUntilIdle()
            // The user keeps typing while the send is in flight.
            model.updateEditor("first and second")
            source.completeSend(0, post("sent-a", owner))
            advanceUntilIdle()

            assertEquals("first and second", model.state.value.editorText)
            assertFalse(model.state.value.sending)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun acceptedSendClearsUnchangedEditorText() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val source = GatedDirectSource()
            val model = setup(source, StandardTestDispatcher(testScheduler))
            advanceUntilIdle()
            val conversationA = conversation("a", "a-last", recipientA)
            source.completeInbox(0, Page(listOf(conversationA)))
            advanceUntilIdle()
            model.openConversation(conversationA)
            advanceUntilIdle()
            source.completeThread(0, listOf(post("a-1", recipientA)))
            advanceUntilIdle()

            model.updateEditor("only text")
            model.send()
            advanceUntilIdle()
            source.completeSend(0, post("sent-a", owner))
            advanceUntilIdle()

            assertEquals("", model.state.value.editorText)
            assertFalse(model.state.value.sending)
            assertNull(model.state.value.error)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun revokedSharedAuthorityStopsTheViewModelWrite() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val source = GatedDirectSource()
            val store = InMemoryDirectMessageStore()
            val authority = DirectMessageWriteAuthority()
            val generation = authority.activate(accountId)
            val model = DirectMessageViewModel(
                accountId = accountId,
                source = source,
                writeGeneration = generation,
                store = store,
                ioDispatcher = StandardTestDispatcher(testScheduler),
                writeAuthority = authority,
            )
            advanceUntilIdle()
            val conversationA = conversation("a", "a-last", recipientA).copy(unread = true)
            store.save(accountId, conversationA)

            // Account removal invalidates the shared authority before the thread
            // load finishes. The ViewModel must not mark the conversation read.
            authority.invalidate(accountId)
            model.openConversation(conversationA)
            advanceUntilIdle()
            source.completeThread(0, listOf(post("a-1", recipientA)))
            advanceUntilIdle()

            assertTrue(store.conversation(accountId, conversationA.id)?.unread == true)
        } finally {
            Dispatchers.resetMain()
        }
    }
}
