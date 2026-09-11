@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package me.foxtails.palustris.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Image
import coil.compose.rememberAsyncImagePainter
import me.foxtails.palustris.R
import me.foxtails.palustris.data.media.MediaImageLoader
import me.foxtails.palustris.domain.Attachment
import me.foxtails.palustris.domain.MediaKind
import me.foxtails.palustris.domain.MediaRequestDecision
import me.foxtails.palustris.domain.MediaRequestPolicy
import me.foxtails.palustris.domain.MediaRequestRole
import me.foxtails.palustris.domain.OwnedPost
import me.foxtails.palustris.ui.media.SensitiveMediaTile
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged

internal data class PhotoGridItem(
    val ownedPost: OwnedPost,
    val attachmentIndex: Int,
    val attachment: Attachment,
)

internal fun photoGridItems(posts: List<OwnedPost>): List<PhotoGridItem> = posts.mapNotNull { ownedPost ->
    ownedPost.post.attachments.withIndex()
        .firstOrNull { (_, attachment) -> attachment.isPhotoGridDisplayable() }
        ?.let { (index, attachment) -> PhotoGridItem(ownedPost, index, attachment) }
}

private fun Attachment.isPhotoGridDisplayable(): Boolean =
    MediaRequestPolicy.resolve(
        attachment = this,
        role = MediaRequestRole.Preview,
        revealed = true,
        explicitlyOpened = false,
    ) is MediaRequestDecision.Request

internal fun photoGridAspectRatio(attachment: Attachment): Float {
    val width = attachment.width ?: attachment.previewWidth
    val height = attachment.height ?: attachment.previewHeight
    return if (width != null && height != null && width > 0 && height > 0) {
        width.toFloat() / height.toFloat()
    } else {
        4f / 3f
    }
}

@Composable
fun PhotoGridScreen(
    state: FeedState = FeedState(),
    posts: List<OwnedPost>? = null,
    onRefresh: () -> Unit = {},
    onLoadMore: () -> Unit = {},
    onOpenPost: (OwnedPost) -> Unit = {},
    compactLayout: Boolean = true,
    compactNavigationVisible: Boolean = false,
    gridState: LazyStaggeredGridState? = null,
) {
    val rows = posts ?: state.ownedPosts.ifEmpty { state.posts.map { OwnedPost(it.author.id, it) } }
    val mediaItems = remember(rows) { photoGridItems(rows) }
    val list = gridState ?: rememberLazyStaggeredGridState()
    val loadMore by rememberUpdatedState(onLoadMore)
    var requestedCursor by remember { mutableStateOf<String?>(null) }
    val bottomClearance = if (compactLayout) {
        compactScrollEndClearance(
            controlStackHeight = 0.dp,
            navigationVisible = compactNavigationVisible,
        )
    } else {
        0.dp
    }

    LaunchedEffect(state.nextCursor) {
        requestedCursor = null
    }
    LaunchedEffect(list, mediaItems.size, state.nextCursor, state.loading, state.loadingMore, state.error) {
        snapshotFlow {
            val lastVisible = list.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            val nearEnd = if (mediaItems.isEmpty()) {
                list.layoutInfo.totalItemsCount > 0
            } else {
                lastVisible >= (mediaItems.size - 3).coerceAtLeast(0)
            }
            nearEnd && state.nextCursor != null && state.error == null &&
                !state.loading && !state.loadingMore
        }.distinctUntilChanged().collectLatest { shouldLoadMore ->
            val cursor = state.nextCursor
            if (shouldLoadMore && cursor != null && requestedCursor != cursor) {
                requestedCursor = cursor
                loadMore()
            }
        }
    }

    when {
        state.loading && mediaItems.isEmpty() -> PhotoGridLoading(Modifier.fillMaxSize().testTag("photo_grid_content"))
        state.error != null && mediaItems.isEmpty() -> PhotoGridError(
            message = state.error,
            onRetry = if (state.nextCursor != null) onLoadMore else onRefresh,
            modifier = Modifier.fillMaxSize().testTag("photo_grid_content"),
        )
        else -> LazyVerticalStaggeredGrid(
            columns = StaggeredGridCells.Adaptive(150.dp),
            state = list,
            modifier = Modifier.fillMaxSize().testTag("photo_grid_content"),
            verticalItemSpacing = 3.dp,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            contentPadding = PaddingValues(bottom = bottomClearance + 3.dp),
        ) {
            items(
                items = mediaItems,
                key = { item -> photoGridItemKey(item) },
            ) { item ->
                PhotoGridTile(item = item, onOpenPost = onOpenPost)
            }
            if (state.loadingMore) {
                item(key = "photo-grid-loading-more", span = StaggeredGridItemSpan.FullLine) {
                    Box(
                        Modifier.fillMaxWidth().padding(20.dp),
                        contentAlignment = Alignment.Center,
                    ) { CircularProgressIndicator(Modifier.size(24.dp)) }
                }
            } else if (state.error != null && mediaItems.isNotEmpty()) {
                item(key = "photo-grid-paging-error", span = StaggeredGridItemSpan.FullLine) {
                    PhotoGridPagingError(state.error, onLoadMore)
                }
            } else if (state.nextCursor != null) {
                item(key = "photo-grid-load-more", span = StaggeredGridItemSpan.FullLine) {
                    TextButton(onClick = onLoadMore, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.photo_grid_load_older))
                    }
                }
            } else if (mediaItems.isNotEmpty()) {
                item(key = "photo-grid-up-to-date", span = StaggeredGridItemSpan.FullLine) {
                    Text(
                        stringResource(R.string.photo_grid_up_to_date),
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                item(key = "photo-grid-empty", span = StaggeredGridItemSpan.FullLine) {
                    EmptyState(
                        AppIcons.WaffleGrid,
                        stringResource(R.string.photo_grid_empty_title),
                        stringResource(R.string.photo_grid_empty_subtitle),
                        modifier = Modifier.fillMaxWidth().height(300.dp),
                    )
                }
            }
        }
    }
}

