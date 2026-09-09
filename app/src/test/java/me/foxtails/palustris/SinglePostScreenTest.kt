package me.foxtails.palustris

import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
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
import me.foxtails.palustris.ui.SinglePostScreen
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-420dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SinglePostScreenTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private val account = Account(
        AccountId(Connection("https://example.org", Protocol.MASTODON), "person"),
        "Display Name",
        "@person@example.org",
    )

    @Test fun photoPostUsesAFullWidthPagerAndKeepsTheBodyBelowIt() {
        val post = Post(
            EntityId("https://example.org", "photo-post"),
            account,
            "The complete photo post body",
            0,
            Audience.Public,
            attachments = listOf(
                image("one"),
                image("two"),
            ),
        )
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                SinglePostScreen(OwnedPost(account.id, post), onClose = {})
            }
        }
        compose.waitForIdle()

        val pager = compose.onNodeWithTag("single_post_photo_pager", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val density = compose.activity.resources.displayMetrics.density
        assertEquals(0f, pager.left, 1f)
        assertEquals(411f * density, pager.right, 1f)
        compose.onNodeWithText("The complete photo post body").assertIsDisplayed()
        compose.onNodeWithText("1 / 2").assertIsDisplayed()

        compose.onNodeWithTag("single_post_photo_pager", useUnmergedTree = true)
            .performTouchInput { swipeLeft() }
        compose.waitForIdle()
        compose.onNodeWithText("2 / 2").assertIsDisplayed()
    }

    @Test fun photoPostKeepsTheCompleteBodyWithoutFeedTruncation() {
        val body = "complete ".repeat(45)
        val post = Post(
            EntityId("https://example.org", "long-photo-post"),
            account,
            body,
            0,
            Audience.Public,
            attachments = listOf(image("long-body")),
        )
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                SinglePostScreen(OwnedPost(account.id, post), onClose = {})
            }
        }
        compose.waitForIdle()

        compose.onNodeWithText(body, substring = false).assertIsDisplayed()
        compose.onNodeWithText("View full post").assertDoesNotExist()
    }

    private fun image(id: String) = Attachment(
        id = id,
        url = "https://cdn.example/$id.jpg",
        mimeType = "image/jpeg",
        kind = MediaKind.Image,
        width = 640,
        height = 480,
    )
}
