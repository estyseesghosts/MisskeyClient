package me.foxtails.palustris.ui.thread

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.foxtails.palustris.R
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.EmojiChoice
import me.foxtails.palustris.domain.OwnedPost
import me.foxtails.palustris.domain.PostAction
import me.foxtails.palustris.ui.PostRow
import me.foxtails.palustris.ui.actionsForPost
import me.foxtails.palustris.ui.media.MediaOpenRequest

@Composable
internal fun ThreadedReplyRow(
    row: me.foxtails.palustris.domain.ThreadRow,
    availableActions: Set<PostAction>,
    onReact: (OwnedPost) -> Unit,
    onReply: (OwnedPost) -> Unit,
    onReshare: (OwnedPost) -> Unit,
    onBookmark: (OwnedPost) -> Unit,
    onReaction: (OwnedPost, EmojiChoice) -> Unit,
    onOpenProfile: (Account) -> Unit,
    onSearchHashtag: (String) -> Unit,
    onOpenHashtagBubble: ((OwnedPost, List<String>, androidx.compose.ui.geometry.Rect) -> Unit)?,
    onOpenReactionBubble: ((OwnedPost, androidx.compose.ui.geometry.Rect) -> Unit)?,
    onOpenMedia: (MediaOpenRequest) -> Unit,
    onOpenUrl: ((String) -> Unit)?,
    onOpenUsername: ((String) -> Unit)?,
    quoteEnabled: Boolean,
    onQuote: (OwnedPost) -> Unit,
    modifier: Modifier = Modifier,
) {
    val visualShift = (row.visualDepth * 20).dp
    val connectorColor = MaterialTheme.colorScheme.outlineVariant
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = visualShift)
            .drawBehind {
                if (row.connector.verifiedParent) {
                    val x = 20.dp.toPx()
                    drawLine(
                        color = connectorColor,
                        start = androidx.compose.ui.geometry.Offset(x, 0f),
                        end = androidx.compose.ui.geometry.Offset(x, size.height),
                        strokeWidth = 1.dp.toPx(),
                    )
                }
            },
    ) {
        if (row.parentMissing) {
            Text(
                stringResource(R.string.thread_earlier_reply_unavailable),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium,
            )
        }
        PostRow(
            ownedPost = row.ownedPost,
            availableActions = actionsForPost(availableActions, row.post),
            onReact = onReact,
            onReply = onReply,
            onReshare = onReshare,
            onBookmark = onBookmark,
            onReaction = onReaction,
            onOpenProfile = onOpenProfile,
            onSearchHashtag = onSearchHashtag,
            onOpenHashtagBubble = onOpenHashtagBubble,
            onOpenReactionBubble = { target, bounds -> onOpenReactionBubble?.invoke(target, bounds) },
            onOpenMedia = onOpenMedia,
            truncateBody = false,
            quoteEnabled = quoteEnabled,
            onQuote = onQuote,
            onOpenUrl = onOpenUrl,
            onOpenUsername = onOpenUsername,
        )
    }
}
