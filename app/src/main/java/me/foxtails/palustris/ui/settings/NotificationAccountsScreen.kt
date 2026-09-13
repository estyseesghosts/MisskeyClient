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

@Composable
fun NotificationAccountsScreen(accounts: List<AccountRef>, onSelect: (AccountId) -> Unit) {
    if (accounts.isEmpty()) {
        Text(stringResource(me.foxtails.palustris.R.string.settings_connect_account_notifications))
        return
    }
    accounts.forEach { account ->
        ListItem(
            modifier = Modifier.fillMaxWidth().clickable { onSelect(account.accountId) },
            headlineContent = { Text(account.displayName) },
            supportingContent = { Text(account.handle) },
        )
    }
}
