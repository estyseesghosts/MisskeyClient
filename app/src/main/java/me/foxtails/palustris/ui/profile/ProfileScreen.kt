@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package me.foxtails.palustris.ui.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.EmojiChoice
import me.foxtails.palustris.domain.OwnedPost
import me.foxtails.palustris.domain.PostAction
import me.foxtails.palustris.R
import me.foxtails.palustris.ui.components.AccountAvatar
import me.foxtails.palustris.ui.AppIcons
import me.foxtails.palustris.ui.EmptyState
import me.foxtails.palustris.ui.layout.compactContextualControlsPositioningInsets
import me.foxtails.palustris.ui.layout.compactScrollEndClearance
import me.foxtails.palustris.ui.layout.CompactFilterDockHeight
import me.foxtails.palustris.ui.layout.CompactOverlayHorizontalPadding
import me.foxtails.palustris.ui.large.LargeBottomDockClearance
import me.foxtails.palustris.ui.components.FilterChipEntry
import me.foxtails.palustris.ui.components.FilterChipRow
import me.foxtails.palustris.ui.emoji.InlineEmojiText
import me.foxtails.palustris.ui.media.MediaOpenRequest

@Composable
fun ProfileScreen(
    account: Account? = null,
    profileState: ProfileUiState = ProfileUiState(),
    compactLayout: Boolean = true,
    compactNavigationVisible: Boolean = true,
    authenticatedAccountId: AccountId? = null,
    onProfileShown: (Account) -> Unit = {},
    onCategorySelected: (ProfileCategory) -> Unit = {},
    onRefresh: () -> Unit = {},
    onLoadMore: () -> Unit = {},
    onFollow: () -> Unit = {},
    onUnfollow: () -> Unit = {},
    onMessage: (Account) -> Unit = {},
    onEditProfile: (() -> Unit)? = null,
    onOpenDrafts: () -> Unit = {},
    onOpenBookmarks: () -> Unit = {},
    onOpenLikes: () -> Unit = {},
    onOpenProfile: (Account) -> Unit = {},
    onOpenProfileImage: (String) -> Unit = {},
    onSearchHashtag: (String) -> Unit = {},
    availableActions: Set<PostAction> = emptySet(),
    onReact: (OwnedPost) -> Unit = {},
    onReply: (OwnedPost) -> Unit = {},
    onReshare: (OwnedPost) -> Unit = {},
    onBookmark: (OwnedPost) -> Unit = {},
    onReaction: (OwnedPost, EmojiChoice) -> Unit = { _, _ -> },
    onOpenReactionBubble: ((OwnedPost, Rect) -> Unit)? = null,
    onOpenReactionPicker: (OwnedPost) -> Unit = {},
    onOpenHashtagBubble: ((OwnedPost, List<String>, Rect) -> Unit)? = null,
    onOpenMedia: (MediaOpenRequest) -> Unit = {},
    onOpenPost: (OwnedPost) -> Unit = {},
    onOpenUrl: ((String) -> Unit)? = null,
    onOpenUsername: ((String) -> Unit)? = null,
    quoteEnabled: Boolean = false,
    onQuote: (OwnedPost) -> Unit = {},
    largeLayout: Boolean = false,
    largeShowSummary: Boolean = true,
    listState: LazyListState? = null,
) {
    LaunchedEffect(account?.id) {
        account?.let(onProfileShown)
    }

    val displayedAccount = profileState.account
        ?.takeIf { account == null || it.id == account.id }
        ?: profileState.seedAccount?.takeIf { account == null || it.id == account.id }
        ?: account
    if (displayedAccount == null) {
        EmptyState(
            icon = AppIcons.DefaultUser,
            title = stringResource(R.string.profile_empty_title),
            subtitle = stringResource(R.string.profile_empty_subtitle),
            modifier = Modifier.fillMaxSize(),
        )
        return
    }

    val isSelf = displayedAccount.id == authenticatedAccountId
    val endContentClearance = if (largeLayout) {
        LargeBottomDockClearance
    } else if (compactLayout) {
        compactScrollEndClearance(
            controlStackHeight = CompactFilterDockHeight,
            navigationVisible = compactNavigationVisible,
            ime = WindowInsets.ime,
        )
    } else {
        0.dp
    }

    if (largeLayout) {
        ProfileLargePresentation(
            account = displayedAccount,
            state = profileState,
            isSelf = isSelf,
            showSummary = largeShowSummary,
            onOpenProfileImage = onOpenProfileImage,
            listState = listState,
            endContentClearance = endContentClearance,
            onCategorySelected = onCategorySelected,
             onOpenDrafts = onOpenDrafts,
             onOpenBookmarks = onOpenBookmarks,
             onOpenLikes = onOpenLikes,
            onRefresh = onRefresh,
            onLoadMore = onLoadMore,
            onFollow = onFollow,
            onUnfollow = onUnfollow,
            onMessage = { onMessage(displayedAccount) },
            onEditProfile = onEditProfile,
            onOpenProfile = onOpenProfile,
            details = { ProfileDetails(displayedAccount) },
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
            onSearchHashtag = onSearchHashtag,
            onOpenUrl = onOpenUrl,
            onOpenUsername = onOpenUsername,
            quoteEnabled = quoteEnabled,
            onQuote = onQuote,
        )
        return
    }

    Box(Modifier.fillMaxSize()) {
        ProfileTimelineList(
            account = displayedAccount,
            state = profileState,
            compactLayout = compactLayout,
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
            header = {
                ProfileHeader(
                    account = displayedAccount,
                    state = profileState,
                    isSelf = isSelf,
                    onRefresh = onRefresh,
                    onFollow = onFollow,
                    onUnfollow = onUnfollow,
                     onMessage = { onMessage(displayedAccount) },
                     onOpenProfile = onOpenProfile,
                     onOpenProfileImage = onOpenProfileImage,
                 )
            },
            details = { ProfileDetails(displayedAccount) },
            listState = listState,
        )

        if (compactLayout) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = CompactOverlayHorizontalPadding)
                    .windowInsetsPadding(
                        compactContextualControlsPositioningInsets(
                            navigationVisible = compactNavigationVisible,
                            ime = WindowInsets.ime,
                        ),
                    ),
            ) {
                FilterChipRow(
                    entries = profileChipEntries(isSelf).map { entry ->
                        when (entry) {
                            is ProfileChipEntry.Timeline -> FilterChipEntry(
                                 label = stringResource(entry.category.labelRes),
                                selected = entry.category == profileState.selectedTab,
                                onClick = { onCategorySelected(entry.category) },
                            )
                            ProfileChipEntry.Drafts -> FilterChipEntry(
                                label = stringResource(R.string.profile_action_drafts),
                                onClick = onOpenDrafts,
                                contentDescription = stringResource(R.string.profile_action_drafts_description),
                                role = Role.Button,
                                testTag = "profile_drafts_chip",
                            )
                             ProfileChipEntry.Bookmarks -> FilterChipEntry(
                                 label = stringResource(R.string.profile_action_bookmarks),
                                 onClick = onOpenBookmarks,
                                 contentDescription = stringResource(R.string.profile_action_bookmarks_description),
                                 role = Role.Button,
                                 testTag = "profile_bookmarks_chip",
                             )
                             ProfileChipEntry.Likes -> FilterChipEntry(
                                 label = stringResource(R.string.profile_action_likes),
                                 onClick = onOpenLikes,
                                 contentDescription = stringResource(R.string.profile_action_likes_description),
                                 role = Role.Button,
                                 testTag = "profile_likes_chip",
                             )
                             ProfileChipEntry.EditProfile -> FilterChipEntry(
                                 label = stringResource(R.string.profile_edit),
                                 onClick = onEditProfile ?: {},
                                 contentDescription = stringResource(R.string.profile_edit_description),
                                 role = Role.Button,
                                 testTag = "profile_edit_profile_chip",
                             )
                        }
                    },
                    rowContentDescription = "Profile categories; swipe horizontally for more",
                )
            }
        }
    }
}

