package me.foxtails.palustris.ui.notifications

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
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

private const val PREFERENCES = "notification_launch"
private const val KEY_ORIGIN = "origin"
private const val KEY_ACCOUNT_LOCAL_ID = "account_local_id"
private const val KEY_PROTOCOL = "protocol"
private const val KEY_NOTIFICATION_ID = "notification_id"

internal interface NotificationLaunchStore {
    fun read(): NotificationLaunch?
    fun write(launch: NotificationLaunch)
    fun clear()
}

private class SharedPreferencesNotificationLaunchStore(
    context: Context,
) : NotificationLaunchStore {
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    override fun read(): NotificationLaunch? {
        val origin = preferences.getString(KEY_ORIGIN, null) ?: return null
        val localId = preferences.getString(KEY_ACCOUNT_LOCAL_ID, null) ?: return null
        val protocol = preferences.getString(KEY_PROTOCOL, null) ?: return null
        val notificationId = preferences.getString(KEY_NOTIFICATION_ID, null) ?: return null
        return runCatching {
            val connection = Connection(origin, Protocol.valueOf(protocol))
            NotificationLaunch(AccountId(connection, localId), EntityId(origin, notificationId))
        }.getOrNull()
    }

    override fun write(launch: NotificationLaunch) {
        preferences.edit()
            .putString(KEY_ORIGIN, launch.accountId.connection.origin)
            .putString(KEY_ACCOUNT_LOCAL_ID, launch.accountId.localId)
            .putString(KEY_PROTOCOL, launch.accountId.connection.protocol.name)
            .putString(KEY_NOTIFICATION_ID, launch.notificationId.value)
            .apply()
    }

    override fun clear() {
        preferences.edit().clear().apply()
    }
}

internal class InMemoryNotificationLaunchStore : NotificationLaunchStore {
    private var value: NotificationLaunch? = null

    override fun read(): NotificationLaunch? = value

    override fun write(launch: NotificationLaunch) {
        value = launch
    }

    override fun clear() {
        value = null
    }
}

/** Parses only the app-owned notification intent contract; payload URLs are never trusted here. */
@Singleton
class NotificationLaunchRouter internal constructor(
    private val store: NotificationLaunchStore,
) {
    @Inject
    constructor(@ApplicationContext context: Context) : this(SharedPreferencesNotificationLaunchStore(context))

    constructor() : this(InMemoryNotificationLaunchStore())

    private val _pending = MutableStateFlow(store.read())
    val pending: StateFlow<NotificationLaunch?> = _pending.asStateFlow()

    fun accept(intent: Intent) {
        parse(intent)?.let {
            store.write(it)
            _pending.value = it
        }
    }

    fun clear() {
        store.clear()
        _pending.value = null
    }

    fun parse(intent: Intent): NotificationLaunch? {
        return parse(intent, Intent.ACTION_VIEW)
    }

    fun parseDismiss(intent: Intent): NotificationLaunch? = parse(intent, ACTION_DISMISS)

    private fun parse(intent: Intent, expectedAction: String): NotificationLaunch? {
        if (intent.action != expectedAction) return null
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
        const val ACTION_DISMISS = "me.foxtails.palustris.action.NOTIFICATION_DISMISSED"
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
