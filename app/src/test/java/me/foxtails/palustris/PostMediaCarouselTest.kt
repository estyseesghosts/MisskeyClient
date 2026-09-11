package me.foxtails.palustris

import android.graphics.Bitmap
import android.graphics.Color
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.Attachment
import me.foxtails.palustris.domain.Audience
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.EntityId
import me.foxtails.palustris.domain.MediaKind
import me.foxtails.palustris.domain.OwnedPost
import me.foxtails.palustris.domain.Post
import me.foxtails.palustris.domain.Protocol
import me.foxtails.palustris.ui.media.PostMediaCarousel
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-420dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PostMediaCarouselTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private val account = Account(
        AccountId(Connection("https://example.org", Protocol.MASTODON), "person"),
        "Display Name",
        "@person@example.org",
    )

    @Test
    fun singleAttachmentUsesTheSameBoundedFrameAsTheFirstMultiAttachment() {
        val single = post("single", listOf(image("one")))
        val multi = post("multi", listOf(image("one"), image("two")))
        show(single, multi)

        val singleBounds = bounds("post_media_frame_single_0")
        val multiBounds = bounds("post_media_frame_multi_0")
        val density = compose.activity.resources.displayMetrics.density
        val availableWidth = compose.activity.resources.displayMetrics.widthPixels / density

        assertEquals(240f, singleBounds.height / density, 1f)
        assertEquals(singleBounds.width, multiBounds.width, 1f)
        assertEquals(singleBounds.height, multiBounds.height, 1f)
        assertTrue(singleBounds.width / density < availableWidth)
    }

    @Test
    fun hiddenAndUnsupportedAttachmentsKeepTheSameFrameGeometry() {
        val visiblePost = post("visible-state", listOf(image("visible")))
        val sensitivePost = post("sensitive-state", listOf(image("sensitive").copy(sensitive = true)))
        val unsupportedPost = post(
            "unsupported-state",
            listOf(Attachment(id = "video", url = "https://cdn.example/video.mp4", mimeType = "video/mp4", kind = MediaKind.Video)),
        )
        show(visiblePost, sensitivePost, unsupportedPost)

        val visible = bounds("post_media_frame_visible-state_0")
        val sensitive = bounds("post_media_frame_sensitive-state_0")
        val unsupported = bounds("post_media_frame_unsupported-state_0")

        assertEquals(visible.width, sensitive.width, 1f)
        assertEquals(visible.width, unsupported.width, 1f)
        assertEquals(visible.height, sensitive.height, 1f)
        assertEquals(visible.height, unsupported.height, 1f)
    }

    @Test
    fun feedImageFillsFrameWithCropInsteadOfTileBackground() {
        val fixture = Bitmap.createBitmap(2, 4, Bitmap.Config.ARGB_8888)
        for (y in 0 until fixture.height) {
            for (x in 0 until fixture.width) {
                fixture.setPixel(x, y, if (y < 2) Color.RED else Color.BLUE)
            }
        }
        val png = ByteArrayOutputStream().also { fixture.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        fixture.recycle()
        val server = MockWebServer()
        server.enqueue(MockResponse().setBody(Buffer().write(png)))
        server.start()

        try {
            show(
                post(
                    "crop",
                    listOf(
                        image("crop").copy(
                            url = server.url("/full.png").toString(),
                            previewUrl = server.url("/crop.png").toString(),
                            previewWidth = 2,
                            previewHeight = 4,
                        ),
                    ),
                ),
            )
            repeat(10) {
                compose.waitForIdle()
                Thread.sleep(100)
            }

            val bounds = bounds("post_media_frame_crop_0")
            lateinit var screenshot: Bitmap
            compose.runOnIdle {
                val view = compose.activity.window.decorView
                screenshot = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                view.draw(android.graphics.Canvas(screenshot))
            }
            val x = bounds.center.x.toInt()
            val top = screenshot.getPixel(x, bounds.top.toInt() + 8)
            val bottom = screenshot.getPixel(x, bounds.bottom.toInt() - 8)

            assertTrue("top of feed frame should contain the image: $top", Color.red(top) > 180 && Color.blue(top) < 80)
            assertTrue("bottom of feed frame should contain the image: $bottom", Color.blue(bottom) > 180 && Color.red(bottom) < 80)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun avifWithAJpegUrlKeepsPreviewFrameGeometry() {
        show(
            post(
                "avif-preview",
                listOf(
                    image("avif").copy(
                        url = "https://cdn.example/image.jpg",
                        previewUrl = "https://cdn.example/preview.jpg",
                        mimeType = "image/avif",
                    ),
                ),
            ),
        )

        val bounds = bounds("post_media_frame_avif-preview_0")

        assertEquals(240f, bounds.height / compose.activity.resources.displayMetrics.density, 1f)
        assertTrue(bounds.width > 0f)
    }

    @Test
    fun imageWithoutPreviewRequestsItsFullUrlAtTimelineSize() {
        val fixture = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        val png = ByteArrayOutputStream().also { fixture.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        fixture.recycle()
        val server = MockWebServer()
        server.enqueue(MockResponse().setBody(Buffer().write(png)))
        server.start()

        try {
            show(post("missing-preview", listOf(image("fallback").copy(url = server.url("/fallback.png").toString(), previewUrl = null))))
            val request = server.takeRequest(5, java.util.concurrent.TimeUnit.SECONDS)
            assertNotNull(request)
            assertTrue(request!!.path!!.endsWith("/fallback.png"))
        } finally {
            server.shutdown()
        }
    }

    private fun show(vararg posts: Post) {
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                Column {
                    posts.forEach { post ->
                        PostMediaCarousel(OwnedPost(account.id, post), onOpenMedia = {})
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun bounds(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true)
        .fetchSemanticsNode().boundsInRoot

    private fun post(id: String, attachments: List<Attachment>) = Post(
        id = EntityId("https://example.org", id),
        author = account,
        text = "Media post",
        publishedAtEpochMillis = 0,
        audience = Audience.Public,
        attachments = attachments,
    )

    private fun image(id: String) = Attachment(
        id = id,
        url = "https://cdn.example/$id.jpg",
        mimeType = "image/jpeg",
        kind = MediaKind.Image,
        previewWidth = 640,
        previewHeight = 480,
    )
}
