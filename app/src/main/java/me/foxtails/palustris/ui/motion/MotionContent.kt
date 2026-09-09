package me.foxtails.palustris.ui.motion

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.unit.IntSize

@Composable
fun <T> SpringAnimatedContent(
    stateKey: T,
    direction: Int,
    modifier: Modifier = Modifier,
    contentKey: (T) -> Any? = { it },
    content: @Composable (T) -> Unit,
) {
    val scheme = LocalPalustrisMotionScheme.current
    AnimatedContent(
        targetState = stateKey,
        modifier = modifier.clipToBounds(),
        contentKey = contentKey,
        transitionSpec = {
            if (scheme.reducedMotion) {
                EnterTransition.None togetherWith ExitTransition.None
            } else if (direction > 0) {
                    (fadeIn(scheme.fastFadeIn) + slideInHorizontally(scheme.spatialOffset) { it / 4 }) togetherWith
                    (fadeOut(scheme.fastFadeOut) + slideOutHorizontally(scheme.spatialOffset) { -it / 4 })
            } else if (direction < 0) {
                (fadeIn(scheme.fastFadeIn) + slideInHorizontally(scheme.spatialOffset) { -it / 4 }) togetherWith
                    (fadeOut(scheme.fastFadeOut) + slideOutHorizontally(scheme.spatialOffset) { it / 4 })
            } else {
                fadeIn(scheme.fastFadeIn) togetherWith fadeOut(scheme.fastFadeOut)
            }.using(SizeTransform(clip = true, sizeAnimationSpec = { _, _ -> scheme.gentleSize }))
        },
        label = "springAnimatedContent",
    ) { content(it) }
}

@Composable
fun <T> AnimatedStatePane(
    stateKey: T,
    modifier: Modifier = Modifier,
    content: @Composable (T) -> Unit,
) {
    val scheme = LocalPalustrisMotionScheme.current
    AnimatedContent(
        targetState = stateKey,
        modifier = modifier.clipToBounds(),
        transitionSpec = {
            if (scheme.reducedMotion) {
                EnterTransition.None togetherWith ExitTransition.None
            } else {
                (fadeIn(scheme.fastFadeIn) + scaleIn(initialScale = 0.98f, animationSpec = scheme.spatial)) togetherWith
                    (fadeOut(scheme.fastFadeOut) + scaleOut(targetScale = 0.98f, animationSpec = scheme.spatial))
            }.using(SizeTransform(clip = true, sizeAnimationSpec = { _, _ -> scheme.gentleSize }))
        },
        label = "animatedStatePane",
    ) { content(it) }
}

@Composable
fun ExpandableContent(
    visible: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val scheme = LocalPalustrisMotionScheme.current
    AnimatedVisibility(
        visible = visible,
        modifier = modifier.clipToBounds(),
        enter = if (scheme.reducedMotion) EnterTransition.None else expandVertically(scheme.gentleSize) + fadeIn(scheme.fastFadeIn),
        exit = if (scheme.reducedMotion) ExitTransition.None else shrinkVertically(scheme.gentleSize) + fadeOut(scheme.fastFadeOut),
    ) {
        Column(Modifier.fillMaxWidth()) {
            content()
        }
    }
}
