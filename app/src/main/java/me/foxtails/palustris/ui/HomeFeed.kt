@file:OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
)

package me.foxtails.palustris.ui

import android.content.Context
import android.content.Intent
import android.text.format.DateUtils
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import coil.compose.AsyncImage
import coil.request.ImageRequest
import me.foxtails.palustris.data.media.MediaImageLoader
import kotlinx.coroutines.flow.distinctUntilChanged
import me.foxtails.palustris.R
import me.foxtails.palustris.domain.*
import me.foxtails.palustris.ui.emoji.AccountDisplayName
import me.foxtails.palustris.ui.emoji.CustomEmojiImage
import me.foxtails.palustris.ui.emoji.InlineEmojiText
import me.foxtails.palustris.ui.media.MediaOpenRequest
import me.foxtails.palustris.ui.media.PostMediaCarousel
import me.foxtails.palustris.ui.motion.AnimatedStatePane
import me.foxtails.palustris.ui.motion.ExpandableContent
import me.foxtails.palustris.ui.motion.LocalPalustrisMotionScheme
import me.foxtails.palustris.ui.motion.PopEffect
import me.foxtails.palustris.ui.motion.rememberSelectedColor
import me.foxtails.palustris.ui.motion.springPress
import me.foxtails.palustris.ui.large.LargeBottomDock