@Composable
internal fun PhotoGridTile(
    item: PhotoGridItem,
    onOpenPost: (OwnedPost) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val mediaImageLoader = remember(context) { MediaImageLoader.get(context) }
    var revealed by rememberSaveable(
        item.ownedPost.fetchedBy,
        item.ownedPost.post.id.connection,
        item.ownedPost.post.id.value,
        item.attachment.id ?: item.attachmentIndex,
    ) { mutableStateOf(!item.attachment.sensitive) }
    val decision = remember(item.attachment, revealed) {
        MediaRequestPolicy.resolve(
            attachment = item.attachment,
            role = MediaRequestRole.Preview,
            revealed = revealed,
            explicitlyOpened = false,
        )
    }
    val imageRequest = remember(item, decision) {
        (decision as? MediaRequestDecision.Request)?.let { request ->
            mediaImageLoader.request(
                context = context,
                decision = request,
                accountIdentity = item.ownedPost.fetchedBy.toString(),
                postIdentity = "${item.ownedPost.post.id.connection}/${item.ownedPost.post.id.value}",
                attachment = item.attachment,
                attachmentIndex = item.attachmentIndex,
                decodeWidthPx = with(density) { 240.dp.roundToPx() },
                decodeHeightPx = with(density) { 240.dp.roundToPx() },
            )
        }
    }
    val painter = imageRequest?.let { rememberAsyncImagePainter(it, mediaImageLoader.imageLoader) }
    val tileKey = photoGridItemKey(item)
    val tileDescription = if (revealed) {
        stringResource(R.string.post_open)
    } else {
        stringResource(R.string.a11y_sensitive_media, item.attachmentIndex + 1)
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(photoGridAspectRatio(item.attachment))
            .clip(RoundedCornerShape(2.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .then(
                if (revealed) {
                    Modifier.clickable { onOpenPost(item.ownedPost) }
                } else {
                    Modifier
                },
            )
            .testTag("photo_grid_tile_$tileKey")
            .semantics {
                contentDescription = tileDescription
                role = Role.Button
            },
        contentAlignment = Alignment.Center,
    ) {
        if (!revealed) {
            SensitiveMediaTile(onReveal = { revealed = true })
        } else if (painter != null) {
            Image(
                painter = painter,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
    }
}

private fun photoGridItemKey(item: PhotoGridItem): String =
    "${item.ownedPost.fetchedBy.connection.origin}/${item.ownedPost.post.id.connection}/${item.ownedPost.post.id.value}/${item.attachment.id ?: item.attachmentIndex}"

@Composable
private fun PhotoGridLoading(modifier: Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

@Composable
private fun PhotoGridError(message: String, onRetry: () -> Unit, modifier: Modifier) {
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(message, color = MaterialTheme.colorScheme.error)
        TextButton(onClick = onRetry) { Text(stringResource(R.string.notifications_retry)) }
    }
}

@Composable
private fun PhotoGridPagingError(message: String, onRetry: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        shape = MaterialTheme.shapes.large,
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.photo_grid_paging_error), color = MaterialTheme.colorScheme.onErrorContainer)
            Text(message, color = MaterialTheme.colorScheme.onErrorContainer)
            TextButton(onClick = onRetry) { Text(stringResource(R.string.notifications_retry)) }
        }
    }
}
