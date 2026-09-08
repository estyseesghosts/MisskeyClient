package me.foxtails.palustris

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import me.foxtails.palustris.data.auth.AccountIndex
import me.foxtails.palustris.data.auth.AccountRef
import me.foxtails.palustris.data.auth.PendingLogin
import me.foxtails.palustris.data.auth.SessionStore
import me.foxtails.palustris.data.notifications.FileNotificationStore
import me.foxtails.palustris.data.notifications.InMemoryNotificationStore
import me.foxtails.palustris.data.notifications.NotificationRepository
import me.foxtails.palustris.data.notifications.push.PushRegistrationRepository
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.NotificationPushRegistrationState
import me.foxtails.palustris.domain.NotificationSyncToken
import me.foxtails.palustris.domain.Protocol
import me.foxtails.palustris.domain.ServerCapabilities
import me.foxtails.palustris.domain.Session
import me.foxtails.palustris.domain.PushRegistration
import me.foxtails.palustris.domain.ValidatedUrl
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PushRegistrationRepositoryTest {
    private val account = AccountId(Connection("https://push.example", Protocol.MASTODON), "receiver")
    private val session = Session(account, "session-token", ServerCapabilities())

    @Test
    fun callbackOwnerCanRehydrateARepositoryAfterProcessRestart() = runBlocking {
        val store = InMemoryNotificationStore()
        val first = NotificationRepository(store)
        val persistedToken = NotificationSyncToken(account, 7)
        first.activate(persistedToken)
        first.updatePushRegistration(persistedToken, PushRegistration(
            accountId = account,
            generation = persistedToken.generation,
            instanceName = "push-instance",
            endpoint = ValidatedUrl.https("https://push.example/endpoint"),
            state = NotificationPushRegistrationState.TemporarilyUnavailable,
        ))

        val restarted = NotificationRepository(store)
        val owner = PushRegistrationRepository(restarted, MemorySessionStore(session)).find("push-instance")

        assertNotNull(owner)
        assertEquals(0L, owner!!.token.generation)
        assertEquals(0L, restarted.currentToken(account)?.generation)
        assertEquals(persistedToken.generation, owner.registration.generation)
    }

    @Test
    fun confirmedServerEndpointSurvivesNotificationRepositoryRestart() = runBlocking {
        val account = AccountId(Connection("https://push-persistence.example", Protocol.MISSKEY), "receiver")
        val token = NotificationSyncToken(account, 1)
        val endpoint = ValidatedUrl.https("https://push.example/distributor")!!
        val confirmed = ValidatedUrl.https("https://push.example/server")!!
        val first = NotificationRepository(FileNotificationStore(ApplicationProvider.getApplicationContext()))
        first.activate(token)
        first.updatePushRegistration(token, PushRegistration(
            accountId = account,
            generation = token.generation,
            instanceName = "persisted-instance",
            endpoint = endpoint,
            serverEndpoint = confirmed,
            state = NotificationPushRegistrationState.Connected,
        ))

        val restarted = NotificationRepository(FileNotificationStore(ApplicationProvider.getApplicationContext()))
        restarted.activate(token)

        assertEquals(confirmed, restarted.pushRegistration(account)?.serverEndpoint)
        restarted.remove(account)
    }

    private class MemorySessionStore(initial: Session) : SessionStore {
        private val sessions = mutableMapOf(initial.accountId to initial)
        private var index = AccountIndex(accounts = listOf(
            AccountRef(initial.accountId, "@receiver", null, "Receiver"),
        ))

        override fun read(accountId: AccountId): Session? = sessions[accountId]

        override fun write(accountId: AccountId, session: Session) {
            sessions[accountId] = session
        }

        override fun delete(accountId: AccountId) {
            sessions.remove(accountId)
        }

        override fun readIndex(): AccountIndex = index

        override fun writeIndex(index: AccountIndex) {
            this.index = index
        }

        override fun clear() {
            sessions.clear()
            index = AccountIndex()
        }

        override fun writePushInstance(accountId: AccountId, instanceName: String) {
            sessions[accountId]?.let { sessions[accountId] = it.copy(pushInstanceName = instanceName) }
        }

        override fun readPending(): PendingLogin? = null

        override fun writePending(pending: PendingLogin) = Unit

        override fun clearPending() = Unit

        override fun writeProfile(accountId: AccountId, profile: JSONObject) = Unit
    }
}
