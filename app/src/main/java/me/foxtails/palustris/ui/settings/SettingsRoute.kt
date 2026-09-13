package me.foxtails.palustris.ui.settings

import me.foxtails.palustris.domain.AccountId

sealed interface SettingsRoute {
    data object Main : SettingsRoute
    data object Display : SettingsRoute
    data object Notifications : SettingsRoute
    data class NotificationAccount(val accountId: AccountId) : SettingsRoute
    data object Privacy : SettingsRoute
    data object Language : SettingsRoute
    data object ContentWarnings : SettingsRoute
    data object Posting : SettingsRoute
    data object PrivacyAccounts : SettingsRoute
    data class Moderation(val accountId: AccountId, val kind: ModerationKind) : SettingsRoute
}

enum class ModerationKind { Blocked, Muted, Hashtags }
