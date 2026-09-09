package me.foxtails.palustris.ui.media

import androidx.compose.ui.geometry.Rect

internal fun MediaTransitionRegistry.updateIfVisible(key: MediaTransitionKey, bounds: Rect) {
    update(key, bounds)
}
