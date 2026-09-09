package me.foxtails.palustris

import androidx.compose.animation.core.SnapSpec
import androidx.compose.animation.core.SpringSpec
import me.foxtails.palustris.ui.motion.PalustrisMotionScheme
import me.foxtails.palustris.ui.motion.motionDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MotionTokensTest {
    @Test
    fun standardTokensUseTheDocumentedPhysicalValues() {
        val scheme = PalustrisMotionScheme.standard(reducedMotion = false)

        assertSpring(scheme.spatial, damping = 0.82f, stiffness = 520f)
        assertSpring(scheme.expressive, damping = 0.68f, stiffness = 620f)
        assertSpring(scheme.press, damping = 0.76f, stiffness = 900f)
        assertSpring(scheme.gentle, damping = 0.9f, stiffness = 380f)
        assertSpring(scheme.color, damping = 1f, stiffness = 700f)
        assertFalse(scheme.reducedMotion)
        assertTrue(scheme.fastFadeIn !is SnapSpec<*>)
        assertTrue(scheme.fastFadeOut !is SnapSpec<*>)
    }

    @Test
    fun reducedMotionUsesSnapSpecsAndRemovesSpatialOffsets() {
        val scheme = PalustrisMotionScheme.standard(reducedMotion = true)

        assertTrue(scheme.reducedMotion)
        assertTrue(scheme.spatial is SnapSpec<*>)
        assertTrue(scheme.expressive is SnapSpec<*>)
        assertTrue(scheme.press is SnapSpec<*>)
        assertTrue(scheme.gentle is SnapSpec<*>)
        assertTrue(scheme.color is SnapSpec<*>)
        assertTrue(scheme.spatialOffset is SnapSpec<*>)
        assertTrue(scheme.gentleOffset is SnapSpec<*>)
        assertEquals(0, scheme.floatingEnterOffsetPx)
    }

    @Test
    fun standardScalesStayWithinApprovedBounds() {
        val scheme = PalustrisMotionScheme.standard(reducedMotion = false)

        assertTrue(scheme.pressedScale >= 0.96f)
        assertTrue(scheme.compactPressedScale in 0.90f..0.92f)
        assertTrue(scheme.largePressedScale >= 0.985f)
        assertTrue(scheme.selectionStartScale >= 0.86f)
        assertTrue(scheme.selectionMaxScale <= 1.08f)
        assertTrue(scheme.floatingEnterScale >= 0.92f)
    }

    @Test
    fun directionHandlesForwardBackwardSameAndReducedMotionTransitions() {
        assertEquals(1, motionDirection(previousOrdinal = 0, targetOrdinal = 2))
        assertEquals(-1, motionDirection(previousOrdinal = 2, targetOrdinal = 0))
        assertEquals(0, motionDirection(previousOrdinal = 1, targetOrdinal = 1))
        assertEquals(0, motionDirection(previousOrdinal = 0, targetOrdinal = 2, reducedMotion = true))
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> assertSpring(spec: androidx.compose.animation.core.FiniteAnimationSpec<T>, damping: Float, stiffness: Float) {
        val spring = spec as SpringSpec<T>
        assertEquals(damping, spring.dampingRatio, 0.0001f)
        assertEquals(stiffness, spring.stiffness, 0.0001f)
    }
}
