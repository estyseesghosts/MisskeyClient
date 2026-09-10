package me.foxtails.palustris.ui.media

import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import me.foxtails.palustris.ui.motion.PalustrisMotionScheme

enum class MediaViewerPhase {
    Opening,
    Open,
    Dragging,
    Returning,
    Closing,
}

/** Owns one interruptible image frame from opening through source restoration. */
class MediaViewerTransitionState(
    initialSourceBounds: Rect,
    destinationBounds: Rect,
    private val motionScheme: PalustrisMotionScheme,
    initialSourceFrame: MediaTransitionFrame? = null,
    initialDestinationFrame: MediaTransitionFrame? = null,
) {
    private val opening = Animatable(if (initialSourceBounds.isValid()) 0f else 1f)
    private val returning = Animatable(1f)
    private val closing = Animatable(0f)
    private val closeCenterX = Animatable(destinationBounds.center.x)
    private val closeCenterY = Animatable(destinationBounds.center.y)

    private var sourceFrame = initialSourceFrame ?: frameForBounds(initialSourceBounds)
    private var destinationFrame = initialDestinationFrame ?: frameForBounds(destinationBounds)
    private var returnStartFrame = sourceFrame
    private var closeStartFrame = destinationFrame
    private var closeTargetFrame = destinationFrame
    private var animationToken = 0
    private var dismissViewport = Rect.Zero

    var destinationBounds by mutableStateOf(destinationBounds)
        private set
    var phase by mutableStateOf(
        if (initialSourceBounds.isValid() && !motionScheme.reducedMotion) {
            MediaViewerPhase.Opening
        } else {
            MediaViewerPhase.Open
        },
    )
        private set
    var dragX by mutableFloatStateOf(0f)
        private set
    var dragY by mutableFloatStateOf(0f)
        private set

    val isDismissGestureActive: Boolean
        get() = phase == MediaViewerPhase.Dragging

    val isClosing: Boolean
        get() = phase == MediaViewerPhase.Closing

    val visualFrame: MediaTransitionFrame
        get() = when (phase) {
            MediaViewerPhase.Opening -> lerpFrame(sourceFrame, destinationFrame, opening.value)
            MediaViewerPhase.Open -> destinationFrame
            MediaViewerPhase.Dragging -> draggedFrame()
            MediaViewerPhase.Returning -> lerpFrame(returnStartFrame, destinationFrame, returning.value)
            MediaViewerPhase.Closing -> centeredFrame(
                lerpFrame(closeStartFrame, closeTargetFrame, closing.value),
                Offset(closeCenterX.value, closeCenterY.value),
            )
        }

    val visualBounds: Rect
        get() = visualFrame.clipBounds

    val dismissProgress: Float
        get() = when (phase) {
            MediaViewerPhase.Dragging -> dismissProgress(Offset(dragX, dragY), dismissDistance())
            else -> 0f
        }

    val backgroundAlpha: Float
        get() = when (phase) {
            MediaViewerPhase.Opening -> opening.value
            MediaViewerPhase.Closing -> 1f - closing.value
            else -> 1f
        }.coerceIn(0f, 1f)

    val chromeAlpha: Float
        get() = when (phase) {
            MediaViewerPhase.Opening -> opening.value
            MediaViewerPhase.Dragging -> (1f - dismissProgress * 2f).coerceIn(0f, 1f)
            MediaViewerPhase.Closing -> 1f - closing.value
            else -> 1f
        }.coerceIn(0f, 1f)

    fun updateViewport(value: Rect) {
        if (value.isValid()) dismissViewport = value
    }

    fun updateDestinationBounds(value: Rect) {
        if (value.isValid()) {
            destinationBounds = value
            destinationFrame = frameForBounds(value)
        }
    }

    fun updateDestinationFrame(value: MediaTransitionFrame) {
        if (value.clipBounds.isValid()) {
            destinationFrame = value
            destinationBounds = value.clipBounds
        }
    }

    fun updateSourceBounds(value: Rect?) {
        value?.takeIf { it.isValid() }?.let { sourceFrame = frameForBounds(it) }
    }

    fun updateSourceFrame(value: MediaTransitionFrame) {
        if (value.clipBounds.isValid()) sourceFrame = value
    }

    fun selectPage(source: MediaTransitionFrame, destination: MediaTransitionFrame) {
        animationToken++
        sourceFrame = source
        destinationFrame = destination
        destinationBounds = destination.clipBounds
        dragX = 0f
        dragY = 0f
        phase = MediaViewerPhase.Open
    }

    suspend fun startOpening() {
        if (phase != MediaViewerPhase.Opening) return
        val token = ++animationToken
        if (motionScheme.reducedMotion) opening.snapTo(1f)
        else opening.animateTo(1f, motionScheme.spatial)
        opening.snapTo(1f)
        if (token == animationToken && phase == MediaViewerPhase.Opening) phase = MediaViewerPhase.Open
    }

    fun beginDrag() {
        if (phase == MediaViewerPhase.Open) phase = MediaViewerPhase.Dragging
    }

    fun dragBy(delta: Offset) {
        if (phase != MediaViewerPhase.Dragging) return
        dragX += delta.x
        dragY += delta.y
    }

    fun shouldDismiss(velocity: Offset, velocityThresholdPx: Float): Boolean =
        shouldDismiss(
            Offset(dragX, dragY),
            velocity,
            dismissViewport.takeIf { it.isValid() } ?: Rect(0f, 0f, destinationBounds.width, destinationBounds.height),
            velocityThresholdPx,
        )

    fun shouldDismiss(velocityY: Float): Boolean =
        shouldDismiss(
            Offset(0f, velocityY),
            velocityThresholdPx = 1_400f,
        )

    suspend fun returnToOpen() {
        if (phase != MediaViewerPhase.Dragging) return
        val token = ++animationToken
        returnStartFrame = draggedFrame()
        phase = MediaViewerPhase.Returning
        returning.snapTo(0f)
        if (motionScheme.reducedMotion) {
            returning.snapTo(1f)
        } else {
            returning.animateTo(1f, motionScheme.spatial)
        }
        returning.snapTo(1f)
        if (token == animationToken) {
            dragX = 0f
            dragY = 0f
            phase = MediaViewerPhase.Open
        }
    }

    suspend fun close(
        targetBounds: Rect?,
        viewport: Rect,
        releaseVelocity: Offset = Offset.Zero,
        targetFrame: MediaTransitionFrame? = null,
    ) {
        if (phase == MediaViewerPhase.Closing) return
        val token = ++animationToken
        closeStartFrame = visualFrame
        closeTargetFrame = targetFrame
            ?.takeIf { it.clipBounds.isValid() }
            ?: targetBounds?.takeIf { it.isValid() }?.let(::frameForBounds)
            ?: fallbackCloseFrame(closeStartFrame, viewport, Offset(dragX, dragY))
        phase = MediaViewerPhase.Closing
        closeCenterX.snapTo(closeStartFrame.clipBounds.center.x)
        closeCenterY.snapTo(closeStartFrame.clipBounds.center.y)
        closing.snapTo(0f)
        if (motionScheme.reducedMotion) {
            closing.snapTo(1f)
            closeCenterX.snapTo(closeTargetFrame.clipBounds.center.x)
            closeCenterY.snapTo(closeTargetFrame.clipBounds.center.y)
        } else {
            coroutineScope {
                listOf(
                    launch { closing.animateTo(1f, motionScheme.spatial) },
                    launch { closeCenterX.animateTo(closeTargetFrame.clipBounds.center.x, motionScheme.spatialPixels, initialVelocity = releaseVelocity.x) },
                    launch { closeCenterY.animateTo(closeTargetFrame.clipBounds.center.y, motionScheme.spatialPixels, initialVelocity = releaseVelocity.y) },
                ).joinAll()
            }
            closing.snapTo(1f)
            closeCenterX.snapTo(closeTargetFrame.clipBounds.center.x)
            closeCenterY.snapTo(closeTargetFrame.clipBounds.center.y)
        }
        if (token != animationToken) return
    }

    private fun draggedFrame(): MediaTransitionFrame {
        val progress = dismissProgress(Offset(dragX, dragY), dismissDistance())
        val scale = 1f - progress * 0.15f
        return transformFrame(destinationFrame, destinationFrame.clipBounds.center, scale, Offset(dragX, dragY))
    }

    private fun dismissDistance(): Float = minOf(
        dismissViewport.width.takeIf { it > 0f } ?: destinationBounds.width,
        dismissViewport.height.takeIf { it > 0f } ?: destinationBounds.height,
    ) * 0.20f
}