internal fun openExternal(context: Context, url: String?) {
    val uri = url?.toUri() ?: return
    if (uri.scheme !in listOf("https", "http") || uri.host.isNullOrBlank()) return
    try { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
    catch (_: android.content.ActivityNotFoundException) { Toast.makeText(context, context.getString(R.string.error_no_app_open_link), Toast.LENGTH_SHORT).show() }
}

private val PostMetadataVerticalPadding = 2.dp * 1.06f
private val PostChromeHeight = 44.dp + (PostMetadataVerticalPadding * 2f)
private val PostInteractionRowHeight = 48.dp
private val PostInteractionIconSize = 24.dp

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
    onReaction: (OwnedPost, EmojiChoice) -> Unit = { _, _ -> },
    onOpenReactionBubble: ((OwnedPost, Rect) -> Unit)? = null,
    onOpenReactionPicker: (OwnedPost) -> Unit = {},
    onQuote: (OwnedPost) -> Unit = {},
    onOpenProfile: (Account) -> Unit = {},
    onSearchHashtag: (String) -> Unit = {},
    onOpenHashtagBubble: ((OwnedPost, List<String>, Rect) -> Unit)? = null,
    onOpenMedia: (MediaOpenRequest) -> Unit = {},
    onOpenPost: (OwnedPost) -> Unit = {},
    onOpenUrl: ((String) -> Unit)? = null,
    onOpenUsername: ((String) -> Unit)? = null,
    listState: LazyListState? = null,
    topContentPadding: Dp? = null,
    bottomContentClearance: Dp? = null,
    refreshIndicatorTopPadding: Dp? = null,
    bottomDock: (@Composable () -> Unit)? = null,
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
        onOpenReactionPicker(ownedPost)
    }
    val currentState by rememberUpdatedState(state)
    val loadMore by rememberUpdatedState(onLoadMore)
    val scrollDirectionChanged by rememberUpdatedState(onScrollDirectionChanged)
    val scheme = LocalPalustrisMotionScheme.current
    val statePaneKey = when {
        state.error != null -> "error"
        state.posts.isEmpty() && state.loading -> "loading"
        state.posts.isEmpty() -> "empty"
        else -> "feed"
    }
    LaunchedEffect(list) {
        snapshotFlow {
            val s = currentState
            s.nextCursor != null && !s.loading && !s.loadingMore && s.error == null &&
                (list.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1) >= s.posts.size - 5
        }.distinctUntilChanged().collect { if (it) loadMore() }
    }
    LaunchedEffect(list) {
        var previousIndex = list.firstVisibleItemIndex
        var previousOffset = list.firstVisibleItemScrollOffset
        snapshotFlow { list.firstVisibleItemIndex to list.firstVisibleItemScrollOffset }
            .distinctUntilChanged()
            .collect { (index, offset) ->
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
                indicator = {
                    PullToRefreshDefaults.Indicator(
                        state = pullToRefreshState,
                        isRefreshing = state.loading,
                        modifier = Modifier.align(Alignment.TopCenter).padding(top = refreshIndicatorTopPadding ?: 96.dp),
                    )
                },
            ) {
            LazyColumn(
                state = list,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = topContentPadding ?: 96.dp,
                    // Floating controls clear the scroll range without shortening the viewport.
                    bottom = bottomContentClearance ?: if (compactLayout) compactHomeScrollEndClearance() else LegacyFeedBottomClearance,
                ),
            ) {
            if (state.error != null) item {
                Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth().padding(16.dp), shape = MaterialTheme.shapes.large) {
                    Column(Modifier.padding(16.dp)) {
                        Text(state.error, color = MaterialTheme.colorScheme.onErrorContainer)
                        TextButton(onClick = if (state.needsSignIn) onSignIn else onRefresh) {
                            Text(if (state.needsSignIn) stringResource(R.string.feed_sign_in_again) else stringResource(R.string.notifications_retry))
                        }
                    }
                }
            }
            if (state.posts.isEmpty() && !state.loading && state.error == null) item {
                Box(Modifier.fillParentMaxSize()) { EmptyState(AppIcons.Home, stringResource(R.string.feed_empty_title), stringResource(R.string.feed_empty_subtitle)) }
            }
            val hasOwnership = ownedPosts.isNotEmpty()
            val rows = if (hasOwnership) ownedPosts else state.posts.map { OwnedPost(it.author.id, it) }
            val enabledActions = if (hasOwnership) state.actions.intersect(ClientReadyPostActions) else emptySet()
            items(rows, key = { "${it.post.id.connection}/${it.post.id.value}" }) { ownedPost ->
                Column(
                    Modifier.animateItem(
                        fadeInSpec = scheme.fastFadeIn,
                        fadeOutSpec = scheme.fastFadeOut,
                        placementSpec = scheme.gentleOffset,
                    ),
                ) {
                    PostRow(
                        ownedPost,
                        enabledActions,
                        onReact,
                        onReply,
                        onReshare,
                        onBookmark,
                        onReaction,
                        onOpenProfile,
                        onSearchHashtag,
                        onOpenHashtagBubble = openHashtagBubble,
                        quoteEnabled = state.quoteStatus == me.foxtails.palustris.domain.CapabilityStatus.Supported,
                        onQuote = onQuote,
                         onOpenReactionBubble = openReactionBubble,
                        onOpenMedia = onOpenMedia,
                        onOpenPost = onOpenPost,
                        largeLayout = !compactLayout,
                        onOpenUrl = onOpenUrl,
                        onOpenUsername = onOpenUsername,
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f))
                }
            }
            if (state.loadingMore) item {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(24.dp)) }
            }
            if (state.posts.isNotEmpty() && !state.loadingMore && state.error == null) item {
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    if (state.nextCursor != null) TextButton(onClick = onLoadMore) { Text(stringResource(R.string.feed_load_older)) }
                    else Text(stringResource(R.string.feed_up_to_date), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
                }
            }
            bottomDock?.let { dock ->
                LargeBottomDock(
                    content = dock,
                    modifier = Modifier.align(Alignment.BottomStart),
                )
            }
        }
    }
    if (onOpenHashtagBubble == null) {
        PostActionBubbleHost(
            target = fallbackBubbleTarget,
            emojiCatalog = me.foxtails.palustris.ui.emoji.EmojiCatalogState(),
            emojiCapabilities = me.foxtails.palustris.domain.EmojiCapabilities(),
            onDismiss = { fallbackBubbleTarget = null },
            onHashtagSelected = onSearchHashtag,
            onReactionSelected = { _, _ -> },
            hashtagBottomClearance = if (compactLayout) compactHomeScrollEndClearance() else 0.dp,
        )
    }
}

