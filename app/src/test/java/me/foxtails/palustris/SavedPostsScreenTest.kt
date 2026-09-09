package me.foxtails.palustris

import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.Audience
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.EntityId
import me.foxtails.palustris.domain.OwnedPost
import me.foxtails.palustris.domain.Post
import me.foxtails.palustris.domain.Protocol
import me.foxtails.palustris.ui.SavedPostsScreen
import me.foxtails.palustris.ui.SavedPostsUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-420dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SavedPostsScreenTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun savedPostRowsUseLinkAwareTruncation() {
        val account = Account(
            AccountId(Connection("https://example.org", Protocol.MASTODON), "person"),
            "Saved author",
            "@person@example.org",
        )
        val url = "https://example.org/a-very-long-path"
        val text = "x".repeat(340) + " " + url + " tail"
        val post = Post(
            EntityId("https://example.org", "saved-link"),
            account,
            text,
            0,
            Audience.Public,
        )
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                SavedPostsScreen(
                    state = SavedPostsUiState(posts = listOf(OwnedPost(account.id, post))),
                    onRefresh = {},
                    onLoadMore = {},
                    onUnsave = {},
                )
            }
        }
        compose.waitForIdle()

        compose.onNodeWithContentDescription("Link url.xyz").assertIsDisplayed()
        compose.onNodeWithText(url, substring = true).assertDoesNotExist()
    }
}
