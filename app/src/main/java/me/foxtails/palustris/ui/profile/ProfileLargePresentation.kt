package me.foxtails.palustris.ui.profile

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.lazy.LazyListState
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.EmojiChoice
import me.foxtails.palustris.domain.OwnedPost
import me.foxtails.palustris.domain.PostAction
import me.foxtails.palustris.ui.large.LargeBottomDock
import me.foxtails.palustris.ui.large.LargeBottomDockClearance
import me.foxtails.palustris.ui.media.MediaOpenRequest

@Composable
internal fun ProfileLargePresentation(
    account: Account,
    state: ProfileUiState,
    isSelf: Boolean,
    showSummary: Boolean,
    listState: LazyListState?,
    endContentClearance: Dp,
    onCategorySelected: (ProfileCategory) -> Unit,
    onOpenDrafts: () -> Unit,
    onOpenBookmarks: () -> Unit,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onFollow: () -> Unit,
    onUnfollow: () -> Unit,
    onMessage: () -> Unit,
    onOpenProfile: (Account) -> Unit,
    onOpenProfileImage: (String) -> Unit,
    details: @Composable () -> Unit,
    availableActions: Set<PostAction>,
    onReact: (OwnedPost) -> Unit,
    onReply: (OwnedPost) -> Unit,
    onReshare: (OwnedPost) -> Unit,
    onBookmark: (OwnedPost) -> Unit,
    onReaction: (OwnedPost, EmojiChoice) -> Unit,
    onOpenReactionBubble: ((OwnedPost, androidx.compose.ui.geometry.Rect) -> Unit)?,
    onOpenReactionPicker: (OwnedPost) -> Unit,
    onOpenHashtagBubble: ((OwnedPost, List<String>, androidx.compose.ui.geometry.Rect) -> Unit)?,
    onOpenMedia: (MediaOpenRequest) -> Unit,
    onOpenPost: (OwnedPost) -> Unit,
    onSearchHashtag: (String) -> Unit,
    onOpenUrl: ((String) -> Unit)?,
    onOpenUsername: ((String) -> Unit)?,
    quoteEnabled: Boolean = false,
    onQuote: (OwnedPost) -> Unit = {},
    onEditProfile: (() -> Unit)?,
) {
    @Composable
    fun timeline() {
        ProfileTimelineList(
            account = account,
            state = state,
            compactLayout = false,
            endContentClearance = endContentClearance,
            isSelf = isSelf,
            onCategorySelected = onCategorySelected,
            onOpenDrafts = onOpenDrafts,
            onOpenBookmarks = onOpenBookmarks,
            onRefresh = onRefresh,
            onLoadMore = onLoadMore,
            onOpenProfile = onOpenProfile,
            onSearchHashtag = onSearchHashtag,
            availableActions = availableActions,
            onReact = onReact,
            onReply = onReply,
            onReshare = onReshare,
            onBookmark = onBookmark,
            onReaction = onReaction,
            onOpenReactionBubble = onOpenReactionBubble,
            onOpenReactionPicker = onOpenReactionPicker,
            onOpenHashtagBubble = onOpenHashtagBubble,
            onOpenMedia = onOpenMedia,
            onOpenPost = onOpenPost,
            onOpenUrl = onOpenUrl,
            onOpenUsername = onOpenUsername,
            quoteEnabled = quoteEnabled,
            onQuote = onQuote,
            header = {},
            details = details,
            listState = listState,
            showHeader = false,
            showInlineCategories = false,
            largeLayout = true,
        )
    }

    @Composable
    fun dock(modifier: Modifier = Modifier) {
        LargeBottomDock(
            content = {
                ProfileCategoryChips(
                    selected = state.selectedTab,
                    isSelf = isSelf,
                    likedAvailable = state.likedAvailable,
                    onCategorySelected = onCategorySelected,
                    onOpenDrafts = onOpenDrafts,
                    onOpenBookmarks = onOpenBookmarks,
                    onEditProfile = onEditProfile ?: {},
                    includeShowMore = false,
                    includeEditProfile = true,
                )
            },
            modifier = modifier,
        )
    }

    if (showSummary) {
        Box(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxSize()) {
                Column(
                    Modifier
                        .weight(0.42f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState()),
                ) {
                    ProfileHeader(
                        account = account,
                        state = state,
                        isSelf = isSelf,
                        onRefresh = onRefresh,
                        onFollow = onFollow,
                        onUnfollow = onUnfollow,
                        onMessage = onMessage,
                        onOpenProfile = onOpenProfile,
                        onOpenProfileImage = onOpenProfileImage,
                        onEditProfile = onEditProfile,
                        largeSummary = showSummary,
                    )
                    details()
                }
                Box(Modifier.weight(0.58f).fillMaxHeight()) { timeline() }
            }
            dock(Modifier.align(Alignment.BottomStart))
        }
    } else {
        Box(Modifier.fillMaxSize()) {
            timeline()
            dock(Modifier.align(Alignment.BottomStart))
        }
    }
}
