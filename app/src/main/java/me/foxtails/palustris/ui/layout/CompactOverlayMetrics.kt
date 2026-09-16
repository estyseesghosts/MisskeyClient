@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package me.foxtails.palustris.ui.layout

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.union
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal val CompactNavigationHeight = 56.dp
internal val CompactTimelineTabsHeight = 56.dp
internal val CompactOverlayControlSpacing = 14.dp
internal val CompactHomeTimelineSpacing = 8.dp
internal val CompactOverlayHorizontalPadding = 16.dp
internal val CompactOverlayVerticalPadding = 12.dp
internal val CompactSearchChipRowHeight = 48.dp
internal val CompactSearchControlsSpacing = 8.dp
internal val CompactSearchFieldHeight = 56.dp
internal val CompactFilterDockHeight = CompactSearchChipRowHeight + CompactSearchControlsSpacing
internal val CompactSearchDockHeight = CompactFilterDockHeight + CompactSearchFieldHeight
internal val CompactContextualControlsPositioningClearance = CompactNavigationHeight +
    CompactOverlayControlSpacing + CompactOverlayVerticalPadding
internal val LegacyFeedBottomClearance = 96.dp

@Composable
internal fun compactGlobalNavigationPositioningInsets(): WindowInsets =
    WindowInsets.navigationBarsIgnoringVisibility.only(WindowInsetsSides.Bottom)

@Composable
internal fun compactContextualControlsPositioningInsets(
    navigationVisible: Boolean,
    ime: WindowInsets = WindowInsets(bottom = 0.dp),
): WindowInsets = compactGlobalNavigationPositioningInsets()
    .add(
        WindowInsets(
            bottom = if (navigationVisible) CompactContextualControlsPositioningClearance else 0.dp,
        ),
    )
    .union(ime)
    .only(WindowInsetsSides.Bottom)

@Composable
internal fun compactScrollEndClearance(
    controlStackHeight: Dp,
    navigationVisible: Boolean,
    ime: WindowInsets = WindowInsets(bottom = 0.dp),
): Dp {
    val systemNavigationBottom = WindowInsets.navigationBarsIgnoringVisibility
        .asPaddingValues()
        .calculateBottomPadding()
    val imeBottom = ime.asPaddingValues().calculateBottomPadding()
    val contextualPositioning = if (navigationVisible) CompactContextualControlsPositioningClearance else 0.dp
    return maxOf(systemNavigationBottom + contextualPositioning, imeBottom) + controlStackHeight
}

@Composable
internal fun compactHomeScrollEndClearance(): Dp {
    val systemNavigationBottom = WindowInsets.navigationBarsIgnoringVisibility
        .asPaddingValues()
        .calculateBottomPadding()
    return systemNavigationBottom + CompactNavigationHeight +
        CompactHomeTimelineSpacing + CompactTimelineTabsHeight +
        (CompactOverlayVerticalPadding * 2f)
}
