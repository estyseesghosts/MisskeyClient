package me.foxtails.palustris

import me.foxtails.palustris.data.mastodon.MastodonMapper
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MastodonRichTextParserTest {
    private val origin = "https://example.org"

    private fun account(id: String = "author") = JSONObject()
        .put("id", id)
        .put("username", "author")
        .put("acct", "author@example.org")

    private fun emojiArray(): JSONArray = JSONArray()
        .put(JSONObject()
            .put("shortcode", "blob_cat")
            .put("url", "https://cdn.example/emoji/blob_cat.gif")
            .put("static_url", "https://cdn.example/emoji/blob_cat.png"))
        .put(JSONObject()
            .put("shortcode", "blob_wave")
            .put("url", "https://cdn.example/emoji/blob_wave.gif")
            .put("static_url", "https://cdn.example/emoji/blob_wave.png"))

    private fun status(content: String, emojis: JSONArray? = null) = JSONObject()
        .put("id", "status-1")
        .put("created_at", "2026-09-01T09:00:00Z")
        .put("account", account())
        .put("content", content)
        .put("visibility", "public")
        .apply { emojis?.let { put("emojis", it) } }

    @Test
    fun emojiImageAltIsPreservedAsItsTokenBeforeTagRemoval() {
        val post = MastodonMapper.post(status(
            "<p>Hello <img draggable=\"false\" class=\"emojione custom-emoji\" alt=\":blob_cat:\" " +
                "title=\":blob_cat:\" src=\"https://cdn.example/emoji/blob_cat.png\" " +
                "data-original=\"https://cdn.example/emoji/blob_cat.gif\"> world</p>",
            emojiArray(),
        ), origin)

        assertEquals("Hello :blob_cat: world", post.text)
        assertEquals(":blob_cat:", post.emoji.getValue("blob_cat").submissionValue)
    }

    @Test
    fun linksLineBreaksAndEntitiesSurviveAlongsideEmojiImages() {
        val post = MastodonMapper.post(status(
            "<p>A &amp; B<br>with <a href=\"https://example.org/link\">a link</a> and " +
                "<img alt=\":blob_cat:\" src=\"https://cdn.example/emoji/blob_cat.png\"></p>",
            emojiArray(),
        ), origin)

        assertEquals(
            "A & B\nwith [a link](https://example.org/link) and :blob_cat:",
            post.text,
        )
    }

    @Test
    fun unknownImagesAndMalformedTagsFallBackToReadableText() {
        val post = MastodonMapper.post(status(
            "<p>text <img alt=\":missing:\" src=\"https://cdn.example/missing.png\"> <img> " +
                "<b>bold</b> tail</p>",
            emojiArray(),
        ), origin)

        assertEquals("text   bold tail", post.text)
    }

    @Test
    fun malformedEmojiUrlsSkipTheEmojiButKeepTheEntity() {
        val emojis = JSONArray()
            .put(JSONObject().put("shortcode", "good").put("url", "https://cdn.example/good.png"))
            .put(JSONObject().put("shortcode", "httpbad").put("url", "http://cdn.example/bad.png"))
            .put(JSONObject().put("shortcode", "credential").put("url", "https://user:pass@cdn.example/x.png"))
            .put(JSONObject().put("shortcode", "blank").put("url", ""))
            .put(JSONObject().put("url", "https://cdn.example/no-name.png"))
        val post = MastodonMapper.post(status("<p>body</p>", emojis), origin)

        assertEquals("body", post.text)
        assertEquals(setOf("good"), post.emoji.keys)
        assertEquals("https://cdn.example/good.png", post.emoji.getValue("good").staticUrl?.value)
    }

    @Test
    fun nestedReblogAndQuoteKeepIndependentEmojiMaps() {
        val quote = status(
            "<p>quoted :quote_blob:</p>",
            JSONArray().put(JSONObject()
                .put("shortcode", "quote_blob")
                .put("url", "https://cdn.example/quote_blob.png")),
        ).put("id", "quoted")
        val inner = status(
            "<p>inner :inner_blob:</p>",
            JSONArray().put(JSONObject()
                .put("shortcode", "inner_blob")
                .put("url", "https://cdn.example/inner_blob.png")),
        ).put("id", "inner").put("quote", JSONObject().put("state", "accepted").put("quoted_status", quote))
        val outer = JSONObject()
            .put("id", "outer")
            .put("created_at", "2026-09-02T09:00:00Z")
            .put("account", account("reshaper"))
            .put("content", "<p>wrapper</p>")
            .put("visibility", "public")
            .put("reblog", inner)

        val post = MastodonMapper.post(outer, origin)

        assertEquals(setOf("inner_blob"), post.emoji.keys)
        assertEquals(setOf("quote_blob"), post.quote?.emoji?.keys)
        assertEquals("reshaper", post.resharedBy?.id?.localId)
        assertTrue(post.quote?.emoji?.containsKey("inner_blob") != true)
    }
}
