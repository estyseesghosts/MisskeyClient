package me.foxtails.palustris.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import me.foxtails.palustris.ui.motion.LocalPalustrisMotionScheme
import me.foxtails.palustris.ui.motion.palustrisMotionScheme

@Composable
fun PalustrisTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val motionScheme = palustrisMotionScheme()
    CompositionLocalProvider(LocalPalustrisMotionScheme provides motionScheme) {
        MaterialTheme(
            colorScheme = if (isSystemInDarkTheme()) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context),
            typography = Typography(),
            content = content,
        )
    }
}
