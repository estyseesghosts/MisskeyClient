package me.foxtails.palustris.ui.shell

import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.NotificationCategory
import me.foxtails.palustris.ui.notifications.NotificationSettingsUiState

/**
 * Notification settings for one explicit target account.
 *
 * The target account is part of the contract. A contract must never carry a second account's
 * state under the active account label. [Empty] is an inert preview value.
 */
data class NotificationSettingsContract(
    val accountId: AccountId?,
    val state: NotificationSettingsUiState,
    val actions: Actions,
) {
    /** Notification-settings commands owned by the notification settings owner. */
    interface Actions {
        fun setAlertsEnabled(enabled: Boolean)
        fun setShowPreviews(enabled: Boolean)
        fun setPeriodicFallback(enabled: Boolean)
        fun setQuietHours(enabled: Boolean)
        fun setCategoryEnabled(category: NotificationCategory, enabled: Boolean)
        fun runLocalTest()
        fun retryRegistration()
        fun refreshPermission()
        fun refreshDistributors()
        fun selectDistributor(packageName: String)
        fun runPushConnectionTest()
    }

    companion object {
        val Empty = NotificationSettingsContract(
            accountId = null,
            state = NotificationSettingsUiState(),
            actions = NotificationSettingsEmptyActions,
        )
    }
}

private object NotificationSettingsEmptyActions : NotificationSettingsContract.Actions {
    override fun setAlertsEnabled(enabled: Boolean) = Unit
    override fun setShowPreviews(enabled: Boolean) = Unit
    override fun setPeriodicFallback(enabled: Boolean) = Unit
    override fun setQuietHours(enabled: Boolean) = Unit
    override fun setCategoryEnabled(category: NotificationCategory, enabled: Boolean) = Unit
    override fun runLocalTest() = Unit
    override fun retryRegistration() = Unit
    override fun refreshPermission() = Unit
    override fun refreshDistributors() = Unit
    override fun selectDistributor(packageName: String) = Unit
    override fun runPushConnectionTest() = Unit
}
