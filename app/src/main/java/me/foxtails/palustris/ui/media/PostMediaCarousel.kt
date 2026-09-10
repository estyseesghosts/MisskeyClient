@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package me.foxtails.palustris.ui.media

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import me.foxtails.palustris.domain.Attachment
import me.foxtails.palustris.R
import me.foxtails.palustris.domain.MediaKind
import me.foxtails.palustris.domain.MediaRequestDecision
import me.foxtails.palustris.domain.MediaRequestPolicy
import me.foxtails.palustris.domain.MediaRequestRole
import me.foxtails.palustris.domain.OwnedPost
import me.foxtails.palustris.ui.AppIcons
import me.foxtails.palustris.data.media.MediaImageLoader
import me.foxtails.palustris.ui.motion.LocalPalustrisMotionScheme
import me.foxtails.palustris.ui.motion.springPress
import java.util.UUID

data class MediaOpenRequest(
    val ownedPost: OwnedPost,
    val attachmentIndex: Int,
    val revealed: Boolean,
    val transitionKey: MediaTransitionKey = MediaTransitionKey.forAttachment(ownedPost, attachmentIndex),
    val initialSourceBounds: androidx.compose.ui.geometry.Rect = androidx.compose.ui.geometry.Rect.Zero,
    val initialSource: MediaTransitionSource? = null,
    val sourceKeys: List<MediaTransitionKey> = emptyList(),
)

