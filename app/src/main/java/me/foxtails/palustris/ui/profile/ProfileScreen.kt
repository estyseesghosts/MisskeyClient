@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package me.foxtails.palustris.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import me.foxtails.palustris.data.media.MediaImageLoader
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.EmojiChoice
import me.foxtails.palustris.domain.OwnedPost
import me.foxtails.palustris.domain.PostAction
import me.foxtails.palustris.R
import me.foxtails.palustris.ui.AccountAvatar
import me.foxtails.palustris.ui.AppIcons
import me.foxtails.palustris.ui.EmptyState
import me.foxtails.palustris.ui.compactContextualControlsPositioningInsets
import me.foxtails.palustris.ui.compactScrollEndClearance
import me.foxtails.palustris.ui.openExternal
import me.foxtails.palustris.ui.CompactFilterDockHeight
import me.foxtails.palustris.ui.CompactOverlayHorizontalPadding
import me.foxtails.palustris.ui.large.LargeBottomDock
import me.foxtails.palustris.ui.large.LargeBottomDockClearance
import me.foxtails.palustris.ui.components.FilterChipEntry
import me.foxtails.palustris.ui.components.FilterChipRow
import me.foxtails.palustris.ui.emoji.InlineEmojiText
import me.foxtails.palustris.ui.media.MediaOpenRequest
import me.foxtails.palustris.ui.motion.PopEffect

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
    onOpenProfile: (Account) -> Unit = {},
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
            icon = AppIcons.Person,
            title = "Your profile",
            subtitle = "Connect an account to load profile details.",
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
        LargeProfilePresentation(
            account = displayedAccount,
            state = profileState,
            isSelf = isSelf,
            showSummary = largeShowSummary,
            listState = listState,
            endContentClearance = endContentClearance,
            onCategorySelected = onCategorySelected,
            onOpenDrafts = onOpenDrafts,
            onOpenBookmarks = onOpenBookmarks,
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
                                label = entry.category.label,
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
                        }
                    },
                    rowContentDescription = "Profile categories; swipe horizontally for more",
                )
            }
        }
    }
}

@Composable
private fun LargeProfilePresentation(
    account: Account,
    state: ProfileUiState,
    isSelf: Boolean,
    showSummary: Boolean,
    listState: LazyListState?,
    endContentClearance: androidx.compose.ui.unit.Dp,
    onCategorySelected: (ProfileCategory) -> Unit,
    onOpenDrafts: () -> Unit,
    onOpenBookmarks: () -> Unit,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onFollow: () -> Unit,
    onUnfollow: () -> Unit,
    onMessage: () -> Unit,
    onOpenProfile: (Account) -> Unit,
    details: @Composable () -> Unit,
    availableActions: Set<PostAction>,
    onReact: (OwnedPost) -> Unit,
    onReply: (OwnedPost) -> Unit,
    onReshare: (OwnedPost) -> Unit,
    onBookmark: (OwnedPost) -> Unit,
    onReaction: (OwnedPost, EmojiChoice) -> Unit,
    onOpenReactionBubble: ((OwnedPost, Rect) -> Unit)?,
    onOpenReactionPicker: (OwnedPost) -> Unit,
    onOpenHashtagBubble: ((OwnedPost, List<String>, Rect) -> Unit)?,
    onOpenMedia: (MediaOpenRequest) -> Unit,
    onOpenPost: (OwnedPost) -> Unit,
    onSearchHashtag: (String) -> Unit,
    onOpenUrl: ((String) -> Unit)?,
    onOpenUsername: ((String) -> Unit)?,
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
                    onCategorySelected = onCategorySelected,
                    onOpenDrafts = onOpenDrafts,
                    onOpenBookmarks = onOpenBookmarks,
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
                        onEditProfile = onEditProfile,
                    )
                    details()
                }
                Box(Modifier.weight(0.58f).fillMaxHeight()) {
                    timeline()
                }
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

