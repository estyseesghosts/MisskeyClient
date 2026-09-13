package me.foxtails.palustris.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import me.foxtails.palustris.R

@Composable
fun PrivacySettingsScreen(
    cleanTrackingParameters: Boolean,
    onTrackingCleanup: (Boolean) -> Unit,
    onSelectAccount: () -> Unit,
    onContentWarnings: () -> Unit,
    onPosting: () -> Unit,
) {
    SettingsRow(
        stringResource(R.string.settings_tracking_cleanup),
        stringResource(R.string.settings_tracking_cleanup_summary),
        { onTrackingCleanup(!cleanTrackingParameters) },
    )
    SettingsRow(
        stringResource(R.string.settings_content_warnings),
        stringResource(R.string.settings_content_warnings_summary),
        onContentWarnings,
    )
    SettingsRow(
        stringResource(R.string.settings_account_privacy),
        stringResource(R.string.settings_account_privacy_summary),
        onSelectAccount,
    )
    SettingsRow(
        stringResource(R.string.settings_posting),
        stringResource(R.string.settings_posting_summary),
        onPosting,
    )
}
