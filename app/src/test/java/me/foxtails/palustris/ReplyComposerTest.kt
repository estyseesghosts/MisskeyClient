package me.foxtails.palustris

import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-420dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReplyComposerTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun replyOpensComposerForTheEffectiveActionTarget() {
        val account = Account(
            AccountId(Connection("https://example.org", Protocol.MASTODON), "person"),
            "Display Name",
            "@person@example.org",
        )
        val post = Post(
            id = EntityId("https://example.org", "wrapper"),
            author = account,
            text = "A post to reply to",
            publishedAtEpochMillis = 0,
            audience = Audience.Public,
            actionTargetId = EntityId("https://example.org", "original"),
        )
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                PalustrisApp(
                    account = account,
                    feedState = FeedState(
                        posts = listOf(post),
                        ownedPosts = listOf(OwnedPost(account.id, post)),
                        actions = setOf(PostAction.Reply),
                    ),
                )
            }
        }
        compose.waitForIdle()

        compose.onNodeWithContentDescription("Reply").performClick()
        compose.onNodeWithText("Replying to Display Name").assertIsDisplayed()
    }
}
