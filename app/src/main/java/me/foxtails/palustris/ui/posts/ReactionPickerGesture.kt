package me.foxtails.palustris.ui.posts

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.abs

/** Classifies the heart gesture before a compact popup can intercept the pointer sequence. */
internal fun Modifier.reactionPickerGesture(
    enabled: Boolean,
    gestureKey: Any,
    thresholdPx: Float,
    onCompact: () -> Unit,
    onExpanded: () -> Unit,
): Modifier = if (!enabled) {
    this
} else {
    pointerInput(enabled, gestureKey, thresholdPx) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val slop = viewConfiguration.touchSlop
            var cancelled = false
            var longPressed = false
            var expanded = false

            while (!cancelled && !expanded) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.changes.size != 1) {
                    cancelled = true
                    continue
                }
                val change = event.changes.singleOrNull()
                if (change == null) {
                    cancelled = true
                } else if (!change.pressed) {
                    longPressed = change.uptimeMillis - down.uptimeMillis >= viewConfiguration.longPressTimeoutMillis
                    if (change.changedToUpIgnoreConsumed() && longPressed) onCompact()
                    cancelled = true
                } else {
                    longPressed = change.uptimeMillis - down.uptimeMillis >= viewConfiguration.longPressTimeoutMillis
                    val dx = change.position.x - down.position.x
                    val dy = change.position.y - down.position.y
                    if (!longPressed && (abs(dx) > slop || abs(dy) > slop)) {
                        cancelled = true
                        continue
                    }
                    if (!longPressed) continue
                    if (!expanded && -dy >= thresholdPx && -dy > abs(dx)) {
                        change.consume()
                        expanded = true
                        onExpanded()
                    }
                }
            }
            while (longPressed && !cancelled && !expanded) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.singleOrNull()
                if (event.changes.size != 1 || change == null) {
                    cancelled = true
                } else if (!change.pressed) {
                    if (change.changedToUpIgnoreConsumed()) onCompact()
                    cancelled = true
                } else {
                    val dx = change.position.x - down.position.x
                    val dy = change.position.y - down.position.y
                    if (-dy >= thresholdPx && -dy > abs(dx)) {
                        change.consume()
                        expanded = true
                        onExpanded()
                    }
                }
            }
        }
    }
}
