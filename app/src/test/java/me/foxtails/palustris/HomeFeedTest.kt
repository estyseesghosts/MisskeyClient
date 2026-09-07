package me.foxtails.palustris

import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.Audience
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.EntityId
import me.foxtails.palustris.domain.OwnedPost
import me.foxtails.palustris.domain.Post
import me.foxtails.palustris.domain.PostAction
import me.foxtails.palustris.domain.Protocol
import me.foxtails.palustris.ui.FeedState
import me.foxtails.palustris.ui.PalustrisApp
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
class HomeFeedTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private val account = Account(
        AccountId(Connection("https://example.org", Protocol.MASTODON), "person"),
        "Display Name",
        "@person@example.org",
    )

    private fun show(
        post: Post,
        feedState: FeedState = FeedState(posts = listOf(post)),
    ) {
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                PalustrisApp(account = account, feedState = feedState)
            }
        }
        compose.waitForIdle()
    }

    @Test fun feedHeaderUsesDisplayNameWithoutHandleAndStaysCompact() {
        show(Post(postId("header"), account, "A visible post", 0, Audience.Public))

        compose.onNodeWithText("Display Name").assertIsDisplayed()
        compose.onNodeWithContentDescription("Profile picture of Display Name").assertIsDisplayed()
        compose.onNodeWithText("@person@example.org").assertDoesNotExist()
        val height = compose.onNodeWithContentDescription("Post metadata").fetchSemanticsNode().boundsInRoot.height
        val heightDp = height / compose.activity.resources.displayMetrics.density
        assertTrue("metadata row should remain compact, was $heightDp dp", heightDp <= 52f)
    }

    @Test fun terminalTagsMoveToSummaryAndPopupWhileInlineTagsStayInBody() {
        show(Post(postId("tags"), account, "A post #inline stays #photo #sunset", 0, Audience.Public))

        compose.onNodeWithText("A post #inline stays").assertIsDisplayed()
        compose.onNodeWithText("A post #inline stays #photo #sunset").assertDoesNotExist()
        compose.onNodeWithContentDescription("2 hashtags: #photo and #sunset").performClick()
        compose.onNodeWithContentDescription("Hashtag #photo").assertIsDisplayed()
        compose.onNodeWithContentDescription("Hashtag #sunset").assertIsDisplayed()
    }

    @Test fun tagOnlyPostDoesNotRenderAnEmptyBody() {
        show(Post(postId("tag-only"), account, "#onlytag #two", 0, Audience.Public))

        compose.onNodeWithContentDescription("2 hashtags: #onlytag and #two").assertIsDisplayed()
        compose.onNodeWithText("#onlytag #two").assertDoesNotExist()
    }

    @Test fun contentWarningSuppressesTagSummaryUntilContentIsRevealed() {
        show(Post(postId("cw"), account, "Hidden #sensitive", 0, Audience.Public, contentWarning = "Spoilers"))

        compose.onNodeWithText("Spoilers").assertIsDisplayed()
        compose.onNodeWithContentDescription("1 hashtag: #sensitive").assertDoesNotExist()
        compose.onNodeWithText("Show content").performClick()
        compose.onNodeWithContentDescription("1 hashtag: #sensitive").assertIsDisplayed()
    }

    @Test fun interactionRowKeepsTheFiveButtonMoshidonLayout() {
        val post = Post(
            postId("actions"),
            account,
            "Action post",
            0,
            Audience.Public,
        )
        show(
            post,
            FeedState(
                posts = listOf(post),
                ownedPosts = listOf(OwnedPost(account.id, post)),
                actions = setOf(PostAction.Reshare, PostAction.Favorite),
            ),
        )

        compose.onNodeWithContentDescription("Reply").assertIsDisplayed()
        compose.onNodeWithContentDescription("Repost").assertIsDisplayed()
        compose.onNodeWithContentDescription("Favorite").assertIsDisplayed()
        compose.onNodeWithContentDescription("Bookmark").assertIsDisplayed()
        compose.onNodeWithContentDescription("Share").assertIsDisplayed()
    }

    private fun postId(value: String) = EntityId("https://example.org", value)
}
