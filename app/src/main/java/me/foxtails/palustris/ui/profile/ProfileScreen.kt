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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.AccountId
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
import me.foxtails.palustris.ui.components.FilterChipEntry
import me.foxtails.palustris.ui.components.FilterChipRow

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
    onEditProfile: () -> Unit = {},
    onOpenDrafts: () -> Unit = {},
    onOpenBookmarks: () -> Unit = {},
    onOpenProfile: (Account) -> Unit = {},
    onSearchHashtag: (String) -> Unit = {},
    availableActions: Set<PostAction> = emptySet(),
    onReact: (OwnedPost) -> Unit = {},
    onReply: (OwnedPost) -> Unit = {},
    onReshare: (OwnedPost) -> Unit = {},
    onBookmark: (OwnedPost) -> Unit = {},
    onReaction: (OwnedPost, String) -> Unit = { _, _ -> },
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
    val endContentClearance = if (compactLayout) {
        compactScrollEndClearance(
            controlStackHeight = CompactFilterDockHeight,
            navigationVisible = compactNavigationVisible,
            ime = WindowInsets.ime,
        )
    } else {
        0.dp
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
            header = {
                ProfileHeader(
                    account = displayedAccount,
                    state = profileState,
                    isSelf = isSelf,
                    onRefresh = onRefresh,
                    onFollow = onFollow,
                    onUnfollow = onUnfollow,
                    onEditProfile = onEditProfile,
                )
            },
            details = { ProfileDetails(displayedAccount) },
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
private fun ProfileHeader(
    account: Account,
    state: ProfileUiState,
    isSelf: Boolean,
    onRefresh: () -> Unit,
    onFollow: () -> Unit,
    onUnfollow: () -> Unit,
    onEditProfile: () -> Unit,
) {
    val statusBarHeight = with(LocalDensity.current) {
        WindowInsets.statusBars.getTop(this).toDp()
    }
    Column(Modifier.fillMaxWidth().testTag("profile_header")) {
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
                AccountAvatar(account, Modifier.padding(4.dp))
            }
        }

        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = account.displayName.ifBlank { account.handle },
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = account.handle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (!isSelf && state.relationshipSupported == true && state.relationship != null) {
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
                } else if (isSelf) {
                    OutlinedButton(
                        onClick = onEditProfile,
                        modifier = Modifier.testTag("profile_edit_action"),
                    ) {
                        Text("Edit profile")
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

            Text(
                text = account.biography.ifBlank { "No biography yet." },
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
                    Text(
                        field.name,
                        modifier = Modifier.width(112.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        field.value,
                        modifier = Modifier.weight(1f),
                        color = if (clickable) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface,
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

private fun String.isWebAddress(): Boolean =
    startsWith("https://") || startsWith("http://")
