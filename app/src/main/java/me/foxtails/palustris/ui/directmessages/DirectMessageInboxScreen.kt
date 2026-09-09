package me.foxtails.palustris.ui.directmessages

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.DirectConversation
import me.foxtails.palustris.ui.AccountAvatar
import me.foxtails.palustris.ui.AppIcons
import me.foxtails.palustris.ui.EmptyState
import me.foxtails.palustris.ui.compactScrollEndClearance
import me.foxtails.palustris.ui.emoji.InlineEmojiText

@Composable
fun DirectMessageInboxScreen(
    accountId: AccountId,
    state: DirectMessageUiState,
    compactLayout: Boolean = true,
    compactNavigationVisible: Boolean = true,
    onRefresh: () -> Unit = {},
    onLoadMore: () -> Unit = {},
    onOpenConversation: (DirectConversation) -> Unit = {},
) {
    val endClearance = if (compactLayout) {
        compactScrollEndClearance(
            controlStackHeight = me.foxtails.palustris.ui.CompactFilterDockHeight,
            navigationVisible = compactNavigationVisible,
        )
    } else {
        0.dp
    }
    Column(Modifier.fillMaxSize().testTag("direct_message_inbox")) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Messages", Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall)
                TextButton(onClick = onRefresh, enabled = !state.loading && !state.loadingMore) { Text("Refresh") }
            }
            Spacer(Modifier.size(6.dp))
            Text(
                "Private messages are federated posts, not end-to-end encrypted. History depends on what your server exposes.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (state.error != null && state.conversations.isNotEmpty()) {
                Spacer(Modifier.size(8.dp))
                Text(state.error, color = MaterialTheme.colorScheme.error)
            }
        }
        when {
            state.loading && state.conversations.isEmpty() -> Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }
            state.error != null && state.conversations.isEmpty() -> EmptyState(
                icon = AppIcons.Chat,
                title = "Messages could not load",
                subtitle = state.error,
                modifier = Modifier.fillMaxSize(),
            )
            state.conversations.isEmpty() -> EmptyState(
                icon = AppIcons.Chat,
                title = "No private messages yet",
                subtitle = "Start a private conversation from someone’s profile.",
                modifier = Modifier.fillMaxSize(),
            )
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().testTag("direct_message_conversation_list"),
                contentPadding = PaddingValues(bottom = endClearance + 24.dp),
            ) {
                items(
                    items = state.conversations,
                    key = { "${it.id.connection}/${it.id.value}" },
                ) { conversation ->
                    DirectConversationRow(
                        accountId = accountId,
                        conversation = conversation,
                        onClick = { onOpenConversation(conversation) },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f))
                }
                item {
                    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        when {
                            state.loadingMore -> CircularProgressIndicator(Modifier.size(24.dp))
                            state.nextCursor != null -> Text(
                                "Load older messages",
                                modifier = Modifier.clickable(onClick = onLoadMore),
                                color = MaterialTheme.colorScheme.primary,
                            )
                            else -> Text(
                                "You're up to date",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DirectConversationRow(
    accountId: AccountId,
    conversation: DirectConversation,
    onClick: () -> Unit,
) {
    val people = conversation.participants.filterNot { it.id == accountId }
    val title = people.joinToString(", ") { it.displayName.ifBlank { it.handle } }
        .ifBlank { conversation.lastPost.author.displayName.ifBlank { conversation.lastPost.author.handle } }
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        color = if (conversation.unread) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = .35f)
        else MaterialTheme.colorScheme.surface,
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AccountAvatar(
                people.firstOrNull() ?: conversation.lastPost.author,
                Modifier.size(52.dp),
                exposeSemantics = false,
            )
            Column(Modifier.weight(1f)) {
                InlineEmojiText(title, people.firstOrNull()?.emoji.orEmpty(), style = MaterialTheme.typography.titleMedium)
                Text(
                    conversation.lastPost.text.ifBlank { "Private post" },
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (conversation.unread) {
                Surface(Modifier.size(10.dp), shape = CircleShape, color = MaterialTheme.colorScheme.primary) {}
            }
        }
    }
}
