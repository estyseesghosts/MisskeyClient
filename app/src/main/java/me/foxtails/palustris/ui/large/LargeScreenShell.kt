@file:OptIn(androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi::class)

package me.foxtails.palustris.ui.large

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.HingeInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import me.foxtails.palustris.domain.Account

@Composable
internal fun LargeScreenShell(
    windowWidth: Dp,
    selectedTarget: LargeNavTarget,
    account: Account?,
    hasDetail: Boolean,
    twoPane: Boolean,
    onTargetSelected: (LargeNavTarget) -> Unit,
    onOpenAccounts: () -> Unit,
    onCompose: () -> Unit,
    primaryContent: @Composable (Modifier) -> Unit,
    detailContent: @Composable (Modifier) -> Unit,
    modifier: Modifier = Modifier,
) {
    val adaptiveInfo = currentWindowAdaptiveInfoV2()
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val systemBars = WindowInsets.systemBars
    Row(
        modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBars)
            .testTag("large_screen_shell"),
    ) {
        LargeNavigationRail(
            selectedTarget = selectedTarget,
            account = account,
            onTargetSelected = onTargetSelected,
            onOpenAccounts = onOpenAccounts,
            onCompose = onCompose,
        )
        BoxWithConstraints(Modifier.weight(1f).fillMaxSize().testTag("large_content_region")) {
            val railPx = with(density) { 80.dp.toPx() }
            val insetLeftPx = systemBars.getLeft(density, layoutDirection).toFloat()
            val insetTopPx = systemBars.getTop(density).toFloat()
            val features = adaptiveInfo.windowPosture.hingeList.map {
                it.toLargeFeature(railPx, insetLeftPx, insetTopPx)
            }
            val layout = calculateLargePaneLayout(
                windowWidthDp = windowWidth.value,
                contentWidthDp = maxWidth.value,
                contentHeightDp = maxHeight.value,
                density = density.density,
                foldingFeatures = features,
            )
            val showDetail = hasDetail && twoPane && layout.detail != null
            Box(Modifier.fillMaxSize()) {
                PaneSlot(layout.primary, density, if (hasDetail && !showDetail) detailContent else primaryContent)
                if (showDetail) {
                    layout.detail?.let { detail -> PaneSlot(detail, density, detailContent) }
                } else if (twoPane && !hasDetail) {
                    layout.detail?.let { detail -> PaneSlot(detail, density, detailContent) }
                }
            }
        }
    }
}

@Composable
private fun PaneSlot(
    bounds: Rect,
    density: Density,
    content: @Composable (Modifier) -> Unit,
) {
    val left = with(density) { bounds.left.toDp() }
    val top = with(density) { bounds.top.toDp() }
    val width = with(density) { bounds.width.toDp() }
    val height = with(density) { bounds.height.toDp() }
    content(
        Modifier
            .offset(left, top)
            .width(width)
            .height(height),
    )
}

private fun HingeInfo.toLargeFeature(railPx: Float, insetLeftPx: Float, insetTopPx: Float): LargeFoldingFeature {
    val pxBounds = bounds.translate(-(railPx + insetLeftPx), -insetTopPx)
    return LargeFoldingFeature(
        bounds = pxBounds,
        isVertical = isVertical,
        isSeparating = isSeparating,
        isOccluding = isOccluding,
    )
}
