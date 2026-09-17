package me.foxtails.palustris.ui.photogrid

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal val PhotoDetailHeaderHeight = 64.dp
internal val PhotoDetailReservedContentHeight = 200.dp
internal val PhotoDetailMinimumHeight = 240.dp
internal val PhotoDetailMinAspect = 1.0f
internal val PhotoDetailMaxAspect = 1.25f

/**
 * Resolve the Photo Grid detail media height.
 *
 * The viewport stays at least square so square media fills the pager width
 * without horizontal letterboxing. The viewport grows to a 5:4 vertical ratio
 * when the detail viewport has room for the header and the reserved post
 * content. A short viewport clamps the media to the largest safe height so
 * the post body stays reachable below the media.
 */
internal fun resolveWidePhotoPagerHeight(
    pagerWidth: Dp,
    viewportHeight: Dp,
    headerHeight: Dp = PhotoDetailHeaderHeight,
    reservedContentHeight: Dp = PhotoDetailReservedContentHeight,
): Dp {
    val desired = pagerWidth * PhotoDetailMaxAspect
    val minimum = (pagerWidth * PhotoDetailMinAspect).coerceAtLeast(PhotoDetailMinimumHeight)
    if (!pagerWidth.value.isFinite() || !viewportHeight.value.isFinite()) {
        return desired.coerceAtLeast(PhotoDetailMinimumHeight)
    }
    val available = viewportHeight - headerHeight - reservedContentHeight
    if (!available.value.isFinite()) {
        return desired.coerceAtLeast(PhotoDetailMinimumHeight)
    }
    if (available < minimum) {
        return available.coerceAtLeast(0.dp)
    }
    return desired.coerceIn(minimum, available)
}
