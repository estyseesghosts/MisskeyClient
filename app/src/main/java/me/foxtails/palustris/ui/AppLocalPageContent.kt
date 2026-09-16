package me.foxtails.palustris.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Rect
import me.foxtails.palustris.R
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.EmojiChoice
import me.foxtails.palustris.domain.OwnedPost
import me.foxtails.palustris.domain.PostAction
import me.foxtails.palustris.domain.PostDraft
import me.foxtails.palustris.ui.composer.DraftsScreen
import me.foxtails.palustris.ui.media.MediaOpenRequest
import me.foxtails.palustris.ui.saved.SavedPostsScreen
import me.foxtails.palustris.ui.saved.SavedPostsUiState

@Composable
internal fun AppLocalPageContent(
    page: LocalPage?,
    savedPostsState: SavedPostsUiState?,
    likedPostsState: SavedPostsUiState?,
    drafts: List<PostDraft>,
    onLoadDraft: (PostDraft) -> Unit,
    onDeleteDraft: (PostDraft) -> Unit,
    onRefreshSavedPosts: () -> Unit,
    onLoadMoreSavedPosts: () -> Unit,
    onUnsaveSavedPost: (OwnedPost) -> Unit,
    onRefreshLikedPosts: () -> Unit,
    onLoadMoreLikedPosts: () -> Unit,
    onUnsaveLikedPost: (OwnedPost) -> Unit,
    onUpgradeSavedPermissions: () -> Unit,
    onReact: (OwnedPost) -> Unit,
    onReply: (OwnedPost) -> Unit,
    onReshare: (OwnedPost) -> Unit,
    onBookmark: (OwnedPost) -> Unit,
    onSavedPostReaction: (OwnedPost, EmojiChoice) -> Unit,
    onLikedPostReaction: (OwnedPost, EmojiChoice) -> Unit,
    onOpenSavedReactionBubble: (OwnedPost, Rect) -> Unit,
    onOpenLikedReactionBubble: (OwnedPost, Rect) -> Unit,
    onOpenReactionPicker: (OwnedPost) -> Unit = {},
    onOpenMedia: (MediaOpenRequest) -> Unit,
    onOpenPost: (OwnedPost, LargePostOrigin) -> Unit,
    onOpenProfile: (Account) -> Unit,
    onSearchHashtag: (String) -> Unit,
    onOpenHashtagBubble: (OwnedPost, List<String>, Rect) -> Unit,
    onOpenUsername: (String) -> Unit,
    availableActions: Set<PostAction>,
    largeLayout: Boolean,
) {
    when (page) {
        LocalPage.SavedPosts -> savedPostsState?.let { state ->
            SavedPostsScreen(
                state = state,
                onRefresh = onRefreshSavedPosts,
                onLoadMore = onLoadMoreSavedPosts,
                onUnsave = onUnsaveSavedPost,
                onSignIn = onUpgradeSavedPermissions,
                onUpgradePermissions = onUpgradeSavedPermissions,
                onReact = onReact,
                onReply = onReply,
                onReshare = onReshare,
                onReaction = onSavedPostReaction,
                onOpenReactionBubble = onOpenSavedReactionBubble,
                onOpenReactionPicker = onOpenReactionPicker,
                onOpenMedia = onOpenMedia,
                onOpenPost = { post -> onOpenPost(post, LargePostOrigin.Saved) },
                availableActions = availableActions + PostAction.Bookmark,
                onOpenProfile = onOpenProfile,
                onSearchHashtag = onSearchHashtag,
                onOpenHashtagBubble = onOpenHashtagBubble,
                onOpenUsername = onOpenUsername,
                largeLayout = largeLayout,
            )
        } ?: EmptyState(AppIcons.HollowBookmark, androidx.compose.ui.res.stringResource(R.string.saved_posts_empty_title), androidx.compose.ui.res.stringResource(R.string.saved_posts_empty_subtitle))
        LocalPage.Likes -> likedPostsState?.let { state ->
            SavedPostsScreen(
                state = state,
                onRefresh = onRefreshLikedPosts,
                onLoadMore = onLoadMoreLikedPosts,
                onUnsave = onUnsaveLikedPost,
                onBookmark = onBookmark,
                onSignIn = onUpgradeSavedPermissions,
                onUpgradePermissions = onUpgradeSavedPermissions,
                onReact = onUnsaveLikedPost,
                onReply = onReply,
                onReshare = onReshare,
                onReaction = onLikedPostReaction,
                onOpenReactionBubble = onOpenLikedReactionBubble,
                onOpenReactionPicker = onOpenReactionPicker,
                onOpenMedia = onOpenMedia,
                onOpenPost = { post -> onOpenPost(post, LargePostOrigin.Liked) },
                availableActions = availableActions + PostAction.Favorite,
                onOpenProfile = onOpenProfile,
                onSearchHashtag = onSearchHashtag,
                onOpenHashtagBubble = onOpenHashtagBubble,
                onOpenUsername = onOpenUsername,
                largeLayout = largeLayout,
            )
        } ?: EmptyState(AppIcons.HollowHeart, androidx.compose.ui.res.stringResource(R.string.liked_posts_empty_title), androidx.compose.ui.res.stringResource(R.string.liked_posts_empty_subtitle))
        LocalPage.Drafts -> DraftsScreen(drafts, onLoadDraft, onDeleteDraft)
        LocalPage.About -> EmptyState(AppIcons.Globe, androidx.compose.ui.res.stringResource(R.string.about_empty_title), androidx.compose.ui.res.stringResource(R.string.about_empty_subtitle))
        null -> Unit
    }
}
