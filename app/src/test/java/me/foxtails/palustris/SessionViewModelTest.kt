package me.foxtails.palustris

import androidx.lifecycle.ViewModelStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import me.foxtails.palustris.data.auth.*
import me.foxtails.palustris.data.misskey.ApiFailure
import me.foxtails.palustris.data.misskey.MisskeyErrorMapper
import me.foxtails.palustris.domain.*
import me.foxtails.palustris.ui.AccountManager
import me.foxtails.palustris.ui.AccountSyncCoordinator
import me.foxtails.palustris.ui.FeedViewModel
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SessionViewModelTest {
    private val login get() = LoginSession("https://example.org", "test-token", JSONObject("""{"id":"a","username":"alice"}"""))
    private class MemoryStore(
        initialSession: Session? = null,
        initialAccount: Account? = null,
    ) : SessionStore {
        val sessions = mutableMapOf<AccountId, Session>().apply {
            initialSession?.let { put(it.accountId, it) }
        }
        var storedSession: Session?
            get() = sessions.values.firstOrNull()
            set(value) {
                sessions.clear()
                value?.let { sessions[it.accountId] = it }
            }
        var pending: PendingLogin? = null
        val readAccountIds = mutableListOf<AccountId>()
        val writtenAccountIds = mutableListOf<AccountId>()
        var index = initialAccount?.let { account ->
            AccountIndex(accounts = listOf(AccountRef(account.id, account.handle, account.avatarUrl, account.displayName)), activeAccountId = account.id)
        } ?: AccountIndex()

        override fun read(accountId: AccountId): Session? {
            readAccountIds += accountId
            return sessions[accountId]
        }
        override fun write(accountId: AccountId, session: Session) {
            writtenAccountIds += accountId
            sessions[accountId] = session
        }
        override fun delete(accountId: AccountId) { sessions.remove(accountId) }
        override fun readIndex() = index
        override fun writeIndex(index: AccountIndex) { this.index = index }
        override fun readPending() = pending
        override fun writePending(pending: PendingLogin) { this.pending = pending }
        override fun clearPending() { pending = null }
        override fun clear() { sessions.clear(); pending = null; index = AccountIndex() }
    }
    private class Source : SocialSource {
        override val capabilities = ServerCapabilities(timelines = setOf(Timeline.Home))
        var error: Exception? = null
        var createError: Exception? = null
        val timelineCalls = mutableListOf<Pair<Timeline, String?>>()
        override suspend fun timeline(timeline: Timeline, cursor: String?): Page<Post> {
            timelineCalls += timeline to cursor
            error?.let { throw MisskeyErrorMapper.map(it) }
            val account = Account(AccountId(Connection("https://example.org", Protocol.MISSKEY), "a"), "Alice", "@alice")
            fun post(id: String) = Post(EntityId("example", id), account, "Text", 0, Audience.Public)
            return if (cursor == null) Page(listOf(post("2")), "2") else Page(listOf(post("2"), post("1")), null)
        }
        override suspend fun create(post: CreatePostRequest): Post {
            createError?.let { throw MisskeyErrorMapper.map(it) }
            val account = Account(AccountId(Connection("https://example.org", Protocol.MISSKEY), "a"), "Alice", "@alice")
            return Post(EntityId("example", "created"), account, post.text, 0, Audience.Public)
        }
    }
    private fun auth(result: LoginSession) = object : AuthGateway {
        override suspend fun prepare(input: String) = PendingLogin(input, "session-id", System.currentTimeMillis())
        override fun browserUrl(pending: PendingLogin) = "${pending.origin}/miauth/${pending.id}"
        override suspend fun complete(pending: PendingLogin) = result
    }

    @Test fun restorePageDeduplicateRetryAndSignOut() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val owner = ViewModelStore()
        try {
            val store = MemoryStore(Session(login.account.id, login.token, ServerCapabilities()), login.account)
            val source = Source()
            val accountManager = AccountManager(store, auth(login), StandardTestDispatcher(testScheduler))
            val feedModel = FeedViewModel(login.account.id, source, AccountSyncCoordinator())
            owner.put("account", accountManager)
            owner.put("feed", feedModel)
            advanceUntilIdle()
            assertEquals("@alice@example.org", accountManager.session.value.account?.handle)
            assertEquals(1, feedModel.feed.value.posts.size)
            assertEquals(login.account.id, feedModel.feed.value.ownedPosts.single().fetchedBy)
            feedModel.loadMore(); advanceUntilIdle()
            assertEquals(listOf("2", "1"), feedModel.feed.value.posts.map { it.id.value })
            source.error = IOException()
            feedModel.refresh(); advanceUntilIdle()
            assertEquals(2, feedModel.feed.value.posts.size)
            assertNotNull(feedModel.feed.value.error)
            source.error = null
            feedModel.refresh(); advanceUntilIdle()
            assertNull(feedModel.feed.value.error)
            source.error = ApiFailure(401)
            feedModel.refresh(); advanceUntilIdle()
            assertTrue(feedModel.feed.value.needsSignIn)
            accountManager.signOut(); advanceUntilIdle()
            assertNull(store.storedSession)
            assertNull(accountManager.session.value.account)
        } finally { owner.clear(); Dispatchers.resetMain() }
    }

    @Test fun pendingAuthSurvivesRestartAndInvalidCallbackDoesNotConnect() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val owner = ViewModelStore()
        try {
            val store = MemoryStore()
            val result = login
            val dispatcher = StandardTestDispatcher(testScheduler)
            val first = AccountManager(store, auth(result), dispatcher)
            owner.put("first", first)
            advanceUntilIdle()
            first.signIn("https://example.org"); advanceUntilIdle()
            assertNotNull(store.pending)
            val restored = AccountManager(store, auth(result), dispatcher)
            owner.put("restored", restored)
            advanceUntilIdle()
            assertTrue(restored.session.value.pending)
            restored.callback("palustris://auth/misskey?session=attacker")
            advanceUntilIdle()
            assertNull(restored.session.value.account)
            restored.callback("palustris://auth/misskey?session=session-id")
            advanceUntilIdle()
            assertNotNull(restored.session.value.account)
            assertNull(store.pending)
            assertNotNull(store.storedSession)
        } finally { owner.clear(); Dispatchers.resetMain() }
    }

    @Test fun failedPublishKeepsStateAndSuccessInvokesCompletion() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val owner = ViewModelStore()
        try {
            val store = MemoryStore(Session(login.account.id, login.token, ServerCapabilities()), login.account)
            val source = Source()
            val model = FeedViewModel(login.account.id, source, AccountSyncCoordinator())
            owner.put("feed", model)
            advanceUntilIdle()

            source.createError = IOException()
            var completed = false
            model.create(CreatePostRequest("Draft text")) { completed = true }
            advanceUntilIdle()
            assertFalse(completed)
            assertFalse(model.feed.value.publishing)
            assertNotNull(model.feed.value.error)

            source.createError = null
            model.create(CreatePostRequest("Draft text")) { completed = true }
            advanceUntilIdle()
            assertTrue(completed)
            assertFalse(model.feed.value.publishing)
        } finally { owner.clear(); Dispatchers.resetMain() }
    }

    @Test fun selectedTimelineOwnsPaginationAndPublishRefresh() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val owner = ViewModelStore()
        try {
            val store = MemoryStore(Session(login.account.id, login.token, ServerCapabilities()), login.account)
            val source = Source()
            val model = FeedViewModel(login.account.id, source, AccountSyncCoordinator())
            owner.put("feed", model)
            advanceUntilIdle()
            source.timelineCalls.clear()

            model.refresh(Timeline.Local)
            advanceUntilIdle()
            assertEquals(Timeline.Local, model.feed.value.timeline)
            assertEquals(listOf(Timeline.Local to null), source.timelineCalls)

            model.loadMore(Timeline.Home)
            advanceUntilIdle()
            assertEquals(listOf(Timeline.Local to null), source.timelineCalls)

            model.loadMore()
            advanceUntilIdle()
            assertEquals(listOf(Timeline.Local to null, Timeline.Local to "2"), source.timelineCalls)

            model.create(CreatePostRequest("Local post"))
            advanceUntilIdle()
            assertEquals(Timeline.Local, source.timelineCalls.last().first)
            assertEquals(Timeline.Local, model.feed.value.timeline)
        } finally { owner.clear(); Dispatchers.resetMain() }
    }

    @Test fun completingSignInLeavesOtherAccountSessionUntouched() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val owner = ViewModelStore()
        try {
            val existingLogin = LoginSession("https://existing.example", "existing-token", JSONObject("""{"id":"existing","username":"existing"}"""))
            val newLogin = LoginSession("https://new.example", "new-token", JSONObject("""{"id":"new","username":"new"}"""))
            val existingSession = Session(existingLogin.account.id, existingLogin.token, ServerCapabilities())
            val store = MemoryStore(existingSession, existingLogin.account)
            val model = AccountManager(store, auth(newLogin), StandardTestDispatcher(testScheduler))
            owner.put("isolated", model)
            advanceUntilIdle()

            model.signIn("https://new.example")
            advanceUntilIdle()
            model.callback("palustris://auth/misskey?session=session-id")
            advanceUntilIdle()

            assertEquals(existingSession, store.sessions[existingSession.accountId])
            assertEquals(newLogin.token, store.sessions[newLogin.account.id]?.token)
            assertEquals(setOf(existingLogin.account.id, newLogin.account.id), store.index.accounts.map { it.accountId }.toSet())
            assertEquals(listOf(existingSession.accountId), store.readAccountIds)
            assertEquals(listOf(newLogin.account.id), store.writtenAccountIds)
        } finally { owner.clear(); Dispatchers.resetMain() }
    }

    @Test fun addingAccountKeepsActiveSessionUntilCanceled() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val owner = ViewModelStore()
        try {
            val existingSession = Session(login.account.id, login.token, ServerCapabilities())
            val store = MemoryStore(existingSession, login.account)
            val model = AccountManager(store, auth(login), StandardTestDispatcher(testScheduler))
            owner.put("add", model)
            advanceUntilIdle()

            model.beginAddAccount()
            assertTrue(model.session.value.addingAccount)
            assertEquals(login.account.id, model.session.value.account?.id)
            model.signIn("https://new.example")
            advanceUntilIdle()

            assertTrue(model.session.value.pending)
            assertTrue(model.session.value.addingAccount)
            assertEquals(existingSession, model.activeSession.value)
            assertEquals(existingSession, store.sessions[existingSession.accountId])

            model.cancelSignIn()
            advanceUntilIdle()
            assertFalse(model.session.value.addingAccount)
            assertFalse(model.session.value.pending)
            assertEquals(login.account.id, model.session.value.account?.id)
            assertEquals(existingSession, model.activeSession.value)
            assertNull(store.pending)
        } finally { owner.clear(); Dispatchers.resetMain() }
    }
}
