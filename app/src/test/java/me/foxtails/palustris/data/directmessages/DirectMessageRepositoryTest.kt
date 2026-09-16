package me.foxtails.palustris.data.directmessages

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import me.foxtails.palustris.data.directmessages.DirectMessageRepository
import me.foxtails.palustris.data.directmessages.DirectMessageWriteAuthority
import me.foxtails.palustris.data.directmessages.InMemoryDirectMessageStore
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.Audience
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.ConversationId
import me.foxtails.palustris.domain.DirectConversation
import me.foxtails.palustris.domain.DirectMessageRequest
import me.foxtails.palustris.domain.DirectMessageSource
import me.foxtails.palustris.domain.EntityId
import me.foxtails.palustris.domain.Page
import me.foxtails.palustris.domain.Post
import me.foxtails.palustris.domain.Protocol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DirectMessageRepositoryTest {
    private val connection = Connection("https://example.org", Protocol.MASTODON)
    private val accountId = AccountId(connection, "owner")
    private val owner = Account(accountId, "Owner", "@owner@example.org")
    private val recipient = Account(AccountId(connection, "alice"), "Alice", "@alice@example.org")

    private fun post(id: String, author: Account = recipient) = Post(
        id = EntityId(connection.origin, id),
        author = author,
        text = id,
        publishedAtEpochMillis = 0L,
        audience = Audience.Direct,
    )

    private fun conversation(id: String, lastId: String, unread: Boolean = false) = DirectConversation(
        id = ConversationId(connection.origin, id),
        participants = listOf(owner, recipient),
        lastPost = post(lastId),
        unread = unread,
    )

    private class GatedSource : DirectMessageSource {
        private val inboxPending = ArrayDeque<CompletableDeferred<Page<DirectConversation>>>()
        private val threadPending = ArrayDeque<CompletableDeferred<List<Post>>>()
        private val sendPending = ArrayDeque<CompletableDeferred<Post>>()
        var markReadCalls = 0

        override suspend fun conversations(cursor: String?): Page<DirectConversation> {
            val gate = CompletableDeferred<Page<DirectConversation>>()
            inboxPending += gate
            return withContext(NonCancellable) { gate.await() }
        }

        override suspend fun conversationThread(id: ConversationId): List<Post> {
            val gate = CompletableDeferred<List<Post>>()
            threadPending += gate
            return withContext(NonCancellable) { gate.await() }
        }

        override suspend fun sendDirectMessage(request: DirectMessageRequest): Post {
            val gate = CompletableDeferred<Post>()
            sendPending += gate
            return withContext(NonCancellable) { gate.await() }
        }

        override suspend fun markConversationRead(id: ConversationId) {
            markReadCalls += 1
        }

        fun completeInbox(index: Int, page: Page<DirectConversation>) { inboxPending[index].complete(page) }
        fun completeThread(index: Int, thread: List<Post>) { threadPending[index].complete(thread) }
        fun completeSend(index: Int, post: Post) { sendPending[index].complete(post) }
    }

    private fun repository(
        authority: DirectMessageWriteAuthority,
        store: InMemoryDirectMessageStore,
        source: GatedSource,
        generation: Long,
        dispatcher: kotlinx.coroutines.CoroutineDispatcher,
    ) = DirectMessageRepository(accountId, source, store, generation, dispatcher, authority)

    @Test
    fun removedAccountsStayDeleted() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val authority = DirectMessageWriteAuthority()
        val store = InMemoryDirectMessageStore()
        val source = GatedSource()
        val repository = repository(authority, store, source, authority.activate(accountId), dispatcher)

        val pending = async { repository.conversations() }
        advanceUntilIdle()
        // Account removal revokes writers before deleting rows.
        authority.invalidateAndDelete(accountId) { store.delete(accountId) }
        source.completeInbox(0, Page(listOf(conversation("late", "late-last"))))
        advanceUntilIdle()

        var cancelled = false
        try {
            pending.await()
        } catch (_: kotlinx.coroutines.CancellationException) {
            cancelled = true
        }
        assertTrue(cancelled)
        assertTrue(store.conversations(accountId).isEmpty())
    }

    @Test
    fun oldSessionCannotWriteAfterReplacement() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val authority = DirectMessageWriteAuthority()
        val store = InMemoryDirectMessageStore()
        val source = GatedSource()
        val oldGeneration = authority.activate(accountId)
        val oldRepository = repository(authority, store, source, oldGeneration, dispatcher)

        val pending = async {
            oldRepository.send(DirectMessageRequest(listOf(recipient.id), "stale"))
        }
        advanceUntilIdle()
        // Session replacement activates a new writer and revokes the old one.
        val newGeneration = authority.activate(accountId)
        val newRepository = repository(authority, store, source, newGeneration, dispatcher)
        source.completeSend(0, post("stale-post", owner))
        advanceUntilIdle()

        var cancelled = false
        try {
            pending.await()
        } catch (_: kotlinx.coroutines.CancellationException) {
            cancelled = true
        }
        assertTrue(cancelled)
        assertTrue(store.conversations(accountId).isEmpty())

        val accepted = async {
            newRepository.send(DirectMessageRequest(listOf(recipient.id), "fresh"))
        }
        advanceUntilIdle()
        source.completeSend(1, post("fresh-post", owner))
        advanceUntilIdle()
        accepted.await()
        assertEquals(1, store.conversations(accountId).size)
    }

    @Test
    fun twoRepositoriesInOneSessionShareWriterAuthority() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val authority = DirectMessageWriteAuthority()
        val store = InMemoryDirectMessageStore()
        val source = GatedSource()
        val generation = authority.activate(accountId)
        val first = repository(authority, store, source, generation, dispatcher)
        val second = repository(authority, store, source, generation, dispatcher)

        val pending = async { first.conversations() }
        advanceUntilIdle()
        source.completeInbox(0, Page(listOf(conversation("a", "last"))))
        assertEquals(1, pending.await().items.size)
        advanceUntilIdle()

        val sending = async {
            second.send(
                DirectMessageRequest(listOf(recipient.id), "hi"),
                conversationId = ConversationId(connection.origin, "a"),
                recipientAccounts = listOf(recipient),
            )
        }
        advanceUntilIdle()
        source.completeSend(0, post("sent", owner))
        sending.await()
        advanceUntilIdle()

        assertEquals(1, store.conversations(accountId).size)
        assertEquals("sent", store.conversations(accountId).single().lastPost.id.value)
    }

    @Test
    fun sendAcceptedDuringThreadLoadSurvivesOlderResponse() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val authority = DirectMessageWriteAuthority()
        val store = InMemoryDirectMessageStore()
        val source = GatedSource()
        val repository = repository(authority, store, source, authority.activate(accountId), dispatcher)
        val id = ConversationId(connection.origin, "a")
        store.save(accountId, conversation("a", "seed"))

        val loading = async { repository.thread(id) }
        advanceUntilIdle()
        // The send is accepted while the thread request waits.
        val sending = async {
            repository.send(
                DirectMessageRequest(listOf(recipient.id), "hello"),
                conversationId = id,
                recipientAccounts = listOf(recipient),
            )
        }
        advanceUntilIdle()
        source.completeSend(0, post("sent", owner))
        sending.await()
        advanceUntilIdle()
        // The thread response predates the send. It must not drop the sent message or preview.
        source.completeThread(0, listOf(post("old", recipient)))
        val merged = loading.await()
        advanceUntilIdle()

        assertTrue(merged.map { it.id.value }.contains("sent"))
        assertEquals("sent", store.conversation(accountId, id)?.lastPost?.id?.value)
    }

    @Test
    fun olderInboxResponseDoesNotReplaceNewerSend() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val authority = DirectMessageWriteAuthority()
        val store = InMemoryDirectMessageStore()
        val source = GatedSource()
        val repository = repository(authority, store, source, authority.activate(accountId), dispatcher)
        val id = ConversationId(connection.origin, "a")

        val inbox = async { repository.conversations() }
        advanceUntilIdle()
        val sending = async {
            repository.send(
                DirectMessageRequest(listOf(recipient.id), "hello"),
                conversationId = id,
                recipientAccounts = listOf(recipient),
            )
        }
        advanceUntilIdle()
        source.completeSend(0, post("sent", owner))
        sending.await()
        advanceUntilIdle()
        // The first page predates the send.
        source.completeInbox(0, Page(listOf(conversation("a", "old"))))
        inbox.await()
        advanceUntilIdle()

        assertEquals("sent", store.conversation(accountId, id)?.lastPost?.id?.value)
    }

    @Test
    fun refetchPreservesLocalReadStateForTheSameLastPost() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val authority = DirectMessageWriteAuthority()
        val store = InMemoryDirectMessageStore()
        val source = GatedSource()
        val repository = repository(authority, store, source, authority.activate(accountId), dispatcher)

        val first = async { repository.conversations() }
        advanceUntilIdle()
        source.completeInbox(0, Page(listOf(conversation("a", "last", unread = true))))
        first.await()
        advanceUntilIdle()
        store.markRead(accountId, ConversationId(connection.origin, "a"))

        val second = async { repository.conversations() }
        advanceUntilIdle()
        source.completeInbox(1, Page(listOf(conversation("a", "last", unread = true))))
        second.await()
        advanceUntilIdle()

        assertEquals(false, store.conversations(accountId).single().unread)
    }

    @Test
    fun removalOfAnotherAccountKeepsWritesValid() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val authority = DirectMessageWriteAuthority()
        val store = InMemoryDirectMessageStore()
        val source = GatedSource()
        val other = AccountId(connection, "other")
        val repository = repository(authority, store, source, authority.activate(accountId), dispatcher)

        val pending = async { repository.conversations() }
        advanceUntilIdle()
        authority.invalidateAndDelete(other) { store.delete(other) }
        source.completeInbox(0, Page(listOf(conversation("a", "last"))))
        val page = pending.await()
        advanceUntilIdle()

        assertEquals(1, page.items.size)
        assertEquals(1, store.conversations(accountId).size)
        assertTrue(store.conversations(other).isEmpty())
    }

    @Test
    fun staleWriterSkipsMarkRead() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val authority = DirectMessageWriteAuthority()
        val store = InMemoryDirectMessageStore()
        val source = GatedSource()
        val repository = repository(authority, store, source, authority.activate(accountId), dispatcher)
        val id = ConversationId(connection.origin, "a")
        store.save(accountId, conversation("a", "last", unread = true))

        authority.invalidate(accountId)
        repository.markRead(id)
        advanceUntilIdle()

        assertEquals(1, source.markReadCalls)
        assertEquals(true, store.conversation(accountId, id)?.unread)
    }
}
