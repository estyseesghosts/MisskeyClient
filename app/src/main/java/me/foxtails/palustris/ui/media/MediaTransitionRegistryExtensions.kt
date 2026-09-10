package me.foxtails.palustris.ui.media

import androidx.compose.ui.geometry.Rect

internal fun MediaTransitionRegistry.updateIfVisible(key: MediaTransitionKey, source: MediaTransitionSource) {
    if (source.visibleBounds.isValid()) update(key, source)
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
