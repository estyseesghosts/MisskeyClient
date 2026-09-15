package me.foxtails.palustris.ui.notifications

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.foxtails.palustris.data.auth.AccountRef
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.ui.AccountManager
import me.foxtails.palustris.ui.navigation.AppRoute

/**
 * Owns the notification launch handoff.
 *
 * A launch is delivered only after its receiving account is the active account. The launch is
 * acknowledged only when the receiving shell accepts its route, so an undelivered launch is
 * never acknowledged. A missing receiving account routes to the recoverable unavailable
 * state. A failed account switch resolves through the accounts update, which reruns this
 * effect and routes the same unavailable state.
 */
@Composable
fun NotificationLaunchHost(
    router: NotificationLaunchRouter,
    accountManager: AccountManager,
    accounts: List<AccountRef>,
    starting: Boolean,
    activeAccountId: AccountId?,
    onRoute: (AppRoute) -> Boolean,
) {
    val pending by router.pending.collectAsStateWithLifecycle()
    // The effect outlives recompositions, so it always calls the latest route callback.
    val latestOnRoute by rememberUpdatedState(onRoute)
    LaunchedEffect(pending, starting, activeAccountId, accounts) {
        val launch = pending ?: return@LaunchedEffect
        if (starting) return@LaunchedEffect
        val receivingAccountExists = accounts.any { it.accountId == launch.accountId }
        if (!receivingAccountExists) {
            if (latestOnRoute(AppRoute.AccountUnavailable(launch.accountId, launch.notificationId))) {
                router.clear(launch)
            }
            return@LaunchedEffect
        }
        if (activeAccountId != launch.accountId) {
            // Deliver the detail route only after the receiving account becomes active.
            // The launch stays pending across the switch, so a newer launch is not dropped
            // by an older account switch.
            accountManager.switchAccount(launch.accountId)
            return@LaunchedEffect
        }
        if (latestOnRoute(NotificationRouteResolver.detail(launch.accountId, launch.notificationId))) {
            router.clear(launch)
        }
    }
}
