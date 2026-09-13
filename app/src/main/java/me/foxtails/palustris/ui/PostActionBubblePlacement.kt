package me.foxtails.palustris.ui

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.PopupPositionProvider
import kotlin.math.roundToInt

internal enum class BubblePlacement { Above, Below }

internal class WindowAnchorPositionProvider(
    private val targetBounds: Rect,
    private val placement: BubblePlacement,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val fallback = Rect(
            anchorBounds.left.toFloat(),
            anchorBounds.top.toFloat(),
            anchorBounds.right.toFloat(),
            anchorBounds.bottom.toFloat(),
        )
        val anchor = targetBounds.takeIf { it.width > 0f && it.height > 0f } ?: fallback
        val margin = 8
        val preferredX = when (placement) {
            BubblePlacement.Above -> anchor.center.x.roundToInt() - popupContentSize.width / 2
            BubblePlacement.Below -> anchor.right.roundToInt() - popupContentSize.width
        }
        val x = preferredX.coerceIn(
            margin,
            (windowSize.width - popupContentSize.width - margin).coerceAtLeast(margin),
        )
        val preferredY = when (placement) {
            BubblePlacement.Above -> anchor.top.roundToInt() - popupContentSize.height - margin
            BubblePlacement.Below -> anchor.bottom.roundToInt() + margin
        }
        val alternateY = when (placement) {
            BubblePlacement.Above -> anchor.bottom.roundToInt() + margin
            BubblePlacement.Below -> anchor.top.roundToInt() - popupContentSize.height - margin
        }
        val maxY = (windowSize.height - popupContentSize.height - margin).coerceAtLeast(margin)
        val y = if (preferredY in margin..maxY) preferredY else alternateY.coerceIn(margin, maxY)
        return IntOffset(x, y)
    }
}
