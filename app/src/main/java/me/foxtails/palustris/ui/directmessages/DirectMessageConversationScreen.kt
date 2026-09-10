package me.foxtails.palustris.ui.directmessages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.R
import me.foxtails.palustris.domain.Post
import me.foxtails.palustris.ui.AccountAvatar
import me.foxtails.palustris.ui.ActionIcon
import me.foxtails.palustris.ui.AppIcons
import me.foxtails.palustris.ui.Avatar
import me.foxtails.palustris.ui.CompactFilterDockHeight
import me.foxtails.palustris.ui.compactGlobalNavigationPositioningInsets
import me.foxtails.palustris.ui.compactScrollEndClearance
import me.foxtails.palustris.ui.emoji.InlineEmojiText

@Composable
fun DirectMessageConversationScreen(
    accountId: AccountId,
    state: DirectMessageUiState,
    compactLayout: Boolean = true,
    compactNavigationVisible: Boolean = true,
    onBack: () -> Unit = {},
    onSend: (String) -> Unit = {},
) {
    var draft by remember(state.selectedConversationId, state.recipient?.id) { mutableStateOf("") }
    val recipient = state.recipient
    val title = state.selectedConversation?.participants
        ?.filterNot { it.id == accountId }
        ?.joinToString(", ") { it.displayName.ifBlank { it.handle } }
        ?.ifBlank { null }
        ?: recipient?.displayName?.ifBlank { recipient.handle }
        ?: stringResource(R.string.dm_private_message)
    val endClearance = if (compactLayout) {
        compactScrollEndClearance(
            controlStackHeight = CompactFilterDockHeight,
            navigationVisible = compactNavigationVisible,
        )
    } else {
        0.dp
    }

    Column(Modifier.fillMaxSize().testTag("direct_message_conversation")) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ActionIcon(AppIcons.Back, stringResource(R.string.dm_back), onBack)
            Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(
            stringResource(R.string.dm_not_encrypted),
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (state.error != null && state.thread.isEmpty()) {
            Text(state.error, Modifier.padding(20.dp), color = MaterialTheme.colorScheme.error)
        }
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth().testTag("direct_message_thread"),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp + endClearance),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(state.thread, key = { "${it.id.connection}/${it.id.value}" }) { post ->
                DirectMessageRow(accountId, post)
            }
            if (state.loadingThread) item {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(24.dp))
                }
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .imePadding()
                .windowInsetsPadding(compactGlobalNavigationPositioningInsets())
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                modifier = Modifier.weight(1f).testTag("direct_message_input"),
                placeholder = { Text(stringResource(R.string.dm_compose_placeholder)) },
                minLines = 1,
                maxLines = 4,
            )
            IconButton(
                onClick = { onSend(draft); draft = "" },
                enabled = draft.isNotBlank() && !state.sending && (recipient != null || state.selectedConversation != null),
                modifier = Modifier.size(52.dp).testTag("direct_message_send"),
            ) {
                if (state.sending) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                else Icon(AppIcons.Compose, stringResource(R.string.dm_send))
            }
        }
    }
}

@Composable
private fun DirectMessageRow(accountId: AccountId, post: Post) {
    val mine = post.author.id == accountId
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom,
    ) {
        if (!mine) {
            AccountAvatar(post.author, Modifier.size(36.dp), exposeSemantics = false)
            Spacer(Modifier.size(8.dp))
        }
        Surface(
            color = if (mine) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = MaterialTheme.shapes.large,
        ) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp).fillMaxWidth(.82f)) {
                if (!mine) InlineEmojiText(post.author.displayName, post.author.emoji, style = MaterialTheme.typography.labelLarge)
                Text(post.text, style = MaterialTheme.typography.bodyLarge)
                Text(
                    post.publishedAtEpochMillis.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (mine) {
            Spacer(Modifier.size(8.dp))
            Avatar(Modifier.size(36.dp), description = null)
        }
    }
}
