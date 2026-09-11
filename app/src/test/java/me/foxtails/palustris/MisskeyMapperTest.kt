package me.foxtails.palustris

import me.foxtails.palustris.data.misskey.MisskeyMapper
import me.foxtails.palustris.domain.MediaKind
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MisskeyMapperTest {
    private val origin = "https://example.org"

    @Test
    fun accountAcceptsOnlyAlreadyResolvedShallowDestination() {
        val origin = "https://example.org"
        val old = JSONObject()
            .put("id", "old-id")
            .put("username", "old")
            .put("name", "Old")
            .put("movedTo", "destination-id")
        val destination = MisskeyMapper.account(JSONObject()
            .put("id", "destination-id")
            .put("username", "new")
            .put("name", "New")
            .put("movedTo", "third-id"), origin)

        assertNull(MisskeyMapper.account(old, origin).movedTo)
        assertEquals(destination, MisskeyMapper.account(old, origin, movedTo = destination).movedTo)
        assertNull(destination.movedTo)
    }

    @Test
    fun pureRenoteKeepsOuterRowIdButUsesDisplayedNoteForActions() {
        val user = JSONObject()
            .put("id", "user-a")
            .put("username", "alice")
            .put("name", "Alice")
            .put("host", JSONObject.NULL)
        val original = JSONObject()
            .put("id", "original-id")
            .put("createdAt", "2026-09-06T10:00:00Z")
            .put("user", user)
            .put("text", "Original")
        val renote = JSONObject()
            .put("id", "outer-id")
            .put("createdAt", "2026-09-06T11:00:00Z")
            .put("user", user)
            .put("text", JSONObject.NULL)
            .put("renote", original)

        val post = MisskeyMapper.post(renote, "https://example.org")

        assertEquals("outer-id", post.id.value)
        assertEquals("original-id", post.actionTargetId?.value)
    }

    @Test
    fun accountAndNoteEmojiMapsPreserveLocalAndRemoteIdentities() {
        val account = MisskeyMapper.account(JSONObject()
            .put("id", "user-emoji")
            .put("username", "emojiuser")
            .put("name", "Emoji User :local_blob:")
            .put("host", JSONObject.NULL)
            .put("emojis", JSONObject()
                .put("local_blob", JSONObject()
                    .put("name", "local_blob")
                    .put("url", "https://cdn.local.example/local_blob.png")
                    .put("aliases", org.json.JSONArray().put("localblob")))
                .put("remote_blob@remote.example", JSONObject()
                    .put("name", "remote_blob@remote.example")
                    .put("url", "https://cdn.remote.example/remote_blob.png"))), origin)

        assertEquals(":local_blob:", account.emoji.getValue(":local_blob:").submissionValue)
        assertEquals("local_blob", account.emoji.getValue("local_blob").shortcode)
        assertEquals(":local_blob:", account.emoji.getValue("localblob").submissionValue)
        val remote = account.emoji.getValue(":remote_blob@remote.example:")
        assertEquals(":remote_blob@remote.example:", remote.submissionValue)
        assertEquals("https://cdn.remote.example/remote_blob.png", remote.staticUrl?.value)
    }

    @Test
    fun noteReactionsClampNegativeCountsAndResolveMetadataWithoutNormalizingIdentity() {
        val note = JSONObject()
            .put("id", "note-reactions")
            .put("createdAt", "2026-09-06T10:00:00Z")
            .put("text", "reactions")
            .put("user", JSONObject().put("id", "u").put("username", "u").put("name", "U"))
            .put("reactions", JSONObject()
                .put(":blob_cat:", 3)
                .put(":blob_cat@remote.example:", 1)
                .put("❤️", -7)
                .put(":broken:", -2))
            .put("reactionEmojis", JSONObject()
                .put("blob_cat", "https://cdn.local.example/blob_cat.png")
                .put("blob_cat@remote.example", "https://cdn.remote.example/blob_cat.png"))
            .put("myReaction", ":blob_cat:")

        val post = MisskeyMapper.post(note, origin)

        val byIdentity = post.reactions.associateBy { it.emoji }
        assertEquals(3, byIdentity.getValue(":blob_cat:").count)
        assertEquals("https://cdn.local.example/blob_cat.png",
            byIdentity.getValue(":blob_cat:").emojiMetadata?.staticUrl?.value)
        assertEquals(":blob_cat:", byIdentity.getValue(":blob_cat:").emojiMetadata?.submissionValue)
        assertEquals(
            "https://cdn.remote.example/blob_cat.png",
            byIdentity.getValue(":blob_cat@remote.example:").emojiMetadata?.staticUrl?.value,
        )
        assertEquals(0, byIdentity.getValue("❤️").count)
        assertEquals(0, byIdentity.getValue(":broken:").count)
        assertNotNull(byIdentity.getValue(":broken:").emojiMetadata)
        assertEquals(listOf(":blob_cat:"), post.selectedReactions.map { it.submissionValue })
        assertEquals(":blob_cat:", post.myReaction)
        assertTrue(post.reactions.none { it.count < 0 })
    }

    @Test
    fun nestedRenotesKeepIndependentEmojiMaps() {
        val user = JSONObject().put("id", "u").put("username", "u").put("name", "U").put("host", JSONObject.NULL)
        val inner = JSONObject()
            .put("id", "inner")
            .put("createdAt", "2026-09-06T10:00:00Z")
            .put("user", user)
            .put("text", "inner")
            .put("emojis", JSONObject().put("inner_blob", JSONObject()
                .put("name", "inner_blob").put("url", "https://cdn.example/inner.png")))
        val outer = JSONObject()
            .put("id", "outer")
            .put("createdAt", "2026-09-06T11:00:00Z")
            .put("user", user)
            .put("renote", inner)

        val post = MisskeyMapper.post(outer, origin)

        assertTrue(post.emoji.containsKey("inner_blob"))
        assertTrue(post.emoji.keys.none { it.contains("outer") })
    }

    @Test
    fun misskeyAvifImagePreservesMissingThumbnail() {
        val attachment = MisskeyMapper.post(noteWithFile("image/avif", "image.avif"), origin).attachments.single()

        assertEquals("https://example.org/image.avif", attachment.url)
        assertNull(attachment.previewUrl)
        assertEquals(MediaKind.Image, attachment.kind)
    }

    @Test
    fun misskeyJpegImagePreservesMissingThumbnail() {
        val attachment = MisskeyMapper.post(noteWithFile("image/jpeg", "image.jpg"), origin).attachments.single()

        assertEquals("https://example.org/image.jpg", attachment.url)
        assertNull(attachment.previewUrl)
        assertEquals(MediaKind.Image, attachment.kind)
    }

    private fun noteWithFile(type: String, fileName: String): JSONObject = JSONObject()
        .put("id", "note-$fileName")
        .put("createdAt", "2026-09-06T10:00:00Z")
        .put("text", "image")
        .put("user", JSONObject().put("id", "user-file").put("username", "fileuser").put("name", "File User"))
        .put("files", org.json.JSONArray().put(
            JSONObject()
                .put("id", "file-$fileName")
                .put("url", "https://example.org/$fileName")
                .put("type", type)
                .put("thumbnailUrl", JSONObject.NULL),
        ))
}
