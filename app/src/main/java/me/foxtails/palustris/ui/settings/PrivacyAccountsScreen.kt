package me.foxtails.palustris.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import me.foxtails.palustris.data.auth.AccountRef
import me.foxtails.palustris.domain.AccountId
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.ui.unit.dp

@Composable
fun PrivacyAccountsScreen(accounts: List<AccountRef>, onSelect: (AccountId, ModerationKind) -> Unit) {
    if (accounts.isEmpty()) {
        Text(stringResource(me.foxtails.palustris.R.string.settings_connect_account_privacy))
        return
    }
    accounts.forEach { account ->
        SettingsSection(account.displayName) {
            Text(account.handle, Modifier.padding(horizontal = 20.dp))
            ModerationKind.entries.forEach { kind ->
                SettingsRow(
                    title = kind.label(),
                    summary = "Manage this account's server list",
                    onClick = { onSelect(account.accountId, kind) },
                )
            }
        }
    }
}

private fun ModerationKind.label(): String = when (this) {
    ModerationKind.Blocked -> "Blocked users"
    ModerationKind.Muted -> "Muted users"
    ModerationKind.Hashtags -> "Muted hashtags"
}
