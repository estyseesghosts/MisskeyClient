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
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import coil.compose.AsyncImage
import kotlinx.coroutines.flow.distinctUntilChanged
import me.foxtails.palustris.domain.*

internal fun openExternal(context: Context, url: String?) {
    val uri = url?.toUri() ?: return
    if (uri.scheme !in listOf("https", "http") || uri.host.isNullOrBlank()) return
    try { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
    catch (_: android.content.ActivityNotFoundException) { Toast.makeText(context, "No app can open this link.", Toast.LENGTH_SHORT).show() }
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
    onReaction: (OwnedPost, String) -> Unit = { _, _ -> },
    onOpenProfile: (Account) -> Unit = {},
    onSearchHashtag: (String) -> Unit = {},
) {
    val list = rememberLazyListState()
    val pullToRefreshState = rememberPullToRefreshState()
    val currentState by rememberUpdatedState(state)
    val loadMore by rememberUpdatedState(onLoadMore)
    val scrollDirectionChanged by rememberUpdatedState(onScrollDirectionChanged)
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
    PullToRefreshBox(
        isRefreshing = state.loading,
        onRefresh = onRefresh,
        state = pullToRefreshState,
        modifier = Modifier.fillMaxSize(),
        indicator = {
            PullToRefreshDefaults.Indicator(
                state = pullToRefreshState,
                isRefreshing = state.loading,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 96.dp),
            )
        },
    ) {
        LazyColumn(
            state = list,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = 96.dp,
                bottom = if (compactLayout) CompactOverlayFeedBottomClearance else LegacyFeedBottomClearance,
            ),
        ) {
            if (state.error != null) item {
                Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth().padding(16.dp), shape = MaterialTheme.shapes.large) {
                    Column(Modifier.padding(16.dp)) {
                        Text(state.error, color = MaterialTheme.colorScheme.onErrorContainer)
                        TextButton(onClick = if (state.needsSignIn) onSignIn else onRefresh) {
                            Text(if (state.needsSignIn) "Sign in again" else "Retry")
                        }
                    }
                }
            }
            if (state.posts.isEmpty() && !state.loading && state.error == null) item {
                Box(Modifier.fillParentMaxSize()) { EmptyState(AppIcons.Home, "Your home feed is quiet", "Posts from accounts you follow will appear here. Pull down to refresh.") }
            }
            val hasOwnership = ownedPosts.isNotEmpty()
            val rows = if (hasOwnership) ownedPosts else state.posts.map { OwnedPost(it.author.id, it) }
            val enabledActions = if (hasOwnership) state.actions.intersect(ClientReadyPostActions) else emptySet()
            items(rows, key = { "${it.post.id.connection}/${it.post.id.value}" }) { ownedPost ->
                PostRow(ownedPost, enabledActions, onReact, onReply, onReshare, onBookmark, onReaction, onOpenProfile, onSearchHashtag)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f))
            }
            if (state.loadingMore) item {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(24.dp)) }
            }
            if (state.posts.isNotEmpty() && !state.loadingMore && state.error == null) item {
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    if (state.nextCursor != null) TextButton(onClick = onLoadMore) { Text("Load older posts") }
                    else Text("You're up to date", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
fun AccountAvatar(account: Account, modifier: Modifier = Modifier, exposeSemantics: Boolean = true) {
    Box(
        modifier
            .clip(CircleShape)
            .then(if (exposeSemantics) Modifier.semantics(mergeDescendants = true) {
                contentDescription = "Profile picture of ${account.displayName}"
            } else Modifier),
    ) {
        Avatar(Modifier.fillMaxSize(), description = null)
        AsyncImage(model = account.avatarUrl, contentDescription = null, contentScale = ContentScale.Crop,
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
    onReaction: (OwnedPost, String) -> Unit,
    onOpenProfile: (Account) -> Unit,
    onSearchHashtag: (String) -> Unit,
) {
    val post = ownedPost.post
    val context = LocalContext.current
    var expanded by rememberSaveable(post.id.connection, post.id.value) { mutableStateOf(false) }
    val presentation = remember(post.text) { parseHashtagBlocks(post.text) }
    val contentVisible = post.contentWarning == null || expanded
    Column(Modifier.fillMaxWidth()) {
        post.resharedBy?.let {
            Text("${it.displayName} reshared", Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 2.dp),
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        PostMetadataRow(
            post = post,
            filteredHashtags = presentation.filteredHashtags.takeIf { contentVisible }.orEmpty(),
            onOpenProfile = { onOpenProfile(post.author) },
            onSearchHashtag = onSearchHashtag,
        )
        if (post.replyTo != null) Text("Reply", Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        if (post.contentWarning != null) {
            Text(post.contentWarning.ifBlank { "Content warning" }, Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodyLarge)
            TextButton(onClick = { expanded = !expanded }, modifier = Modifier.padding(horizontal = 4.dp)) { Text(if (expanded) "Hide content" else "Show content") }
        }
        if (contentVisible) {
            val timestamp = postTimestamp(post)
            if (presentation.visibleText.isNotBlank() || timestamp != null) SelectionContainer {
                Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp)) {
                    if (presentation.visibleText.isNotBlank()) {
                        MarkdownPostText(presentation.visibleText, style = MaterialTheme.typography.bodyLarge)
                    }
                    timestamp?.let {
                        if (presentation.visibleText.isNotBlank()) Spacer(Modifier.height(2.dp))
                        Text(
                            it,
                            modifier = Modifier.semantics { contentDescription = "Post time" },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            post.attachments.forEach { AttachmentView(it) }
            post.pollOptions.forEach { option ->
                Surface(Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth(), shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainer) {
                    Row(Modifier.padding(12.dp)) { Text(option.text, Modifier.weight(1f)); Text("${option.votes}", style = MaterialTheme.typography.labelLarge) }
                }
            }
            post.quote?.let { quote ->
                OutlinedCard(modifier = Modifier.fillMaxWidth().padding(16.dp), onClick = { openExternal(context, quote.url) }) {
                    Column(Modifier.padding(16.dp)) {
                        Text(quote.author.displayName, style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(8.dp))
                        Text(quote.contentWarning?.ifBlank { "Content warning" } ?: quote.text, maxLines = 5, overflow = TextOverflow.Ellipsis)
                        Text("View quoted post", Modifier.padding(top = 12.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    }
                }
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
            onShare = { sharePost(context, post) },
        )
    }
}

@Composable
private fun PostMetadataRow(post: Post, filteredHashtags: List<String>, onOpenProfile: () -> Unit, onSearchHashtag: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = PostMetadataVerticalPadding)
            .height(PostChromeHeight)
            .semantics { contentDescription = "Post metadata" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.weight(1f).clickable(onClick = onOpenProfile),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AccountAvatar(post.author, Modifier.size(40.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                post.author.displayName,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (filteredHashtags.isNotEmpty()) {
            Spacer(Modifier.width(4.dp))
            FilteredHashtagSummary(filteredHashtags, onSearchHashtag)
        }
    }
}

private fun postTimestamp(post: Post): String? = if (post.publishedAtEpochMillis > 0) {
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
private fun FilteredHashtagSummary(hashtags: List<String>, onSearchHashtag: (String) -> Unit) {
    var menuVisible by rememberSaveable(hashtags) { mutableStateOf(false) }
    val label = if (hashtags.size == 1) hashtags.first() else "${hashtags.first()} +${hashtags.size - 1}"
    Box {
        Surface(
            modifier = Modifier
                .widthIn(max = 124.dp)
                .height(32.dp)
                .clickable { menuVisible = true }
                .semantics {
                    contentDescription = hashtagSummaryDescription(hashtags)
                    role = Role.Button
                },
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            Text(
                label,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        DropdownMenu(expanded = menuVisible, onDismissRequest = { menuVisible = false }) {
            Text("Hashtags", Modifier.padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.titleSmall)
            hashtags.forEach { hashtag ->
                DropdownMenuItem(
                    modifier = Modifier.semantics { contentDescription = "Hashtag $hashtag" },
                    text = { Text(hashtag) },
                    onClick = { menuVisible = false; onSearchHashtag(hashtag) },
                )
            }
        }
    }
}

private fun hashtagSummaryDescription(hashtags: List<String>): String {
    if (hashtags.size == 1) return "1 hashtag: ${hashtags.first()}"
    return "${hashtags.size} hashtags: " + hashtags.dropLast(1).joinToString(", ") + " and ${hashtags.last()}"
}

private val CircleShapeForReaction = RoundedCornerShape(50)

@Composable
private fun ReactionRow(
    reactions: List<Reaction>,
    ownedPost: OwnedPost,
    enabled: Boolean,
    onReaction: (OwnedPost, String) -> Unit,
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        reactions.forEach { reaction ->
            Surface(
                modifier = Modifier.combinedClickable(enabled = enabled, onClick = { onReaction(ownedPost, reaction.emoji) }),
                shape = CircleShapeForReaction,
                color = if (reaction.selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer,
            ) {
                Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (reaction.imageUrl != null) AsyncImage(reaction.imageUrl, reaction.emoji, Modifier.size(20.dp))
                    else Text(reaction.emoji, style = MaterialTheme.typography.labelMedium)
                    Text(" ${reaction.count}", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
private fun InteractionRow(
    ownedPost: OwnedPost,
    availableActions: Set<PostAction>,
    onReply: (OwnedPost) -> Unit,
    onReact: (OwnedPost) -> Unit,
    onReshare: (OwnedPost) -> Unit,
    onBookmark: (OwnedPost) -> Unit,
    onReaction: (OwnedPost, String) -> Unit,
    onShare: () -> Unit,
) {
    var reactionMenuVisible by rememberSaveable(ownedPost.post.id.connection, ownedPost.post.id.value) { mutableStateOf(false) }
    var customReactionDialogVisible by rememberSaveable(ownedPost.post.id.connection, ownedPost.post.id.value) { mutableStateOf(false) }
    var customReaction by rememberSaveable(ownedPost.post.id.connection, ownedPost.post.id.value) { mutableStateOf("") }
    val postReactions = ownedPost.post.reactions.map { it.emoji }
    val reactionChoices = remember(postReactions) {
        (postReactions + listOf("👍", "❤️", "😂", "🎉", "🤔")).distinct()
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(PostInteractionRowHeight)
            .padding(horizontal = 8.dp)
            .semantics { contentDescription = "Post actions" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        InteractionButton(Modifier.weight(1f), AppIcons.Reply, "Reply", enabled = PostAction.Reply in availableActions, onClick = { onReply(ownedPost) })
        InteractionButton(Modifier.weight(1f), AppIcons.Repost, "Repost", enabled = PostAction.Reshare in availableActions, onClick = { onReshare(ownedPost) })
        Box(Modifier.weight(1f)) {
            InteractionButton(
                modifier = Modifier.fillMaxWidth(),
                icon = AppIcons.Heart,
                label = "Favorite",
                enabled = PostAction.Favorite in availableActions,
                onClick = { onReact(ownedPost) },
                onLongClick = if (PostAction.React in availableActions) ({ reactionMenuVisible = true }) else null,
            )
            DropdownMenu(expanded = reactionMenuVisible, onDismissRequest = { reactionMenuVisible = false }) {
                Text("Add reaction", Modifier.padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.titleSmall)
                reactionChoices.forEach { emoji ->
                    DropdownMenuItem(
                        text = { Text(emoji, fontSize = 24.sp) },
                        onClick = {
                            reactionMenuVisible = false
                            onReaction(ownedPost, emoji)
                        },
                    )
                }
                DropdownMenuItem(
                    text = { Text("Add another reaction…") },
                    onClick = {
                        reactionMenuVisible = false
                        customReactionDialogVisible = true
                    },
                )
            }
        }
        InteractionButton(Modifier.weight(1f), AppIcons.Bookmark, "Bookmark", enabled = PostAction.Bookmark in availableActions, onClick = { onBookmark(ownedPost) })
        InteractionButton(Modifier.weight(1f), AppIcons.Share, "Share", onClick = onShare)
    }

    if (customReactionDialogVisible) AlertDialog(
        onDismissRequest = { customReactionDialogVisible = false },
        title = { Text("Add reaction") },
        text = {
            OutlinedTextField(
                value = customReaction,
                onValueChange = { customReaction = it },
                label = { Text("Emoji or custom reaction") },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(
                enabled = customReaction.trim().isNotEmpty(),
                onClick = {
                    customReactionDialogVisible = false
                    onReaction(ownedPost, customReaction.trim())
                    customReaction = ""
                },
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = { customReactionDialogVisible = false }) { Text("Cancel") } },
    )
}

@Composable
private fun InteractionButton(
    modifier: Modifier,
    icon: ImageVector,
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    Box(
        modifier = modifier.height(PostInteractionRowHeight).combinedClickable(enabled = enabled, onClick = onClick, onLongClick = onLongClick)
            .semantics { contentDescription = label; role = Role.Button },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            label,
            Modifier.size(PostInteractionIconSize),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else 0.38f),
        )
    }
}

private fun sharePost(context: Context, post: Post) {
    val text = post.url ?: post.text
    try {
        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }, "Share post"))
    } catch (_: android.content.ActivityNotFoundException) {
        Toast.makeText(context, "No app can share this post.", Toast.LENGTH_SHORT).show()
    }
}

@Composable
private fun AttachmentView(attachment: Attachment) {
    var revealed by rememberSaveable(attachment.url) { mutableStateOf(!attachment.sensitive) }
    val context = LocalContext.current
    if (!revealed) Surface(Modifier.fillMaxWidth().height(180.dp).padding(horizontal = 16.dp, vertical = 4.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = MaterialTheme.shapes.large) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(AppIcons.Image, null)
            TextButton(onClick = { revealed = true }) { Text("Show sensitive media") }
        }
    } else Column {
        if (attachment.mimeType.startsWith("image/") || attachment.previewUrl != null) AsyncImage(
            model = attachment.previewUrl ?: attachment.url,
            contentDescription = attachment.description ?: "Post attachment",
            modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp, max = 360.dp).padding(vertical = 4.dp).clickable { openExternal(context, attachment.url) },
            contentScale = ContentScale.Fit,
        )
        if (attachment.sensitive) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                TextButton(onClick = { revealed = false }) { Text("Hide media") }
            }
        }
    }
}
