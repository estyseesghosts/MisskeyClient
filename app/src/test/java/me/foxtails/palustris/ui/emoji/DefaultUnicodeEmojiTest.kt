package me.foxtails.palustris.ui.emoji

import org.junit.Assert.assertEquals
import org.junit.Test

class DefaultUnicodeEmojiTest {
    /**
     * Ordered snapshot captured before the catalog moved out of EmojiPicker.kt. The
     * snapshot freezes element order, duplicates, variation selectors, surrogates, and
     * joiners. Do not rebuild it from DefaultUnicodeEmojis at runtime.
     */
    private val snapshot: List<String> = checkNotNull(
        javaClass.classLoader?.getResource("emoji/default-unicode-emojis.txt"),
    ) { "missing emoji/default-unicode-emojis.txt" }.readText().lines().filter(String::isNotEmpty)

    @Test
    fun catalogMatchesTheOrderedSnapshot() {
        assertEquals(snapshot, DefaultUnicodeEmojis)
    }
}
