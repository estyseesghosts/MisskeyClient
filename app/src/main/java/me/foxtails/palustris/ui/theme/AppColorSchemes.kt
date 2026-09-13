package me.foxtails.palustris.ui.theme

import android.content.Context
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import me.foxtails.palustris.domain.AppBackground
import me.foxtails.palustris.domain.AppColorScheme

@Composable
fun resolvedAppColorScheme(
    context: Context,
    colorScheme: AppColorScheme,
    background: AppBackground,
    darkTheme: Boolean = isSystemInDarkTheme(),
): ColorScheme {
    val base = when (colorScheme) {
        AppColorScheme.System -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        } else if (darkTheme) darkColorScheme() else lightColorScheme()
        AppColorScheme.Pastel -> accentScheme(darkTheme, Color(0xFF7C6FA4), Color(0xFFD8C9FF))
        AppColorScheme.Vibrant -> accentScheme(darkTheme, Color(0xFF005CCC), Color(0xFF72A7FF))
        AppColorScheme.Monochrome -> accentScheme(darkTheme, Color(0xFF505050), Color(0xFFD0D0D0))
    }
    return when (background) {
        AppBackground.Default -> base
        AppBackground.Dark -> base.withNeutralBackground(Color(0xFF121212), dark = true)
        AppBackground.PureBlack -> base.withNeutralBackground(Color.Black, dark = true)
    }
}

private fun accentScheme(dark: Boolean, lightPrimary: Color, darkPrimary: Color): ColorScheme =
    if (dark) darkColorScheme(primary = darkPrimary) else lightColorScheme(primary = lightPrimary)

private fun ColorScheme.withNeutralBackground(background: Color, dark: Boolean): ColorScheme = copy(
    background = background,
    surface = background,
    surfaceDim = background,
    surfaceBright = background,
    surfaceContainerLowest = background,
    surfaceContainerLow = background,
    surfaceContainer = if (dark) background.copy(alpha = 0.96f) else background,
    surfaceContainerHigh = if (dark) Color(0xFF1D1D1D) else Color(0xFFF2F2F2),
    surfaceContainerHighest = if (dark) Color(0xFF282828) else Color(0xFFE8E8E8),
    onBackground = if (dark) Color.White else Color.Black,
    onSurface = if (dark) Color.White else Color.Black,
    onSurfaceVariant = if (dark) Color(0xFFD0D0D0) else Color(0xFF454545),
)
