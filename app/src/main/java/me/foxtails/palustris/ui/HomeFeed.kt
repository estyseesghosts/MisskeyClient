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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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

@Composable
fun HomeFeed(
    state: FeedState,
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
) {
    val list = rememberLazyListState()
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
    PullToRefreshBox(isRefreshing = state.loading, onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) {
        LazyColumn(state = list, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp)) {
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
            val rows = ownedPosts.ifEmpty { state.posts.map { OwnedPost(it.author.id, it) } }
            items(rows, key = { "${it.post.id.connection}/${it.post.id.value}" }) { ownedPost ->
                PostRow(ownedPost, state.actions, onReact, onReply, onReshare, onBookmark, onReaction)
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
fun AccountAvatar(account: Account, modifier: Modifier = Modifier) {
    Box(modifier) {
        Avatar(Modifier.fillMaxSize())
        AsyncImage(model = account.avatarUrl, contentDescription = null, contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(28)))
    }
}

@Composable
private fun PostRow(
    ownedPost: OwnedPost,
    availableActions: Set<PostAction>,
    onReact: (OwnedPost) -> Unit,
    onReply: (OwnedPost) -> Unit,
    onReshare: (OwnedPost) -> Unit,
    onBookmark: (OwnedPost) -> Unit,
    onReaction: (OwnedPost, String) -> Unit,
) {
    val post = ownedPost.post
    val context = LocalContext.current
    var expanded by rememberSaveable(post.id.connection, post.id.value) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        post.resharedBy?.let {
            Text("${it.displayName} reshared", Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp),
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            AccountAvatar(post.author, Modifier.size(44.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(post.author.displayName, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(post.author.handle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(8.dp))
            if (post.publishedAtEpochMillis > 0) Text(
                DateUtils.getRelativeTimeSpanString(post.publishedAtEpochMillis, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE).toString(),
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (post.replyTo != null) Text("Reply", Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        if (post.contentWarning != null) {
            Text(post.contentWarning.ifBlank { "Content warning" }, Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodyLarge)
            TextButton(onClick = { expanded = !expanded }, modifier = Modifier.padding(horizontal = 4.dp)) { Text(if (expanded) "Hide content" else "Show content") }
        }
        if (post.contentWarning == null || expanded) {
            if (post.text.isNotBlank()) SelectionContainer {
                Text(post.text, Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp), style = MaterialTheme.typography.bodyLarge)
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
        if (PostAction.React in availableActions && post.reactions.isNotEmpty()) {
            ReactionRow(post.reactions, ownedPost, onReaction)
        }
        InteractionRow(
            ownedPost = ownedPost,
            canAddReaction = PostAction.React in availableActions,
            onReply = onReply,
            onReact = onReact,
            onReshare = onReshare,
            onBookmark = onBookmark,
            onReaction = onReaction,
            onShare = { sharePost(context, post) },
        )
    }
}

private val CircleShapeForReaction = RoundedCornerShape(50)

@Composable
private fun ReactionRow(
    reactions: List<Reaction>,
    ownedPost: OwnedPost,
    onReaction: (OwnedPost, String) -> Unit,
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        reactions.forEach { reaction ->
            Surface(
                modifier = Modifier.combinedClickable(onClick = { onReaction(ownedPost, reaction.emoji) }),
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
    canAddReaction: Boolean,
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
        modifier = Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        InteractionButton(Modifier.weight(1f), AppIcons.Reply, "Reply", onClick = { onReply(ownedPost) })
        InteractionButton(Modifier.weight(1f), AppIcons.Repost, "Repost", onClick = { onReshare(ownedPost) })
        Box(Modifier.weight(1f)) {
            InteractionButton(
                modifier = Modifier.fillMaxWidth(),
                icon = AppIcons.Heart,
                label = "Favorite",
                onClick = { onReact(ownedPost) },
                onLongClick = if (canAddReaction) ({ reactionMenuVisible = true }) else null,
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
        InteractionButton(Modifier.weight(1f), AppIcons.Bookmark, "Bookmark", onClick = { onBookmark(ownedPost) })
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
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    Box(
        modifier = modifier.height(56.dp).combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .semantics { contentDescription = label; role = Role.Button },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, label, Modifier.size(28.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
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
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
            TextButton(onClick = { openExternal(context, attachment.url) }) { Text(if (attachment.mimeType.startsWith("image/")) "Open image" else "Open attachment") }
            if (attachment.sensitive) TextButton(onClick = { revealed = false }) { Text("Hide media") }
        }
    }
}
