package me.foxtails.palustris.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/** Applies the app-shell navigation-bar appearance for normal and viewer modes. */
@Composable
internal fun SystemBars(mediaViewerOpen: Boolean) {
    val context = LocalContext.current
    val view = LocalView.current
    val window = (view.rootView.context.findActivity() ?: context.findActivity())?.window ?: return
    val navigationBarColor = if (mediaViewerOpen) Color.Black else MaterialTheme.colorScheme.background
    val useDarkNavigationIcons = !mediaViewerOpen && navigationBarColor.luminance() > 0.5f

    SideEffect {
        window.navigationBarColor = navigationBarColor.toArgb()
        window.navigationBarDividerColor = navigationBarColor.toArgb()
        window.isNavigationBarContrastEnforced = false
        WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = useDarkNavigationIcons
    }
}

private fun Context.findActivity(): Activity? {
    var current: Context = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        val base = current.baseContext
        if (base === current) return null
        current = base
    }
    return current as? Activity
}
