package me.foxtails.palustris.ui.motion

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.animation.animateColorAsState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import kotlinx.coroutines.launch
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState

@Composable
fun Modifier.springPress(
    interactionSource: MutableInteractionSource,
    enabled: Boolean = true,
    pressedScale: Float = LocalPalustrisMotionScheme.current.pressedScale,
): Modifier {
    val scheme = LocalPalustrisMotionScheme.current
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (enabled && pressed && !scheme.reducedMotion) pressedScale else 1f,
        animationSpec = scheme.press,
        label = "pressScale",
    )
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
    }.semantics { this[MotionScaleKey] = scale }
}

@Composable
fun SpringyIconButton(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interactionSource,
        modifier = modifier.springPress(interactionSource, enabled, LocalPalustrisMotionScheme.current.compactPressedScale),
    ) {
        Icon(icon, contentDescription)
    }
}

@Composable
fun SpringyFilledIconButton(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    androidx.compose.material3.FilledIconButton(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interactionSource,
        modifier = modifier.springPress(interactionSource, enabled, LocalPalustrisMotionScheme.current.compactPressedScale),
    ) {
        Icon(icon, contentDescription)
    }
}

@Composable
fun Modifier.springClickable(
    interactionSource: MutableInteractionSource,
    enabled: Boolean = true,
    role: Role? = null,
    onClick: () -> Unit,
): Modifier = springPress(interactionSource, enabled).clickable(
    interactionSource = interactionSource,
    indication = LocalIndication.current,
    enabled = enabled,
    role = role,
    onClick = onClick,
)

@Composable
fun rememberPopSignal(key: Any?): Float {
    val scheme = LocalPalustrisMotionScheme.current
    val value = remember { Animatable(1f) }
    var initialized by remember { mutableIntStateOf(0) }
    LaunchedEffect(key, scheme.reducedMotion) {
        if (initialized++ == 0) {
            value.snapTo(1f)
        } else if (scheme.reducedMotion) {
            value.snapTo(1f)
        } else {
            value.animateTo(scheme.selectionStartScale, scheme.expressive)
            value.animateTo(1f, scheme.expressive)
        }
    }
    return value.value
}

@Composable
fun PopEffect(
    trigger: Any?,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val scale = rememberPopSignal(trigger)
    androidx.compose.foundation.layout.Box(
        modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
        },
    ) { content() }
}

@Composable
fun rememberSelectedScale(selected: Boolean): Float {
    val scheme = LocalPalustrisMotionScheme.current
    val value = remember { Animatable(1f) }
    LaunchedEffect(selected, scheme.reducedMotion) {
        if (scheme.reducedMotion) {
            value.snapTo(1f)
        } else if (selected) {
            value.snapTo(scheme.selectionStartScale)
            value.animateTo(1f, scheme.expressive)
        } else {
            value.animateTo(1f, scheme.spatial)
        }
    }
    return value.value.coerceAtMost(scheme.selectionMaxScale)
}

@Composable
fun rememberSelectedColor(selected: Boolean, selectedColor: Color, unselectedColor: Color): Color =
    animateColorAsState(
        targetValue = if (selected) selectedColor else unselectedColor,
        animationSpec = LocalPalustrisMotionScheme.current.color,
        label = "selectedColor",
    ).value