@Composable
internal fun ProfileRedirectBanner(
    account: Account,
    destination: Account,
    onOpenProfile: () -> Unit,
) {
    val destinationName = destination.displayName.ifBlank { destination.handle }
    val destinationDescription = stringResource(
        R.string.profile_redirect_destination_description,
        destinationName,
        destination.handle,
    )
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag("profile_redirect"),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(
                text = stringResource(
                    R.string.profile_redirect_notice,
                    account.displayName.ifBlank { account.handle },
                ),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(12.dp))
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("profile_redirect_destination")
                    .semantics {
                        contentDescription = destinationDescription
                        role = Role.Button
                    }
                    .clickable(role = Role.Button, onClick = onOpenProfile),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surface,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    AccountAvatar(destination, Modifier.size(56.dp), exposeSemantics = false)
                    Column(Modifier.weight(1f)) {
                        InlineEmojiText(
                            text = destinationName,
                            emoji = destination.emoji,
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            destination.handle,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onOpenProfile,
                modifier = Modifier.fillMaxWidth().testTag("profile_redirect_go_to_profile"),
            ) {
                Text(stringResource(R.string.profile_redirect_go_to_profile))
            }
        }
    }
}

@Composable
internal fun ProfileStats(account: Account) {
    val stats = listOfNotNull(
        account.postsCount?.let { "${formatProfileCount(it)} posts" },
        account.followersCount?.let { "${formatProfileCount(it)} followers" },
        account.followingCount?.let { "${formatProfileCount(it)} following" },
    )
    if (stats.isNotEmpty()) {
        Row(
            modifier = Modifier.padding(top = 18.dp).testTag("profile_stats"),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            stats.forEach { stat ->
                Text(stat, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
internal fun ProfileBadge(text: String) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@Composable
internal fun LinearProfileProgress(description: String) {
    Column(Modifier.fillMaxWidth().padding(top = 12.dp).testTag("profile_detail_loading")) {
        Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        androidx.compose.material3.LinearProgressIndicator(Modifier.fillMaxWidth())
    }
}

@Composable
internal fun ProfileStatus(
    message: String,
    action: String,
    onAction: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            message,
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
        )
        TextButton(onClick = onAction) { Text(action) }
    }
}

internal fun formatProfileCount(value: Long): String = when {
    value >= 1_000_000 -> "%.1fM".format(value / 1_000_000.0)
    value >= 1_000 -> "%.1fK".format(value / 1_000.0)
    else -> value.toString()
}

internal fun Account.hasUsableProfileIdentity(): Boolean = id.localId.isNotBlank() &&
    (displayName.isNotBlank() || handle.removePrefix("@").substringBefore("@").isNotBlank() || avatarUrl != null)

private fun String.isWebAddress(): Boolean =
    startsWith("https://") || startsWith("http://")
