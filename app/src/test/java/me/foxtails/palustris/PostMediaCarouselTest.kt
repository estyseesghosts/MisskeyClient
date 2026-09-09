package me.foxtails.palustris

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
import org.junit.Assert.assertEquals
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
