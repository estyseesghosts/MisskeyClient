package me.foxtails.palustris.ui.navigation

import android.os.Build
import android.view.View
import android.view.WindowInsets

enum class NavigationMode { Gesture, NonGesture, Unknown }

object NavigationModeObserver {
    fun classify(hasGestureInsets: Boolean, hasWindowInsets: Boolean): NavigationMode = when {
        !hasWindowInsets -> NavigationMode.Unknown
        hasGestureInsets -> NavigationMode.Gesture
        else -> NavigationMode.NonGesture
    }

    fun current(view: View): NavigationMode {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return NavigationMode.Unknown
        val insets = view.rootWindowInsets ?: return NavigationMode.Unknown
        return classify(
            hasGestureInsets = insets.systemGestureInsets.left > 0 || insets.systemGestureInsets.right > 0,
            hasWindowInsets = insets.systemWindowInsets.left > 0 || insets.systemWindowInsets.right > 0 ||
                insets.systemWindowInsets.bottom > 0,
        )
    }

    fun classify(insets: WindowInsets): NavigationMode = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
        NavigationMode.Unknown
    } else {
        classify(
            hasGestureInsets = insets.systemGestureInsets.left > 0 || insets.systemGestureInsets.right > 0,
            hasWindowInsets = insets.systemWindowInsets.left > 0 || insets.systemWindowInsets.right > 0 ||
                insets.systemWindowInsets.bottom > 0,
        )
    }
}