private fun frameForBounds(bounds: Rect): MediaTransitionFrame = MediaTransitionFrame(bounds, bounds)

private fun transformFrame(
    frame: MediaTransitionFrame,
    center: Offset,
    scale: Float,
    offset: Offset,
): MediaTransitionFrame = MediaTransitionFrame(
    imageBounds = transformRect(frame.imageBounds, center, scale, offset),
    clipBounds = transformRect(frame.clipBounds, center, scale, offset),
    visibleBounds = transformRect(frame.visibleBounds, center, scale, offset),
    cornerRadiusPx = frame.cornerRadiusPx * scale,
)

private fun centeredFrame(frame: MediaTransitionFrame, center: Offset): MediaTransitionFrame =
    transformFrame(frame, frame.clipBounds.center, 1f, center - frame.clipBounds.center)

private fun transformRect(rect: Rect, center: Offset, scale: Float, offset: Offset): Rect = Rect(
    left = center.x + (rect.left - center.x) * scale + offset.x,
    top = center.y + (rect.top - center.y) * scale + offset.y,
    right = center.x + (rect.right - center.x) * scale + offset.x,
    bottom = center.y + (rect.bottom - center.y) * scale + offset.y,
)

private fun fallbackCloseFrame(start: MediaTransitionFrame, viewport: Rect, drag: Offset): MediaTransitionFrame {
    val direction = if (drag.getDistance() > 0f) drag / drag.getDistance() else Offset(0f, 1f)
    return transformFrame(
        start,
        start.clipBounds.center,
        0.82f,
        direction * maxOf(viewport.width, viewport.height) * 0.9f,
    )
}

private fun Rect.isValid(): Boolean = width > 0f && height > 0f
