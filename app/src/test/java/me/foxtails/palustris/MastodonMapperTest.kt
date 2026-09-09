package me.foxtails.palustris

import me.foxtails.palustris.data.mastodon.MastodonEmojiMapper
import me.foxtails.palustris.data.mastodon.MastodonMapper
import me.foxtails.palustris.domain.MediaKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.json.JSONArray
import org.json.JSONObject
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MastodonMapperTest {
    @Test
    fun attachmentPreservesIndependentRolesAndMetadata() {
        val attachment = MastodonMapper.attachment(
            JSONObject()
                .put("id", "media-1")
                .put("type", "image")
                .put("url", JSONObject.NULL)
                .put("preview_url", "https://cdn.example/small.jpg")
                .put("remote_url", "https://remote.example/original.jpg")
                .put("description", "A photo")
                .put("blurhash", "hash")
                .put("meta", JSONObject()
                    .put("small", JSONObject().put("width", 320).put("height", 180))
                    .put("original", JSONObject().put("width", 1920).put("height", 1080).put("mime_type", "image/jpeg"))),
        )

        assertEquals("media-1", attachment.id)
        assertNull(attachment.url)
        assertEquals("https://cdn.example/small.jpg", attachment.previewUrl)
        assertEquals("https://remote.example/original.jpg", attachment.remoteOriginalUrl)
        assertEquals(MediaKind.Image, attachment.kind)
        assertEquals(1920, attachment.width)
        assertEquals(180, attachment.previewHeight)
        assertEquals("hash", attachment.blurhash)
    }

    @Test
    fun gifvIsAnimatedAndDoesNotInventImageMime() {
        val attachment = MastodonMapper.attachment(
            JSONObject()
                .put("id", "gifv-1")
                .put("type", "gifv")
                .put("url", "https://cdn.example/clip.mp4")
                .put("preview_url", "https://cdn.example/clip.jpg"),
        )
        assertEquals(MediaKind.Video, attachment.kind)
        assertEquals("video/*", attachment.mimeType)
    }

    @Test
    fun editableProfileKeepsRawValuesAndNullSafeImages() {
        val json = JSONObject()
            .put("id", "self")
            .put("display_name", "Self")
            .put("note", "<p>Raw <b>note</b></p>")
            .put("fields", JSONArray()
                .put(JSONObject().put("name", "Site").put("value", "<a href=\"https://example.org\">site</a>")))
            .put("avatar", JSONObject.NULL)
            .put("header", JSONObject.NULL)
            .put("locked", true)
            .put("bot", false)
            .put("hide_collections", true)
            .put("discoverable", false)
            .put("indexable", true)
            .put("show_media", false)
            .put("show_media_replies", true)
            .put("show_featured", false)
            .put("attribution_domains", JSONArray().put("example.org"))

        val profile = MastodonMapper.editableProfile(json, "https://example.org")

        assertEquals("self", profile.id)
        assertEquals("Self", profile.displayName)
        assertEquals("<p>Raw <b>note</b></p>", profile.biography)
        assertEquals("<a href=\"https://example.org\">site</a>", profile.fields.single().value)
        assertNull(profile.avatarUrl)
        assertNull(profile.headerUrl)
        assertNull(profile.avatarDescription)
        assertNull(profile.headerDescription)
        assertTrue(profile.locked)
        assertFalse(profile.bot)
        assertTrue(profile.hideCollections)
        assertFalse(profile.discoverable)
        assertTrue(profile.indexable)
        assertFalse(profile.showMedia)
        assertTrue(profile.showMediaReplies)
        assertFalse(profile.showFeatured)
        assertEquals(listOf("example.org"), profile.attributionDomains)
    }

    @Test
    fun editableProfileReadsApiNineImageDescriptionsWhenPresent() {
        val profile = MastodonMapper.editableProfile(
            JSONObject()
                .put("id", "self")
                .put("avatar", "https://example.org/avatar.png")
                .put("avatar_description", "A picture")
                .put("header", "https://example.org/header.png")
                .put("header_description", "A banner"),
            "https://example.org",
        )

        assertEquals("https://example.org/avatar.png", profile.avatarUrl)
        assertEquals("A picture", profile.avatarDescription)
        assertEquals("https://example.org/header.png", profile.headerUrl)
        assertEquals("A banner", profile.headerDescription)
    }

    @Test
    fun legacyEditableProfilePrefersPlaintextSourceValues() {
        val json = JSONObject()
            .put("id", "self")
            .put("display_name", "Rendered name")
            .put("note", "<p>Rendered <b>note</b></p>")
            .put("fields", JSONArray()
                .put(JSONObject().put("name", "Site").put("value", "<a href=\"https://example.org\">site</a>")))
            .put("source", JSONObject()
                .put("note", "Plaintext note")
                .put("fields", JSONArray()
                    .put(JSONObject().put("name", "Site").put("value", "https://example.org"))))

        val profile = MastodonMapper.legacyEditableProfile(json, "https://example.org")

        assertEquals("self", profile.id)
        assertEquals("Rendered name", profile.displayName)
        assertEquals("Plaintext note", profile.biography)
        assertEquals("https://example.org", profile.fields.single().value)
    }

    @Test
    fun legacyEditableProfileFallsBackToRenderedValues() {
        val profile = MastodonMapper.legacyEditableProfile(
            JSONObject()
                .put("id", "self")
                .put("display_name", "Rendered name")
                .put("note", "<p>Rendered note</p>")
                .put("fields", JSONArray()
                    .put(JSONObject().put("name", "Site").put("value", "<a href=\"https://example.org\">site</a>"))),
            "https://example.org",
        )

        assertEquals("Rendered name", profile.displayName)
        assertEquals("<p>Rendered note</p>", profile.biography)
        assertEquals("<a href=\"https://example.org\">site</a>", profile.fields.single().value)
    }

    @Test
    fun emojiMapperSeparatesStaticAndAnimatedUrlsAndRejectsBadEntries() {
        val emojis = MastodonEmojiMapper.parseEmojis(
            JSONArray()
                .put(JSONObject()
                    .put("shortcode", "blobcat")
                    .put("url", "https://example.org/blobcat.gif")
                    .put("static_url", "https://example.org/blobcat.png")
                    .put("category", "Cats"))
                .put(JSONObject()
                    .put("shortcode", "")
                    .put("url", "https://example.org/empty.png"))
                .put(JSONObject()
                    .put("shortcode", "insecure")
                    .put("url", "http://example.org/insecure.png"))
                .put(JSONObject()
                    .put("shortcode", "credentialed")
                    .put("url", "https://user:pass@example.org/credentialed.png")),
            "https://example.org",
        )

        assertEquals(setOf("blobcat"), emojis.keys)
        assertEquals("https://example.org/blobcat.gif", emojis.getValue("blobcat").animatedUrl?.value)
        assertEquals("https://example.org/blobcat.png", emojis.getValue("blobcat").staticUrl?.value)
        assertEquals("Cats", emojis.getValue("blobcat").category)
    }

    @Test
    fun accountMappingKeepsEntityLocalEmojiMap() {
        val account = MastodonMapper.account(
            JSONObject()
                .put("id", "self")
                .put("username", "self")
                .put("acct", "self")
                .put("display_name", "Self :blobcat:")
                .put("note", "Bio :blobcat:")
                .put("emojis", JSONArray()
                    .put(JSONObject()
                        .put("shortcode", "blobcat")
                        .put("url", "https://example.org/blobcat.gif")
                        .put("static_url", "https://example.org/blobcat.png"))),
            "https://example.org",
        )

        assertEquals(setOf("blobcat"), account.emoji.keys)
        assertNull(MastodonMapper.account(
            JSONObject().put("id", "plain").put("username", "plain").put("acct", "plain"),
            "https://example.org",
        ).emoji["blobcat"])
    }

    @Test
    fun postMappingKeepsEmojiMapScopedToTheDisplayedEntity() {
        val outer = JSONObject()
            .put("id", "outer")
            .put("created_at", "2026-09-06T10:00:00Z")
            .put("account", JSONObject()
                .put("id", "outer-user")
                .put("username", "outer")
                .put("acct", "outer"))
            .put("content", "<p>Outer</p>")
            .put("visibility", "public")
            .put("emojis", JSONArray()
                .put(JSONObject().put("shortcode", "outer-emoji").put("url", "https://example.org/outer.png")))
            .put("reblog", JSONObject()
                .put("id", "inner")
                .put("created_at", "2026-09-06T10:00:00Z")
                .put("account", JSONObject()
                    .put("id", "inner-user")
                    .put("username", "inner")
                    .put("acct", "inner"))
                .put("content", "<p>Inner</p>")
                .put("visibility", "public")
                .put("emojis", JSONArray()
                    .put(JSONObject().put("shortcode", "inner-emoji").put("url", "https://example.org/inner.png"))))

        val post = MastodonMapper.post(outer, "https://example.org")

        assertEquals(setOf("inner-emoji"), post.emoji.keys)
        assertTrue(post.emoji.containsKey("inner-emoji"))
        assertFalse(post.emoji.containsKey("outer-emoji"))
    }
}
