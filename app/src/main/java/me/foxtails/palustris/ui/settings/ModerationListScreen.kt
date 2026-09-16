package me.foxtails.palustris.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import me.foxtails.palustris.R
import me.foxtails.palustris.domain.ModerationAccount
import me.foxtails.palustris.domain.ModerationListKind

@Composable
fun ModerationListScreen(
    state: ModerationUiState,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onRemove: (ModerationAccount) -> Unit,
) {
    var pendingRemoval by remember { mutableStateOf<ModerationAccount?>(null) }
    val entries = state.accounts
    when {
        state.loading -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
            CircularProgressIndicator()
        }
        state.unsupported -> Column(Modifier.fillMaxWidth().padding(24.dp)) {
            Text(stringResource(R.string.settings_moderation_unsupported))
        }
        state.error != null && entries.isEmpty() -> Column(Modifier.fillMaxWidth().padding(24.dp)) {
            Text(state.error)
            Button(onClick = onRetry, modifier = Modifier.padding(top = 12.dp)) { Text(stringResource(R.string.notifications_retry)) }
        }
        entries.isEmpty() -> Text(stringResource(R.string.settings_moderation_no_entries), Modifier.padding(24.dp))
        else -> LazyColumn(Modifier.fillMaxSize()) {
            items(entries, key = { it.account.id.connection.origin + ":" + it.account.id.localId }) { entry ->
                ListItem(
                    headlineContent = { Text(entry.account.displayName) },
                    supportingContent = { Text(entry.account.handle) },
                    trailingContent = {
                        TextButton(
                            enabled = entry.account.id.localId !in state.removing,
                            onClick = { pendingRemoval = entry },
                        ) { Text(if (state.kind == ModerationListKind.Blocked) stringResource(R.string.post_share_unblock) else stringResource(R.string.post_share_unmute)) }
                    },
                )
            }
            if (state.error != null) item { Text(state.error, Modifier.padding(16.dp)) }
            if (state.nextCursor != null) item {
                TextButton(onClick = onLoadMore, enabled = !state.loadingMore, modifier = Modifier.fillMaxWidth()) {
                    if (state.loadingMore) CircularProgressIndicator() else Text(stringResource(R.string.settings_moderation_load_more))
                }
            }
        }
    }
    pendingRemoval?.let { entry ->
        AlertDialog(
            onDismissRequest = { pendingRemoval = null },
            title = { Text(if (state.kind == ModerationListKind.Blocked) stringResource(R.string.settings_moderation_unblock_title) else stringResource(R.string.settings_moderation_unmute_title)) },
            text = { Text(entry.account.handle) },
            confirmButton = {
                TextButton(onClick = { pendingRemoval = null; onRemove(entry) }) { Text(stringResource(R.string.settings_moderation_confirm)) }
            },
            dismissButton = { TextButton(onClick = { pendingRemoval = null }) { Text(stringResource(R.string.dialog_cancel)) } },
        )
    }
}