@Composable
fun PostMediaCarousel(
    ownedPost: OwnedPost,
    onOpenMedia: (MediaOpenRequest) -> Unit,
    attachmentIndices: List<Int>? = null,
    modifier: Modifier = Modifier,
) {
    val attachments = ownedPost.post.attachments
    if (attachments.isEmpty()) return
    val visibleIndices = attachmentIndices ?: attachments.indices.toList()
    val context = LocalContext.current
    val density = LocalDensity.current
    val mediaImageLoader = remember(context) { MediaImageLoader.get(context) }
    val sourceGroup = remember { UUID.randomUUID().toString() }
    var visibleViewport by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    BoxWithConstraints(modifier.fillMaxWidth()) {
        LazyRow(
            modifier = Modifier.onGloballyPositioned { visibleViewport = it.fullBoundsInRoot() },
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(visibleIndices, key = { index ->
                val attachment = attachments[index]
                "${ownedPost.fetchedBy.connection.origin}/${ownedPost.post.id.connection}/${ownedPost.post.id.value}/${attachment.id ?: index}"
            }) { index ->
                val attachment = attachments[index]
                val width = previewWidth(attachment)
                    .coerceIn(152.dp, minOf(320.dp, maxWidth * .78f))
                MediaPreviewTile(
                    ownedPost = ownedPost,
                    attachment = attachment,
                    index = index,
                    decodeWidthPx = with(density) { width.roundToPx() },
                    decodeHeightPx = with(density) { 240.dp.roundToPx() },
                    mediaImageLoader = mediaImageLoader,
                    onOpenMedia = onOpenMedia,
                    visibleViewport = visibleViewport,
                    sourceKeys = attachments.indices.map { attachmentIndex ->
                        MediaTransitionKey.forAttachment(ownedPost, attachmentIndex, "$sourceGroup-$attachmentIndex")
                    },
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
    decodeWidthPx: Int,
    decodeHeightPx: Int,
    mediaImageLoader: MediaImageLoader,
    onOpenMedia: (MediaOpenRequest) -> Unit,
    visibleViewport: androidx.compose.ui.geometry.Rect?,
    sourceKeys: List<MediaTransitionKey>,
    modifier: Modifier,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val registry = LocalMediaTransitionRegistry.current
    var revealed by rememberSaveable(
        ownedPost.fetchedBy,
        ownedPost.post.id.connection,
        ownedPost.post.id.value,
        attachment.id ?: index,
    ) { mutableStateOf(!attachment.sensitive) }
    val transitionKey = sourceKeys.getOrNull(index) ?: MediaTransitionKey.forAttachment(ownedPost, index)
    val active = registry.isSourceHidden(transitionKey)
    val visibleDecision = MediaRequestPolicy.resolve(
        attachment,
        MediaRequestRole.Preview,
        revealed = revealed,
        explicitlyOpened = false,
    )
    val imageRequest = remember(
        visibleDecision,
        ownedPost.fetchedBy,
        ownedPost.post.id,
        attachment,
        index,
        decodeWidthPx,
        decodeHeightPx,
    ) {
        (visibleDecision as? MediaRequestDecision.Request)?.let {
            mediaImageLoader.request(
                context = context,
                decision = it,
                accountIdentity = ownedPost.fetchedBy.toString(),
                postIdentity = "${ownedPost.post.id.connection}/${ownedPost.post.id.value}",
                attachment = attachment,
                attachmentIndex = index,
                decodeWidthPx = decodeWidthPx,
                decodeHeightPx = decodeHeightPx,
            )
        }
    }
    val previewPainter = imageRequest?.let { rememberAsyncImagePainter(it, mediaImageLoader.imageLoader) }
    val decodedPreviewSize = (previewPainter?.state as? AsyncImagePainter.State.Success)
        ?.painter
        ?.intrinsicSize
        ?.takeIf { it.width.isFinite() && it.height.isFinite() && it.width > 0f && it.height > 0f }
    val open = {
        onOpenMedia(
            MediaOpenRequest(
                ownedPost = ownedPost,
                attachmentIndex = index,
                revealed = revealed,
                transitionKey = transitionKey,
                initialSourceBounds = registry.boundsFor(transitionKey)
                    ?: androidx.compose.ui.geometry.Rect.Zero,
                initialSource = registry.sourceFor(transitionKey),
                sourceKeys = sourceKeys,
            ),
        )
    }
    val scheme = LocalPalustrisMotionScheme.current
    val interactionSource = remember { MutableInteractionSource() }
    val mediaDescription = if (revealed) {
        stringResource(R.string.a11y_open_media, index + 1, ownedPost.post.attachments.size)
    } else {
        stringResource(R.string.a11y_sensitive_media, index + 1)
    }
    DisposableEffect(transitionKey) {
        onDispose { registry.remove(transitionKey) }
    }
    Surface(
        modifier = modifier
            .onGloballyPositioned { coordinates ->
                val fullBounds = coordinates.fullBoundsInRoot()
                registry.updateIfVisible(
                    transitionKey,
                    MediaTransitionSource(
                        fullBounds = fullBounds,
                        visibleBounds = coordinates.visibleBoundsInRoot(visibleViewport),
                        cornerRadiusPx = with(density) { 12.dp.toPx() },
                        previewRequest = imageRequest,
                        imageWidth = decodedPreviewSize?.width
                            ?: (attachment.previewWidth ?: attachment.width ?: 4).toFloat(),
                        imageHeight = decodedPreviewSize?.height
                            ?: (attachment.previewHeight ?: attachment.height ?: 3).toFloat(),
                    ),
                )
            }
            .clip(RoundedCornerShape(12.dp))
            .testTag("post_media_frame_${ownedPost.post.id.value}_$index")
            .semantics {
                if (revealed) {
                    contentDescription = mediaDescription
                    role = Role.Button
                } else {
                    contentDescription = mediaDescription
                }
            }
            .then(if (active) Modifier.clearAndSetSemantics {} else Modifier),
        color = if (active) Color.Transparent else MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .then(if (revealed) {
                    Modifier
                        .springPress(interactionSource, pressedScale = scheme.largePressedScale)
                        .clickable(
                            interactionSource = interactionSource,
                            indication = LocalIndication.current,
                            onClick = open,
                        )
                } else Modifier)
                .then(if (active) Modifier.graphicsLayer { alpha = 0f } else Modifier),
        ) {
            AnimatedContent(
                targetState = revealed,
                transitionSpec = {
                    if (scheme.reducedMotion) EnterTransition.None togetherWith ExitTransition.None
                    else (fadeIn(scheme.fastFadeIn) + scaleIn(initialScale = 0.98f, animationSpec = scheme.spatial)) togetherWith
                        (fadeOut(scheme.fastFadeOut) + scaleOut(targetScale = 0.98f, animationSpec = scheme.spatial))
                },
                modifier = Modifier.fillMaxSize(),
                label = "sensitiveMediaReveal",
            ) { isRevealed ->
                if (!isRevealed) {
                    SensitiveMediaTile(onReveal = { revealed = true })
                } else {
                    when {
                        attachment.kind !in setOf(MediaKind.Image, MediaKind.AnimatedImage) -> UnsupportedMediaTile(attachment)
                        visibleDecision is MediaRequestDecision.Request && previewPainter != null -> {
                            Image(
                                painter = previewPainter,
                                contentDescription = attachment.description ?: "Post attachment ${index + 1}",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop,
                            )
                        }
                        else -> MissingPreviewTile(attachment.description)
                    }
                }
            }
        }
    }
}

@Composable
private fun SensitiveMediaTile(onReveal: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(AppIcons.Image, null)
        TextButton(onClick = onReveal) { Text(stringResource(R.string.media_show_sensitive)) }
    }
}

@Composable
private fun UnsupportedMediaTile(attachment: Attachment) {
    Column(Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(AppIcons.Image, null)
        Text(attachment.kind.name, style = MaterialTheme.typography.labelLarge)
        Text(stringResource(R.string.media_type_unavailable), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun MissingPreviewTile(description: String?) {
    val fallbackDescription = stringResource(R.string.post_open)
    Column(
        Modifier.fillMaxSize().padding(16.dp).semantics {
            contentDescription = description ?: fallbackDescription
        },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator(Modifier.padding(8.dp).height(24.dp))
        Text(stringResource(R.string.media_preview_unavailable), style = MaterialTheme.typography.bodySmall)
    }
}

private fun previewWidth(attachment: Attachment): androidx.compose.ui.unit.Dp {
    val width = attachment.previewWidth ?: attachment.width ?: 4
    val height = attachment.previewHeight ?: attachment.height ?: 3
    return 240.dp * (width.toFloat() / height.coerceAtLeast(1))
}
