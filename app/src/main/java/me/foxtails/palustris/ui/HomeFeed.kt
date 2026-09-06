@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package me.foxtails.palustris.ui

import android.content.Context
import android.content.Intent
import android.text.format.DateUtils
import android.widget.Toast
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
fun HomeFeed(state: FeedState, onRefresh: () -> Unit, onLoadMore: () -> Unit, onSignIn: () -> Unit) {
    val list = rememberLazyListState()
    val currentState by rememberUpdatedState(state)
    val loadMore by rememberUpdatedState(onLoadMore)
    LaunchedEffect(list) {
        snapshotFlow {
            val s = currentState
            s.nextCursor != null && !s.loading && !s.loadingMore && s.error == null &&
                (list.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1) >= s.posts.size - 5
        }.distinctUntilChanged().collect { if (it) loadMore() }
    }
    PullToRefreshBox(isRefreshing = state.loading, onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) {
        LazyColumn(state = list, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 12.dp)) {
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
            items(state.posts, key = { "${it.id.connection}/${it.id.value}" }) { post ->
                PostRow(post)
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
private fun PostRow(post: Post) {
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
        if (post.reactions.isNotEmpty()) FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            post.reactions.forEach { reaction ->
                Surface(shape = CircleShapeForReaction, color = if (reaction.selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer) {
                    Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (reaction.imageUrl != null) AsyncImage(reaction.imageUrl, reaction.emoji, Modifier.size(20.dp))
                        else Text(reaction.emoji, style = MaterialTheme.typography.labelMedium)
                        Text(" ${reaction.count}", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(AppIcons.Chat, "Replies", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(" ${post.replyCount}   ·   ${post.reshareCount} reshares", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { openExternal(context, post.url) }) { Text("Open post") }
        }
    }
}

private val CircleShapeForReaction = RoundedCornerShape(50)

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
