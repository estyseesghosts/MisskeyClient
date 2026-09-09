@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package me.foxtails.palustris.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.asPaddingValues
import me.foxtails.palustris.domain.OwnedPost
import me.foxtails.palustris.domain.PostAction
import me.foxtails.palustris.ui.media.MediaOpenRequest
import me.foxtails.palustris.ui.motion.LocalPalustrisMotionScheme

@Composable
fun SavedPostsScreen(
    state: SavedPostsUiState,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onUnsave: (OwnedPost) -> Unit,
    onSignIn: () -> Unit = {},
    onUpgradePermissions: () -> Unit = {},
    onReact: (OwnedPost) -> Unit = {},
    onReply: (OwnedPost) -> Unit = {},
    onReshare: (OwnedPost) -> Unit = {},
    onReaction: (OwnedPost, me.foxtails.palustris.domain.EmojiChoice) -> Unit = { _, _ -> },
    onOpenReactionBubble: ((OwnedPost, Rect) -> Unit)? = null,
    onOpenReactionPicker: (OwnedPost) -> Unit = {},
    availableActions: Set<PostAction> = setOf(PostAction.Bookmark),
    onOpenProfile: (me.foxtails.palustris.domain.Account) -> Unit = {},
    onSearchHashtag: (String) -> Unit = {},
    onOpenHashtagBubble: ((OwnedPost, List<String>, Rect) -> Unit)? = null,
    onOpenMedia: (MediaOpenRequest) -> Unit = {},
) {
    val title = if (state.kind == me.foxtails.palustris.domain.SavedPostsKind.Favourites) "favourites" else "bookmarks"
    val refreshState = rememberPullToRefreshState()
    PullToRefreshBox(
        isRefreshing = state.loading,
        onRefresh = onRefresh,
        state = refreshState,
        modifier = Modifier.fillMaxSize(),
        indicator = {
            PullToRefreshDefaults.Indicator(
                state = refreshState,
                isRefreshing = state.loading,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        },
    ) {
        when {
            state.loading && state.posts.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            state.permissionRequired -> SavedPostsMessage(
                title = "Reauthorization required",
                body = "Allow access to your $title to view them here.",
                action = "Reauthorize",
                onAction = onUpgradePermissions,
            )
            state.error != null && state.posts.isEmpty() -> SavedPostsMessage(
                title = "Couldn’t load $title",
                body = state.error,
                action = if (state.needsSignIn) "Sign in again" else "Retry",
                onAction = if (state.needsSignIn) onSignIn else onRefresh,
            )
            state.posts.isEmpty() -> EmptyState(
                AppIcons.Bookmark,
                if (state.kind == me.foxtails.palustris.domain.SavedPostsKind.Favourites) "No favourites yet" else "No bookmarks yet",
                "Posts you save will appear here.",
            )
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 24.dp,
                ),
            ) {
                items(state.posts, key = { "${it.post.id.connection}/${it.post.id.value}" }) { ownedPost ->
                    PostRow(
                        ownedPost = ownedPost,
                        availableActions = availableActions,
                        onReact = onReact,
                        onReply = onReply,
                        onReshare = onReshare,
                        onBookmark = onUnsave,
                        onReaction = onReaction,
                        onOpenProfile = onOpenProfile,
                        onSearchHashtag = onSearchHashtag,
                         onOpenHashtagBubble = onOpenHashtagBubble,
                         onOpenReactionBubble = { target, bounds ->
                            onOpenReactionBubble?.invoke(target, bounds)
                        },
                        onOpenReactionPicker = onOpenReactionPicker,
                         onOpenMedia = onOpenMedia,
                         modifier = Modifier.animateItem(
                             fadeInSpec = LocalPalustrisMotionScheme.current.fastFadeIn,
                             fadeOutSpec = LocalPalustrisMotionScheme.current.fastFadeOut,
                             placementSpec = LocalPalustrisMotionScheme.current.gentleOffset,
                         ),
                     )
                    androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f))
                }
                item {
                    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        when {
                            state.loadingMore -> CircularProgressIndicator(Modifier.padding(8.dp))
                            state.error != null -> TextButton(onClick = onLoadMore) { Text("Retry") }
                            state.nextCursor != null -> TextButton(onClick = onLoadMore) { Text("Load older posts") }
                            else -> Text("You’re up to date", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SavedPostsMessage(title: String, body: String, action: String, onAction: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        androidx.compose.foundation.layout.Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(body, modifier = Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = onAction, modifier = Modifier.padding(top = 8.dp)) { Text(action) }
        }
    }
}
