package me.foxtails.palustris.ui.notifications

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.foxtails.palustris.data.auth.AccountRef
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.ui.AccountManager
import me.foxtails.palustris.ui.navigation.AppRoute

/**
 * Owns the notification launch handoff.
 *
 * A launch is delivered only after its receiving account is the active account. The launch is
 * acknowledged only when its route is accepted, so a newer launch is not dropped by an older
 * account switch.
 */
@Composable
fun NotificationLaunchHost(
    router: NotificationLaunchRouter,
    accountManager: AccountManager,
    accounts: List<AccountRef>,
    starting: Boolean,
    activeAccountId: AccountId?,
    onRoute: (AppRoute?) -> Unit,
) {
    val pending by router.pending.collectAsStateWithLifecycle()
    LaunchedEffect(pending, starting, activeAccountId, accounts) {
        val launch = pending ?: return@LaunchedEffect
        if (starting) return@LaunchedEffect
        val receivingAccountExists = accounts.any { it.accountId == launch.accountId }
        if (!receivingAccountExists) {
            onRoute(AppRoute.AccountUnavailable(launch.accountId, launch.notificationId))
            router.clear(launch)
            return@LaunchedEffect
        }
        if (activeAccountId != launch.accountId) {
            // Deliver the detail route only after the receiving account becomes active.
            accountManager.switchAccount(launch.accountId)
            return@LaunchedEffect
        }
        onRoute(NotificationRouteResolver.detail(launch.accountId, launch.notificationId))
        router.clear(launch)
    }
}
