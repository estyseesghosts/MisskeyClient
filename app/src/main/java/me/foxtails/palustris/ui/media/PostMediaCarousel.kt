@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package me.foxtails.palustris.ui.media

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import me.foxtails.palustris.domain.Attachment
import me.foxtails.palustris.domain.MediaKind
import me.foxtails.palustris.domain.MediaRequestDecision
import me.foxtails.palustris.domain.MediaRequestPolicy
import me.foxtails.palustris.domain.MediaRequestRole
import me.foxtails.palustris.domain.OwnedPost
import me.foxtails.palustris.ui.AppIcons
import me.foxtails.palustris.data.media.MediaImageLoader

data class MediaOpenRequest(
    val ownedPost: OwnedPost,
    val attachmentIndex: Int,
    val revealed: Boolean,
)

@Composable
fun PostMediaCarousel(
    ownedPost: OwnedPost,
    onOpenMedia: (MediaOpenRequest) -> Unit,
    modifier: Modifier = Modifier,
) {
    val attachments = ownedPost.post.attachments
    if (attachments.isEmpty()) return
    BoxWithConstraints(modifier.fillMaxWidth()) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(attachments, key = { index, attachment ->
                "${ownedPost.fetchedBy.connection.origin}/${ownedPost.post.id.connection}/${ownedPost.post.id.value}/${attachment.id ?: index}"
            }) { index, attachment ->
                val width = previewWidth(attachment)
                    .coerceIn(152.dp, minOf(320.dp, maxWidth * .78f))
                MediaPreviewTile(
                    ownedPost = ownedPost,
                    attachment = attachment,
                    index = index,
                    onOpenMedia = onOpenMedia,
                    modifier = Modifier
                        .width(width)
                        .height(240.dp),
                )
            }
        }
    }
}

@Composable
private fun MediaPreviewTile(
    ownedPost: OwnedPost,
    attachment: Attachment,
    index: Int,
    onOpenMedia: (MediaOpenRequest) -> Unit,
    modifier: Modifier,
) {
    val context = LocalContext.current
    var revealed by rememberSaveable(
        ownedPost.fetchedBy,
        ownedPost.post.id.connection,
        ownedPost.post.id.value,
        attachment.id ?: index,
    ) { mutableStateOf(!attachment.sensitive) }
    val decision = if (revealed) {
        MediaRequestPolicy.resolve(attachment, MediaRequestRole.Preview, revealed = true, explicitlyOpened = false)
    } else {
        MediaRequestDecision.NoRequest(me.foxtails.palustris.domain.MediaRequestReason.HiddenSensitiveMedia)
    }
    val open = { onOpenMedia(MediaOpenRequest(ownedPost, index, revealed)) }
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = open)
            .testTag("post_media_frame_${ownedPost.post.id.value}_$index")
            .semantics {
                contentDescription = "Open media ${index + 1} of ${ownedPost.post.attachments.size}"
                role = Role.Button
            },
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        when {
            !revealed -> SensitiveMediaTile(onReveal = { revealed = true })
            attachment.kind !in setOf(MediaKind.Image, MediaKind.AnimatedImage) -> UnsupportedMediaTile(attachment)
            decision is MediaRequestDecision.Request -> {
                AsyncImage(
                    model = MediaImageLoader.get(context).request(
                        context = context,
                        decision = decision,
                        accountIdentity = ownedPost.fetchedBy.toString(),
                        postIdentity = "${ownedPost.post.id.connection}/${ownedPost.post.id.value}",
                        attachment = attachment,
                        attachmentIndex = index,
                        decodeWidthPx = context.resources.displayMetrics.widthPixels,
                        decodeHeightPx = (240 * context.resources.displayMetrics.density).toInt(),
                    ),
                    imageLoader = MediaImageLoader.get(context).imageLoader,
                    contentDescription = attachment.description ?: "Post attachment ${index + 1}",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
            else -> MissingPreviewTile(attachment.description)
        }
    }
}

@Composable
private fun SensitiveMediaTile(onReveal: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(AppIcons.Image, null)
        TextButton(onClick = onReveal) { Text("Show sensitive media") }
    }
}

@Composable
private fun UnsupportedMediaTile(attachment: Attachment) {
    Column(Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(AppIcons.Image, null)
        Text(attachment.kind.name, style = MaterialTheme.typography.labelLarge)
        Text("This media type is not available in the timeline viewer.", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun MissingPreviewTile(description: String?) {
    Column(
        Modifier.fillMaxSize().padding(16.dp).semantics {
            contentDescription = description ?: "Post attachment"
        },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator(Modifier.padding(8.dp).height(24.dp))
        Text("Preview unavailable", style = MaterialTheme.typography.bodySmall)
    }
}

private fun previewWidth(attachment: Attachment): androidx.compose.ui.unit.Dp {
    val width = attachment.previewWidth ?: attachment.width ?: 4
    val height = attachment.previewHeight ?: attachment.height ?: 3
    return 240.dp * (width.toFloat() / height.coerceAtLeast(1))
}
