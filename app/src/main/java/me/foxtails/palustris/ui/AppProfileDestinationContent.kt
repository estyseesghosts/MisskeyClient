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
import me.foxtails.palustris.ui.profile.ProfileCategory
import me.foxtails.palustris.ui.profile.ProfileScreen
import me.foxtails.palustris.ui.profile.ProfileUiState

@Composable
internal fun AppProfileDestinationContent(
    account: Account?,
    profileState: ProfileUiState,
    compactLayout: Boolean,
    largeLayout: Boolean,
    largeShowSummary: Boolean,
    listState: LazyListState,
    compactNavigationVisible: Boolean,
    authenticatedAccountId: AccountId?,
    onProfileShown: (Account) -> Unit,
    onCategorySelected: (ProfileCategory) -> Unit,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onFollow: () -> Unit,
    onUnfollow: () -> Unit,
    onMessage: (Account) -> Unit,
    onEditProfile: () -> Unit,
    onOpenDrafts: () -> Unit,
    onOpenBookmarks: () -> Unit,
    onOpenLikes: () -> Unit,
    onOpenProfile: (Account) -> Unit,
    onOpenProfileImage: (String) -> Unit,
    onSearchHashtag: (String) -> Unit,
    availableActions: Set<PostAction>,
    onReact: (OwnedPost) -> Unit,
    onReply: (OwnedPost) -> Unit,
    onReshare: (OwnedPost) -> Unit,
    onBookmark: (OwnedPost) -> Unit,
    onReaction: (OwnedPost, EmojiChoice) -> Unit,
    onOpenReactionBubble: (OwnedPost, Rect) -> Unit,
    onOpenHashtagBubble: (OwnedPost, List<String>, Rect) -> Unit,
    onOpenMedia: (MediaOpenRequest) -> Unit,
    onOpenPost: (OwnedPost) -> Unit,
    onOpenUsername: (String) -> Unit,
) {
    ProfileScreen(
        account = account,
        profileState = profileState,
        compactLayout = compactLayout,
        compactNavigationVisible = compactNavigationVisible,
        authenticatedAccountId = authenticatedAccountId,
        onProfileShown = onProfileShown,
        onCategorySelected = onCategorySelected,
        onRefresh = onRefresh,
        onLoadMore = onLoadMore,
        onFollow = onFollow,
        onUnfollow = onUnfollow,
        onMessage = onMessage,
        onEditProfile = onEditProfile,
        onOpenDrafts = onOpenDrafts,
        onOpenBookmarks = onOpenBookmarks,
        onOpenLikes = onOpenLikes,
        onOpenProfile = onOpenProfile,
        onOpenProfileImage = onOpenProfileImage,
        onSearchHashtag = onSearchHashtag,
        availableActions = availableActions,
        onReact = onReact,
        onReply = onReply,
        onReshare = onReshare,
        onBookmark = onBookmark,
        onReaction = onReaction,
        onOpenReactionBubble = onOpenReactionBubble,
        onOpenHashtagBubble = onOpenHashtagBubble,
        onOpenMedia = onOpenMedia,
        onOpenPost = onOpenPost,
        onOpenUsername = onOpenUsername,
        largeLayout = largeLayout,
        largeShowSummary = largeShowSummary,
        listState = listState,
    )
}
