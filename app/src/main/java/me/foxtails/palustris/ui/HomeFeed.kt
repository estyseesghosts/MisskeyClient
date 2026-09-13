@file:OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
)

package me.foxtails.palustris.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.distinctUntilChanged
import me.foxtails.palustris.R
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.ContentWarningPolicy
import me.foxtails.palustris.domain.ContentWarningRules
import me.foxtails.palustris.domain.OwnedPost
import me.foxtails.palustris.domain.PostAction
import me.foxtails.palustris.ui.emoji.EmojiCatalogState
import me.foxtails.palustris.ui.layout.LegacyFeedBottomClearance
import me.foxtails.palustris.ui.layout.compactHomeScrollEndClearance
import me.foxtails.palustris.ui.large.LargeBottomDock
import me.foxtails.palustris.ui.media.MediaOpenRequest
import me.foxtails.palustris.ui.motion.AnimatedStatePane
import me.foxtails.palustris.ui.motion.LocalPalustrisMotionScheme

@Composable
fun HomeFeed(
    state: FeedState,
    compactLayout: Boolean = true,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onSignIn: () -> Unit,
    ownedPosts: List<OwnedPost> = state.ownedPosts,
    onScrollDirectionChanged: (Boolean) -> Unit = {},
    onReact: (OwnedPost) -> Unit = {},
    onReply: (OwnedPost) -> Unit = {},
    onReshare: (OwnedPost) -> Unit = {},
    onBookmark: (OwnedPost) -> Unit = {},
    onReaction: (OwnedPost, me.foxtails.palustris.domain.EmojiChoice) -> Unit = { _, _ -> },
    onOpenReactionBubble: ((OwnedPost, Rect) -> Unit)? = null,
    onOpenReactionPicker: (OwnedPost) -> Unit = {},
    onQuote: (OwnedPost) -> Unit = {},
    onOpenProfile: (Account) -> Unit = {},
    onSearchHashtag: (String) -> Unit = {},
    onOpenHashtagBubble: ((OwnedPost, List<String>, Rect) -> Unit)? = null,
    onOpenMedia: (MediaOpenRequest) -> Unit = {},
    onOpenPost: ((OwnedPost) -> Unit)? = null,
    onOpenUrl: ((String) -> Unit)? = null,
    onOpenUsername: ((String) -> Unit)? = null,
    listState: LazyListState? = null,
    topContentPadding: Dp? = null,
    bottomContentClearance: Dp? = null,
    refreshIndicatorTopPadding: Dp? = null,
    bottomDock: (@Composable () -> Unit)? = null,
    cleanTrackingParameters: Boolean = false,
    contentWarningRules: ContentWarningRules = LocalContentWarningRules.current,
) {
    val list = listState ?: rememberLazyListState()
    val pullToRefreshState = rememberPullToRefreshState()
    var fallbackBubbleTarget by remember { mutableStateOf<PostActionBubbleTarget?>(null) }
    val openHashtagBubble: (OwnedPost, List<String>, Rect) -> Unit = { ownedPost, hashtags, bounds ->
        if (onOpenHashtagBubble != null) onOpenHashtagBubble(ownedPost, hashtags, bounds)
        else fallbackBubbleTarget = PostActionBubbleTarget.HashtagList(ownedPost.post.id, hashtags, bounds)
    }
    val openReactionBubble: (OwnedPost, Rect) -> Unit = { ownedPost, bounds ->
        onOpenReactionBubble?.invoke(ownedPost, bounds)
        if (onOpenReactionBubble == null) onOpenReactionPicker(ownedPost)
    }
    val currentState by rememberUpdatedState(state)
    val loadMore by rememberUpdatedState(onLoadMore)
    val scrollDirectionChanged by rememberUpdatedState(onScrollDirectionChanged)
    val scheme = LocalPalustrisMotionScheme.current
    val mutedHashtags = LocalMutedHashtags.current
    val hasOwnership = ownedPosts.isNotEmpty()
    val rows = if (hasOwnership) ownedPosts else state.posts.map { OwnedPost(it.author.id, it) }
    val visibleRows = rows.filterNot { ownedPost -> ContentWarningPolicy.matchesHashtagMute(postHashtags(ownedPost.post.text, ownedPost.post.emoji), mutedHashtags) }
    val statePaneKey = when {
        state.error != null -> "error"
        state.posts.isEmpty() && state.loading -> "loading"
        state.posts.isEmpty() -> "empty"
        else -> "feed"
    }
    LaunchedEffect(list, visibleRows) {
        snapshotFlow {
            val s = currentState
            val lastVisibleIndex = list.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            s.nextCursor != null && visibleRows.isNotEmpty() && !s.loading && !s.loadingMore && s.error == null && lastVisibleIndex >= visibleRows.size - 5
        }.distinctUntilChanged().collect { if (it) loadMore() }
    }
    LaunchedEffect(list) {
        var previousIndex = list.firstVisibleItemIndex
        var previousOffset = list.firstVisibleItemScrollOffset
        snapshotFlow { list.firstVisibleItemIndex to list.firstVisibleItemScrollOffset }.distinctUntilChanged().collect { (index, offset) ->
            when {
                index > previousIndex || (index == previousIndex && offset > previousOffset) -> scrollDirectionChanged(false)
                index < previousIndex || (index == previousIndex && offset < previousOffset) -> scrollDirectionChanged(true)
            }
            previousIndex = index
            previousOffset = offset
        }
    }
    AnimatedStatePane(statePaneKey, Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize()) {
            PullToRefreshBox(
                isRefreshing = state.loading,
                onRefresh = onRefresh,
                state = pullToRefreshState,
                modifier = Modifier.fillMaxSize().testTag("home_feed_content"),
                indicator = { PullToRefreshDefaults.Indicator(state = pullToRefreshState, isRefreshing = state.loading, modifier = Modifier.align(Alignment.TopCenter).padding(top = refreshIndicatorTopPadding ?: 96.dp)) },
            ) {
                LazyColumn(
                    state = list,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = topContentPadding ?: 96.dp, bottom = bottomContentClearance ?: if (compactLayout) compactHomeScrollEndClearance() else LegacyFeedBottomClearance),
                ) {
                    if (state.error != null) item {
                        Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth().padding(16.dp), shape = MaterialTheme.shapes.large) {
                            Column(Modifier.padding(16.dp)) {
                                Text(state.error, color = MaterialTheme.colorScheme.onErrorContainer)
                                TextButton(onClick = if (state.needsSignIn) onSignIn else onRefresh) { Text(if (state.needsSignIn) stringResource(R.string.feed_sign_in_again) else stringResource(R.string.notifications_retry)) }
                            }
                        }
                    }
                    if (state.posts.isEmpty() && !state.loading && state.error == null) item { Box(Modifier.fillParentMaxSize()) { EmptyState(AppIcons.Home, stringResource(R.string.feed_empty_title), stringResource(R.string.feed_empty_subtitle)) } }
                    val enabledActions = if (hasOwnership) state.actions.intersect(ClientReadyPostActions) else emptySet()
                    items(visibleRows, key = { "${it.post.id.connection}/${it.post.id.value}" }) { ownedPost ->
                        Column(Modifier.animateItem(fadeInSpec = scheme.fastFadeIn, fadeOutSpec = scheme.fastFadeOut, placementSpec = scheme.gentleOffset)) {
                            PostRow(ownedPost, enabledActions, onReact, onReply, onReshare, onBookmark, onReaction, onOpenProfile, onSearchHashtag, onOpenHashtagBubble = openHashtagBubble, quoteEnabled = state.quoteStatus == me.foxtails.palustris.domain.CapabilityStatus.Supported, onQuote = onQuote, onOpenReactionBubble = openReactionBubble, onOpenReactionPicker = onOpenReactionPicker, onOpenMedia = onOpenMedia, onOpenPost = onOpenPost, largeLayout = !compactLayout, onOpenUrl = onOpenUrl, onOpenUsername = onOpenUsername, contentWarningRules = contentWarningRules)
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f))
                        }
                    }
                    if (state.loadingMore) item { Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(24.dp)) } }
                    if (state.posts.isNotEmpty() && !state.loadingMore && state.error == null) item {
                        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                            if (state.nextCursor != null) TextButton(onClick = onLoadMore) { Text(stringResource(R.string.feed_load_older)) }
                            else Text(stringResource(R.string.feed_up_to_date), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            bottomDock?.let { LargeBottomDock(content = it, modifier = Modifier.align(Alignment.BottomStart)) }
        }
    }
    if (onOpenHashtagBubble == null) PostActionBubbleHost(target = fallbackBubbleTarget, emojiCatalog = EmojiCatalogState(), emojiCapabilities = me.foxtails.palustris.domain.EmojiCapabilities(), onDismiss = { fallbackBubbleTarget = null }, onHashtagSelected = onSearchHashtag, onReactionSelected = { _, _ -> }, hashtagBottomClearance = if (compactLayout) compactHomeScrollEndClearance() else 0.dp)
}
