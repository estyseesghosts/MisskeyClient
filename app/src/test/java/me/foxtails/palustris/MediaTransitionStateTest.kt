package me.foxtails.palustris

import androidx.compose.ui.geometry.Rect
import me.foxtails.palustris.ui.media.MediaTransitionKey
import me.foxtails.palustris.ui.media.MediaTransitionRegistry
import me.foxtails.palustris.ui.media.dismissProgress
import me.foxtails.palustris.ui.media.fitRect
import me.foxtails.palustris.ui.media.lerpRect
import me.foxtails.palustris.ui.media.shouldDismiss
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaTransitionStateTest {
    @Test
    fun fitRectCentersPortraitAndLandscapeImages() {
        assertEquals(Rect(250f, 0f, 750f, 1000f), fitRect(Rect(0f, 0f, 1000f, 1000f), 1f, 2f))
        assertEquals(Rect(0f, 250f, 1000f, 750f), fitRect(Rect(0f, 0f, 1000f, 1000f), 2f, 1f))
    }

    @Test
    fun geometryClampsInteractiveProgressAndInterpolation() {
        assertEquals(0.5f, dismissProgress(-100f, 200f), 0.001f)
        assertEquals(1f, dismissProgress(500f, 200f), 0.001f)
        assertEquals(Rect(25f, 25f, 75f, 75f), lerpRect(Rect(0f, 0f, 50f, 50f), Rect(50f, 50f, 100f, 100f), 0.5f))
    }

    @Test
    fun distanceAndVelocityShareDismissThresholds() {
        assertFalse(shouldDismiss(100f, 0f, 1_000f))
        assertTrue(shouldDismiss(200f, 0f, 1_000f))
        assertTrue(shouldDismiss(10f, -1_400f, 1_000f))
    }

    @Test
    fun registryTracksActiveKeyAndCurrentBounds() {
        val registry = MediaTransitionRegistry()
        val key = MediaTransitionKey("account", "post", "attachment")
        val bounds = Rect(1f, 2f, 101f, 202f)

        registry.update(key, bounds)
        assertEquals(bounds, registry.boundsFor(key))
        assertFalse(registry.isActive(key))
        registry.begin(key)
        assertTrue(registry.isActive(key))
        registry.end(key)
        assertNull(registry.currentActiveKey)
        registry.remove(key)
        assertNull(registry.boundsFor(key))
    }
}
