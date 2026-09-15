package me.foxtails.palustris

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import me.foxtails.palustris.data.auth.AccountIndex
import me.foxtails.palustris.data.auth.AccountRef
import me.foxtails.palustris.data.auth.AuthGateway
import me.foxtails.palustris.data.auth.LoginSession
import me.foxtails.palustris.data.auth.PendingLogin
import me.foxtails.palustris.data.auth.SessionStore
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.EntityId
import me.foxtails.palustris.domain.ServerCapabilities
import me.foxtails.palustris.domain.Session
import me.foxtails.palustris.ui.AccountManager
import me.foxtails.palustris.ui.navigation.AppRoute
import me.foxtails.palustris.ui.notifications.NotificationLaunch
import me.foxtails.palustris.ui.notifications.NotificationLaunchHost
import me.foxtails.palustris.ui.notifications.NotificationLaunchRouter
import me.foxtails.palustris.ui.notifications.NotificationRouteResolver
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A launch is acknowledged only when the receiving shell accepts its route. An undelivered
 * launch stays pending. A missing account routes to the recoverable unavailable state. A
 * launch for another account requests the switch and stays pending across it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class NotificationLaunchHostTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val mainDispatcher = UnconfinedTestDispatcher()

    private val loginA = LoginSession(
        "https://a.example",
        "token-a",
        JSONObject("""{"id":"a","username":"alice"}"""),
    )
    private val loginB = LoginSession(
        "https://b.example",
        "token-b",
        JSONObject("""{"id":"b","username":"bob"}"""),
    )

    private class MemoryStore : SessionStore {
        val sessions = mutableMapOf<AccountId, Session>()
        var index = AccountIndex()

        override fun read(accountId: AccountId): Session? = sessions[accountId]
        override fun write(accountId: AccountId, session: Session) { sessions[accountId] = session }
        override fun delete(accountId: AccountId) { sessions.remove(accountId) }
        override fun readIndex() = index
        override fun writeIndex(index: AccountIndex) { this.index = index }
        override fun clear() { sessions.clear(); index = AccountIndex() }
    }

    private val auth = object : AuthGateway {
        override suspend fun prepare(input: String): PendingLogin = TODO("not used")
        override fun browserUrl(pending: PendingLogin): String = TODO("not used")
        override suspend fun complete(pending: PendingLogin): LoginSession = TODO("not used")
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun ref(login: LoginSession) = AccountRef(
        login.account.id,
        login.account.handle,
        login.account.avatarUrl,
        login.account.displayName,
    )

    private fun launchFor(login: LoginSession, event: String) =
        NotificationLaunch(login.account.id, EntityId(login.account.id.connection.origin, event))

    private fun manager(store: MemoryStore) = AccountManager(store, auth, mainDispatcher)

    @Test
    fun deliveredLaunchIsAcknowledgedWhenAccepted() {
        val store = MemoryStore()
        val router = NotificationLaunchRouter()
        val launch = launchFor(loginA, "event")
        router.accept(NotificationLaunchRouter.intentFor(launch))
        val routes = mutableListOf<AppRoute>()

        compose.activity.runOnUiThread {
            compose.activity.setContent {
                NotificationLaunchHost(
                    router = router,
                    accountManager = manager(store),
                    accounts = listOf(ref(loginA)),
                    starting = false,
                    activeAccountId = loginA.account.id,
                    onRoute = { route -> routes += route; true },
                )
            }
        }
        compose.waitForIdle()

        assertEquals(listOf(NotificationRouteResolver.detail(launch.accountId, launch.notificationId)), routes)
        assertNull(router.pending.value)
    }

    @Test
    fun undeliveredLaunchIsNotAcknowledged() {
        val store = MemoryStore()
        val router = NotificationLaunchRouter()
        val launch = launchFor(loginA, "event")
        router.accept(NotificationLaunchRouter.intentFor(launch))
        val routes = mutableListOf<AppRoute>()

        compose.activity.runOnUiThread {
            compose.activity.setContent {
                NotificationLaunchHost(
                    router = router,
                    accountManager = manager(store),
                    accounts = listOf(ref(loginA)),
                    starting = false,
                    activeAccountId = loginA.account.id,
                    onRoute = { route -> routes += route; false },
                )
            }
        }
        compose.waitForIdle()

        // The route is offered but the launch stays pending and unacknowledged.
        assertEquals(listOf(NotificationRouteResolver.detail(launch.accountId, launch.notificationId)), routes)
        assertEquals(launch, router.pending.value)
    }

    @Test
    fun missingAccountRoutesUnavailableAndClears() {
        val store = MemoryStore()
        val router = NotificationLaunchRouter()
        val launch = launchFor(loginB, "event")
        router.accept(NotificationLaunchRouter.intentFor(launch))
        val routes = mutableListOf<AppRoute>()

        compose.activity.runOnUiThread {
            compose.activity.setContent {
                NotificationLaunchHost(
                    router = router,
                    accountManager = manager(store),
                    accounts = listOf(ref(loginA)),
                    starting = false,
                    activeAccountId = loginA.account.id,
                    onRoute = { route -> routes += route; true },
                )
            }
        }
        compose.waitForIdle()

        assertEquals(listOf(AppRoute.AccountUnavailable(launch.accountId, launch.notificationId)), routes)
        assertNull(router.pending.value)
    }

    @Test
    fun launchForAnotherAccountSwitchesAndStaysPending() {
        val store = MemoryStore()
        store.sessions[loginA.account.id] = Session(loginA.account.id, loginA.token, ServerCapabilities())
        store.sessions[loginB.account.id] = Session(loginB.account.id, loginB.token, ServerCapabilities())
        store.index = AccountIndex(
            accounts = listOf(ref(loginA), ref(loginB)),
            activeAccountId = loginA.account.id,
        )
        val router = NotificationLaunchRouter()
        val launch = launchFor(loginB, "event")
        router.accept(NotificationLaunchRouter.intentFor(launch))
        val routes = mutableListOf<AppRoute>()
        val accountManager = manager(store)

        compose.activity.runOnUiThread {
            compose.activity.setContent {
                NotificationLaunchHost(
                    router = router,
                    accountManager = accountManager,
                    accounts = listOf(ref(loginA), ref(loginB)),
                    starting = false,
                    activeAccountId = loginA.account.id,
                    onRoute = { route -> routes += route; true },
                )
            }
        }
        compose.waitForIdle()

        // The switch is requested and the launch stays pending across it.
        assertEquals(loginB.account.id, accountManager.session.value.account?.id)
        assertEquals(launch, router.pending.value)
        assertTrue(routes.isEmpty())
    }
}
