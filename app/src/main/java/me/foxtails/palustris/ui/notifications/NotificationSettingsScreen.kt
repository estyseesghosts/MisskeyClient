package me.foxtails.palustris.ui.notifications

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.foxtails.palustris.R
import me.foxtails.palustris.domain.NotificationCategory
import me.foxtails.palustris.domain.NotificationPushRegistrationState
import me.foxtails.palustris.domain.PushRegistrationFailureReason

@Composable
fun NotificationSettingsScreen(
    state: NotificationSettingsUiState,
    onAlertsEnabled: (Boolean) -> Unit = {},
    onShowPreviews: (Boolean) -> Unit = {},
    onPeriodicFallback: (Boolean) -> Unit = {},
    onQuietHours: (Boolean) -> Unit = {},
    onCategoryChanged: (NotificationCategory, Boolean) -> Unit = { _, _ -> },
    onRunLocalTest: () -> Unit = {},
    onRetryRegistration: () -> Unit = {},
    onRefreshDistributors: () -> Unit = {},
    onSelectDistributor: (String) -> Unit = {},
    onRunPushConnectionTest: () -> Unit = {},
    onPermissionChanged: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { onPermissionChanged() }
    Column(
        modifier
            .testTag("notification_settings_form")
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        SettingSwitch(
            title = stringResource(R.string.notifications_settings_alerts),
            subtitle = stringResource(R.string.notifications_settings_alerts_subtitle),
            checked = state.settings.alertsEnabled,
            enabled = !state.saving,
            onCheckedChange = onAlertsEnabled,
        )
        SettingSwitch(
            title = stringResource(R.string.notifications_settings_previews),
            subtitle = stringResource(R.string.notifications_settings_previews_subtitle),
            checked = state.settings.showPreviews,
            enabled = !state.saving,
            onCheckedChange = onShowPreviews,
        )
        SettingSwitch(
            title = stringResource(R.string.notifications_settings_quiet_hours),
            subtitle = stringResource(R.string.notifications_settings_quiet_hours_subtitle),
            checked = state.settings.quietHoursStartMinutes != null,
            enabled = !state.saving,
            onCheckedChange = onQuietHours,
        )
        SettingSwitch(
            title = stringResource(R.string.notifications_settings_periodic),
            subtitle = stringResource(R.string.notifications_settings_periodic_subtitle),
            checked = state.settings.periodicFallbackEnabled,
            enabled = !state.saving,
            onCheckedChange = onPeriodicFallback,
        )
        HorizontalDivider(Modifier.padding(vertical = 12.dp))
        Text(stringResource(R.string.notifications_settings_categories), style = MaterialTheme.typography.titleMedium)
        listOf(
            NotificationCategory.Mentions to R.string.notifications_settings_mentions,
            NotificationCategory.Replies to R.string.notifications_settings_replies,
            NotificationCategory.Quotes to R.string.notifications_settings_quotes,
            NotificationCategory.Social to R.string.notifications_settings_social,
            NotificationCategory.Polls to R.string.notifications_settings_polls,
            NotificationCategory.System to R.string.notifications_settings_system,
        ).forEach { (category, label) ->
            SettingSwitch(
                title = stringResource(label),
                subtitle = null,
                checked = NotificationCategory.All in state.settings.categories || category in state.settings.categories,
                enabled = !state.saving,
                onCheckedChange = { enabled -> onCategoryChanged(category, enabled) },
            )
        }
        HorizontalDivider(Modifier.padding(vertical = 12.dp))
        Text(stringResource(R.string.notifications_settings_delivery), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.notifications_push_provider), style = MaterialTheme.typography.titleMedium)
        when {
            state.distributorLoading -> Text(stringResource(R.string.notifications_push_registering_distributor))
            state.availableDistributors.isEmpty() -> Text(stringResource(R.string.notifications_push_no_provider))
            else -> state.availableDistributors.forEach { distributor ->
                Row(
                    Modifier.fillMaxWidth().clickable { onSelectDistributor(distributor.packageName) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = state.selectedDistributor == distributor.packageName,
                        onClick = { onSelectDistributor(distributor.packageName) },
                        enabled = !state.saving,
                    )
                    Text(distributor.label)
                }
            }
        }
        TextButton(onClick = onRefreshDistributors, enabled = !state.distributorLoading) {
            Text(stringResource(R.string.notifications_push_select_provider))
        }
        Text(
            if (state.permissionGranted) stringResource(R.string.notifications_settings_permission_granted)
            else stringResource(R.string.notifications_settings_permission_missing),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (!state.permissionGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            TextButton(onClick = { permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }) {
                Text(stringResource(R.string.notifications_settings_grant_permission))
            }
        }
        Text(
            registrationText(state.registrationState, state.failureReason),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (state.registrationState != NotificationPushRegistrationState.Connected &&
            state.registrationState != NotificationPushRegistrationState.Off
        ) {
            TextButton(
                onClick = onRetryRegistration,
                enabled = !state.saving,
            ) { Text(stringResource(R.string.notifications_settings_retry_registration)) }
        }
        TextButton(onClick = onRunLocalTest, enabled = !state.saving) {
            Text(stringResource(R.string.notifications_push_test))
        }
        TextButton(onClick = onRunPushConnectionTest, enabled = !state.saving) {
            Text(stringResource(R.string.notifications_push_connection_test))
        }
        state.localTestMessage?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Spacer(Modifier.padding(bottom = 16.dp))
    }
}

@Composable
private fun SettingSwitch(
    title: String,
    subtitle: String?,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

@Composable
private fun registrationText(
    state: NotificationPushRegistrationState,
    reason: PushRegistrationFailureReason?,
): String = when {
    reason == PushRegistrationFailureReason.Unsupported -> stringResource(R.string.notifications_settings_registration_unsupported)
    reason == PushRegistrationFailureReason.Unauthorized -> stringResource(R.string.notifications_settings_registration_reauthorize)
    state == NotificationPushRegistrationState.RegisteringWithDistributor -> stringResource(R.string.notifications_settings_registration_distributor)
    state == NotificationPushRegistrationState.EndpointReceived -> stringResource(R.string.notifications_settings_registration_endpoint)
    state == NotificationPushRegistrationState.RegisteringWithServer -> stringResource(R.string.notifications_settings_registration_server)
    state == NotificationPushRegistrationState.TemporarilyUnavailable -> stringResource(R.string.notifications_settings_registration_retrying)
    state == NotificationPushRegistrationState.AccessDenied -> stringResource(R.string.notifications_settings_registration_reauthorize)
    state == NotificationPushRegistrationState.DistributorSelectionRequired -> stringResource(R.string.notifications_settings_registration_choose_distributor)
    state == NotificationPushRegistrationState.PermissionRequired -> stringResource(R.string.notifications_settings_registration_permission)
    else -> when (state) {
    NotificationPushRegistrationState.Off -> stringResource(R.string.notifications_settings_registration_off)
    NotificationPushRegistrationState.NoDistributor -> stringResource(R.string.notifications_settings_registration_no_distributor)
    NotificationPushRegistrationState.Connected -> stringResource(R.string.notifications_settings_registration_connected)
    else -> stringResource(R.string.notifications_settings_registration_pending)
    }
}
