package me.foxtails.palustris.ui

import androidx.compose.runtime.Composable
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.Dp
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.ContentWarningRules
import me.foxtails.palustris.domain.EmojiChoice
import me.foxtails.palustris.domain.OwnedPost
import me.foxtails.palustris.domain.PostAction
import me.foxtails.palustris.ui.media.MediaOpenRequest
import me.foxtails.palustris.ui.shell.HomeFeedUiState

@Composable
internal fun AppHomeDestinationContent(
    state: HomeFeedUiState,
    compactLayout: Boolean,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onSignIn: () -> Unit,
    ownedPosts: List<OwnedPost>,
    availableActions: Set<PostAction>,
    quoteEnabled: Boolean,
    onScrollDirectionChanged: (Boolean) -> Unit,
    onReact: (OwnedPost) -> Unit,
    onReply: (OwnedPost) -> Unit,
    onReshare: (OwnedPost) -> Unit,
    onBookmark: (OwnedPost) -> Unit,
    onReaction: (OwnedPost, EmojiChoice) -> Unit,
    onOpenReactionBubble: (OwnedPost, Rect) -> Unit,
    onOpenReactionPicker: (OwnedPost) -> Unit = {},
    onQuote: (OwnedPost) -> Unit,
    onOpenProfile: (Account) -> Unit,
    onSearchHashtag: (String) -> Unit,
    onOpenHashtagBubble: (OwnedPost, List<String>, Rect) -> Unit,
    onOpenMedia: (MediaOpenRequest) -> Unit,
    onOpenPost: (OwnedPost) -> Unit,
    onOpenUsername: (String) -> Unit,
    listState: LazyListState?,
    topContentPadding: Dp?,
    bottomContentClearance: Dp?,
    refreshIndicatorTopPadding: Dp?,
    bottomDock: (@Composable () -> Unit)?,
    contentWarningRules: ContentWarningRules = LocalContentWarningRules.current,
) {
    HomeFeed(
        state = state,
        compactLayout = compactLayout,
        onRefresh = onRefresh,
        onLoadMore = onLoadMore,
        onSignIn = onSignIn,
        ownedPosts = ownedPosts,
        availableActions = availableActions,
        quoteEnabled = quoteEnabled,
        onScrollDirectionChanged = onScrollDirectionChanged,
        onReact = onReact,
        onReply = onReply,
        onReshare = onReshare,
        onBookmark = onBookmark,
        onReaction = onReaction,
        onOpenReactionBubble = onOpenReactionBubble,
        onOpenReactionPicker = onOpenReactionPicker,
        onQuote = onQuote,
        onOpenProfile = onOpenProfile,
        onSearchHashtag = onSearchHashtag,
        onOpenHashtagBubble = onOpenHashtagBubble,
        onOpenMedia = onOpenMedia,
        onOpenPost = onOpenPost,
        onOpenUsername = onOpenUsername,
        listState = listState,
        topContentPadding = topContentPadding,
        bottomContentClearance = bottomContentClearance,
        refreshIndicatorTopPadding = refreshIndicatorTopPadding,
        bottomDock = bottomDock,
        contentWarningRules = contentWarningRules,
    )
}
