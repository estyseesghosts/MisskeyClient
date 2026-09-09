package me.foxtails.palustris.ui.media

import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import me.foxtails.palustris.ui.motion.PalustrisMotionScheme

enum class MediaViewerPhase {
    Opening,
    Open,
    Dragging,
    Returning,
    Closing,
}

/** Owns the interruptible geometry and alpha values for the media overlay. */
class MediaViewerTransitionState(
    initialSourceBounds: Rect,
    destinationBounds: Rect,
    private val motionScheme: PalustrisMotionScheme,
) {
    private val opening = Animatable(if (initialSourceBounds.isValid()) 0f else 1f)
    private val returning = Animatable(1f)
    private val closing = Animatable(0f)

    private var sourceBounds = initialSourceBounds.takeIf { it.isValid() }
    private var closeStart = destinationBounds
    private var closeTarget = destinationBounds

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

    val visualBounds: Rect
        get() = when (phase) {
            MediaViewerPhase.Opening -> lerpRect(sourceBounds ?: destinationBounds, destinationBounds, opening.value)
            MediaViewerPhase.Open -> destinationBounds
            MediaViewerPhase.Dragging -> draggedBounds()
            MediaViewerPhase.Returning -> lerpRect(draggedBounds(), destinationBounds, returning.value)
            MediaViewerPhase.Closing -> lerpRect(closeStart, closeTarget, closing.value)
        }

    val dismissProgress: Float
        get() = when (phase) {
            MediaViewerPhase.Dragging -> dismissProgress(dragY, destinationBounds.height * 0.20f)
            MediaViewerPhase.Returning -> 0f
            MediaViewerPhase.Closing -> 1f
            else -> 0f
        }

    val backgroundAlpha: Float
        get() = when (phase) {
            MediaViewerPhase.Opening -> opening.value
            MediaViewerPhase.Dragging -> 1f - dismissProgress
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

    fun updateDestinationBounds(value: Rect) {
        if (value.isValid()) destinationBounds = value
    }

    fun updateSourceBounds(value: Rect?) {
        if (value?.isValid() == true) sourceBounds = value
    }

    suspend fun startOpening() {
        if (phase != MediaViewerPhase.Opening) return
        if (motionScheme.reducedMotion) opening.snapTo(1f)
        else opening.animateTo(1f, motionScheme.spatial)
        phase = MediaViewerPhase.Open
    }

    fun beginDrag() {
        if (phase == MediaViewerPhase.Open) phase = MediaViewerPhase.Dragging
    }

    fun dragBy(delta: Offset) {
        if (phase != MediaViewerPhase.Dragging) return
        dragX += delta.x * 0.15f
        dragY += delta.y
    }

    fun shouldDismiss(velocityY: Float): Boolean = shouldDismiss(dragY, velocityY, destinationBounds.height)

    suspend fun returnToOpen() {
        if (phase != MediaViewerPhase.Dragging) return
        phase = MediaViewerPhase.Returning
        returning.snapTo(0f)
        if (motionScheme.reducedMotion) returning.snapTo(1f)
        else returning.animateTo(1f, motionScheme.spatial)
        dragX = 0f
        dragY = 0f
        phase = MediaViewerPhase.Open
    }

    suspend fun close(targetBounds: Rect?, viewport: Rect) {
        if (phase == MediaViewerPhase.Closing) return
        closeStart = visualBounds
        closeTarget = targetBounds?.takeIf { it.isValid() } ?: fallbackCloseBounds(closeStart, viewport, dragY)
        phase = MediaViewerPhase.Closing
        closing.snapTo(0f)
        if (motionScheme.reducedMotion) closing.snapTo(1f)
        else closing.animateTo(1f, motionScheme.spatial)
    }

    private fun draggedBounds(): Rect {
        val progress = dismissProgress(dragY, destinationBounds.height * 0.20f)
        val scale = 1f - progress * 0.15f
        val width = destinationBounds.width * scale
        val height = destinationBounds.height * scale
        val center = destinationBounds.center + Offset(dragX, dragY)
        return Rect(center.x - width / 2f, center.y - height / 2f, center.x + width / 2f, center.y + height / 2f)
    }
}

private fun Rect.isValid(): Boolean = width > 0f && height > 0f

private fun fallbackCloseBounds(start: Rect, viewport: Rect, dragY: Float): Rect {
    val direction = if (dragY < 0f) -1f else 1f
    val scale = 0.82f
    val width = start.width * scale
    val height = start.height * scale
    val center = start.center + Offset(0f, direction * viewport.height * 0.9f)
    return Rect(center.x - width / 2f, center.y - height / 2f, center.x + width / 2f, center.y + height / 2f)
}
