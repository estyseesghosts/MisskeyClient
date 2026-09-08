package me.foxtails.palustris

import me.foxtails.palustris.data.mastodon.MastodonMapper
import me.foxtails.palustris.domain.MediaKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
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
}
