package me.foxtails.palustris

import androidx.lifecycle.ViewModelStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import me.foxtails.palustris.data.auth.*
import me.foxtails.palustris.data.misskey.ApiFailure
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
    private class MemoryStore(var value: StoredLogin = StoredLogin()) : SessionStore {
        override fun read() = value
        override fun write(value: StoredLogin) { this.value = value }
        override fun clear() { value = StoredLogin() }
    }
    private class Source : SocialSource {
        override val capabilities = ServerCapabilities(timelines = setOf(Timeline.Home))
        var error: Exception? = null
        override suspend fun timeline(timeline: Timeline, cursor: String?): Page<Post> {
            error?.let { throw it }
            val account = Account(EntityId("example", "a"), "Alice", "@alice")
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
            val store = MemoryStore(StoredLogin(session = login))
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
            assertNull(store.value.session)
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
            assertNotNull(store.value.pending)
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
            assertNull(store.value.pending)
            assertNotNull(store.value.session)
        } finally { owner.clear(); Dispatchers.resetMain() }
    }
}
