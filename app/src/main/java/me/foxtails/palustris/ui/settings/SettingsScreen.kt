package me.foxtails.palustris.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import me.foxtails.palustris.R
import me.foxtails.palustris.domain.AppPreferences

@Composable
fun SettingsScreen(
    preferences: AppPreferences,
    onDisplay: () -> Unit,
    onNotifications: () -> Unit,
    onPrivacy: () -> Unit,
    onLanguage: () -> Unit,
) {
    SettingsRow(
        stringResource(R.string.settings_display),
        "${preferences.colorScheme.name}, ${preferences.textSize.name}",
        onDisplay,
        "settings_display",
    )
    SettingsSection(stringResource(R.string.settings_section_notifications)) {
        SettingsRow(stringResource(R.string.settings_notifications), stringResource(R.string.settings_notifications_summary), onNotifications, "settings_notifications")
    }
    SettingsSection(stringResource(R.string.settings_section_privacy)) {
        SettingsRow(stringResource(R.string.settings_privacy), stringResource(R.string.settings_privacy_summary), onPrivacy, "settings_privacy")
    }
    SettingsSection(stringResource(R.string.settings_section_language)) {
        SettingsRow(stringResource(R.string.settings_language), preferences.language.name, onLanguage, "settings_language")
    }
}
