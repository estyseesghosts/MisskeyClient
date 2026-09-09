package me.foxtails.palustris.ui.emoji

import me.foxtails.palustris.domain.CustomEmoji
import me.foxtails.palustris.domain.ValidatedUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EmojiTextParserTest {
    private fun emoji(shortcode: String, submissionValue: String = ":$shortcode:") = CustomEmoji(
        shortcode = shortcode,
        animatedUrl = ValidatedUrl.https("https://cdn.example/$shortcode.png"),
        staticUrl = ValidatedUrl.https("https://cdn.example/$shortcode.png"),
        submissionValue = submissionValue,
    )

    private val map = mapOf(
        "blob_cat" to emoji("blob_cat"),
        "blob_wave" to emoji("blob_wave"),
        "alias" to emoji("blob_cat", ":blob_cat:").copy(aliases = listOf("blobcat_alias")),
    )

    @Test
    fun plainTextProducesOneTextSegmentWithSourceRange() {
        val model = EmojiTextParser.parse("plain text", emptyMap())
        assertEquals(listOf("plain text"), model.segments.map { (it as RichTextSegment.Text).text })
        assertEquals(0 until 10, model.segments.single().range)
        assertTrue(model.emojiRanges.isEmpty())
    }

    @Test
    fun knownTokensBecomeEmojiSegmentsWithExactSourceRanges() {
        val text = "hi :blob_cat: there"
        val model = EmojiTextParser.parse(text, map)

        assertEquals(3, model.segments.size)
        assertEquals("hi ", (model.segments[0] as RichTextSegment.Text).text)
        assertEquals(3 until 13, model.segments[1].range)
        assertEquals(":blob_cat:", (model.segments[1] as RichTextSegment.Emoji).token)
        assertEquals(" there", (model.segments[2] as RichTextSegment.Text).text)
        assertEquals(listOf(3 until 12), model.emojiRanges)
    }

    @Test
    fun unknownTokensRemainPlainText() {
        val text = ":unknown: stays"
        val model = EmojiTextParser.parse(text, map)
        assertEquals(listOf(text), model.segments.map { (it as RichTextSegment.Text).text })
        assertTrue(model.emojiRanges.isEmpty())
    }

    @Test
    fun longestKnownTokenWins() {
        val extended = map + ("blob_cat_x" to emoji("blob_cat_x"))
        val text = "a :blob_cat: :blob_cat_x: b"
        val model = EmojiTextParser.parse(text, extended)
        val tokens = model.emojiRanges.map { text.substring(it) }
        assertEquals(listOf(":blob_cat:", ":blob_cat_x:"), tokens)
    }

    @Test
    fun linksAreNeverParsedInsideTheUrlAndEmojiRenderInLabels() {
        val text = "see [:blob_cat:](https://example.org/x) and [link](https://example.org/:blob_cat:) end"
        val model = EmojiTextParser.parse(text, map)

        val links = model.segments.filterIsInstance<RichTextSegment.Link>()
        assertEquals(2, links.size)
        val link = links.first()
        assertEquals("https://example.org/x", link.url)
        assertTrue(link.label.any { it is RichTextSegment.Emoji })
        assertEquals("https://example.org/:blob_cat:", links[1].url)
        assertTrue(links[1].label.none { it is RichTextSegment.Emoji })
        assertEquals(listOf("see ", link, " and ", links[1], " end"), model.segments)
        val outside = model.emojiRanges.map { text.substring(it) }
        assertEquals(listOf(":blob_cat:"), outside)
    }

    @Test
    fun unicodeOffsetsSurviveMixedWidthText() {
        val text = "東京 :blob_cat: 🎉 :blob_wave:"
        val model = EmojiTextParser.parse(text, map)
        assertEquals(2, model.emojiRanges.size)
        model.emojiRanges.forEach { range ->
            assertTrue(text.substring(range).startsWith(":"))
        }
    }

    @Test
    fun rawIdentityTokensWithRemoteHostsMatchWithoutTrimLookup() {
        val remote = map + (":blob_remote@remote.example:" to emoji("blob_remote@remote.example", ":blob_remote@remote.example:"))
        val text = "remote :blob_remote@remote.example: done"
        val model = EmojiTextParser.parse(text, remote)
        assertEquals(listOf(":blob_remote@remote.example:"), model.emojiRanges.map { text.substring(it) })
    }

    @Test
    fun originalShortcodeTextRemainsInTheModelForCopyAndFallback() {
        val text = "keep :blob_cat: copyable"
        val model = EmojiTextParser.parse(text, map)
        assertEquals(text, model.source)
        assertEquals(":blob_cat:", (model.segments[1] as RichTextSegment.Emoji).token)
    }
}
