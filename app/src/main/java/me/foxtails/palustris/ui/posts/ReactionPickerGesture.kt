package me.foxtails.palustris.ui.posts

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.withTimeoutOrNull
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

            while (!cancelled && !longPressed) {
                val event = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                    awaitPointerEvent(PointerEventPass.Initial)
                }
                if (event == null) {
                    longPressed = true
                    continue
                }
                if (event.changes.size != 1) {
                    cancelled = true
                    continue
                }
                val change = event.changes.singleOrNull()
                if (change == null) {
                    cancelled = true
                    continue
                }
                if (!change.pressed) {
                    if (change.uptimeMillis - down.uptimeMillis >= viewConfiguration.longPressTimeoutMillis) {
                        longPressed = true
                        if (change.changedToUpIgnoreConsumed()) onCompact()
                    }
                    cancelled = true
                    continue
                }
                val dx = change.position.x - down.position.x
                val dy = change.position.y - down.position.y
                if (change.uptimeMillis - down.uptimeMillis >= viewConfiguration.longPressTimeoutMillis) {
                    longPressed = true
                    if (-dy >= thresholdPx && -dy > abs(dx)) {
                        change.consume()
                        expanded = true
                        onExpanded()
                    }
                } else if (abs(dx) > slop || abs(dy) > slop) {
                    cancelled = true
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
