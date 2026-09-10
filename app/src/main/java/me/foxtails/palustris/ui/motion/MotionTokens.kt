package me.foxtails.palustris.ui.motion

import android.animation.ValueAnimator
import android.os.Build
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize

@Immutable
data class PalustrisMotionScheme(
    val spatial: FiniteAnimationSpec<Float>,
    val spatialPixels: FiniteAnimationSpec<Float>,
    val expressive: FiniteAnimationSpec<Float>,
    val press: FiniteAnimationSpec<Float>,
    val gentle: FiniteAnimationSpec<Float>,
    val color: FiniteAnimationSpec<androidx.compose.ui.graphics.Color>,
    val fastFadeIn: FiniteAnimationSpec<Float>,
    val fastFadeOut: FiniteAnimationSpec<Float>,
    val spatialOffset: FiniteAnimationSpec<IntOffset>,
    val gentleOffset: FiniteAnimationSpec<IntOffset>,
    val gentleSize: FiniteAnimationSpec<IntSize>,
    val reducedMotion: Boolean,
    val pressedScale: Float = 0.96f,
    val compactPressedScale: Float = 0.91f,
    val largePressedScale: Float = 0.985f,
    val selectionStartScale: Float = 0.86f,
    val selectionMaxScale: Float = 1.08f,
    val floatingEnterScale: Float = 0.92f,
    val floatingEnterOffsetPx: Int = 16,
) {
    companion object {
        fun standard(reducedMotion: Boolean): PalustrisMotionScheme {
            if (reducedMotion) {
                val snapFloat = androidx.compose.animation.core.snap<Float>()
                val snapColor = androidx.compose.animation.core.snap<androidx.compose.ui.graphics.Color>()
                val snapOffset = androidx.compose.animation.core.snap<IntOffset>()
                val snapSize = androidx.compose.animation.core.snap<IntSize>()
                return PalustrisMotionScheme(
                    spatial = snapFloat,
                    spatialPixels = snapFloat,
                    expressive = snapFloat,
                    press = snapFloat,
                    gentle = snapFloat,
                    color = snapColor,
                    fastFadeIn = snapFloat,
                    fastFadeOut = snapFloat,
                    spatialOffset = snapOffset,
                    gentleOffset = snapOffset,
                    gentleSize = snapSize,
                    reducedMotion = true,
                    floatingEnterOffsetPx = 0,
                )
            }
            return PalustrisMotionScheme(
                spatial = spring(dampingRatio = 0.82f, stiffness = 520f, visibilityThreshold = 0.001f),
                spatialPixels = spring(dampingRatio = 0.82f, stiffness = 520f, visibilityThreshold = 0.5f),
                expressive = spring(dampingRatio = 0.68f, stiffness = 620f, visibilityThreshold = 0.001f),
                press = spring(dampingRatio = 0.76f, stiffness = 900f, visibilityThreshold = 0.001f),
                gentle = spring(dampingRatio = 0.9f, stiffness = 380f, visibilityThreshold = 0.001f),
                color = spring(dampingRatio = 1f, stiffness = 700f),
                fastFadeIn = tween(120),
                fastFadeOut = tween(90),
                spatialOffset = spring(dampingRatio = 0.82f, stiffness = 520f, visibilityThreshold = IntOffset(1, 1)),
                gentleOffset = spring(dampingRatio = 0.9f, stiffness = 380f, visibilityThreshold = IntOffset(1, 1)),
                gentleSize = spring(dampingRatio = 0.9f, stiffness = 380f, visibilityThreshold = IntSize(1, 1)),
                reducedMotion = false,
            )
        }
    }
}

val LocalPalustrisMotionScheme = compositionLocalOf { PalustrisMotionScheme.standard(reducedMotion = false) }

/** Direction for ordinal/state transitions; reduced motion intentionally has no spatial direction. */
fun motionDirection(previousOrdinal: Int, targetOrdinal: Int, reducedMotion: Boolean = false): Int = when {
    reducedMotion || previousOrdinal == targetOrdinal -> 0
    targetOrdinal > previousOrdinal -> 1
    else -> -1
}

val MotionScaleKey = SemanticsPropertyKey<Float>("MotionScale")

@Composable
fun palustrisMotionScheme(): PalustrisMotionScheme {
    val durationScale = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ValueAnimator.getDurationScale()
        } else {
            1f
        }
    }
    val reducedMotion = durationScale == 0f
    return remember(reducedMotion) { PalustrisMotionScheme.standard(reducedMotion) }
}

fun PalustrisMotionScheme.compactFloatingEnter(bottom: Boolean): EnterTransition = if (reducedMotion) {
    EnterTransition.None
} else {
    fadeIn(fastFadeIn) +
        scaleIn(initialScale = floatingEnterScale, animationSpec = expressive) +
        slideInVertically(spatialOffset) { if (bottom) floatingEnterOffsetPx else -floatingEnterOffsetPx }
}

fun PalustrisMotionScheme.compactFloatingExit(bottom: Boolean): ExitTransition = if (reducedMotion) {
    ExitTransition.None
} else {
    fadeOut(fastFadeOut) +
        scaleOut(targetScale = floatingEnterScale, animationSpec = expressive) +
        slideOutVertically(spatialOffset) { if (bottom) floatingEnterOffsetPx else -floatingEnterOffsetPx }
}
