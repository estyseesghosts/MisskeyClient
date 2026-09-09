@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package me.foxtails.palustris.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.distinctUntilChanged
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.EmojiChoice
import me.foxtails.palustris.domain.OwnedPost
import me.foxtails.palustris.domain.PostAction
import me.foxtails.palustris.domain.ProfileTimelineTab
import me.foxtails.palustris.R
import me.foxtails.palustris.ui.AppIcons
import me.foxtails.palustris.ui.ClientReadyPostActions
import me.foxtails.palustris.ui.EmptyState
import me.foxtails.palustris.ui.PostRow
import me.foxtails.palustris.ui.components.FilterChipEntry
import me.foxtails.palustris.ui.components.FilterChipRow
import me.foxtails.palustris.ui.media.MediaOpenRequest
import me.foxtails.palustris.ui.motion.AnimatedStatePane
import me.foxtails.palustris.ui.motion.LocalPalustrisMotionScheme

@Composable
internal fun ProfileTimelineList(
    account: Account,
    state: ProfileUiState,
    compactLayout: Boolean,
    endContentClearance: Dp,
    isSelf: Boolean,
    onCategorySelected: (ProfileCategory) -> Unit,
    onOpenDrafts: () -> Unit,
    onOpenBookmarks: () -> Unit,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onOpenProfile: (Account) -> Unit,
    onSearchHashtag: (String) -> Unit,
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
    onOpenUrl: ((String) -> Unit)?,
    onOpenUsername: ((String) -> Unit)?,
    header: @Composable () -> Unit,
    details: @Composable () -> Unit,
) {
    val list = rememberLazyListState()
    val currentState by rememberUpdatedState(state)
    val loadMore by rememberUpdatedState(onLoadMore)
    val selectedTab = state.selectedTab.timelineTab
    val page = selectedTab?.let { state.pages[it] }
    val pullToRefreshState = rememberPullToRefreshState()
    val firstPostId = page?.posts?.firstOrNull()?.post?.id

    LaunchedEffect(state.targetId, selectedTab, firstPostId, state.pinnedPosts.size, compactLayout) {
        list.scrollToItem(
            firstTimelineItemIndex(
                compactLayout = compactLayout,
                pinnedLoading = state.pinnedLoading,
                pinnedError = state.pinnedError != null,
                pinnedPostCount = state.pinnedPosts.size,
                hasFirstPost = firstPostId != null,
            ),
        )
    }

    LaunchedEffect(list, state.targetId, selectedTab) {
        snapshotFlow {
            val currentPage = currentState.selectedTab.timelineTab?.let { currentState.pages[it] }
            val nearEnd = (list.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1) >=
                (list.layoutInfo.totalItemsCount - 5).coerceAtLeast(0)
            currentPage?.nextCursor != null &&
                currentPage.error == null &&
                !currentPage.initialLoading &&
                !currentPage.refreshing &&
                !currentPage.loadingMore &&
                currentPage.consecutiveEmptyPages < MAX_AUTOMATIC_EMPTY_PAGES &&
                nearEnd
        }.distinctUntilChanged().collect { if (it) loadMore() }
    }

    PullToRefreshBox(
        isRefreshing = page?.refreshing == true,
        onRefresh = onRefresh,
        state = pullToRefreshState,
        modifier = Modifier.fillMaxSize().testTag("profile_content"),
        indicator = {
            PullToRefreshDefaults.Indicator(
                state = pullToRefreshState,
                isRefreshing = page?.refreshing == true,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        },
    ) {
        LazyColumn(
            state = list,
            modifier = Modifier.fillMaxSize().testTag("profile_timeline_list"),
            contentPadding = PaddingValues(bottom = endContentClearance + 24.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            item(key = "profile-header") { header() }
            if (!compactLayout) {
                item(key = "profile-categories") {
                    ProfileCategoryChips(
                        selected = state.selectedTab,
                        isSelf = isSelf,
                        onCategorySelected = onCategorySelected,
                        onOpenDrafts = onOpenDrafts,
                        onOpenBookmarks = onOpenBookmarks,
                    )
                }
            }
            profilePinnedItems(
                state = state,
                onRefresh = onRefresh,
                availableActions = availableActions,
                onReact = onReact,
                onReply = onReply,
                onReshare = onReshare,
                onBookmark = onBookmark,
                onReaction = onReaction,
                onOpenReactionBubble = { ownedPost, bounds -> onOpenReactionBubble?.invoke(ownedPost, bounds) },
                onOpenReactionPicker = onOpenReactionPicker,
                onOpenProfile = onOpenProfile,
                onSearchHashtag = onSearchHashtag,
                 onOpenHashtagBubble = onOpenHashtagBubble,
                      onOpenMedia = onOpenMedia,
                      onOpenPost = onOpenPost,
                      onOpenUrl = onOpenUrl,
                      onOpenUsername = onOpenUsername,
             )
            if (state.selectedTab == ProfileCategory.ShowMore) {
                item(key = "profile-details") { details() }
            } else {
                profilePageItems(
                    page = page,
                    onRefresh = onRefresh,
                    onLoadMore = onLoadMore,
                    availableActions = availableActions.intersect(ClientReadyPostActions),
                    onReact = onReact,
                    onReply = onReply,
                    onReshare = onReshare,
                    onBookmark = onBookmark,
                    onReaction = onReaction,
                    onOpenReactionBubble = { ownedPost, bounds -> onOpenReactionBubble?.invoke(ownedPost, bounds) },
                    onOpenReactionPicker = onOpenReactionPicker,
                    onOpenProfile = onOpenProfile,
                    onSearchHashtag = onSearchHashtag,
                     onOpenHashtagBubble = onOpenHashtagBubble,
                      onOpenMedia = onOpenMedia,
                      onOpenPost = onOpenPost,
                      onOpenUrl = onOpenUrl,
                      onOpenUsername = onOpenUsername,
                 )
            }
        }
    }
}

@Composable
private fun ProfileCategoryChips(
    selected: ProfileCategory,
    isSelf: Boolean,
    onCategorySelected: (ProfileCategory) -> Unit,
    onOpenDrafts: () -> Unit,
    onOpenBookmarks: () -> Unit,
) {
    FilterChipRow(
        entries = profileChipEntries(isSelf).map { entry ->
            when (entry) {
                is ProfileChipEntry.Timeline -> FilterChipEntry(
                    label = entry.category.label,
                    selected = entry.category == selected,
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
        rowContentDescription = PROFILE_CATEGORY_DESCRIPTION,
    )
}

private fun LazyListScope.profilePinnedItems(
    state: ProfileUiState,
    onRefresh: () -> Unit,
    availableActions: Set<PostAction>,
    onReact: (OwnedPost) -> Unit,
    onReply: (OwnedPost) -> Unit,
    onReshare: (OwnedPost) -> Unit,
    onBookmark: (OwnedPost) -> Unit,
    onReaction: (OwnedPost, EmojiChoice) -> Unit,
    onOpenReactionBubble: ((OwnedPost, Rect) -> Unit)?,
    onOpenReactionPicker: (OwnedPost) -> Unit,
    onOpenProfile: (Account) -> Unit,
    onSearchHashtag: (String) -> Unit,
    onOpenHashtagBubble: ((OwnedPost, List<String>, Rect) -> Unit)?,
    onOpenMedia: (MediaOpenRequest) -> Unit,
    onOpenPost: (OwnedPost) -> Unit,
    onOpenUrl: ((String) -> Unit)?,
    onOpenUsername: ((String) -> Unit)?,
) {
    if (state.pinnedLoading) item(key = "profile-pinned-loading") {
        Text("Featured posts", Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp), style = MaterialTheme.typography.titleMedium)
        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(Modifier.size(24.dp))
        }
    }
    if (state.pinnedError != null) item(key = "profile-pinned-error") {
        ProfileMessage(
            title = "Featured posts unavailable",
            message = state.pinnedError,
            action = "Retry",
            onAction = onRefresh,
        )
    }
    if (state.pinnedPosts.isNotEmpty()) {
        item(key = "profile-pinned-title") {
            Text("Featured posts", Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp), style = MaterialTheme.typography.titleMedium)
        }
        items(state.pinnedPosts, key = { "pinned/${it.post.id.connection}/${it.post.id.value}" }) { ownedPost ->
            PostRow(
                ownedPost = ownedPost,
                availableActions = availableActions,
                onReact = onReact,
                onReply = onReply,
                onReshare = onReshare,
                onBookmark = onBookmark,
                onReaction = onReaction,
                onOpenReactionBubble = { ownedPost, bounds -> onOpenReactionBubble?.invoke(ownedPost, bounds) },
                onOpenReactionPicker = onOpenReactionPicker,
                onOpenProfile = onOpenProfile,
                onSearchHashtag = onSearchHashtag,
                     onOpenHashtagBubble = onOpenHashtagBubble,
                      onOpenMedia = onOpenMedia,
                      onOpenPost = onOpenPost,
                      onOpenUrl = onOpenUrl,
                      onOpenUsername = onOpenUsername,
                modifier = Modifier.animateItem(
                    fadeInSpec = LocalPalustrisMotionScheme.current.fastFadeIn,
                    fadeOutSpec = LocalPalustrisMotionScheme.current.fastFadeOut,
                    placementSpec = LocalPalustrisMotionScheme.current.gentleOffset,
                ),
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f))
        }
    }
}

private fun LazyListScope.profilePageItems(
    page: ProfilePageState?,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    availableActions: Set<PostAction>,
    onReact: (OwnedPost) -> Unit,
    onReply: (OwnedPost) -> Unit,
    onReshare: (OwnedPost) -> Unit,
    onBookmark: (OwnedPost) -> Unit,
    onReaction: (OwnedPost, EmojiChoice) -> Unit,
    onOpenReactionBubble: ((OwnedPost, Rect) -> Unit)?,
    onOpenReactionPicker: (OwnedPost) -> Unit,
    onOpenProfile: (Account) -> Unit,
    onSearchHashtag: (String) -> Unit,
    onOpenHashtagBubble: ((OwnedPost, List<String>, Rect) -> Unit)?,
    onOpenMedia: (MediaOpenRequest) -> Unit,
    onOpenPost: (OwnedPost) -> Unit,
    onOpenUrl: ((String) -> Unit)?,
    onOpenUsername: ((String) -> Unit)?,
) {
    if (page == null || page.initialLoading && page.posts.isEmpty()) {
        item(key = "profile-timeline-loading") {
            AnimatedStatePane(stateKey = "loading", modifier = Modifier.fillMaxWidth()) {
                Box(Modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        }
        return
    }
    if (page.error != null && page.posts.isEmpty()) {
        item(key = "profile-timeline-error") {
            AnimatedStatePane(stateKey = "error", modifier = Modifier.fillMaxWidth()) {
                ProfileMessage(
                    title = if (page.needsSignIn) "Sign-in required" else "Profile posts unavailable",
                    message = page.error,
                    action = if (page.needsSignIn) "Try again" else "Retry",
                    onAction = onRefresh,
                )
            }
        }
    }
    if (page.posts.isEmpty() && page.error == null && !page.refreshing) {
        item(key = "profile-timeline-empty") {
            AnimatedStatePane(stateKey = "empty", modifier = Modifier.fillMaxWidth()) {
                EmptyState(AppIcons.Person, "No posts in this view", "This profile has no matching posts yet.")
            }
        }
    }
    items(page.posts, key = { "timeline/${it.post.id.connection}/${it.post.id.value}" }) { ownedPost ->
        PostRow(
            ownedPost = ownedPost,
            availableActions = availableActions,
            onReact = onReact,
            onReply = onReply,
            onReshare = onReshare,
            onBookmark = onBookmark,
            onReaction = onReaction,
            onOpenReactionBubble = { ownedPost, bounds -> onOpenReactionBubble?.invoke(ownedPost, bounds) },
            onOpenReactionPicker = onOpenReactionPicker,
            onOpenProfile = onOpenProfile,
            onSearchHashtag = onSearchHashtag,
                     onOpenHashtagBubble = onOpenHashtagBubble,
                      onOpenMedia = onOpenMedia,
                      onOpenPost = onOpenPost,
                      onOpenUrl = onOpenUrl,
                      onOpenUsername = onOpenUsername,
            modifier = Modifier.animateItem(
                fadeInSpec = LocalPalustrisMotionScheme.current.fastFadeIn,
                fadeOutSpec = LocalPalustrisMotionScheme.current.fastFadeOut,
                placementSpec = LocalPalustrisMotionScheme.current.gentleOffset,
            ),
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f))
    }
    if (page.loadingMore) item(key = "profile-timeline-loading-more") {
        Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(Modifier.size(24.dp))
        }
    }
    if (page.posts.isNotEmpty() && page.error != null && !page.loadingMore) item(key = "profile-timeline-inline-error") {
        ProfileMessage(
            title = "Couldn’t load more posts",
            message = page.error,
            action = if (page.needsSignIn) "Try again" else "Retry",
            onAction = if (page.needsSignIn) onRefresh else onLoadMore,
        )
    }
    if (!page.loadingMore && page.error == null) item(key = "profile-timeline-footer") {
        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
            if (page.nextCursor != null) {
                TextButton(onClick = onLoadMore) {
                    Text(if (page.consecutiveEmptyPages > 0) "Continue browsing" else "Load older posts")
                }
            } else {
                Text("You're up to date", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ProfileMessage(
    title: String,
    message: String,
    action: String,
    onAction: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(16.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(message, Modifier.padding(top = 4.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = onAction, Modifier.padding(top = 4.dp)) { Text(action) }
    }
}

private const val MAX_AUTOMATIC_EMPTY_PAGES = 3
private const val PROFILE_CATEGORY_DESCRIPTION = "Profile categories; swipe horizontally for more"

private fun firstTimelineItemIndex(
    compactLayout: Boolean,
    pinnedLoading: Boolean,
    pinnedError: Boolean,
    pinnedPostCount: Int,
    hasFirstPost: Boolean,
): Int {
    if (!hasFirstPost) return 0
    var index = 1 // profile header
    if (!compactLayout) index++
    if (pinnedLoading) index++
    if (pinnedError) index++
    if (pinnedPostCount > 0) index += 1 + pinnedPostCount // title and pinned rows
    return index
}
