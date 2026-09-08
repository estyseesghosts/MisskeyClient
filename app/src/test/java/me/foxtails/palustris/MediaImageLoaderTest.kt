package me.foxtails.palustris

import me.foxtails.palustris.data.media.MediaImageLoader
import me.foxtails.palustris.domain.Attachment
import me.foxtails.palustris.domain.MediaKind
import me.foxtails.palustris.domain.MediaRequestRole
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaImageLoaderTest {
    private val attachment = Attachment(
        id = "media-1",
        url = "https://cdn.example/full.jpg",
        previewUrl = "https://cdn.example/preview.jpg",
        mimeType = "image/jpeg",
        kind = MediaKind.Image,
    )

    @Test
    fun previewAndFullUseDifferentCacheRoles() {
        val preview = MediaImageLoader.cacheKey(
            "account-a", "post-a", attachment, 0, MediaRequestRole.Preview, 320, 240,
        )
        val full = MediaImageLoader.cacheKey(
            "account-a", "post-a", attachment, 0, MediaRequestRole.Full, 320, 240,
        )
        assertNotEquals(preview, full)
    }

    @Test
    fun duplicateUrlsStillUseDifferentAttachmentPositions() {
        val first = MediaImageLoader.cacheKey(
            "account-a", "post-a", attachment.copy(id = null), 0, MediaRequestRole.Preview, 320, 240,
        )
        val second = MediaImageLoader.cacheKey(
            "account-a", "post-a", attachment.copy(id = null), 1, MediaRequestRole.Preview, 320, 240,
        )
        assertNotEquals(first, second)
        assertTrue(first.contains("position-0"))
    }

    @Test
    fun accountAndPostOwnershipArePartOfKey() {
        val otherAccount = MediaImageLoader.cacheKey(
            "account-b", "post-a", attachment, 0, MediaRequestRole.Preview, 320, 240,
        )
        val otherPost = MediaImageLoader.cacheKey(
            "account-a", "post-b", attachment, 0, MediaRequestRole.Preview, 320, 240,
        )
        assertNotEquals(otherAccount, otherPost)
    }
}
