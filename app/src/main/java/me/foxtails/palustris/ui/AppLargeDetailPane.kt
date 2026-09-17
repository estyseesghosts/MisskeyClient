package me.foxtails.palustris.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import me.foxtails.palustris.R
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.ContentWarningRules
import me.foxtails.palustris.domain.EmojiChoice
import me.foxtails.palustris.domain.OwnedPost
import me.foxtails.palustris.domain.PostAction
import me.foxtails.palustris.ui.media.MediaOpenRequest
import me.foxtails.palustris.ui.thread.PostThreadUiState

@Composable
internal fun AppLargeDetailPane(
    selected: OwnedPost?,
    origin: LargePostOrigin,
    availableActions: Set<PostAction>,
    threadState: PostThreadUiState?,
    onClose: () -> Unit,
    onReact: (OwnedPost) -> Unit,
    onReply: (OwnedPost) -> Unit,
    onReshare: (OwnedPost) -> Unit,
    onBookmark: (OwnedPost) -> Unit,
    onReaction: (OwnedPost, EmojiChoice) -> Unit,
    onOpenProfile: (Account) -> Unit,
    onSearchHashtag: (String) -> Unit,
    onOpenHashtagBubble: ((OwnedPost, List<String>, Rect) -> Unit)? = null,
    onOpenReactionBubble: (OwnedPost, Rect, (OwnedPost, EmojiChoice) -> Unit) -> Unit,
    onOpenReactionPicker: (OwnedPost) -> Unit,
    onOpenMedia: (MediaOpenRequest) -> Unit,
    onOpenUsername: ((String) -> Unit)? = null,
    onThreadRefresh: () -> Unit,
    onThreadContinue: () -> Unit,
    quoteEnabled: Boolean,
    onQuote: (OwnedPost) -> Unit,
    contentWarningRules: ContentWarningRules = LocalContentWarningRules.current,
    modifier: Modifier,
) {
    if (selected == null) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            EmptyState(AppIcons.HoneyHome, androidx.compose.ui.res.stringResource(R.string.post_select_title), androidx.compose.ui.res.stringResource(R.string.post_select_subtitle))
        }
        return
    }

    val threadEnabled = threadState != null && origin.supportsComments()
    SinglePostScreen(
        ownedPost = selected,
        presentation = origin.singlePostPresentation(),
        onClose = onClose,
        availableActions = availableActions,
        onReact = onReact,
        onReply = onReply,
        onReshare = onReshare,
        onBookmark = onBookmark,
        onReaction = onReaction,
        onOpenProfile = onOpenProfile,
        onSearchHashtag = onSearchHashtag,
        onOpenHashtagBubble = onOpenHashtagBubble,
         onOpenReactionBubble = { post, bounds -> onOpenReactionBubble(post, bounds, onReaction) },
         onOpenReactionPicker = onOpenReactionPicker,
        onOpenMedia = onOpenMedia,
        onOpenUsername = onOpenUsername,
        embedded = true,
        threadState = threadState.takeIf { threadEnabled },
        onThreadRefresh = onThreadRefresh,
        onThreadContinue = onThreadContinue,
        contentWarningRules = contentWarningRules,
        quoteEnabled = quoteEnabled,
        onQuote = onQuote,
        modifier = modifier,
    )
}
