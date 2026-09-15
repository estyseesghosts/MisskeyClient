package me.foxtails.palustris.ui.notifications

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.foxtails.palustris.R
import me.foxtails.palustris.domain.NotificationCategory
import me.foxtails.palustris.ui.ActionIcon
import me.foxtails.palustris.ui.AppIcons

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun NotificationSettingsSheet(
    state: NotificationSettingsUiState,
    onDismiss: () -> Unit,
    onAlertsEnabled: (Boolean) -> Unit,
    onShowPreviews: (Boolean) -> Unit,
    onPeriodicFallback: (Boolean) -> Unit,
    onQuietHours: (Boolean) -> Unit,
    onCategoryChanged: (NotificationCategory, Boolean) -> Unit,
    onRunLocalTest: () -> Unit,
    onRetryRegistration: () -> Unit,
    onPermissionChanged: () -> Unit,
    onRefreshDistributors: () -> Unit,
    onSelectDistributor: (String) -> Unit,
    onRunPushConnectionTest: () -> Unit,
    onRetryStorage: () -> Unit,
    onResetStorage: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().fillMaxHeight().testTag("notification_settings_sheet")) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                ActionIcon(AppIcons.Close, stringResource(R.string.notification_settings_sheet_close), onDismiss)
                Text(stringResource(R.string.notification_settings_sheet_title), style = MaterialTheme.typography.titleLarge)
            }
            NotificationSettingsScreen(
                state = state,
                onAlertsEnabled = onAlertsEnabled,
                onShowPreviews = onShowPreviews,
                onPeriodicFallback = onPeriodicFallback,
                onQuietHours = onQuietHours,
                onCategoryChanged = onCategoryChanged,
                onRunLocalTest = onRunLocalTest,
                onRetryRegistration = onRetryRegistration,
                onPermissionChanged = onPermissionChanged,
                onRefreshDistributors = onRefreshDistributors,
                onSelectDistributor = onSelectDistributor,
                onRunPushConnectionTest = onRunPushConnectionTest,
                onRetryStorage = onRetryStorage,
                onResetStorage = onResetStorage,
                modifier = Modifier.fillMaxWidth().fillMaxHeight(),
            )
        }
    }
}
