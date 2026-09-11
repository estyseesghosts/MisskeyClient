package me.foxtails.palustris.ui.emoji

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test

class EmojiGridScrollbarTest {
    @Test
    fun hidesWhenAllItemsFit() {
        assertNull(
            calculateEmojiGridScrollbarThumb(
                firstVisibleItemIndex = 0,
                visibleItemCount = 10,
                totalItemCount = 10,
                viewportHeightPx = 1000f,
                minimumThumbHeightPx = 48f,
            ),
        )
    }

    @Test
    fun appliesMinimumThumbHeightAtTheTop() {
        val thumb = calculateEmojiGridScrollbarThumb(
            firstVisibleItemIndex = 0,
            visibleItemCount = 1,
            totalItemCount = 100,
            viewportHeightPx = 1000f,
            minimumThumbHeightPx = 48f,
        )

        assertNotNull(thumb)
        assertEquals(48f, thumb!!.heightPx, 0.001f)
        assertEquals(0f, thumb.topPx, 0.001f)
    }

    @Test
    fun advancesThumbWithTheFirstVisibleItem() {
        val thumb = calculateEmojiGridScrollbarThumb(
            firstVisibleItemIndex = 50,
            visibleItemCount = 10,
            totalItemCount = 100,
            viewportHeightPx = 1000f,
            minimumThumbHeightPx = 48f,
        )

        assertNotNull(thumb)
        assertEquals(100f, thumb!!.heightPx, 0.001f)
        assertEquals(500f, thumb.topPx, 0.001f)
    }
}
