package me.foxtails.palustris.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.foxtails.palustris.R
import me.foxtails.palustris.data.auth.AccountRef
import me.foxtails.palustris.data.auth.toAccount
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.Timeline
import me.foxtails.palustris.ui.components.AccountAvatar

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun AppSelectionSheet(
    sheet: String,
    account: Account?,
    accounts: List<AccountRef>,
    timeline: Timeline,
    availableTimelines: Set<Timeline>,
    onDismiss: () -> Unit,
    onTimelineSelected: (Timeline) -> Unit,
    onSwitchAccount: (me.foxtails.palustris.domain.AccountId) -> Unit,
    onAddAccount: () -> Unit,
    onOpenSettings: () -> Unit,
    onSignOut: () -> Unit,
    onRefresh: (Timeline) -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            if (sheet == "Timelines") stringResource(R.string.nav_choose_timeline)
            else stringResource(R.string.nav_switch_account),
            Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
            style = MaterialTheme.typography.headlineSmall,
        )
        if (sheet == "Timelines") {
            me.foxtails.palustris.domain.timelineDisplayOrder.filter { it in availableTimelines }.forEach { item ->
                ListItem(
                    modifier = Modifier.clickable {
                        val changed = item != timeline
                        onTimelineSelected(item)
                        onDismiss()
                        if (changed) onRefresh(item)
                    },
                    headlineContent = { Text(stringResource(timelineLabelRes(item))) },
                    supportingContent = { Text(stringResource(timelineDescriptionRes(item))) },
                    leadingContent = { Icon(if (item == Timeline.Home) AppIcons.Home else AppIcons.Globe, null) },
                    trailingContent = { if (timeline == item) Icon(AppIcons.Check, stringResource(R.string.a11y_selected)) },
                )
            }
            Text(
                stringResource(if (account != null) R.string.timeline_detected else R.string.timeline_preview),
                Modifier.padding(24.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            accounts.forEach { accountRef ->
                val listedAccount = accountRef.toAccount()
                ListItem(
                    modifier = Modifier.clickable { onDismiss(); if (accountRef.accountId != account?.id) onSwitchAccount(accountRef.accountId) },
                    headlineContent = { Text(accountRef.displayName) },
                    supportingContent = { Text(accountRef.handle) },
                    leadingContent = { AccountAvatar(listedAccount, Modifier.size(48.dp)) },
                    trailingContent = { if (accountRef.accountId == account?.id) Icon(AppIcons.Check, stringResource(R.string.a11y_current_account)) },
                )
            }
            if (accounts.isEmpty()) ListItem(
                headlineContent = { Text(account?.displayName ?: stringResource(R.string.account_none_connected)) },
                supportingContent = { Text(account?.handle ?: stringResource(R.string.account_preview_unavailable)) },
                leadingContent = { if (account != null) AccountAvatar(account, Modifier.size(48.dp)) else Avatar(Modifier.size(48.dp)) },
            )
            if (account != null) TextButton(onClick = { onDismiss(); onAddAccount() }, modifier = Modifier.padding(horizontal = 16.dp)) { Text(stringResource(R.string.account_add)) }
            TextButton(onClick = { onDismiss(); onOpenSettings() }, modifier = Modifier.padding(horizontal = 16.dp)) { Text(stringResource(R.string.settings_title)) }
            if (account != null) TextButton(onClick = { onDismiss(); onSignOut() }, modifier = Modifier.padding(horizontal = 16.dp)) { Text(stringResource(R.string.account_sign_out)) }
            Spacer(Modifier.height(32.dp))
        }
    }
}
