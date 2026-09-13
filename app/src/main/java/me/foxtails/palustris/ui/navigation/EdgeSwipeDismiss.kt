package me.foxtails.palustris.ui.navigation

import androidx.compose.ui.Modifier
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import kotlin.math.abs

private const val EDGE_WIDTH_PX = 144f
private const val DISMISS_DISTANCE_PX = 180f

/** Deliberately only consumes a clear horizontal gesture that began near either edge. */
fun Modifier.edgeSwipeDismiss(enabled: Boolean, onDismiss: () -> Unit): Modifier = if (!enabled) this else {
    pointerInput(onDismiss) {
        awaitPointerEventScope {
            while (true) {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                val width = size.width.toFloat()
                val fromEdge = down.position.x <= EDGE_WIDTH_PX || down.position.x >= width - EDGE_WIDTH_PX
                if (!fromEdge) continue
                var horizontal = 0f
                var vertical = 0f
                var cancelled = false
                while (!cancelled) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull() ?: break
                    if (!change.pressed) {
                        if (abs(horizontal) >= DISMISS_DISTANCE_PX && abs(horizontal) > abs(vertical)) onDismiss()
                        break
                    }
                    val delta = change.positionChange()
                    horizontal += delta.x
                    vertical += delta.y
                    if (abs(vertical) > abs(horizontal) + 24f || event.changes.size > 1) cancelled = true
                }
            }
        }
    }
}
