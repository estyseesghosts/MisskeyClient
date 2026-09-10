package me.foxtails.palustris.ui.large

import androidx.compose.ui.geometry.Rect
import kotlin.math.max
import kotlin.math.min

internal enum class LargeLayoutMode {
    Compact,
    Single,
    Expanded,
}

internal data class LargeFoldingFeature(
    val bounds: Rect,
    val isVertical: Boolean,
    val isSeparating: Boolean,
    val isOccluding: Boolean,
)

internal data class LargePaneLayout(
    val mode: LargeLayoutMode,
    val safeRegions: List<Rect>,
    val primary: Rect,
    val detail: Rect?,
)

internal fun largeLayoutMode(windowWidthDp: Float): LargeLayoutMode = when {
    windowWidthDp < 600f -> LargeLayoutMode.Compact
    windowWidthDp < 840f -> LargeLayoutMode.Single
    else -> LargeLayoutMode.Expanded
}

/** Calculates pane bounds after the rail, margins, and any separating feature are removed. */
internal fun calculateLargePaneLayout(
    windowWidthDp: Float,
    contentWidthDp: Float,
    contentHeightDp: Float,
    density: Float,
    foldingFeatures: List<LargeFoldingFeature> = emptyList(),
    outerMarginDp: Float = 16f,
    dividerDp: Float = 1f,
    minimumListDp: Float = 320f,
    minimumDetailDp: Float = 360f,
): LargePaneLayout {
    val mode = largeLayoutMode(windowWidthDp)
    val scale = density.coerceAtLeast(0.1f)
    val width = contentWidthDp * scale
    val height = contentHeightDp * scale
    val margin = outerMarginDp * scale
    val divider = dividerDp * scale
    val minimumList = minimumListDp * scale
    val minimumDetail = minimumDetailDp * scale
    val content = Rect(margin, 0f, max(margin, width - margin), max(0f, height))

    val structuralFeatures = foldingFeatures.filter {
        (it.isSeparating || it.isOccluding) && it.bounds.overlaps(content)
    }
    val verticalFeatures = structuralFeatures.filter {
        it.isVertical && (it.isSeparating || it.isOccluding) && it.bounds.overlaps(content)
    }
    val safeRegions = verticalFeatures.fold(listOf(content)) { regions, feature ->
        regions.flatMap { region ->
            if (!feature.bounds.overlaps(region)) {
                listOf(region)
            } else {
                listOfNotNull(
                    Rect(region.left, region.top, min(region.right, feature.bounds.left), region.bottom)
                        .takeIf { it.width > 0f },
                    Rect(max(region.left, feature.bounds.right), region.top, region.right, region.bottom)
                        .takeIf { it.width > 0f },
                )
            }
        }
    }

    val horizontalFeatures = structuralFeatures.filter { !it.isVertical }
    val postureRegions = horizontalFeatures.fold(safeRegions) { regions, feature ->
        regions.flatMap { region ->
            if (!feature.bounds.overlaps(region)) {
                listOf(region)
            } else {
                listOfNotNull(
                    Rect(region.left, region.top, region.right, min(region.bottom, feature.bounds.top))
                        .takeIf { it.height > 0f },
                    Rect(region.left, max(region.top, feature.bounds.bottom), region.right, region.bottom)
                        .takeIf { it.height > 0f },
                )
            }
        }
    }

    val largestRegion = postureRegions.maxByOrNull { it.width * it.height } ?: content
    if (mode != LargeLayoutMode.Expanded || horizontalFeatures.isNotEmpty()) {
        return LargePaneLayout(mode, postureRegions, largestRegion, null)
    }

    if (postureRegions.size >= 2) {
        val left = postureRegions.first()
        val right = postureRegions.last()
        if (left.width >= minimumList && right.width >= minimumDetail) {
            return LargePaneLayout(mode, safeRegions, left, right)
        }
    }

    val available = largestRegion.width
    val preferredList = available * 0.45f
    val listWidth = preferredList.coerceIn(
        minimumList,
        (available - minimumDetail - divider).coerceAtLeast(minimumList),
    )
    val canSplit = available >= minimumList + divider + minimumDetail
    if (!canSplit) return LargePaneLayout(mode, safeRegions, largestRegion, null)

    val primary = Rect(largestRegion.left, largestRegion.top, largestRegion.left + listWidth, largestRegion.bottom)
    val detail = Rect(primary.right + divider, largestRegion.top, largestRegion.right, largestRegion.bottom)
    return LargePaneLayout(mode, safeRegions, primary, detail)
}