@Composable
private fun ProfileHeader(
    account: Account,
    state: ProfileUiState,
    isSelf: Boolean,
    onRefresh: () -> Unit,
    onFollow: () -> Unit,
    onUnfollow: () -> Unit,
    onMessage: () -> Unit,
    onOpenProfile: (Account) -> Unit,
    onEditProfile: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val mediaImageLoader = remember(context) { MediaImageLoader.get(context) }
    val statusBarHeight = with(LocalDensity.current) {
        WindowInsets.statusBars.getTop(this).toDp()
    }.coerceAtLeast(1.dp)
    Column(Modifier.fillMaxWidth().testTag("profile_header")) {
        val movedTo = account.movedTo?.takeIf { it.hasUsableProfileIdentity() }
        movedTo?.let { destination ->
            ProfileRedirectBanner(
                account = account,
                destination = destination,
                onOpenProfile = { onOpenProfile(destination) },
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .testTag("profile_banner"),
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth().height(152.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
            ) {
                AsyncImage(
                    model = account.bannerUrl,
                    imageLoader = mediaImageLoader.imageLoader,
                    contentDescription = "Profile banner",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            if (statusBarHeight > 0.dp) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(statusBarHeight)
                        .clip(RectangleShape)
                        .testTag("profile_banner_status_bar_blur"),
                ) {
                    AsyncImage(
                        model = account.bannerUrl,
                        imageLoader = mediaImageLoader.imageLoader,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().blur(24.dp),
                    )
                }
            }
            Surface(
                modifier = Modifier
                    .padding(start = 16.dp, top = 104.dp)
                    .size(112.dp)
                    .clip(CircleShape),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 4.dp,
            ) {
                PopEffect(account.id) {
                    AccountAvatar(account, Modifier.padding(4.dp))
                }
            }
        }

        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(Modifier.weight(1f)) {
                    InlineEmojiText(
                        text = account.displayName.ifBlank { account.handle },
                        emoji = account.emoji,
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    )
                    Text(
                        text = account.handle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (isSelf && onEditProfile != null && state.editableSupported) {
                    androidx.compose.material3.OutlinedButton(
                        onClick = onEditProfile,
                        modifier = Modifier.testTag("profile_edit_action"),
                    ) { Text("Edit profile") }
                } else if (!isSelf && movedTo == null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        androidx.compose.material3.OutlinedButton(
                            onClick = onMessage,
                            modifier = Modifier.testTag("profile_message_action"),
                        ) { Text("Message") }
                        if (state.relationshipSupported == true && state.relationship != null) {
                            val relationship = state.relationship
                            val following = relationship.following || relationship.requested
                            Button(
                                onClick = if (following) onUnfollow else onFollow,
                                enabled = !state.relationshipMutation,
                                modifier = Modifier.testTag("profile_follow_action"),
                            ) {
                                if (state.relationshipMutation) {
                                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                } else {
                                    Text(
                                        when {
                                            relationship.following -> "Following"
                                            relationship.requested -> "Requested"
                                            else -> "Follow"
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (account.locked || account.bot) {
                Row(
                    modifier = Modifier.padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (account.locked) ProfileBadge("Locked")
                    if (account.bot) ProfileBadge("Bot")
                }
            }

            InlineEmojiText(
                text = account.biography.ifBlank { "No biography yet." },
                emoji = account.emoji,
                modifier = Modifier.padding(top = 16.dp).testTag("profile_biography"),
                style = MaterialTheme.typography.bodyLarge,
            )

            ProfileStats(account)

            when {
                state.detailLoading -> LinearProfileProgress("Loading profile details")
                state.staleDetails && state.detailError != null -> ProfileStatus(
                    message = "Showing saved profile details. ${state.detailError}",
                    action = "Retry",
                    onAction = onRefresh,
                )
                state.detailError != null -> ProfileStatus(
                    message = state.detailError,
                    action = "Retry",
                    onAction = onRefresh,
                )
            }

            if (!isSelf && state.relationshipError != null) {
                ProfileStatus(
                    message = state.relationshipError,
                    action = "Retry",
                    onAction = onRefresh,
                )
            }

            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun ProfileRedirectBanner(
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
private fun ProfileStats(account: Account) {
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
private fun ProfileDetails(account: Account) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag("profile_details"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Profile details", style = MaterialTheme.typography.titleLarge)
        if (account.profileFields.isEmpty()) {
            Text(
                "No additional profile details.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            account.profileFields.forEachIndexed { index, field ->
                val context = LocalContext.current
                val clickable = field.value.isWebAddress()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .then(
                            if (clickable) Modifier.clickable { openExternal(context, field.value) }
                            else Modifier,
                        )
                        .padding(vertical = 4.dp)
                        .testTag("profile_field_$index"),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    InlineEmojiText(
                        text = field.name,
                        emoji = account.emoji,
                        modifier = Modifier.width(112.dp),
                        style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                    )
                    InlineEmojiText(
                        text = field.value,
                        emoji = account.emoji,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = if (clickable) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface,
                        ),
                    )
                }
                if (index < account.profileFields.lastIndex) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f))
                }
            }
        }
    }
}

@Composable
private fun ProfileBadge(text: String) {
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
private fun LinearProfileProgress(description: String) {
    Column(Modifier.fillMaxWidth().padding(top = 12.dp).testTag("profile_detail_loading")) {
        Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        androidx.compose.material3.LinearProgressIndicator(Modifier.fillMaxWidth())
    }
}

@Composable
private fun ProfileStatus(
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

private fun formatProfileCount(value: Long): String = when {
    value >= 1_000_000 -> "%.1fM".format(value / 1_000_000.0)
    value >= 1_000 -> "%.1fK".format(value / 1_000.0)
    else -> value.toString()
}

private fun Account.hasUsableProfileIdentity(): Boolean = id.localId.isNotBlank() &&
    (displayName.isNotBlank() || handle.removePrefix("@").substringBefore("@").isNotBlank() || avatarUrl != null)

private fun String.isWebAddress(): Boolean =
    startsWith("https://") || startsWith("http://")
