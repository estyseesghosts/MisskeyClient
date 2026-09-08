package me.foxtails.palustris

import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.Attachment
import me.foxtails.palustris.domain.Audience
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.EntityId
import me.foxtails.palustris.domain.OwnedPost
import me.foxtails.palustris.domain.Post
import me.foxtails.palustris.domain.PostAction
import me.foxtails.palustris.domain.ProfileField
import me.foxtails.palustris.domain.Protocol
import me.foxtails.palustris.ui.FeedState
import me.foxtails.palustris.ui.PalustrisApp
import me.foxtails.palustris.ui.SearchScreen
import me.foxtails.palustris.ui.AccountSearchState
import me.foxtails.palustris.ui.profile.ProfileUiState
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
        val actionHeight = compose.onNodeWithContentDescription("Post actions").fetchSemanticsNode().boundsInRoot.height
        val actionHeightDp = actionHeight / compose.activity.resources.displayMetrics.density
        assertTrue("action row should remain a 48dp touch row, was $actionHeightDp dp", actionHeightDp in 47f..49f)
        assertTrue("action row should be shorter than metadata", actionHeight < height)
        val replyHeightDp = compose.onNodeWithContentDescription("Reply").fetchSemanticsNode().boundsInRoot.height /
            compose.activity.resources.displayMetrics.density
        assertTrue("action button should retain a comfortable touch target", replyHeightDp >= 47f)
    }

    @Test fun compactHomeFinalPostCanScrollAboveFloatingAssembly() {
        val first = Post(
            postId("compact-first"),
            account,
            (1..18).joinToString("\n") { "First fixture line $it" },
            0,
            Audience.Public,
        )
        val final = Post(postId("compact-final"), account, "Compact final home post", 0, Audience.Public)
        show(first, FeedState(posts = listOf(first, final)))

        val timeline = compose.onNodeWithContentDescription("Choose timeline").fetchSemanticsNode().boundsInRoot
        repeat(14) {
            compose.onNodeWithTag("home_feed_content", useUnmergedTree = true).performTouchInput { swipeUp() }
        }
        compose.waitForIdle()

        val finalBounds = compose.onNodeWithTag("post_row_compact-final", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        assertTrue("the final Home post should clear the timeline selector", finalBounds.bottom <= timeline.top)
        compose.onNodeWithText("Compact final home post").assertIsDisplayed()
    }

    @Test fun compactSearchResultsScrollFinalPostAboveFloatingControls() {
        val results = (0..6).map { index ->
            Post(
                postId("search-$index"),
                account,
                "Search result $index",
                0,
                Audience.Public,
            )
        }
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                PalustrisApp(
                    account = account,
                    feedState = FeedState(
                        accountSearch = AccountSearchState(
                            query = "#cats",
                            tagQuery = "cats",
                            posts = results,
                        ),
                    ),
                )
            }
        }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Search").performClick()
        compose.onNode(hasSetTextAction()).performTextInput("#cats")
        compose.waitForIdle()

        repeat(14) {
            compose.onNodeWithTag("search_content", useUnmergedTree = true).performTouchInput { swipeUp() }
        }
        compose.waitForIdle()

        val finalBounds = compose.onNodeWithTag("post_row_search-6", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val chips = compose.onNodeWithContentDescription("Search categories; swipe horizontally for more")
            .fetchSemanticsNode().boundsInRoot
        assertTrue("the final Search result should clear the floating controls", finalBounds.bottom <= chips.top)
        compose.onNodeWithText("Search result 6").assertIsDisplayed()
    }

    @Test fun terminalTagsMoveToSummaryAndPopupWhileInlineTagsStayInBody() {
        show(Post(postId("tags"), account, "A post #inline stays #photo #sunset", 0, Audience.Public))

        compose.onNodeWithText("A post #inline stays").assertIsDisplayed()
        compose.onNodeWithText("A post #inline stays #photo #sunset").assertDoesNotExist()
        compose.onNodeWithContentDescription("2 hashtags: #photo and #sunset").performClick()
        compose.onNodeWithContentDescription("Hashtag #photo").assertIsDisplayed()
        compose.onNodeWithContentDescription("Hashtag #sunset").assertIsDisplayed()
    }

    @Test fun detachedDecorativeBlocksDisappearFromBodyAndAllTagsReachPopup() {
        show(
            Post(
                postId("detached-tags"),
                account,
                "First paragraph.\n#Scape ✨ #ForestFriday ✨ …\nSecond paragraph.\n#one • #two\nA #visible inline tag remains.",
                0,
                Audience.Public,
            ),
        )

        compose.onNodeWithText("First paragraph.\nSecond paragraph.\nA #visible inline tag remains.").assertIsDisplayed()
        compose.onNodeWithText("First paragraph.\n#Scape ✨ #ForestFriday ✨ …").assertDoesNotExist()
        compose.onNodeWithContentDescription("4 hashtags: #Scape, #ForestFriday, #one and #two").performClick()
        compose.onNodeWithContentDescription("Hashtag #Scape").assertIsDisplayed()
        compose.onNodeWithContentDescription("Hashtag #ForestFriday").assertIsDisplayed()
        compose.onNodeWithContentDescription("Hashtag #one").assertIsDisplayed()
        compose.onNodeWithContentDescription("Hashtag #two").assertIsDisplayed()
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

    @Test fun postTimeMovesBelowBodyAndImagesHaveNoOpenImageButton() {
        val post = Post(
            postId("timestamp"),
            account,
            "A post with an image",
            System.currentTimeMillis(),
            Audience.Public,
            attachments = listOf(Attachment("https://example.org/photo.jpg", "image/jpeg", "A photo")),
        )
        show(post)

        compose.onNodeWithContentDescription("Post time").assertIsDisplayed()
        compose.onNodeWithText("Open image").assertDoesNotExist()
    }

    @Test fun tappingPostAuthorOpensProfileWithCategoryChips() {
        val author = account.copy(
            displayName = "Author Profile",
            biography = "A profile biography",
            profileFields = listOf(ProfileField("Website", "https://example.org"), ProfileField("Matrix", "@author:example.org")),
        )
        val profileState = mutableStateOf(ProfileUiState())
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                PalustrisApp(
                    account = account,
                    feedState = FeedState(posts = listOf(Post(postId("profile"), author, "A visible post", 0, Audience.Public))),
                    profileState = profileState.value,
                    onProfileShown = { seed ->
                        profileState.value = profileState.value.copy(
                            targetId = seed.id,
                            seedAccount = seed,
                            account = seed,
                        )
                    },
                    onProfileCategorySelected = { category ->
                        profileState.value = profileState.value.copy(selectedTab = category)
                    },
                )
            }
        }
        compose.waitForIdle()

        compose.onNodeWithText("Author Profile").performClick()
        compose.onNodeWithText("A profile biography").assertIsDisplayed()
        val categories = compose.onNodeWithContentDescription("Profile categories; swipe horizontally for more")
        categories.assert(hasScrollAction())
        compose.onNodeWithText("Posts").assertIsSelected()
        listOf("Posts", "Media", "Reposts", "Replies", "Show more...").forEach { label ->
            categories.performScrollToNode(hasText(label))
            compose.onNodeWithText(label).assertExists()
        }
        categories.performScrollToNode(hasText("Media"))
        compose.onNodeWithText("Media").performClick()
        compose.onNodeWithText("Media").assertIsSelected()
        categories.performScrollToNode(hasText("Show more..."))
        compose.onNodeWithText("Show more...").performClick()
        compose.onNodeWithText("Show more...").assertIsSelected()
        compose.onNodeWithText("Profile details").assertIsDisplayed()
        compose.onNodeWithText("https://example.org").assertIsDisplayed()
        compose.onNodeWithText("@author:example.org").assertIsDisplayed()
        compose.onNodeWithText("More profile views coming soon").assertDoesNotExist()
    }

    @Test fun searchSubmitsWebfingerHandleWithKeyboardSearch() {
        var submitted = ""
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                SearchScreen(onSearchAccounts = { submitted = it })
            }
        }
        compose.onNode(hasSetTextAction()).performTextInput("@alice@example.org")
        compose.onNode(hasSetTextAction()).performImeAction()

        assertTrue(submitted == "@alice@example.org")
    }

    @Test fun searchChipsUseCompactSelectionSemanticsAndHorizontalScrolling() {
        compose.activity.runOnUiThread {
            compose.activity.setContent { SearchScreen() }
        }
        compose.waitForIdle()

        compose.onNodeWithText("Profiles").assertIsSelected()
        compose.onNodeWithText("Hashtags").performClick()
        compose.onNodeWithText("Hashtags").assertIsSelected()
        compose.onNodeWithText("Explore hashtags").assertIsDisplayed()
        compose.onNodeWithContentDescription("Search categories; swipe horizontally for more").assert(hasScrollAction())
    }

    @Test fun searchSubmitsExactHashtagAndDisplaysRecentPosts() {
        var submitted = ""
        val result = Post(postId("tag-result"), account, "A recent #cats post", 0, Audience.Public)
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                SearchScreen(
                    accountSearch = AccountSearchState(query = "#cats", tagQuery = "cats", posts = listOf(result)),
                    onSearchAccounts = { submitted = it },
                )
            }
        }
        compose.waitForIdle()
        compose.onNode(hasSetTextAction()).performTextInput("#cats")
        compose.onNode(hasSetTextAction()).performImeAction()

        assertTrue(submitted == "#cats")
        compose.onNodeWithText("A recent #cats post").assertIsDisplayed()
    }

    @Test fun tappingOverflowHashtagInvokesSearchCallback() {
        var searched = ""
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                me.foxtails.palustris.ui.HomeFeed(
                    state = FeedState(posts = listOf(Post(postId("tap-tag"), account, "Body #one #two", 0, Audience.Public))),
                    onRefresh = {}, onLoadMore = {}, onSignIn = {}, onSearchHashtag = { searched = it },
                )
            }
        }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("2 hashtags: #one and #two").performClick()
        compose.onNodeWithContentDescription("Hashtag #two").performClick()

        assertTrue(searched == "#two")
    }

    @Test fun clearingHashtagSearchRemovesPreviousResults() {
        val result = Post(postId("clear-tag"), account, "A cached tag result", 0, Audience.Public)
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                SearchScreen(
                    accountSearch = AccountSearchState(query = "#cats", tagQuery = "cats", posts = listOf(result)),
                    initialQuery = "#cats",
                )
            }
        }
        compose.waitForIdle()
        compose.onNodeWithText("A cached tag result").assertIsDisplayed()
        compose.onNodeWithContentDescription("Clear search").performClick()
        compose.waitForIdle()

        compose.onNodeWithText("A cached tag result").assertDoesNotExist()
        compose.onNodeWithText("Find an account").assertIsDisplayed()
    }

    private fun postId(value: String) = EntityId("https://example.org", value)
}
