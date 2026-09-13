package me.foxtails.palustris.ui.media

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import kotlin.math.max
import me.foxtails.palustris.domain.Attachment

@Composable
internal fun MediaTransitionImage(
    frame: MediaTransitionFrame,
    previewRequest: ImageRequest?,
    fullRequest: ImageRequest?,
    imageLoader: coil.ImageLoader,
    useFullImage: Boolean,
    onFullImageReady: () -> Unit,
) {
    if (!frame.visibleBounds.isValid() || (previewRequest == null && fullRequest == null)) return
    val previewPainter = previewRequest?.let { rememberAsyncImagePainter(it, imageLoader) }
    val fullPainter = fullRequest?.let { rememberAsyncImagePainter(it, imageLoader) }
    val fullReady = fullPainter?.state is AsyncImagePainter.State.Success
    LaunchedEffect(fullPainter?.state) {
        if (fullPainter?.state is AsyncImagePainter.State.Success) onFullImageReady()
    }
    val painter = if (useFullImage && fullReady) fullPainter else previewPainter ?: fullPainter
    if (painter == null) return
    MediaTransitionImageCanvas(painter, frame)
}

@Composable
internal fun MediaTransitionImageCanvas(painter: Painter, frame: MediaTransitionFrame) {
    if (!frame.visibleBounds.isValid() || !frame.clipBounds.isValid() || !frame.imageBounds.isValid()) return
    Box(
        Modifier
            .drawWithCache {
                val radius = frame.cornerRadiusPx.coerceIn(
                    0f,
                    minOf(frame.clipBounds.width, frame.clipBounds.height) / 2f,
                )
                val roundedClip = Path().apply {
                    addRoundRect(
                        RoundRect(
                            rect = frame.clipBounds,
                            radiusX = radius,
                            radiusY = radius,
                        ),
                    )
                }
                onDrawWithContent {
                    drawContent()
                    clipRect(
                        left = frame.visibleBounds.left,
                        top = frame.visibleBounds.top,
                        right = frame.visibleBounds.right,
                        bottom = frame.visibleBounds.bottom,
                    ) {
                        clipPath(roundedClip) {
                            translate(frame.imageBounds.left, frame.imageBounds.top) {
                                with(painter) { draw(size = Size(frame.imageBounds.width, frame.imageBounds.height)) }
                            }
                        }
                    }
                }
            },
    )
}

internal fun sourceFrame(
    source: MediaTransitionSource?,
    fallbackBounds: Rect,
    attachment: Attachment,
): MediaTransitionFrame {
    val fullBounds = source?.fullBounds ?: fallbackBounds
    if (!fullBounds.isValid()) return MediaTransitionFrame(Rect.Zero, Rect.Zero, Rect.Zero)
    val imageWidth = source?.imageWidth ?: attachment.imageWidth()
    val imageHeight = source?.imageHeight ?: attachment.imageHeight()
    return MediaTransitionFrame(
        imageBounds = cropRect(fullBounds, imageWidth, imageHeight),
        clipBounds = fullBounds,
        visibleBounds = source?.visibleBounds?.takeIf { it.isValid() } ?: fullBounds,
        cornerRadiusPx = source?.cornerRadiusPx ?: 0f,
    )
}

internal fun destinationFrame(
    viewport: Rect,
    attachment: Attachment,
    density: androidx.compose.ui.unit.Density,
    drawableSize: Size? = null,
): MediaTransitionFrame {
    val contentBounds = mediaPageContentBounds(
        viewport,
        with(density) { MediaPageHorizontalPadding.toPx() },
    )
    val imageBounds = fitRect(
        contentBounds,
        drawableSize?.width ?: attachment.imageWidth(),
        drawableSize?.height ?: attachment.imageHeight(),
    )
    return MediaTransitionFrame(imageBounds, imageBounds, imageBounds)
}

internal fun cropRect(container: Rect, imageWidth: Float, imageHeight: Float): Rect {
    if (!container.isValid() || imageWidth <= 0f || imageHeight <= 0f) return container
    val scale = max(container.width / imageWidth, container.height / imageHeight)
    val width = imageWidth * scale
    val height = imageHeight * scale
    return Rect(
        left = container.center.x - width / 2f,
        top = container.center.y - height / 2f,
        right = container.center.x + width / 2f,
        bottom = container.center.y + height / 2f,
    )
}

private fun Attachment.imageWidth(): Float = (width ?: previewWidth ?: 4).toFloat().coerceAtLeast(1f)
private fun Attachment.imageHeight(): Float = (height ?: previewHeight ?: 3).toFloat().coerceAtLeast(1f)
private fun Rect.isValid(): Boolean = width > 0f && height > 0f
