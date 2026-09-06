package me.foxtails.palustris

import androidx.lifecycle.ViewModelStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import me.foxtails.palustris.data.auth.*
import me.foxtails.palustris.data.misskey.ApiFailure
import me.foxtails.palustris.data.misskey.MisskeyErrorMapper
import me.foxtails.palustris.domain.*
import me.foxtails.palustris.ui.SessionViewModel
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
        var index = initialAccount?.let { account ->
            AccountIndex(accounts = listOf(AccountRef(account.id, account.handle, account.avatarUrl, account.displayName)), activeAccountId = account.id)
        } ?: AccountIndex()

        override fun read(accountId: AccountId) = sessions[accountId]
        override fun write(accountId: AccountId, session: Session) { sessions[accountId] = session }
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
        override suspend fun timeline(timeline: Timeline, cursor: String?): Page<Post> {
            error?.let { throw MisskeyErrorMapper.map(it) }
            val account = Account(AccountId(Connection("https://example.org", Protocol.MISSKEY), "a"), "Alice", "@alice")
            fun post(id: String) = Post(EntityId("example", id), account, "Text", 0, Audience.Public)
            return if (cursor == null) Page(listOf(post("2")), "2") else Page(listOf(post("2"), post("1")), null)
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
            val model = SessionViewModel(store, auth(login), { source }, StandardTestDispatcher(testScheduler))
            owner.put("session", model)
            advanceUntilIdle()
            assertEquals("@alice@example.org", model.session.value.account?.handle)
            assertEquals(1, model.feed.value.posts.size)
            model.loadMore(); advanceUntilIdle()
            assertEquals(listOf("2", "1"), model.feed.value.posts.map { it.id.value })
            source.error = IOException()
            model.refresh(); advanceUntilIdle()
            assertEquals(2, model.feed.value.posts.size)
            assertNotNull(model.feed.value.error)
            source.error = null
            model.refresh(); advanceUntilIdle()
            assertNull(model.feed.value.error)
            source.error = ApiFailure(401)
            model.refresh(); advanceUntilIdle()
            assertTrue(model.feed.value.needsSignIn)
            model.signOut(); advanceUntilIdle()
            assertNull(store.storedSession)
            assertNull(model.session.value.account)
            assertTrue(model.feed.value.posts.isEmpty())
        } finally { owner.clear(); Dispatchers.resetMain() }
    }

    @Test fun pendingAuthSurvivesRestartAndInvalidCallbackDoesNotConnect() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val owner = ViewModelStore()
        try {
            val store = MemoryStore()
            val result = login
            val dispatcher = StandardTestDispatcher(testScheduler)
            val first = SessionViewModel(store, auth(result), { Source() }, dispatcher)
            owner.put("first", first)
            advanceUntilIdle()
            first.signIn("https://example.org"); advanceUntilIdle()
            assertNotNull(store.pending)
            val restored = SessionViewModel(store, auth(result), { Source() }, dispatcher)
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

    @Test fun completingSignInLeavesOtherAccountSessionUntouched() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val owner = ViewModelStore()
        try {
            val existingLogin = LoginSession("https://existing.example", "existing-token", JSONObject("""{"id":"existing","username":"existing"}"""))
            val newLogin = LoginSession("https://new.example", "new-token", JSONObject("""{"id":"new","username":"new"}"""))
            val existingSession = Session(existingLogin.account.id, existingLogin.token, ServerCapabilities())
            val store = MemoryStore(existingSession, existingLogin.account)
            val model = SessionViewModel(store, auth(newLogin), { Source() }, StandardTestDispatcher(testScheduler))
            owner.put("isolated", model)
            advanceUntilIdle()

            model.signIn("https://new.example")
            advanceUntilIdle()
            model.callback("palustris://auth/misskey?session=session-id")
            advanceUntilIdle()

            assertEquals(existingSession, store.sessions[existingSession.accountId])
            assertEquals(newLogin.token, store.sessions[newLogin.account.id]?.token)
            assertEquals(setOf(existingLogin.account.id, newLogin.account.id), store.index.accounts.map { it.accountId }.toSet())
        } finally { owner.clear(); Dispatchers.resetMain() }
    }
}
