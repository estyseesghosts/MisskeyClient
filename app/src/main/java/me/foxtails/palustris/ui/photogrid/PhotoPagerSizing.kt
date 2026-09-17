package me.foxtails.palustris.ui.photogrid

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import me.foxtails.palustris.domain.Attachment

internal val PhotoDetailHeaderHeight = 64.dp
internal val PhotoDetailReservedContentHeight = 200.dp
internal val PhotoDetailMinimumHeight = 240.dp
internal val PhotoDetailMinAspect = 1.0f
internal val PhotoDetailMaxAspect = 1.25f
internal val PhotoDetailWideAspect = 9.0f / 16.0f

/**
 * Return the height to width ratio for an attachment.
 *
 * The function prefers the full dimensions and then the preview
 * dimensions. It returns null when no valid dimensions exist.
 */
internal fun attachmentAspect(attachment: Attachment): Float? {
    attachmentAspect(attachment.width, attachment.height)?.let { return it }
    return attachmentAspect(attachment.previewWidth, attachment.previewHeight)
}

/**
 * Return the height to width ratio for raw dimensions.
 *
 * The function returns null for missing or invalid dimensions.
 */
internal fun attachmentAspect(width: Int?, height: Int?): Float? {
    if (width == null || height == null) return null
    if (width <= 0 || height <= 0) return null
    val ratio = height.toFloat() / width.toFloat()
    if (!ratio.isFinite() || ratio <= 0f) return null
    return ratio
}

/**
 * Resolve the Photo Grid detail media height.
 *
 * Known dimensions use their natural aspect. Square through 4:5 keeps its
 * natural height. Wide media at or beyond 16:9 keeps its natural short
 * height, which prevents vertical letterboxing. Media taller than 4:5
 * stays capped at the 4:5 viewport. A short viewport clamps the result to
 * the available height so the post body stays below the media.
 * Unknown dimensions use the square to 5:4 viewport fallback.
 */
internal fun resolveWidePhotoPagerHeight(
    pagerWidth: Dp,
    viewportHeight: Dp,
    aspect: Float? = null,
    headerHeight: Dp = PhotoDetailHeaderHeight,
    reservedContentHeight: Dp = PhotoDetailReservedContentHeight,
): Dp {
    if (aspect != null && aspect.isFinite() && aspect > 0f) {
        val natural = if (aspect <= PhotoDetailWideAspect) {
            pagerWidth * aspect
        } else {
            pagerWidth * aspect.coerceAtMost(PhotoDetailMaxAspect)
        }
        if (!pagerWidth.value.isFinite() || !viewportHeight.value.isFinite()) {
            return natural
        }
        val available = viewportHeight - headerHeight - reservedContentHeight
        if (!available.value.isFinite()) {
            return natural
        }
        if (available < natural) {
            return available.coerceAtLeast(0.dp)
        }
        return natural
    }
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

/**
 * Resolve one shared height for all pages in a multi-photo pager.
 *
 * The height is the smallest aspect-aware height across the photos. The
 * shared height stays stable when the user changes pages. The minimum
 * choice prevents vertical letterboxing for the active photo. Horizontal
 * letterboxing on taller pages is the accepted tradeoff. The shared height
 * still clamps to the available viewport height.
 */
internal fun resolveSharedPhotoPagerHeight(
    pagerWidth: Dp,
    viewportHeight: Dp,
    attachments: List<Attachment>,
    headerHeight: Dp = PhotoDetailHeaderHeight,
    reservedContentHeight: Dp = PhotoDetailReservedContentHeight,
): Dp {
    if (attachments.isEmpty()) {
        return resolveWidePhotoPagerHeight(pagerWidth, viewportHeight, null, headerHeight, reservedContentHeight)
    }
    return attachments.map { attachment ->
        resolveWidePhotoPagerHeight(
            pagerWidth,
            viewportHeight,
            attachmentAspect(attachment),
            headerHeight,
            reservedContentHeight,
        )
    }.minOrNull() ?: resolveWidePhotoPagerHeight(pagerWidth, viewportHeight, null, headerHeight, reservedContentHeight)
}
