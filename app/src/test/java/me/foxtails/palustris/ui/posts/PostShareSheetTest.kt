package me.foxtails.palustris.ui.posts

import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import me.foxtails.palustris.MainActivity
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.Audience
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.EntityId
import me.foxtails.palustris.domain.Post
import me.foxtails.palustris.domain.Protocol
import me.foxtails.palustris.ui.PalustrisTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PostShareSheetTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun sheetUsesOneCardWithFourPrimaryActionsAndThreeBottomControls() {
        val connection = Connection("https://example.org", Protocol.MISSKEY)
        val account = Account(AccountId(connection, "owner"), "Owner", "@owner@example.org")
        val post = Post(EntityId(connection.origin, "post"), account, "Post", 0, Audience.Public, url = "https://example.org/post")
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                PalustrisTheme {
                    PostShareSheet(post = post, onDismiss = {}, onShare = {})
                }
            }
        }

        compose.onNodeWithTag("post_share_sheet").assertIsDisplayed()
        compose.onNodeWithTag("post_share_card").assertIsDisplayed()
        compose.onNodeWithTag("post_share_follow").assertIsDisplayed()
        compose.onNodeWithTag("post_share_block").assertIsDisplayed()
        compose.onNodeWithTag("post_share_mute").assertIsDisplayed()
        compose.onNodeWithTag("post_share_report").assertIsDisplayed()
        compose.onNodeWithTag("post_share_bottom").assertIsDisplayed()
        compose.onNodeWithTag("post_share_pm").assertIsDisplayed()
        compose.onNodeWithTag("post_share_copy").assertIsDisplayed()
        compose.onNodeWithTag("post_share_system").assertIsDisplayed()
        compose.onAllNodesWithText("Share post").assertCountEquals(0)
    }
}
