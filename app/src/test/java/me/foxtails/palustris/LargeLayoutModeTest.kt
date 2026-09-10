package me.foxtails.palustris

import androidx.compose.ui.geometry.Rect
import me.foxtails.palustris.ui.large.LargeFoldingFeature
import me.foxtails.palustris.ui.large.LargeLayoutMode
import me.foxtails.palustris.ui.large.calculateLargePaneLayout
import me.foxtails.palustris.ui.large.largeLayoutMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LargeLayoutModeTest {
    @Test
    fun widthPolicyKeepsCompactBoundaryAndExpandedBaseline() {
        assertEquals(LargeLayoutMode.Compact, largeLayoutMode(599f))
        assertEquals(LargeLayoutMode.Single, largeLayoutMode(600f))
        assertEquals(LargeLayoutMode.Single, largeLayoutMode(839f))
        assertEquals(LargeLayoutMode.Expanded, largeLayoutMode(840f))
        assertEquals(LargeLayoutMode.Expanded, largeLayoutMode(1600f))
    }

    @Test
    fun singleLayoutAlwaysUsesOnePrimaryPane() {
        val layout = calculateLargePaneLayout(
            windowWidthDp = 600f,
            contentWidthDp = 560f,
            contentHeightDp = 600f,
            density = 1f,
        )

        assertEquals(LargeLayoutMode.Single, layout.mode)
        assertNull(layout.detail)
        assertEquals(528f, layout.primary.width, 0.001f)
    }

    @Test
    fun unobstructedExpandedWindowClampsListToPaneMinimums() {
        val layout = calculateLargePaneLayout(
            windowWidthDp = 840f,
            contentWidthDp = 760f,
            contentHeightDp = 600f,
            density = 1f,
        )

        assertEquals(LargeLayoutMode.Expanded, layout.mode)
        assertTrue(layout.primary.width >= 320f)
        assertTrue((layout.detail?.width ?: 0f) >= 360f)
        assertEquals(727f, layout.primary.width + (layout.detail?.width ?: 0f), 0.001f)
    }

    @Test
    fun verticalOccludingHingeCreatesSeparateSafePanes() {
        val layout = calculateLargePaneLayout(
            windowWidthDp = 840f,
            contentWidthDp = 760f,
            contentHeightDp = 600f,
            density = 1f,
            foldingFeatures = listOf(
                LargeFoldingFeature(Rect(379f, 0f, 381f, 600f), isVertical = true, isSeparating = false, isOccluding = true),
            ),
        )

        assertEquals(2, layout.safeRegions.size)
        assertTrue(layout.primary.right <= 379f)
        assertTrue((layout.detail?.left ?: 0f) >= 381f)
    }

    @Test
    fun narrowHingeRegionFallsBackToSinglePane() {
        val layout = calculateLargePaneLayout(
            windowWidthDp = 840f,
            contentWidthDp = 700f,
            contentHeightDp = 600f,
            density = 1f,
            foldingFeatures = listOf(
                LargeFoldingFeature(Rect(340f, 0f, 360f, 600f), isVertical = true, isSeparating = true, isOccluding = false),
            ),
        )

        assertNull(layout.detail)
    }

    @Test
    fun nonSeparatingCreaseDoesNotRemoveContent() {
        val layout = calculateLargePaneLayout(
            windowWidthDp = 840f,
            contentWidthDp = 760f,
            contentHeightDp = 600f,
            density = 1f,
            foldingFeatures = listOf(
                LargeFoldingFeature(Rect(379f, 0f, 381f, 600f), isVertical = true, isSeparating = false, isOccluding = false),
            ),
        )

        assertEquals(1, layout.safeRegions.size)
        assertTrue(layout.detail != null)
    }

    @Test
    fun horizontalSeparatingFoldSplitsSafeRegionsWithoutDetailPane() {
        val layout = calculateLargePaneLayout(
            windowWidthDp = 840f,
            contentWidthDp = 760f,
            contentHeightDp = 900f,
            density = 1f,
            foldingFeatures = listOf(
                LargeFoldingFeature(Rect(0f, 449f, 760f, 451f), isVertical = false, isSeparating = true, isOccluding = false),
            ),
        )

        assertEquals(2, layout.safeRegions.size)
        assertNull(layout.detail)
        assertTrue(layout.primary.bottom <= 449f || layout.primary.top >= 451f)
    }
}
