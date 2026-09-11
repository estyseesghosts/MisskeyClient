package me.foxtails.palustris

import coil.decode.BitmapFactoryDecoder
import me.foxtails.palustris.data.emoji.EmojiAssetFetcher
import me.foxtails.palustris.data.media.MediaImageLoader
import me.foxtails.palustris.data.media.AvifDecoder
import me.foxtails.palustris.domain.Attachment
import me.foxtails.palustris.domain.MediaKind
import me.foxtails.palustris.domain.MediaRequestRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
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
        val preview = MediaImageLoader.renderCacheKey(
            "account-a", "post-a", attachment, 0, MediaRequestRole.Preview, 320, 240,
        )
        val full = MediaImageLoader.renderCacheKey(
            "account-a", "post-a", attachment, 0, MediaRequestRole.Full, 320, 240,
        )
        assertNotEquals(preview, full)
    }

    @Test
    fun duplicateUrlsStillUseDifferentAttachmentPositions() {
        val first = MediaImageLoader.renderCacheKey(
            "account-a", "post-a", attachment.copy(id = null), 0, MediaRequestRole.Preview, 320, 240,
        )
        val second = MediaImageLoader.renderCacheKey(
            "account-a", "post-a", attachment.copy(id = null), 1, MediaRequestRole.Preview, 320, 240,
        )
        assertNotEquals(first, second)
        assertTrue(first.contains("position-0"))
    }

    @Test
    fun accountAndPostOwnershipArePartOfKey() {
        val otherAccount = MediaImageLoader.renderCacheKey(
            "account-b", "post-a", attachment, 0, MediaRequestRole.Preview, 320, 240,
        )
        val otherPost = MediaImageLoader.renderCacheKey(
            "account-a", "post-b", attachment, 0, MediaRequestRole.Preview, 320, 240,
        )
        assertNotEquals(otherAccount, otherPost)
    }

    @Test
    fun sourceKeysReuseOneEncodedResourceAcrossRenderRolesAndSizes() {
        val first = MediaImageLoader.sourceCacheKey("account-a", attachment.url!!)
        val second = MediaImageLoader.sourceCacheKey("account-a", attachment.url!!)

        assertEquals(first, second)
        assertTrue(first.startsWith("media-source-v1\u0000account-a\u0000https://cdn.example/full.jpg"))
    }

    @Test
    fun emojiKeysShareAcrossAccountsAndIdentitiesForOneCanonicalUrl() {
        val first = MediaImageLoader.emojiCacheKey("https://CDN.example:443/emoji/../blob.png#fragment", 96)
        val second = MediaImageLoader.emojiCacheKey("https://cdn.example/blob.png", 96)

        assertEquals(first, second)
        assertTrue(first.startsWith("emoji-v2\u0000https://cdn.example/blob.png\u0000"))
    }

    @Test
    fun emojiDecodeSizesRemainSeparateMemoryEntries() {
        val small = MediaImageLoader.emojiCacheKey("https://cdn.example/blob.png", 48)
        val large = MediaImageLoader.emojiCacheKey("https://cdn.example/blob.png", 96)

        assertNotEquals(small, large)
    }

    @Test
    fun sharedLoaderPutsAvifDecoderBeforeBitmapDecoder() {
        val loader = MediaImageLoader.get(RuntimeEnvironment.getApplication())
        val factories = loader.imageLoader.components.decoderFactories

        assertTrue(factories.first() is AvifDecoder.Factory)
        assertTrue(factories.last() is BitmapFactoryDecoder.Factory)
    }

    @Test
    fun emojiLoaderUsesPersistentFetcherAndNoCoilDiskCache() {
        val loader = MediaImageLoader.get(RuntimeEnvironment.getApplication()).emojiImageLoader

        assertTrue(loader.diskCache == null)
        assertTrue(loader.components.fetcherFactories.any { it.first is EmojiAssetFetcher.Factory })
        assertTrue(loader.components.decoderFactories.first() is AvifDecoder.Factory)
    }
}
