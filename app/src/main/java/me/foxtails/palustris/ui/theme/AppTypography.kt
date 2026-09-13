package me.foxtails.palustris.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import me.foxtails.palustris.domain.AppFont
import me.foxtails.palustris.domain.AppTextSize

fun appTypography(font: AppFont, textSize: AppTextSize): Typography {
    val family = when (font) {
        AppFont.Device -> FontFamily.Default
        AppFont.Serif -> FontFamily.Serif
        // The bundled font can be added without changing this public preference contract.
        // SansSerif is the safe fallback when a platform cannot load the optional font.
        AppFont.OpenDyslexic -> FontFamily.SansSerif
    }
    val scale = when (textSize) {
        AppTextSize.Device -> 1f
        AppTextSize.Smaller -> 0.9f
        AppTextSize.Larger -> 1.1f
    }
    val base = Typography()
    return base.copy(
        displayLarge = base.displayLarge.with(family, scale),
        displayMedium = base.displayMedium.with(family, scale),
        displaySmall = base.displaySmall.with(family, scale),
        headlineLarge = base.headlineLarge.with(family, scale),
        headlineMedium = base.headlineMedium.with(family, scale),
        headlineSmall = base.headlineSmall.with(family, scale),
        titleLarge = base.titleLarge.with(family, scale),
        titleMedium = base.titleMedium.with(family, scale),
        titleSmall = base.titleSmall.with(family, scale),
        bodyLarge = base.bodyLarge.with(family, scale),
        bodyMedium = base.bodyMedium.with(family, scale),
        bodySmall = base.bodySmall.with(family, scale),
        labelLarge = base.labelLarge.with(family, scale),
        labelMedium = base.labelMedium.with(family, scale),
        labelSmall = base.labelSmall.with(family, scale),
    )
}

private fun TextStyle.with(family: FontFamily, scale: Float): TextStyle = copy(
    fontFamily = family,
    fontSize = fontSize * scale,
    lineHeight = lineHeight * scale,
)
