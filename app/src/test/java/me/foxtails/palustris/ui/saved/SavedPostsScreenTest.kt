package me.foxtails.palustris.ui.saved

import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import me.foxtails.palustris.MainActivity
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.Audience
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.EntityId
import me.foxtails.palustris.domain.OwnedPost
import me.foxtails.palustris.domain.Post
import me.foxtails.palustris.domain.PostInteractionCounts
import me.foxtails.palustris.domain.Protocol
import me.foxtails.palustris.domain.Reaction
import me.foxtails.palustris.ui.saved.SavedPostsScreen
import me.foxtails.palustris.ui.saved.SavedPostsCollection
import me.foxtails.palustris.ui.saved.SavedPostsUiState
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
        val text = "x".repeat(334) + " " + url + " tail"
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

        compose.onNodeWithContentDescription("Link example.org").assertIsDisplayed()
        compose.onNodeWithText(url, substring = true).assertDoesNotExist()
    }

    @Test
    fun likesUseTheirOwnEmptyState() {
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                SavedPostsScreen(
                    state = SavedPostsUiState(collection = SavedPostsCollection.Likes),
                    onRefresh = {},
                    onLoadMore = {},
                    onUnsave = {},
                )
            }
        }
        compose.waitForIdle()

        compose.onNodeWithText("No likes yet").assertIsDisplayed()
        compose.onNodeWithText("Posts you like will appear here.").assertIsDisplayed()
    }

    @Test
    fun savedRowsShowEmojiReactionsWithoutNumbersOrAggregateMetrics() {
        val account = Account(
            AccountId(Connection("https://example.org", Protocol.MASTODON), "person"),
            "Saved author",
            "@person@example.org",
        )
        val post = Post(
            EntityId("https://example.org", "saved-reactions"),
            account,
            "Saved reaction post",
            0,
            Audience.Public,
            reactions = listOf(Reaction("🎉", 2, false)),
            interactionCounts = PostInteractionCounts(favouriteCount = 4, repostCount = 3),
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

        compose.onNodeWithTag("reaction_chip_🎉", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("reaction_count_🎉", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("interaction_summary", useUnmergedTree = true).assertDoesNotExist()
    }
}
