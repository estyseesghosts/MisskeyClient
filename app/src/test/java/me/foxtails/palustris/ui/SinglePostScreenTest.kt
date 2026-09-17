package me.foxtails.palustris.ui

import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import me.foxtails.palustris.MainActivity
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.Attachment
import me.foxtails.palustris.domain.Audience
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.EntityId
import me.foxtails.palustris.domain.MediaKind
import me.foxtails.palustris.domain.OwnedPost
import me.foxtails.palustris.domain.Post
import me.foxtails.palustris.domain.PostAction
import me.foxtails.palustris.domain.PostInteractionCounts
import me.foxtails.palustris.domain.Reaction
import me.foxtails.palustris.domain.Protocol
import me.foxtails.palustris.ui.SinglePostScreen
import me.foxtails.palustris.ui.SinglePostPresentation
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
                SinglePostScreen(OwnedPost(account.id, post), SinglePostPresentation.PhotoGrid, onClose = {})
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
                SinglePostScreen(OwnedPost(account.id, post), SinglePostPresentation.PhotoGrid, onClose = {})
            }
        }
        compose.waitForIdle()

        compose.onNodeWithText(body, substring = false).assertIsDisplayed()
        compose.onNodeWithText("View full post").assertDoesNotExist()
    }

    @Test fun photoPostShowsInteractionsBetweenMediaAndBody() {
        val post = Post(
            EntityId("https://example.org", "photo-actions"),
            account,
            "Body below the action row",
            0,
            Audience.Public,
            attachments = listOf(image("actions")),
        )
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                SinglePostScreen(
                    ownedPost = OwnedPost(account.id, post),
                    presentation = SinglePostPresentation.PhotoGrid,
                    onClose = {},
                    availableActions = PostAction.entries.toSet(),
                )
            }
        }
        compose.waitForIdle()

        val actions = compose.onNodeWithContentDescription("Post actions", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val body = compose.onNodeWithText("Body below the action row")
            .fetchSemanticsNode().boundsInRoot
        assertTrue(actions.bottom <= body.top)
        compose.onNodeWithContentDescription("Reply").assertIsDisplayed()
        compose.onNodeWithContentDescription("Favorite").assertIsDisplayed()
    }

    @Test fun photoPostDetailExposesTheSharedQuoteAction() {
        val post = Post(
            EntityId("https://example.org", "quote-action"),
            account,
            "Quote target",
            0,
            Audience.Public,
        )
        var quotes = 0
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                SinglePostScreen(
                    ownedPost = OwnedPost(account.id, post),
                    presentation = SinglePostPresentation.PhotoGrid,
                    onClose = {},
                    availableActions = setOf(PostAction.Reshare),
                    quoteEnabled = true,
                    onQuote = { quotes++ },
                )
            }
        }
        compose.waitForIdle()

        compose.onNodeWithContentDescription("Repost").performTouchInput { longClick() }
        assertEquals(1, quotes)
    }

    @Test fun photoPostDetailShowsRepostConfirmation() {
        val post = Post(
            EntityId("https://example.org", "photo-repost"),
            account,
            "Photo repost",
            0,
            Audience.Public,
            attachments = listOf(image("photo-repost")),
        )
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                SinglePostScreen(
                    ownedPost = OwnedPost(account.id, post),
                    presentation = SinglePostPresentation.PhotoGrid,
                    onClose = {},
                    availableActions = setOf(PostAction.Reshare),
                )
            }
        }
        compose.waitForIdle()

        compose.onNodeWithContentDescription("Repost").performClick()
        compose.onNodeWithTag("repost_confirmation", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test fun photoPostDetailRendersUpdatedInteractionState() {
        val post = Post(
            EntityId("https://example.org", "photo-state"),
            account,
            "Photo state",
            0,
            Audience.Public,
            attachments = listOf(image("photo-state")),
        )
        var displayed by mutableStateOf(OwnedPost(account.id, post))
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                SinglePostScreen(
                    ownedPost = displayed,
                    presentation = SinglePostPresentation.PhotoGrid,
                    onClose = {},
                    availableActions = PostAction.entries.toSet(),
                )
            }
        }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Favorite").assertIsDisplayed()

        compose.activity.runOnUiThread {
            displayed = displayed.copy(post = displayed.post.copy(favourited = true, saved = true, reposted = true))
        }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Unfavorite").assertIsDisplayed()
        compose.onNodeWithContentDescription("Remove bookmark").assertIsDisplayed()
        compose.onNodeWithContentDescription("Undo repost").assertIsDisplayed()
    }

    @Test
    fun wideMisskeyDetailRendersAllUpdatedInteractionStates() {
        val misskeyAccount = account.copy(
            id = AccountId(Connection("https://misskey.example", Protocol.MISSKEY), "person"),
        )
        val post = Post(
            EntityId("https://misskey.example", "misskey-photo-state"),
            misskeyAccount,
            "Misskey photo state",
            0,
            Audience.Public,
            attachments = listOf(image("misskey-photo-state")),
        )
        var displayed by mutableStateOf(OwnedPost(misskeyAccount.id, post))
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                SinglePostScreen(
                    ownedPost = displayed,
                    presentation = SinglePostPresentation.PhotoGrid,
                    onClose = {},
                    availableActions = PostAction.entries.toSet(),
                )
            }
        }
        compose.waitForIdle()

        compose.onNodeWithContentDescription("Favorite").assertIsDisplayed()
        compose.onNodeWithContentDescription("Bookmark").assertIsDisplayed()

        compose.activity.runOnUiThread {
            displayed = displayed.copy(
                post = displayed.post.copy(
                    favourited = true,
                    myReaction = "👍",
                    selectedReactions = listOf(
                        me.foxtails.palustris.domain.EmojiChoice("👍", "👍", null),
                    ),
                    reposted = true,
                    saved = true,
                ),
            )
        }
        compose.waitForIdle()

        compose.onNodeWithContentDescription("Unfavorite").assertIsSelected()
        compose.onNodeWithContentDescription("Undo repost").assertIsSelected()
        compose.onNodeWithContentDescription("Remove bookmark").assertIsSelected()
    }

    @Test fun standardPresentationUsesThePostRowEvenWhenPhotosExist() {
        val post = Post(
            EntityId("https://example.org", "standard-photo-post"),
            account,
            "Standard detail body",
            0,
            Audience.Public,
            attachments = listOf(image("standard")),
        )
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                SinglePostScreen(
                    ownedPost = OwnedPost(account.id, post),
                    presentation = SinglePostPresentation.Standard,
                    onClose = {},
                )
            }
        }
        compose.waitForIdle()

        compose.onNodeWithText("Standard detail body").assertIsDisplayed()
        compose.onNodeWithTag("single_post_photo_pager").assertDoesNotExist()
    }

    @Test
    fun standardFocalPostShowsDetailedCountsAndHidesNumericOne() {
        val post = Post(
            EntityId("https://example.org", "detailed"),
            account,
            "Detailed body",
            0,
            Audience.Public,
            reactions = listOf(Reaction("❤️", 1, false), Reaction("👍", 4, false)),
            interactionCounts = PostInteractionCounts(
                favouriteCount = 0,
                reactionCount = 5,
                repostCount = 2,
                quoteRepostCount = 0,
                replyCount = 3,
            ),
        )
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                SinglePostScreen(OwnedPost(account.id, post), onClose = {})
            }
        }
        compose.waitForIdle()

        compose.onNodeWithTag("interaction_summary", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("0 favorites").assertIsDisplayed()
        compose.onNodeWithText("5 reactions").assertIsDisplayed()
        compose.onNodeWithTag("reaction_count_👍", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("reaction_count_❤️", useUnmergedTree = true).assertDoesNotExist()
        val summaryTop = compose.onNodeWithTag("interaction_summary", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot.top
        val metadataBottom = compose.onNodeWithContentDescription("Post metadata", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot.bottom
        assertTrue(summaryTop >= metadataBottom)
    }

    @Test
    fun photoGridFocalPostOrdersActionsReactionsBodyAndSummary() {
        val post = Post(
            EntityId("https://example.org", "photo-detailed"),
            account,
            "Photo detailed body",
            0,
            Audience.Public,
            attachments = listOf(image("photo-detailed")),
            reactions = listOf(Reaction("👍", 2, false)),
            interactionCounts = PostInteractionCounts(replyCount = 0, repostCount = 0),
        )
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                SinglePostScreen(
                    ownedPost = OwnedPost(account.id, post),
                    presentation = SinglePostPresentation.PhotoGrid,
                    onClose = {},
                    availableActions = PostAction.entries.toSet(),
                )
            }
        }
        compose.waitForIdle()

        val actions = compose.onNodeWithContentDescription("Post actions", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val reaction = compose.onNodeWithTag("reaction_chip_👍", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val body = compose.onNodeWithText("Photo detailed body").fetchSemanticsNode().boundsInRoot
        val summary = compose.onNodeWithTag("interaction_summary", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue(actions.bottom <= reaction.top)
        assertTrue(reaction.bottom <= body.top)
        assertTrue(body.bottom <= summary.top)
        compose.onNodeWithText("0 replies").assertIsDisplayed()
        compose.onNodeWithText("0 reposts").assertIsDisplayed()
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
