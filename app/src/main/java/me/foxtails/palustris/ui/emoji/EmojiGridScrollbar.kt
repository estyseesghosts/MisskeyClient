package me.foxtails.palustris.ui.emoji

internal data class EmojiGridScrollbarThumb(
    val topPx: Float,
    val heightPx: Float,
)

internal fun calculateEmojiGridScrollbarThumb(
    firstVisibleItemIndex: Int,
    visibleItemCount: Int,
    totalItemCount: Int,
    viewportHeightPx: Float,
    minimumThumbHeightPx: Float,
): EmojiGridScrollbarThumb? {
    if (visibleItemCount <= 0 || totalItemCount <= visibleItemCount || viewportHeightPx <= 0f) return null
    val visible = visibleItemCount.toFloat()
    val total = totalItemCount.toFloat()
    val thumbHeight = (viewportHeightPx * visible / total).coerceAtLeast(minimumThumbHeightPx).coerceAtMost(viewportHeightPx)
    val maximumIndex = (totalItemCount - visibleItemCount).coerceAtLeast(1)
    val progress = firstVisibleItemIndex.coerceIn(0, maximumIndex).toFloat() / maximumIndex
    return EmojiGridScrollbarThumb(
        topPx = (viewportHeightPx - thumbHeight) * progress,
        heightPx = thumbHeight,
    )
}
