package me.foxtails.palustris.ui.settings

import me.foxtails.palustris.data.auth.AccountRef
import me.foxtails.palustris.domain.AccountId

/**
 * Pure account-route gating for the settings overlay.
 *
 * The overlay keeps these decisions out of its composition body so unit tests
 * can prove them without Hilt or activity recreation. An account route is valid
 * only while its exact account exists. Before the account index is restored, an
 * empty list is not proof that the account was removed, so the pending route is
 * kept. A removed account remaps to the route safe parent, never to the active
 * account.
 */
fun resolveAccountRoute(
    route: SettingsRoute,
    accounts: List<AccountRef>,
    accountsReady: Boolean,
): SettingsRoute {
    if (!accountsReady) return route
    val routeAccountId = route.accountIdOrNull() ?: return route
    if (accounts.any { it.accountId == routeAccountId }) return route
    return route.safeParent()
}

/**
 * Returns the notification account to construct while the route is valid.
 * Null before the account index is restored or after the account was removed.
 */
fun notificationAccountFor(
    route: SettingsRoute,
    accounts: List<AccountRef>,
    accountsReady: Boolean,
): AccountId? {
    val requested = (route as? SettingsRoute.NotificationAccount)?.accountId ?: return null
    if (!accountsReady) return null
    return requested.takeIf { accounts.any { it.accountId == requested } }
}

/**
 * Returns the moderation route to construct while the route is valid.
 * Null before the account index is restored or after the account was removed.
 */
fun moderationRouteFor(
    route: SettingsRoute,
    accounts: List<AccountRef>,
    accountsReady: Boolean,
): SettingsRoute.Moderation? {
    val requested = (route as? SettingsRoute.Moderation) ?: return null
    if (!accountsReady) return null
    return requested.takeIf { accounts.any { it.accountId == requested.accountId } }
}

private fun SettingsRoute.accountIdOrNull(): AccountId? = when (this) {
    is SettingsRoute.NotificationAccount -> accountId
    is SettingsRoute.Moderation -> accountId
    else -> null
}
