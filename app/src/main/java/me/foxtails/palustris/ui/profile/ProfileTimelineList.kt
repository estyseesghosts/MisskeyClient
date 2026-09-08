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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.distinctUntilChanged
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.OwnedPost
import me.foxtails.palustris.domain.PostAction
import me.foxtails.palustris.domain.ProfileTimelineTab
import me.foxtails.palustris.ui.AppIcons
import me.foxtails.palustris.ui.ClientReadyPostActions
import me.foxtails.palustris.ui.EmptyState
import me.foxtails.palustris.ui.PostRow

@Composable
internal fun ProfileTimelineList(
    account: Account,
    state: ProfileUiState,
    compactLayout: Boolean,
    endContentClearance: Dp,
    onCategorySelected: (ProfileCategory) -> Unit,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onOpenProfile: (Account) -> Unit,
    onSearchHashtag: (String) -> Unit,
    availableActions: Set<PostAction>,
    onReact: (OwnedPost) -> Unit,
    onReply: (OwnedPost) -> Unit,
    onReshare: (OwnedPost) -> Unit,
    onBookmark: (OwnedPost) -> Unit,
    onReaction: (OwnedPost, String) -> Unit,
    header: @Composable () -> Unit,
    details: @Composable () -> Unit,
) {
    val list = rememberLazyListState()
    val currentState by rememberUpdatedState(state)
    val loadMore by rememberUpdatedState(onLoadMore)
    val selectedTab = state.selectedTab.timelineTab
    val page = selectedTab?.let { state.pages[it] }
    val pullToRefreshState = rememberPullToRefreshState()

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
                    ProfileCategoryChips(state.selectedTab, onCategorySelected)
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
                onOpenProfile = onOpenProfile,
                onSearchHashtag = onSearchHashtag,
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
                    onOpenProfile = onOpenProfile,
                    onSearchHashtag = onSearchHashtag,
                )
            }
        }
    }
}

@Composable
private fun ProfileCategoryChips(
    selected: ProfileCategory,
    onCategorySelected: (ProfileCategory) -> Unit,
) {
    me.foxtails.palustris.ui.components.CategoryChips(
        titles = ProfileCategory.entries.map(ProfileCategory::label),
        selected = ProfileCategory.entries.indexOf(selected),
        rowContentDescription = PROFILE_CATEGORY_DESCRIPTION,
        onSelect = { index -> onCategorySelected(ProfileCategory.entries[index]) },
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
    onReaction: (OwnedPost, String) -> Unit,
    onOpenProfile: (Account) -> Unit,
    onSearchHashtag: (String) -> Unit,
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
                onOpenProfile = onOpenProfile,
                onSearchHashtag = onSearchHashtag,
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
    onReaction: (OwnedPost, String) -> Unit,
    onOpenProfile: (Account) -> Unit,
    onSearchHashtag: (String) -> Unit,
) {
    if (page == null || page.initialLoading && page.posts.isEmpty()) {
        item(key = "profile-timeline-loading") {
            Box(Modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        return
    }
    if (page.error != null && page.posts.isEmpty()) {
        item(key = "profile-timeline-error") {
            ProfileMessage(
                title = if (page.needsSignIn) "Sign-in required" else "Profile posts unavailable",
                message = page.error,
                action = if (page.needsSignIn) "Try again" else "Retry",
                onAction = onRefresh,
            )
        }
    }
    if (page.posts.isEmpty() && page.error == null && !page.refreshing) {
        item(key = "profile-timeline-empty") {
            EmptyState(AppIcons.Person, "No posts in this view", "This profile has no matching posts yet.")
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
            onOpenProfile = onOpenProfile,
            onSearchHashtag = onSearchHashtag,
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
