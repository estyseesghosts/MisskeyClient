package me.foxtails.palustris

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.swipe
import androidx.compose.ui.geometry.Offset
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
import me.foxtails.palustris.ui.media.MediaOpenRequest
import me.foxtails.palustris.ui.media.MediaTransitionFrame
import me.foxtails.palustris.ui.media.MediaTransitionImageCanvas
import me.foxtails.palustris.ui.media.MediaTransitionKey
import me.foxtails.palustris.ui.media.MediaViewerScreen
import me.foxtails.palustris.ui.media.PostMediaCarousel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
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
class MediaViewerScreenTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private val account = Account(
        AccountId(Connection("https://example.org", Protocol.MASTODON), "person"),
        "Display Name",
        "@person@example.org",
    )
    private val post = Post(
        id = EntityId("https://example.org", "post-1"),
        author = account,
        text = "Media post",
        publishedAtEpochMillis = 0,
        audience = Audience.Public,
        attachments = listOf(
            Attachment(url = "https://cdn.example/one.jpg", mimeType = "image/jpeg", kind = MediaKind.Image),
            Attachment(url = "https://cdn.example/two.jpg", mimeType = "image/jpeg", kind = MediaKind.Image),
        ),
    )

    @Test
    fun tappingAVisibleNeighborReportsItsExactAttachmentIndex() {
        var request: MediaOpenRequest? = null
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                PostMediaCarousel(OwnedPost(account.id, post), onOpenMedia = { request = it })
            }
        }
        compose.waitForIdle()

        compose.onNodeWithContentDescription("Open media 2 of 2").performClick()
        compose.waitForIdle()

        assertEquals(1, request?.attachmentIndex)
        val transitionKey = request?.transitionKey
        assertTrue(transitionKey != null)
        assertEquals(MediaTransitionKey.forAttachment(OwnedPost(account.id, post), 1).copy(occurrence = transitionKey!!.occurrence), transitionKey)
        assertNotEquals("default", transitionKey.occurrence)
        assertTrue(request?.initialSourceBounds?.width ?: 0f > 0f)
    }

    @Test
    fun viewerKeepsPagerChromeAndPostActionOrderVisible() {
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                MediaViewerScreen(
                    request = MediaOpenRequest(OwnedPost(account.id, post), attachmentIndex = 1, revealed = true),
                    onClose = {},
                )
            }
        }
        compose.waitForIdle()

        compose.onNodeWithText("2 / 2").assertIsDisplayed()
        compose.onNodeWithContentDescription("Close media viewer").assertIsDisplayed()
        listOf("Favorite", "Reply", "Repost", "Share").forEach {
            compose.onNodeWithContentDescription(it).assertIsDisplayed()
        }
    }

    @Test
    fun shortVerticalDragReturnsWithoutClosingViewer() {
        var closeCount = 0
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                MediaViewerScreen(
                    request = MediaOpenRequest(OwnedPost(account.id, post), attachmentIndex = 0, revealed = true),
                    onClose = { closeCount++ },
                )
            }
        }
        compose.waitForIdle()

        compose.onNodeWithContentDescription("Media viewer").performTouchInput {
            swipe(center, center + Offset(0f, 70f), durationMillis = 180)
        }
        compose.waitForIdle()

        assertEquals(0, closeCount)
        compose.onNodeWithText("1 / 2").assertIsDisplayed()
    }

    @Test
    fun closeButtonUsesControlledCloseCallback() {
        var closeCount = 0
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                MediaViewerScreen(
                    request = MediaOpenRequest(OwnedPost(account.id, post), attachmentIndex = 0, revealed = true),
                    onClose = { closeCount++ },
                )
            }
        }
        compose.waitForIdle()

        compose.onNodeWithContentDescription("Close media viewer").performClick()
        compose.waitForIdle()

        assertEquals(1, closeCount)
    }

    @Test
    fun horizontalPagerMovementDoesNotInvokeDismissal() {
        var closeCount = 0
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                MediaViewerScreen(
                    request = MediaOpenRequest(OwnedPost(account.id, post), attachmentIndex = 0, revealed = true),
                    onClose = { closeCount++ },
                )
            }
        }
        compose.waitForIdle()

        compose.onNodeWithContentDescription("Media viewer").performTouchInput {
            swipe(center, center + Offset(-1_200f, 0f), durationMillis = 180)
        }
        compose.waitForIdle()

        assertEquals(0, closeCount)
        compose.onNodeWithText("2 / 2").assertIsDisplayed()

        compose.onNodeWithContentDescription("Media viewer").performTouchInput {
            swipe(center, center + Offset(1_200f, 0f), durationMillis = 180)
        }
        compose.waitForIdle()

        assertEquals(0, closeCount)
        compose.onNodeWithText("1 / 2").assertIsDisplayed()
    }

    @Test
    fun viewerKeepsControlsForAvifWithSeparatePreviewAndFullUrls() {
        val avifPost = post.copy(
            attachments = listOf(
                Attachment(
                    url = "https://cdn.example/full.jpg",
                    previewUrl = "https://cdn.example/preview.jpg",
                    mimeType = "image/avif",
                    kind = MediaKind.Image,
                ),
            ),
        )
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                MediaViewerScreen(
                    request = MediaOpenRequest(OwnedPost(account.id, avifPost), attachmentIndex = 0, revealed = true),
                    onClose = {},
                )
            }
        }
        compose.waitForIdle()

        compose.onNodeWithText("1 / 1").assertIsDisplayed()
        compose.onNodeWithContentDescription("Close media viewer").assertIsDisplayed()
    }

    @Test
    fun transitionCanvasUsesTheRequestedImageFrameAndRoundedClip() {
        val fixture = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
        for (y in 0 until fixture.height) {
            for (x in 0 until fixture.width) {
                fixture.setPixel(
                    x,
                    y,
                    when {
                        y < 2 && x < 2 -> android.graphics.Color.RED
                        y < 2 -> android.graphics.Color.GREEN
                        x < 2 -> android.graphics.Color.BLUE
                        else -> android.graphics.Color.YELLOW
                    },
                )
            }
        }
        val frame = MediaTransitionFrame(
            imageBounds = androidx.compose.ui.geometry.Rect(32f, 180f, 352f, 420f),
            clipBounds = androidx.compose.ui.geometry.Rect(32f, 180f, 352f, 420f),
            visibleBounds = androidx.compose.ui.geometry.Rect(32f, 180f, 352f, 420f),
            cornerRadiusPx = 24f,
        )
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                Box(Modifier.fillMaxSize().background(Color.Black)) {
                    MediaTransitionImageCanvas(BitmapPainter(fixture.asImageBitmap()), frame)
                }
            }
        }
        compose.waitForIdle()

        lateinit var screenshot: Bitmap
        compose.runOnIdle {
            val view = compose.activity.window.decorView
            screenshot = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(screenshot))
        }

        assertEquals(android.graphics.Color.RED, screenshot.getPixel(112, 240))
        assertEquals(android.graphics.Color.GREEN, screenshot.getPixel(272, 240))
        assertEquals(android.graphics.Color.BLUE, screenshot.getPixel(112, 360))
        assertEquals(android.graphics.Color.YELLOW, screenshot.getPixel(272, 360))
        assertEquals(android.graphics.Color.BLACK, screenshot.getPixel(34, 182))
        fixture.recycle()
    }
}
