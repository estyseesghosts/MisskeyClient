package me.foxtails.palustris.ui.setup

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import me.foxtails.palustris.R

private val SetupFont = FontFamily(
    Font(R.font.noto_sans, weight = FontWeight.Normal),
    Font(R.font.noto_sans, weight = FontWeight.Bold),
)

internal fun setupTypography(base: Typography): Typography = base.copy(
    displaySmall = base.displaySmall.copy(
        fontFamily = SetupFont,
        fontSize = 48.sp,
        lineHeight = 56.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.05).em,
        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
    ),
    headlineLarge = base.headlineLarge.copy(
        fontFamily = SetupFont,
        fontSize = 44.sp,
        lineHeight = 52.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.05).em,
    ),
    headlineMedium = base.headlineMedium.copy(
        fontFamily = SetupFont,
        fontSize = 36.sp,
        lineHeight = 44.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.05).em,
    ),
    titleLarge = base.titleLarge.copy(
        fontFamily = SetupFont,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.05).em,
    ),
    labelLarge = base.labelLarge.copy(
        fontFamily = SetupFont,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.05).em,
    ),
    bodyLarge = base.bodyLarge.copy(fontFamily = SetupFont, letterSpacing = (-0.05).em),
    bodyMedium = base.bodyMedium.copy(fontFamily = SetupFont, letterSpacing = (-0.05).em),
)