@Composable
fun AccountAvatar(account: Account, modifier: Modifier = Modifier, exposeSemantics: Boolean = true) {
    val context = LocalContext.current
    val mediaImageLoader = remember(context) { MediaImageLoader.get(context) }
    val avatarRequest = remember(context, account.avatarUrl) {
        ImageRequest.Builder(context)
            .data(account.avatarUrl)
            // Avoid starting an animation while a recycled timeline row is scrolling into view.
            .crossfade(false)
            .build()
    }
    val avatarDescription = stringResource(R.string.post_profile_picture, account.displayName)
    Box(
        modifier
            .clip(CircleShape)
            .then(if (exposeSemantics) Modifier.semantics(mergeDescendants = true) {
                contentDescription = avatarDescription
            } else Modifier),
    ) {
        Avatar(Modifier.fillMaxSize(), description = null)
        AsyncImage(
            model = avatarRequest,
            imageLoader = mediaImageLoader.imageLoader,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize())
    }
}

@Composable
internal fun PostRow(
    ownedPost: OwnedPost,
    availableActions: Set<PostAction>,
    onReact: (OwnedPost) -> Unit,
    onReply: (OwnedPost) -> Unit,
    onReshare: (OwnedPost) -> Unit,
    onBookmark: (OwnedPost) -> Unit,
    onReaction: (OwnedPost, EmojiChoice) -> Unit,
    onOpenProfile: ((Account) -> Unit)?,
    onSearchHashtag: ((String) -> Unit)?,
    onOpenHashtagBubble: ((OwnedPost, List<String>, Rect) -> Unit)? = null,
    quoteEnabled: Boolean = false,
    onQuote: (OwnedPost) -> Unit = {},
    onOpenReactionBubble: (OwnedPost, Rect) -> Unit = { _, _ -> },
    onOpenReactionPicker: (OwnedPost) -> Unit = {},
    onOpenMedia: (MediaOpenRequest) -> Unit = {},
    modifier: Modifier = Modifier,
    truncateBody: Boolean = true,
    onOpenPost: (OwnedPost) -> Unit = {},
    largeLayout: Boolean = false,
    onOpenUrl: ((String) -> Unit)? = null,
    onOpenUsername: ((String) -> Unit)? = null,
) {
    val post = ownedPost.post
    val context = LocalContext.current
    var expanded by rememberSaveable(post.id.connection, post.id.value) { mutableStateOf(false) }
    val presentation = remember(post.text, post.emoji) { parseHashtagBlocks(post.text, post.emoji) }
    val contentVisible = post.contentWarning == null || expanded
    val bodyTruncated = truncateBody && postBodyCharacterCount(presentation.visibleText, post.emoji) > PostBodyCharacterLimit
    val bodyText = if (bodyTruncated) truncatedPostBody(presentation.visibleText, post.emoji) else presentation.visibleText
    Column(modifier.fillMaxWidth().testTag("post_row_${post.id.value}")) {
        post.resharedBy?.let {
            Row(Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 2.dp)) {
                AccountDisplayName(
                    it,
                    style = MaterialTheme.typography.labelMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                )
                Text(stringResource(R.string.post_reshared), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        PostMetadataRow(
            post = post,
            filteredHashtags = presentation.filteredHashtags.takeIf { contentVisible }.orEmpty(),
            onOpenProfile = onOpenProfile?.let { callback -> { callback(post.author) } },
            onSearchHashtag = onSearchHashtag,
            onOpenHashtagBubble = onOpenHashtagBubble,
            postOwned = ownedPost,
        )
        if (post.replyTo != null) Text(stringResource(R.string.post_reply), Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        if (post.contentWarning != null) {
            InlineEmojiText(post.contentWarning.ifBlank { stringResource(R.string.content_warning) }, post.emoji, Modifier.padding(horizontal = 16.dp), MaterialTheme.typography.bodyLarge)
            TextButton(onClick = { expanded = !expanded }, modifier = Modifier.padding(horizontal = 4.dp)) { Text(stringResource(if (expanded) R.string.content_warning_hide else R.string.content_warning_show)) }
        }
        ExpandableContent(visible = contentVisible, modifier = Modifier.fillMaxWidth()) {
            val timestamp = postTimestamp(post)
            if (presentation.visibleText.isNotBlank() || timestamp != null) SelectionContainer {
                Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp)) {
                    if (presentation.visibleText.isNotBlank()) {
                        if (bodyTruncated) {
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                PostBodyText(
                                    text = bodyText,
                                    emoji = post.emoji,
                                    onOpenUrl = onOpenUrl,
                                    onOpenUsername = onOpenUsername,
                                    onSearchHashtag = onSearchHashtag,
                                    style = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                                )
                                ViewFullPostBubble { onOpenPost(ownedPost) }
                            }
                        } else {
                            PostBodyText(
                                text = bodyText,
                                emoji = post.emoji,
                                onOpenUrl = onOpenUrl,
                                onOpenUsername = onOpenUsername,
                                onSearchHashtag = onSearchHashtag,
                                style = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                            )
                        }
                    }
                    timestamp?.let {
                        if (presentation.visibleText.isNotBlank()) Spacer(Modifier.height(2.dp))
                        Text(
                            it,
                            modifier = Modifier.semantics { contentDescription = context.getString(R.string.post_time) },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            PostMediaCarousel(ownedPost = ownedPost, onOpenMedia = onOpenMedia)
            post.pollOptions.forEach { option ->
                Surface(Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth(), shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainer) {
                    Row(Modifier.padding(12.dp)) {
                        InlineEmojiText(option.text, post.emoji, Modifier.weight(1f), MaterialTheme.typography.bodyMedium)
                        Text(pluralStringResource(R.plurals.post_poll_votes, option.votes, option.votes), style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
            post.quote?.let { quote ->
                OutlinedCard(modifier = Modifier.fillMaxWidth().padding(16.dp), onClick = { openExternal(context, quote.url) }) {
                    Column(Modifier.padding(16.dp)) {
                        AccountDisplayName(quote.author, style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(8.dp))
                        InlineEmojiText(
                            quote.contentWarning?.ifBlank { stringResource(R.string.content_warning) } ?: quote.text,
                            quote.emoji,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 5,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(stringResource(R.string.post_view_quoted), Modifier.padding(top = 12.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
        if (largeLayout && !bodyTruncated) {
            TextButton(
                onClick = { onOpenPost(ownedPost) },
                modifier = Modifier.padding(horizontal = 8.dp),
            ) {
                Text(stringResource(R.string.post_open))
            }
        }
        if (post.reactions.isNotEmpty()) {
            ReactionRow(post.reactions, ownedPost, PostAction.React in availableActions, onReaction)
        }
        InteractionRow(
            ownedPost = ownedPost,
            availableActions = availableActions,
            onReply = onReply,
            onReact = onReact,
            onReshare = onReshare,
            onBookmark = onBookmark,
            onReaction = onReaction,
            quoteEnabled = quoteEnabled,
            onQuote = onQuote,
            onOpenReactionBubble = { target, bounds ->
                onOpenReactionBubble(target, bounds)
                onOpenReactionPicker(target)
            },
            onShare = { sharePost(context, post) },
        )
    }
}

@Composable
internal fun PostBodyText(
    text: String,
    emoji: Map<String, CustomEmoji>,
    modifier: Modifier = Modifier,
    style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodyLarge,
    onOpenUrl: ((String) -> Unit)? = null,
    onOpenUsername: ((String) -> Unit)? = null,
    onSearchHashtag: ((String) -> Unit)? = null,
) {
    InlineEmojiText(
        text = text,
        emoji = emoji,
        modifier = modifier,
        style = style,
        enableInlineEntities = true,
        onOpenUrl = onOpenUrl,
        onOpenUsername = onOpenUsername,
        onSearchHashtag = onSearchHashtag,
    )
}

@Composable
internal fun PostMetadataRow(
    post: Post,
    filteredHashtags: List<String>,
    onOpenProfile: (() -> Unit)?,
    onSearchHashtag: ((String) -> Unit)?,
    onOpenHashtagBubble: ((OwnedPost, List<String>, Rect) -> Unit)?,
    postOwned: OwnedPost? = null,
) {
    val profileInteractionSource = remember { MutableInteractionSource() }
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = PostMetadataVerticalPadding)
            .height(PostChromeHeight)
            .semantics { contentDescription = context.getString(R.string.post_metadata) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .then(onOpenProfile?.let { callback ->
                    Modifier
                        .springPress(profileInteractionSource)
                        .clickable(
                            interactionSource = profileInteractionSource,
                            indication = LocalIndication.current,
                            onClick = callback,
                        )
                } ?: Modifier),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AccountAvatar(post.author, Modifier.size(40.dp))
            Spacer(Modifier.width(8.dp))
            AccountDisplayName(
                post.author,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (filteredHashtags.isNotEmpty() && (onSearchHashtag != null || onOpenHashtagBubble != null)) {
            Spacer(Modifier.width(4.dp))
            FilteredHashtagSummary(
                hashtags = filteredHashtags,
                onOpen = { bounds ->
                    if (onOpenHashtagBubble != null && postOwned != null) {
                        onOpenHashtagBubble(postOwned, filteredHashtags, bounds)
                    } else {
                        onSearchHashtag?.invoke(filteredHashtags.first())
                    }
                },
            )
        }
    }
}

@Composable
private fun ViewFullPostBubble(onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val fullPostDescription = stringResource(R.string.post_view_full)
    Surface(
        modifier = Modifier
            .height(32.dp)
            .widthIn(max = 124.dp)
            .springPress(interactionSource, pressedScale = LocalPalustrisMotionScheme.current.compactPressedScale)
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = onClick,
            )
            .semantics {
                 contentDescription = fullPostDescription
                role = Role.Button
            },
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Text(
            stringResource(R.string.post_view_full),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
        )
    }
}

internal fun postTimestamp(post: Post): String? = if (post.publishedAtEpochMillis > 0) {
    DateUtils.getRelativeTimeSpanString(
        post.publishedAtEpochMillis,
        System.currentTimeMillis(),
        DateUtils.MINUTE_IN_MILLIS,
        DateUtils.FORMAT_ABBREV_RELATIVE,
    ).toString()
} else {
    null
}

@Composable
private fun FilteredHashtagSummary(hashtags: List<String>, onOpen: (Rect) -> Unit) {
    val label = if (hashtags.size == 1) hashtags.first() else "${hashtags.first()} +${hashtags.size - 1}"
    val collapsedDescription = stringResource(R.string.post_action_bubble_collapsed)
    var bounds by remember { mutableStateOf(Rect.Zero) }
    val interactionSource = remember { MutableInteractionSource() }
    Box {
        Surface(
            modifier = Modifier
                .widthIn(max = 124.dp)
                .height(32.dp)
                .onGloballyPositioned { bounds = it.boundsInWindow() }
                .springPress(interactionSource, pressedScale = LocalPalustrisMotionScheme.current.compactPressedScale)
                .clickable(interactionSource = interactionSource, indication = LocalIndication.current) { onOpen(bounds) }
                .semantics {
                    contentDescription = hashtagSummaryDescription(hashtags)
                    role = Role.Button
                     stateDescription = collapsedDescription
                },
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ) {
            Text(
                label,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun hashtagSummaryDescription(hashtags: List<String>): String {
    if (hashtags.size == 1) return "1 hashtag: ${hashtags.first()}"
    return "${hashtags.size} hashtags: " + hashtags.dropLast(1).joinToString(", ") + " and ${hashtags.last()}"
}

private val CircleShapeForReaction = RoundedCornerShape(50)
private val ReactionChipHeight = 32.dp
private val ReactionEmojiSlotSize = 20.dp
private val ReactionChipMinWidth = 56.dp

@Composable
private fun ReactionRow(
    reactions: List<Reaction>,
    ownedPost: OwnedPost,
    enabled: Boolean,
    onReaction: (OwnedPost, EmojiChoice) -> Unit,
) {
    val scheme = LocalPalustrisMotionScheme.current
    val context = LocalContext.current
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        reactions.forEach { reaction ->
            val interactionSource = remember(reaction.emoji) { MutableInteractionSource() }
            val countText = pluralStringResource(R.plurals.reaction_count, reaction.count, reaction.count)
            val selectedStateDescription = if (reaction.selected) {
                if (enabled) {
                    stringResource(R.string.emoji_reaction_remove, reaction.emoji)
                } else {
                    stringResource(R.string.emoji_reaction_selected, reaction.emoji)
                }
            } else {
                null
            }
            val chipColor = rememberSelectedColor(
                selected = reaction.selected,
                selectedColor = MaterialTheme.colorScheme.secondaryContainer,
                unselectedColor = MaterialTheme.colorScheme.primaryContainer,
            )
            Surface(
                modifier = Modifier
                    .height(ReactionChipHeight)
                    .widthIn(min = ReactionChipMinWidth)
                    .springPress(interactionSource, pressedScale = scheme.compactPressedScale)
                    .combinedClickable(
                        enabled = enabled,
                        interactionSource = interactionSource,
                        indication = LocalIndication.current,
                        onClick = {
                            onReaction(ownedPost, EmojiChoice(reaction.emoji, reaction.emoji, reaction.emojiMetadata))
                        },
                    )
                    .testTag("reaction_chip_${reaction.emoji}")
                    .semantics {
                        contentDescription = context.getString(R.string.post_reaction_accessibility, reaction.emoji, countText)
                        role = Role.Button
                        this.selected = reaction.selected
                        selectedStateDescription?.let { stateDescription = it }
                },
                shape = CircleShapeForReaction,
                color = chipColor,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ) {
                Row(
                    Modifier.fillMaxHeight().padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.size(ReactionEmojiSlotSize).testTag("reaction_emoji_slot_${reaction.emoji}"),
                        contentAlignment = Alignment.Center,
                    ) {
                        CustomEmojiImage(
                            emoji = reaction.emojiMetadata,
                            fallbackText = reaction.emoji,
                            modifier = Modifier.fillMaxSize(),
                            textStyle = MaterialTheme.typography.labelLarge.copy(fontSize = 16.sp),
                        )
                    }
                    Spacer(Modifier.width(4.dp))
                    Box(
                        Modifier.widthIn(min = 16.dp).testTag("reaction_count_${reaction.emoji}"),
                        contentAlignment = Alignment.Center,
                    ) {
                        AnimatedContent(
                            targetState = reaction.count,
                            transitionSpec = {
                                if (scheme.reducedMotion) {
                                    EnterTransition.None togetherWith ExitTransition.None
                                } else {
                                    (fadeIn(scheme.fastFadeIn) + scaleIn(initialScale = 0.86f, animationSpec = scheme.expressive)) togetherWith
                                        (fadeOut(scheme.fastFadeOut) + scaleOut(targetScale = 0.86f, animationSpec = scheme.expressive))
                                }
                            },
                            label = "reactionCount",
                        ) { count -> Text(count.toString(), style = MaterialTheme.typography.labelMedium) }
                    }
                }
            }
        }
    }
}

@Composable
internal fun InteractionRow(
    ownedPost: OwnedPost,
    availableActions: Set<PostAction>,
    onReply: (OwnedPost) -> Unit,
    onReact: (OwnedPost) -> Unit,
    onReshare: (OwnedPost) -> Unit,
    onBookmark: (OwnedPost) -> Unit,
    onReaction: (OwnedPost, EmojiChoice) -> Unit,
    quoteEnabled: Boolean,
    onQuote: (OwnedPost) -> Unit,
    onOpenReactionBubble: (OwnedPost, Rect) -> Unit,
    onShare: () -> Unit,
) {
    var repostMenuVisible by rememberSaveable(ownedPost.post.id.connection, ownedPost.post.id.value) { mutableStateOf(false) }
    val context = LocalContext.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(PostInteractionRowHeight)
            .padding(horizontal = 8.dp)
            .semantics { contentDescription = context.getString(R.string.post_actions) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        InteractionButton(
            Modifier.weight(1f),
            AppIcons.Reply,
            stringResource(R.string.post_action_reply),
            enabled = PostAction.Reply in availableActions,
            onClick = { onReply(ownedPost) },
        )
        Box(Modifier.weight(1f)) {
            InteractionButton(
                modifier = Modifier.fillMaxWidth(),
                icon = AppIcons.Repost,
                label = stringResource(if (ownedPost.post.reposted) R.string.post_action_undo_repost else R.string.post_action_repost),
                enabled = PostAction.Reshare in availableActions,
                isSelected = ownedPost.post.reposted,
                onClick = { onReshare(ownedPost) },
                onLongClick = if (quoteEnabled) ({ repostMenuVisible = true }) else null,
            )
            DropdownMenu(expanded = repostMenuVisible, onDismissRequest = { repostMenuVisible = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.post_action_quote)) },
                    onClick = { repostMenuVisible = false; onQuote(ownedPost) },
                )
            }
        }
        Box(Modifier.weight(1f)) {
            val favouriteEnabled = PostAction.Favorite in availableActions
            val reactionEnabled = PostAction.React in availableActions
            fun openReactionBubble(bounds: Rect) = onOpenReactionBubble(ownedPost, bounds)
            InteractionButton(
                modifier = Modifier.fillMaxWidth(),
                icon = AppIcons.Heart,
                label = stringResource(if (ownedPost.post.favourited) R.string.post_action_unfavorite else R.string.post_action_favorite),
                enabled = favouriteEnabled || reactionEnabled,
                isSelected = ownedPost.post.favourited,
                onClick = {
                    when {
                        favouriteEnabled -> onReact(ownedPost)
                    }
                },
                onClickWithBounds = if (reactionEnabled) ({ bounds ->
                    if (!favouriteEnabled) openReactionBubble(bounds)
                }) else null,
                onLongClick = if (reactionEnabled) ::openReactionBubble else null,
            )
        }
        InteractionButton(
            Modifier.weight(1f),
            AppIcons.Bookmark,
            stringResource(if (ownedPost.post.saved) R.string.post_action_remove_bookmark else R.string.post_action_bookmark),
            enabled = PostAction.Bookmark in availableActions,
            isSelected = ownedPost.post.saved,
            onClick = { onBookmark(ownedPost) },
        )
        InteractionButton(Modifier.weight(1f), AppIcons.Share, stringResource(R.string.post_action_share), onClick = onShare)
    }
}

@Composable
private fun InteractionButton(
    modifier: Modifier,
    icon: ImageVector,
    label: String,
    enabled: Boolean = true,
    isSelected: Boolean = false,
    onClick: () -> Unit,
    onClickWithBounds: ((Rect) -> Unit)? = null,
    onLongClick: ((Rect) -> Unit)? = null,
) {
    var bounds by remember { mutableStateOf(Rect.Zero) }
    var popTrigger by remember { mutableIntStateOf(0) }
    val interactionSource = remember { MutableInteractionSource() }
    val scheme = LocalPalustrisMotionScheme.current
    val selectedDescription = stringResource(if (isSelected) R.string.post_action_selected else R.string.post_action_not_selected)
    Box(
        modifier = modifier
            .height(PostInteractionRowHeight)
            .onGloballyPositioned { bounds = it.boundsInWindow() }
            .springPress(interactionSource, enabled, scheme.compactPressedScale)
            .combinedClickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = {
                    popTrigger++
                    onClick()
                    onClickWithBounds?.invoke(bounds)
                },
                onLongClick = onLongClick?.let { callback ->
                    {
                        popTrigger++
                        callback(bounds)
                    }
                },
            )
            .semantics {
                contentDescription = label
                role = Role.Button
                this.selected = isSelected
                    stateDescription = selectedDescription
            },
        contentAlignment = Alignment.Center,
    ) {
        PopEffect(popTrigger) {
            Icon(
                icon,
                label,
                Modifier.size(PostInteractionIconSize),
                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else 0.38f),
            )
        }
    }
}

internal fun sharePost(context: Context, post: Post) {
    val text = post.url ?: post.text
    try {
        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }, context.getString(R.string.post_action_share_post)))
    } catch (_: android.content.ActivityNotFoundException) {
        Toast.makeText(context, context.getString(R.string.error_no_app_share_post), Toast.LENGTH_SHORT).show()
    }
}
