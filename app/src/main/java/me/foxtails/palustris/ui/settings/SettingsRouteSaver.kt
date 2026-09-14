package me.foxtails.palustris.ui.settings

import androidx.compose.runtime.saveable.listSaver
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.Protocol

/**
 * Saves only stable route identifiers and required account identity fields.
 *
 * An account route carries connection origin, protocol, and local account ID together, because
 * a local ID alone is not unique across servers. Views, sources, callbacks, and secrets are
 * never saved. A malformed or unknown value restores to [SettingsRoute.Main].
 */
val SettingsRouteSaver = listSaver<SettingsRoute, String>(
    save = { route -> encodeSettingsRoute(route) },
    restore = { values -> restoreSettingsRoute(values) },
)

/** Restores a saved route, or [SettingsRoute.Main] when the value is unknown or malformed. */
internal fun restoreSettingsRoute(values: List<*>): SettingsRoute =
    decodeSettingsRoute(values) ?: SettingsRoute.Main

internal fun encodeSettingsRoute(route: SettingsRoute): List<String> = when (route) {
    SettingsRoute.Main -> listOf("Main")
    SettingsRoute.Display -> listOf("Display")
    SettingsRoute.Notifications -> listOf("Notifications")
    SettingsRoute.Privacy -> listOf("Privacy")
    SettingsRoute.Language -> listOf("Language")
    SettingsRoute.ContentWarnings -> listOf("ContentWarnings")
    SettingsRoute.Posting -> listOf("Posting")
    SettingsRoute.PrivacyAccounts -> listOf("PrivacyAccounts")
    is SettingsRoute.NotificationAccount -> listOf(
        "NotificationAccount",
        route.accountId.connection.origin,
        route.accountId.connection.protocol.name,
        route.accountId.localId,
    )
    is SettingsRoute.Moderation -> listOf(
        "Moderation",
        route.accountId.connection.origin,
        route.accountId.connection.protocol.name,
        route.accountId.localId,
        route.kind.name,
    )
}

internal fun decodeSettingsRoute(values: List<*>): SettingsRoute? {
    return when (val type = values.firstOrNull() as? String) {
        null -> null
        "Main" -> SettingsRoute.Main
        "Display" -> SettingsRoute.Display
        "Notifications" -> SettingsRoute.Notifications
        "Privacy" -> SettingsRoute.Privacy
        "Language" -> SettingsRoute.Language
        "ContentWarnings" -> SettingsRoute.ContentWarnings
        "Posting" -> SettingsRoute.Posting
        "PrivacyAccounts" -> SettingsRoute.PrivacyAccounts
        "NotificationAccount" -> decodeAccount(values)?.let(SettingsRoute::NotificationAccount)
        "Moderation" -> {
            val accountId = decodeAccount(values) ?: return null
            val kind = (values.getOrNull(4) as? String)
                ?.let { runCatching { ModerationKind.valueOf(it) }.getOrNull() }
                ?: return null
            SettingsRoute.Moderation(accountId, kind)
        }
        else -> null
    }
}

private fun decodeAccount(values: List<*>): AccountId? {
    val origin = values.getOrNull(1) as? String ?: return null
    val protocol = (values.getOrNull(2) as? String)
        ?.let { runCatching { Protocol.valueOf(it) }.getOrNull() }
        ?: return null
    val localId = values.getOrNull(3) as? String ?: return null
    val connection = Connection(origin, protocol)
    if (!connection.isValid()) return null
    return AccountId(connection, localId)
}

/** The safe parent page for a route whose target account is unavailable. */
internal fun SettingsRoute.safeParent(): SettingsRoute = when (this) {
    is SettingsRoute.NotificationAccount -> SettingsRoute.Notifications
    is SettingsRoute.Moderation -> SettingsRoute.PrivacyAccounts
    else -> SettingsRoute.Main
}
