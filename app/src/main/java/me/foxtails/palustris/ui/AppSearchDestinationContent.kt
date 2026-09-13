package me.foxtails.palustris.ui

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Rect
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.EmojiChoice
import me.foxtails.palustris.domain.OwnedPost
import me.foxtails.palustris.domain.PostAction
import me.foxtails.palustris.ui.media.MediaOpenRequest

@Composable
internal fun AppSearchDestinationContent(
    accountSearch: AccountSearchState,
    onSearchAccounts: (String) -> Unit,
    onAccountClick: (Account) -> Unit,
    availableActions: Set<PostAction>,
    onReact: (OwnedPost) -> Unit,
    onReply: (OwnedPost) -> Unit,
    onReshare: (OwnedPost) -> Unit,
    onBookmark: (OwnedPost) -> Unit,
    onReaction: (OwnedPost, EmojiChoice) -> Unit,
    onOpenReactionBubble: (OwnedPost, Rect) -> Unit,
    quoteEnabled: Boolean,
    onQuote: (OwnedPost) -> Unit,
    onSearchHashtag: (String) -> Unit,
    onOpenHashtagBubble: (OwnedPost, List<String>, Rect) -> Unit,
    onLoadMoreSearch: () -> Unit,
    initialQuery: String,
    sharedQuery: String,
    sharedTab: Int,
    onSharedQueryChange: (String) -> Unit,
    onSharedTabChange: (Int) -> Unit,
    listState: LazyListState?,
    largeLayout: Boolean,
    compactLayout: Boolean,
    mediaOwner: AccountId?,
    onOpenMedia: (MediaOpenRequest) -> Unit,
    onOpenPost: (OwnedPost) -> Unit,
    onOpenUsername: (String) -> Unit,
) {
    SearchScreen(
        accountSearch = accountSearch,
        onSearchAccounts = onSearchAccounts,
        onAccountClick = onAccountClick,
        availableActions = availableActions,
        onReact = onReact,
        onReply = onReply,
        onReshare = onReshare,
        onBookmark = onBookmark,
        onReaction = onReaction,
        onOpenReactionBubble = onOpenReactionBubble,
        quoteEnabled = quoteEnabled,
        onQuote = onQuote,
        onSearchHashtag = onSearchHashtag,
        onOpenHashtagBubble = onOpenHashtagBubble,
        onLoadMoreSearch = onLoadMoreSearch,
        initialQuery = initialQuery,
        sharedQuery = sharedQuery,
        sharedTab = sharedTab,
        onSharedQueryChange = onSharedQueryChange,
        onSharedTabChange = onSharedTabChange,
        listState = listState,
        largeLayout = largeLayout,
        compactLayout = compactLayout,
        compactNavigationVisible = compactLayout,
        mediaOwner = mediaOwner,
        onOpenMedia = onOpenMedia,
        onOpenPost = onOpenPost,
        onOpenUsername = onOpenUsername,
    )
}
