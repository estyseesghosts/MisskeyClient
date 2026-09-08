package me.foxtails.palustris.ui.notifications

import android.content.Intent
import androidx.core.net.toUri
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.EntityId
import me.foxtails.palustris.domain.Protocol

data class NotificationLaunch(
    val accountId: AccountId,
    val notificationId: EntityId,
)

/** Parses only the app-owned notification intent contract; payload URLs are never trusted here. */
@Singleton
class NotificationLaunchRouter @Inject constructor() {
    private val _pending = MutableStateFlow<NotificationLaunch?>(null)
    val pending: StateFlow<NotificationLaunch?> = _pending.asStateFlow()

    fun accept(intent: Intent) {
        parse(intent)?.let { _pending.value = it }
    }

    fun clear() {
        _pending.value = null
    }

    fun parse(intent: Intent): NotificationLaunch? {
        if (intent.action != Intent.ACTION_VIEW) return null
        val origin = intent.getStringExtra(EXTRA_ORIGIN)?.trim().orEmpty()
        val localId = intent.getStringExtra(EXTRA_ACCOUNT_LOCAL_ID)?.trim().orEmpty()
        val protocol = intent.getStringExtra(EXTRA_PROTOCOL)?.trim().orEmpty()
        val notificationId = intent.getStringExtra(EXTRA_NOTIFICATION_ID)?.trim().orEmpty()
        if (origin.isBlank() || localId.isBlank() || protocol.isBlank() || notificationId.isBlank()) return null
        val uri = intent.data ?: return null
        if (uri.scheme != SCHEME || uri.host != HOST || uri.pathSegments.size != 2 || uri.pathSegments.first() != PATH_SEGMENT) return null
        val connection = runCatching { Connection(origin, Protocol.valueOf(protocol)) }.getOrNull() ?: return null
        if (connection.origin != origin) return null
        val accountId = AccountId(connection, localId)
        val launch = NotificationLaunch(accountId, EntityId(origin, notificationId))
        return launch.takeIf { uri.pathSegments.last() == launchKey(it) }
    }

    companion object {
        const val EXTRA_ORIGIN = "me.foxtails.palustris.notification.origin"
        const val EXTRA_ACCOUNT_LOCAL_ID = "me.foxtails.palustris.notification.account"
        const val EXTRA_PROTOCOL = "me.foxtails.palustris.notification.protocol"
        const val EXTRA_NOTIFICATION_ID = "me.foxtails.palustris.notification.id"
        const val SCHEME = "palustris"
        const val HOST = "notification"
        const val PATH = "/open"
        private const val PATH_SEGMENT = "open"

        fun intentFor(launch: NotificationLaunch): Intent = Intent(Intent.ACTION_VIEW).apply {
            data = "$SCHEME://$HOST$PATH/${launchKey(launch)}".toUri()
            putExtra(EXTRA_ORIGIN, launch.accountId.connection.origin)
            putExtra(EXTRA_ACCOUNT_LOCAL_ID, launch.accountId.localId)
            putExtra(EXTRA_PROTOCOL, launch.accountId.connection.protocol.name)
            putExtra(EXTRA_NOTIFICATION_ID, launch.notificationId.value)
        }

        private fun launchKey(launch: NotificationLaunch): String = MessageDigest.getInstance("SHA-256")
            .digest(("${launch.accountId.connection.origin}\u0000${launch.accountId.connection.protocol}\u0000" +
                "${launch.accountId.localId}\u0000${launch.notificationId.value}").toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }
    }
}
