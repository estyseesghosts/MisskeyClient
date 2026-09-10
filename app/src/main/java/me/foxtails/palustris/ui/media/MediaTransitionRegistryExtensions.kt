package me.foxtails.palustris.ui.media

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.LayoutCoordinates

internal fun MediaTransitionRegistry.updateIfVisible(key: MediaTransitionKey, source: MediaTransitionSource) {
    if (source.fullBounds.isValid() && source.visibleBounds.isValid()) {
        update(key, source)
    } else {
        remove(key)
    }
}

private fun Rect.isValid(): Boolean = width > 0f && height > 0f

internal fun Rect.visiblePartIn(viewport: Rect?): Rect = if (viewport == null) {
    this
} else {
    Rect(
        left = maxOf(left, viewport.left),
        top = maxOf(top, viewport.top),
        right = minOf(right, viewport.right),
        bottom = minOf(bottom, viewport.bottom),
    )
}

internal fun LayoutCoordinates.fullBoundsInRoot(): Rect {
    val topLeft = localToRoot(Offset.Zero)
    val bottomRight = localToRoot(Offset(size.width.toFloat(), size.height.toFloat()))
    return Rect(
        left = minOf(topLeft.x, bottomRight.x),
        top = minOf(topLeft.y, bottomRight.y),
        right = maxOf(topLeft.x, bottomRight.x),
        bottom = maxOf(topLeft.y, bottomRight.y),
    )
}

internal fun LayoutCoordinates.visibleBoundsInRoot(viewport: Rect?): Rect {
    var visible = fullBoundsInRoot()
    var ancestor = parentLayoutCoordinates
    while (ancestor != null) {
        visible = visible.intersectWith(ancestor.fullBoundsInRoot())
        ancestor = ancestor.parentLayoutCoordinates
    }
    return visible.visiblePartIn(viewport)
}

private fun Rect.intersectWith(other: Rect): Rect = Rect(
    left = maxOf(left, other.left),
    top = maxOf(top, other.top),
    right = minOf(right, other.right),
    bottom = minOf(bottom, other.bottom),
)
