package me.foxtails.palustris

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Offset
import me.foxtails.palustris.ui.media.MediaTransitionKey
import me.foxtails.palustris.ui.media.MediaTransitionRegistry
import me.foxtails.palustris.ui.media.MediaTransitionSource
import me.foxtails.palustris.ui.media.MediaViewerPhase
import me.foxtails.palustris.ui.media.MediaViewerTransitionState
import me.foxtails.palustris.ui.media.ZoomableMediaState
import me.foxtails.palustris.ui.media.dismissProgress
import me.foxtails.palustris.ui.media.cropRect
import me.foxtails.palustris.ui.media.fitRect
import me.foxtails.palustris.ui.media.lerpRect
import me.foxtails.palustris.ui.media.shouldDismiss
import me.foxtails.palustris.ui.media.updateIfVisible
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.coroutines.runBlocking

class MediaTransitionStateTest {
    @Test
    fun fitRectCentersPortraitAndLandscapeImages() {
        assertEquals(Rect(250f, 0f, 750f, 1000f), fitRect(Rect(0f, 0f, 1000f, 1000f), 1f, 2f))
        assertEquals(Rect(0f, 250f, 1000f, 750f), fitRect(Rect(0f, 0f, 1000f, 1000f), 2f, 1f))
    }

    @Test
    fun deterministicEdgeMarkerFixtureVariantsKeepCropGeometry() {
        val boundedFeed = Rect(0f, 0f, 240f, 240f)
        val landscape = cropRect(boundedFeed, 16f, 9f)
        val portrait = cropRect(boundedFeed, 9f, 16f)
        val sameAspect = cropRect(Rect(0f, 0f, 320f, 180f), 16f, 9f)
        val differentAspect = cropRect(Rect(0f, 0f, 320f, 180f), 4f, 3f)
        val missingMetadata = cropRect(boundedFeed, 4f, 3f)

        assertTrue(landscape.width > boundedFeed.width)
        assertEquals(boundedFeed.height, landscape.height, 0.001f)
        assertTrue(portrait.height > boundedFeed.height)
        assertEquals(boundedFeed.width, portrait.width, 0.001f)
        assertEquals(Rect(0f, 0f, 320f, 180f), sameAspect)
        assertTrue(differentAspect.height > sameAspect.height)
        assertEquals(4f / 3f, missingMetadata.width / missingMetadata.height, 0.001f)
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

    @Test
    fun registryKeepsCompleteBoundsForPartialClipsAndInvalidatesInvisibleSources() {
        val registry = MediaTransitionRegistry()
        val key = MediaTransitionKey("account", "post", "attachment", "occurrence")
        val full = Rect(10f, 20f, 210f, 220f)
        val visible = Rect(10f, 40f, 210f, 220f)

        registry.updateIfVisible(key, MediaTransitionSource(fullBounds = full, visibleBounds = visible))
        assertEquals(full, registry.sourceFor(key)?.fullBounds)
        assertEquals(visible, registry.sourceFor(key)?.visibleBounds)

        registry.updateIfVisible(key, MediaTransitionSource(fullBounds = full, visibleBounds = Rect(0f, 0f, 0f, 0f)))
        assertNull(registry.sourceFor(key))
    }

    @Test
    fun oldViewerOwnerCannotClearAReplacementOrItsHandoff() {
        val registry = MediaTransitionRegistry()
        val key = MediaTransitionKey("account", "post", "attachment")
        val first = registry.begin(key)
        registry.prepareHandoff(first)
        assertFalse(registry.isSourceHidden(key))

        val replacement = registry.begin(key)
        registry.end(first)
        assertTrue(registry.isOwnerActive(replacement))
        assertTrue(registry.isSourceHidden(key))

        registry.end(replacement)
        assertNull(registry.currentActiveKey)
    }

    @Test
    fun reducedMotionTransitionSnapsThroughReturnAndClosePhases() = runBlocking {
        val source = Rect(20f, 40f, 220f, 240f)
        val destination = Rect(0f, 0f, 1_000f, 1_000f)
        val state = MediaViewerTransitionState(source, destination, me.foxtails.palustris.ui.motion.PalustrisMotionScheme.standard(true))

        assertEquals(MediaViewerPhase.Open, state.phase)
        state.beginDrag()
        state.dragBy(Offset(0f, 240f))
        assertEquals(MediaViewerPhase.Dragging, state.phase)
        state.returnToOpen()
        assertEquals(MediaViewerPhase.Open, state.phase)
        state.close(source, destination)
        assertEquals(MediaViewerPhase.Closing, state.phase)
        assertEquals(source, state.visualBounds)
    }

    @Test
    fun longDragUsesTheSameControlledClosePath() = runBlocking {
        val source = Rect(20f, 40f, 220f, 240f)
        val destination = Rect(0f, 0f, 1_000f, 1_000f)
        val state = MediaViewerTransitionState(source, destination, me.foxtails.palustris.ui.motion.PalustrisMotionScheme.standard(true))

        state.beginDrag()
        state.dragBy(Offset(0f, 250f))
        assertTrue(state.shouldDismiss(0f))
        state.close(source, destination)
        assertEquals(MediaViewerPhase.Closing, state.phase)
        assertEquals(source, state.visualBounds)
    }

    @Test
    fun zoomStateExposesWhetherDismissIsAllowed() {
        val state = ZoomableMediaState()

        assertFalse(state.isZoomed)
        state.applyTransform(2f, Offset(20f, 10f))
        assertTrue(state.isZoomed)
        state.setDoubleTapZoom()
        assertFalse(state.isZoomed)
        assertEquals(Offset.Zero, state.offset)
    }
}
