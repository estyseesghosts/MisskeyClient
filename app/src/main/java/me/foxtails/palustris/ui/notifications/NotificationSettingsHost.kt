package me.foxtails.palustris.ui.notifications

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.NotificationCategory
import me.foxtails.palustris.ui.shell.NotificationSettingsContract

/**
 * Owns notification settings for one explicit target account.
 *
 * The account is always explicit, so the active account and a settings-selected account cannot
 * share one presentation. The shell receives only the narrow [NotificationSettingsContract].
 */
@Composable
fun NotificationSettingsHost(
    accountId: AccountId,
    sessionGeneration: Long,
): NotificationSettingsContract {
    val model = hiltViewModel<NotificationSettingsViewModel, NotificationSettingsViewModel.Factory>(
        key = "notification-settings-$accountId-$sessionGeneration",
        creationCallback = { factory -> factory.create(accountId) },
    )
    val state by model.state.collectAsStateWithLifecycle()
    val actions = remember(model) {
        object : NotificationSettingsContract.Actions {
            override fun setAlertsEnabled(enabled: Boolean) { model.setAlertsEnabled(enabled) }
            override fun setShowPreviews(enabled: Boolean) { model.setShowPreviews(enabled) }
            override fun setPeriodicFallback(enabled: Boolean) { model.setPeriodicFallbackEnabled(enabled) }
            override fun setQuietHours(enabled: Boolean) { model.setQuietHours(enabled) }
            override fun setCategoryEnabled(category: NotificationCategory, enabled: Boolean) {
                model.setCategoryEnabled(category, enabled)
            }
            override fun runLocalTest() { model.runLocalPresentationTest() }
            override fun retryStorage() { model.retryStorage() }
            override fun resetStorage() { model.resetStorage() }
            override fun retryRegistration() { model.retryRegistration() }
            override fun refreshPermission() { model.refreshPermission() }
            override fun refreshDistributors() { model.refreshDistributors() }
            override fun selectDistributor(packageName: String) { model.selectDistributor(packageName) }
            override fun runPushConnectionTest() { model.runPushConnectionTest() }
        }
    }
    return remember(accountId, state, actions) {
        NotificationSettingsContract(accountId = accountId, state = state, actions = actions)
    }
}
