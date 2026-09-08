package me.foxtails.palustris

import android.content.Intent
import androidx.core.net.toUri
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.EntityId
import me.foxtails.palustris.domain.Protocol
import me.foxtails.palustris.ui.notifications.NotificationLaunch
import me.foxtails.palustris.ui.notifications.NotificationLaunchRouter
import me.foxtails.palustris.ui.notifications.InMemoryNotificationLaunchStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class NotificationLaunchRouterTest {
    private val router = NotificationLaunchRouter()

    @Test
    fun pendingLaunchSurvivesRouterRecreation() {
        val store = InMemoryNotificationLaunchStore()
        val launch = NotificationLaunch(account, EntityId(account.connection.origin, "event"))
        NotificationLaunchRouter(store).accept(NotificationLaunchRouter.intentFor(launch))

        val restored = NotificationLaunchRouter(store)
        assertEquals(launch, restored.pending.value)
        restored.clear()
        assertNull(NotificationLaunchRouter(store).pending.value)
    }
    private val account = AccountId(Connection("https://example.org", Protocol.MISSKEY), "receiver")
    private val launch = NotificationLaunch(account, EntityId(account.connection.origin, "event"))

    @Test
    fun roundTripIntentPreservesOnlyValidatedAccountAndEventIdentity() {
        assertEquals(launch, router.parse(NotificationLaunchRouter.intentFor(launch)))
    }

    @Test
    fun accountAndEventSpecificUriPreventsPendingIntentIdentityCollisions() {
        val secondAccount = account.copy(localId = "receiver-b")
        val firstIntent = NotificationLaunchRouter.intentFor(launch)
        val secondIntent = NotificationLaunchRouter.intentFor(NotificationLaunch(secondAccount, launch.notificationId))

        assertNotEquals(firstIntent.data, secondIntent.data)
        assertEquals(launch, router.parse(firstIntent))
        assertNull(router.parse(firstIntent.apply {
            putExtra(NotificationLaunchRouter.EXTRA_ACCOUNT_LOCAL_ID, secondAccount.localId)
        }))
    }

    @Test
    fun authAndForeignNotificationUrisAreIgnored() {
        val auth = Intent(Intent.ACTION_VIEW, "palustris://auth/misskey".toUri())
        val foreign = NotificationLaunchRouter.intentFor(launch).apply {
            data = "palustris://notification/other".toUri()
        }

        assertNull(router.parse(auth))
        assertNull(router.parse(foreign))
    }
}
