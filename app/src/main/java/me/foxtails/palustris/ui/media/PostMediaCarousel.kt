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
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
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
import me.foxtails.palustris.ui.motion.LocalPalustrisMotionScheme
import me.foxtails.palustris.ui.motion.springPress

data class MediaOpenRequest(
    val ownedPost: OwnedPost,
    val attachmentIndex: Int,
    val revealed: Boolean,
    val transitionKey: MediaTransitionKey = MediaTransitionKey.forAttachment(ownedPost, attachmentIndex),
    val initialSourceBounds: androidx.compose.ui.geometry.Rect = androidx.compose.ui.geometry.Rect.Zero,
)

@Composable
fun PostMediaCarousel(
    ownedPost: OwnedPost,
    onOpenMedia: (MediaOpenRequest) -> Unit,
    modifier: Modifier = Modifier,
) {
    val attachments = ownedPost.post.attachments
    if (attachments.isEmpty()) return
    val context = LocalContext.current
    val density = LocalDensity.current
    val mediaImageLoader = remember(context) { MediaImageLoader.get(context) }
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
                    decodeWidthPx = with(density) { width.roundToPx() },
                    decodeHeightPx = with(density) { 240.dp.roundToPx() },
                    mediaImageLoader = mediaImageLoader,
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
    decodeWidthPx: Int,
    decodeHeightPx: Int,
    mediaImageLoader: MediaImageLoader,
    onOpenMedia: (MediaOpenRequest) -> Unit,
    modifier: Modifier,
) {
    val context = LocalContext.current
    val registry = LocalMediaTransitionRegistry.current
    var revealed by rememberSaveable(
        ownedPost.fetchedBy,
        ownedPost.post.id.connection,
        ownedPost.post.id.value,
        attachment.id ?: index,
    ) { mutableStateOf(!attachment.sensitive) }
    val transitionKey = remember(ownedPost.fetchedBy, ownedPost.post.id, attachment.id, index) {
        MediaTransitionKey.forAttachment(ownedPost, index)
    }
    val active = registry.isActive(transitionKey)
    val open = {
        onOpenMedia(
            MediaOpenRequest(
                ownedPost = ownedPost,
                attachmentIndex = index,
                revealed = revealed,
                transitionKey = transitionKey,
                initialSourceBounds = registry.boundsFor(transitionKey)
                    ?: androidx.compose.ui.geometry.Rect.Zero,
            ),
        )
    }
    val scheme = LocalPalustrisMotionScheme.current
    val interactionSource = remember { MutableInteractionSource() }
    DisposableEffect(transitionKey) {
        onDispose { registry.remove(transitionKey) }
    }
    Surface(
        modifier = modifier
            .onGloballyPositioned { coordinates ->
                registry.updateIfVisible(transitionKey, coordinates.boundsInRoot())
            }
            .then(if (active) Modifier.clearAndSetSemantics {} else Modifier)
            .clip(RoundedCornerShape(12.dp))
            .then(if (revealed) {
                Modifier
                    .springPress(interactionSource, pressedScale = scheme.largePressedScale)
                    .clickable(
                        interactionSource = interactionSource,
                        indication = LocalIndication.current,
                        onClick = open,
                    )
            } else Modifier)
            .testTag("post_media_frame_${ownedPost.post.id.value}_$index")
            .semantics {
                if (revealed) {
                    contentDescription = "Open media ${index + 1} of ${ownedPost.post.attachments.size}"
                    role = Role.Button
                } else {
                    contentDescription = "Sensitive media ${index + 1}"
                }
            },
        color = if (active) Color.Transparent else MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        AnimatedContent(
            targetState = revealed,
            transitionSpec = {
                if (scheme.reducedMotion) EnterTransition.None togetherWith ExitTransition.None
                else (fadeIn(scheme.fastFadeIn) + scaleIn(initialScale = 0.98f, animationSpec = scheme.spatial)) togetherWith
                    (fadeOut(scheme.fastFadeOut) + scaleOut(targetScale = 0.98f, animationSpec = scheme.spatial))
            },
            modifier = Modifier.fillMaxSize().then(
                if (active) Modifier.graphicsLayer { alpha = 0f } else Modifier,
            ),
            label = "sensitiveMediaReveal",
        ) { isRevealed ->
            if (!isRevealed) {
                SensitiveMediaTile(onReveal = { revealed = true })
            } else {
                val visibleDecision = MediaRequestPolicy.resolve(
                    attachment,
                    MediaRequestRole.Preview,
                    revealed = true,
                    explicitlyOpened = false,
                )
                when {
                    attachment.kind !in setOf(MediaKind.Image, MediaKind.AnimatedImage) -> UnsupportedMediaTile(attachment)
                    visibleDecision is MediaRequestDecision.Request -> {
                        val imageRequest = remember(
                            visibleDecision,
                            ownedPost.fetchedBy,
                            ownedPost.post.id,
                            attachment,
                            index,
                            decodeWidthPx,
                            decodeHeightPx,
                        ) {
                            mediaImageLoader.request(
                                context = context,
                                decision = visibleDecision,
                                accountIdentity = ownedPost.fetchedBy.toString(),
                                postIdentity = "${ownedPost.post.id.connection}/${ownedPost.post.id.value}",
                                attachment = attachment,
                                attachmentIndex = index,
                                decodeWidthPx = decodeWidthPx,
                                decodeHeightPx = decodeHeightPx,
                            )
                        }
                        AsyncImage(
                            model = imageRequest,
                            imageLoader = mediaImageLoader.imageLoader,
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
