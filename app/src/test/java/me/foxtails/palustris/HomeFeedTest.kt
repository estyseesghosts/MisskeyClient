package me.foxtails.palustris

import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.performTouchInput
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.Attachment
import me.foxtails.palustris.domain.Audience
import me.foxtails.palustris.domain.CapabilityStatus
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.EntityId
import me.foxtails.palustris.domain.EmojiCapabilities
import me.foxtails.palustris.domain.OwnedPost
import me.foxtails.palustris.domain.Post
import me.foxtails.palustris.domain.PostAction
import me.foxtails.palustris.domain.ProfileField
import me.foxtails.palustris.domain.Protocol
import me.foxtails.palustris.domain.Reaction
import me.foxtails.palustris.ui.FeedState
import me.foxtails.palustris.ui.PalustrisApp
import me.foxtails.palustris.ui.SearchScreen
import me.foxtails.palustris.ui.AccountSearchState
import me.foxtails.palustris.ui.profile.ProfileUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
        onReaction: (OwnedPost, me.foxtails.palustris.domain.EmojiChoice) -> Unit = { _, _ -> },
    ) {
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                PalustrisApp(
                    account = account,
                    feedState = feedState,
                    onReaction = onReaction,
                )
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

@Test fun reactionsShareChipAndEmojiSlotGeometryAcrossUnicodeAndCustomEmoji() {
        val blob = me.foxtails.palustris.domain.CustomEmoji(
            shortcode = "blob",
            animatedUrl = me.foxtails.palustris.domain.ValidatedUrl.https("https://example.org/blob.gif"),
            staticUrl = me.foxtails.palustris.domain.ValidatedUrl.https("https://example.org/blob.png"),
            submissionValue = ":blob:",
        )
        val reactions = listOf(
            Reaction("❤️", 3, selected = false),
            Reaction("👨‍👩‍👧‍👦", 3, selected = false),
            Reaction(":blob:", 3, selected = true, emojiMetadata = blob),
        )
        val post = Post(
            postId("reaction-geometry"),
            account,
            "Reactions",
            0,
            Audience.Public,
            reactions = reactions,
        )
        var clicked = ""
        show(
            post,
            FeedState(
                posts = listOf(post),
                ownedPosts = listOf(OwnedPost(account.id, post)),
                actions = setOf(PostAction.React),
            ),
            onReaction = { _, choice -> clicked = choice.submissionValue },
        )

        val chips = reactions.map { reaction ->
            compose.onNodeWithTag("reaction_chip_${reaction.emoji}", useUnmergedTree = true)
                .fetchSemanticsNode().boundsInRoot
        }
        val slots = reactions.map { reaction ->
            compose.onNodeWithTag("reaction_emoji_slot_${reaction.emoji}", useUnmergedTree = true)
                .fetchSemanticsNode().boundsInRoot
        }
        chips.forEach { bounds -> assertEquals(chips.first().height, bounds.height, 1f) }
        slots.forEach { bounds ->
            assertEquals(slots.first().width, bounds.width, 1f)
            assertEquals(slots.first().height, bounds.height, 1f)
        }
        compose.onAllNodesWithText("3").assertCountEquals(reactions.size)

        compose.onNodeWithTag("reaction_chip_${reactions.last().emoji}", useUnmergedTree = true).performClick()
        assertTrue(clicked == reactions.last().emoji)
    }

    @Test fun longPressingTheHeartOpensTheSharedReactionPicker() {
        val post = Post(postId("picker-post"), account, "Picker post", 0, Audience.Public)
        var opened: OwnedPost? = null
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                me.foxtails.palustris.ui.HomeFeed(
                    state = FeedState(
                        posts = listOf(post),
                        ownedPosts = listOf(OwnedPost(account.id, post)),
                        actions = setOf(PostAction.React),
                    ),
                    onRefresh = {}, onLoadMore = {}, onSignIn = {},
                    onOpenReactionPicker = { opened = it },
                )
            }
        }
        compose.waitForIdle()

        compose.onNodeWithContentDescription("Favorite").performTouchInput { longClick() }
        assertTrue(opened != null && opened?.post?.id == post.id)
    }

    @Test fun reactionOnlyServerUsesActionableTapInsteadOfNoOpHeart() {
        val post = Post(postId("reaction-only"), account, "Reaction only", 0, Audience.Public)
        var opened: OwnedPost? = null
        var favoured = false
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                me.foxtails.palustris.ui.HomeFeed(
                    state = FeedState(
                        posts = listOf(post),
                        ownedPosts = listOf(OwnedPost(account.id, post)),
                        actions = setOf(PostAction.React),
                    ),
                    onRefresh = {}, onLoadMore = {}, onSignIn = {},
                    onReact = { favoured = true },
                    onOpenReactionPicker = { opened = it },
                )
            }
        }
        compose.waitForIdle()

        compose.onNodeWithContentDescription("Favorite").performClick()
        assertTrue(opened != null)
        assertTrue(!favoured)
    }

    @Test fun unselectableReactionChipsStayReadOnlyWhenMutationIsUnavailable() {
        val reactions = listOf(Reaction("🎉", 2, false))
        val post = Post(
            postId("read-only-reactions"),
            account,
            "Read only",
            0,
            Audience.Public,
            reactions = reactions,
        )
        var clicked: String? = null
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                me.foxtails.palustris.ui.HomeFeed(
                    state = FeedState(
                        posts = listOf(post),
                        ownedPosts = listOf(OwnedPost(account.id, post)),
                        actions = emptySet(),
                    ),
                    onRefresh = {}, onLoadMore = {}, onSignIn = {},
                    onReaction = { _, choice -> clicked = choice.submissionValue },
                )
            }
        }
        compose.waitForIdle()

        compose.onNodeWithTag("reaction_chip_🎉", useUnmergedTree = true).performClick()
        assertNull(clicked)
    }

    @Test fun postBodyRendersKnownCustomEmojiAndFallsBackForUnknownTokens() {
        val blob = me.foxtails.palustris.domain.CustomEmoji(
            shortcode = "blob_cat",
            animatedUrl = me.foxtails.palustris.domain.ValidatedUrl.https("https://example.org/blob_cat.gif"),
            staticUrl = me.foxtails.palustris.domain.ValidatedUrl.https("https://example.org/blob_cat.png"),
            submissionValue = ":blob_cat:",
        )
        val post = Post(
            postId("inline-emoji"),
            account,
            "A post with :blob_cat: and :unknown_token: text",
            0,
            Audience.Public,
            emoji = mapOf("blob_cat" to blob),
        )
        show(post)

        compose.onNodeWithText("A post with :blob_cat: and :unknown_token: text").assertIsDisplayed()
        compose.onNodeWithText("unknown_token").assertDoesNotExist()
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

    @Test fun postsLongerThan350CharactersShowAnInlineFullPostAction() {
        val text = "x".repeat(351)
        show(Post(postId("long-body"), account, text, 0, Audience.Public))

        compose.onNodeWithText("x".repeat(350) + "…").assertIsDisplayed()
        compose.onNodeWithText(text).assertDoesNotExist()
        compose.onNodeWithContentDescription("View full post").assertIsDisplayed().performClick()
        compose.onNodeWithText("Post").assertIsDisplayed()
        compose.onNodeWithText(text).assertIsDisplayed()
    }

    @Test fun postsWith350OrFewerCharactersRemainUntruncated() {
        val text = "x".repeat(350)
        show(Post(postId("boundary-body"), account, text, 0, Audience.Public))

        compose.onNodeWithText(text).assertIsDisplayed()
        compose.onNodeWithContentDescription("View full post").assertDoesNotExist()
    }

    @Test fun linkAwareTruncationUsesTheShortLabelAndKeepsTheLinkBubbleIntact() {
        val url = "https://example.org/a-very-long-path"
        val text = "x".repeat(340) + " " + url + " tail"
        show(Post(postId("link-truncation"), account, text, 0, Audience.Public))

        compose.onNodeWithContentDescription("Link url.xyz").assertIsDisplayed()
        compose.onNodeWithText(url, substring = true).assertDoesNotExist()
        compose.onNodeWithContentDescription("View full post").assertIsDisplayed()
    }

    @Test fun markdownHashtagLinksUseHashtagBubblesInsteadOfLinkBubbles() {
        val text = "Meet at [#Zurich](<https://pixelfed.social/discover/tags/Zurich?src=hash>) today"
        show(Post(postId("markdown-hashtag"), account, text, 0, Audience.Public))

        compose.onNodeWithContentDescription("Hashtag #Zurich").assertIsDisplayed()
        compose.onNodeWithContentDescription("Link #Zurich").assertDoesNotExist()
    }

    @Test fun searchRowsUseTheSameLinkAwareTruncationAsHomeRows() {
        val url = "https://example.org/a-very-long-path"
        val text = "x".repeat(340) + " " + url + " tail"
        val post = Post(postId("search-link-truncation"), account, text, 0, Audience.Public)
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                SearchScreen(
                    accountSearch = AccountSearchState(
                        query = "#cats",
                        tagQuery = "cats",
                        posts = listOf(post),
                    ),
                    initialQuery = "#cats",
                )
            }
        }
        compose.waitForIdle()

        compose.onNodeWithContentDescription("Link url.xyz").assertIsDisplayed()
        compose.onNodeWithText(url, substring = true).assertDoesNotExist()
    }

    @Test fun searchResultsExposePostActionCallbacks() {
        val result = Post(postId("search-actions"), account, "Search actions", 0, Audience.Public)
        var replied = false
        var reshared = false
        var favourited = false
        var bookmarked = false
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                SearchScreen(
                    accountSearch = AccountSearchState(query = "#cats", tagQuery = "cats", posts = listOf(result)),
                    initialQuery = "#cats",
                    mediaOwner = account.id,
                    availableActions = setOf(
                        PostAction.Reply,
                        PostAction.Reshare,
                        PostAction.Favorite,
                        PostAction.Bookmark,
                    ),
                    onReply = { replied = it.post.id == result.id },
                    onReshare = { reshared = it.post.id == result.id },
                    onReact = { favourited = it.post.id == result.id },
                    onBookmark = { bookmarked = it.post.id == result.id },
                )
            }
        }
        compose.waitForIdle()

        compose.onNodeWithContentDescription("Reply").performClick()
        compose.onNodeWithContentDescription("Repost").performClick()
        compose.onNodeWithContentDescription("Favorite").performClick()
        compose.onNodeWithContentDescription("Bookmark").performClick()

        assertTrue(replied)
        assertTrue(reshared)
        assertTrue(favourited)
        assertTrue(bookmarked)
    }

    @Test fun searchReactionControlsRouteToSharedReactionCallbacks() {
        val result = Post(
            postId("search-reaction"),
            account,
            "Search reaction",
            0,
            Audience.Public,
            reactions = listOf(Reaction("👍", 1, selected = false)),
        )
        var selected: String? = null
        var bubbleTarget: OwnedPost? = null
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                SearchScreen(
                    accountSearch = AccountSearchState(query = "#cats", tagQuery = "cats", posts = listOf(result)),
                    initialQuery = "#cats",
                    mediaOwner = account.id,
                    availableActions = setOf(PostAction.React),
                    onReaction = { _, choice -> selected = choice.submissionValue },
                    onOpenReactionBubble = { ownedPost, _ -> bubbleTarget = ownedPost },
                )
            }
        }
        compose.waitForIdle()

        compose.onNodeWithTag("reaction_chip_👍", useUnmergedTree = true).performClick()
        compose.onNodeWithContentDescription("Favorite").performTouchInput { longClick() }

        assertEquals("👍", selected)
        assertEquals(result.id, bubbleTarget?.post?.id)
        assertEquals(account.id, bubbleTarget?.fetchedBy)
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

    @Test fun appHashtagBubbleSendsExactTagAndPrefillsSearch() {
        var searched = ""
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                PalustrisApp(
                    account = account,
                    feedState = FeedState(
                        posts = listOf(Post(postId("app-tag"), account, "Body #one #alongertag", 0, Audience.Public)),
                    ),
                    onSearchAccounts = { searched = it },
                )
            }
        }
        compose.waitForIdle()

        compose.onNodeWithContentDescription("2 hashtags: #one and #alongertag").performClick()
        val shortBounds = compose.onNodeWithTag("hashtag_bubble_#one", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val longBounds = compose.onNodeWithTag("hashtag_bubble_#alongertag", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        assertTrue(longBounds.width > shortBounds.width)
        assertEquals(longBounds.right, shortBounds.right, 1f)
        compose.onNodeWithContentDescription("Hashtag #alongertag").performClick()
        compose.waitForIdle()

        assertEquals("#alongertag", searched)
        compose.onNodeWithText("#alongertag", substring = false).assertIsDisplayed()
    }

    @Test fun longPressingHeartOpensCompactReactionBubbleAndEmojiSelectionUsesChoice() {
        val post = Post(postId("bubble-reaction"), account, "Reaction bubble", 0, Audience.Public)
        var selected: String? = null
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                PalustrisApp(
                    account = account,
                    feedState = FeedState(
                        posts = listOf(post),
                        ownedPosts = listOf(OwnedPost(account.id, post)),
                        actions = setOf(PostAction.React),
                    ),
                    emojiCapabilities = EmojiCapabilities(
                        reactionMutation = CapabilityStatus.Supported,
                    ),
                    onReaction = { _, choice -> selected = choice.submissionValue },
                )
            }
        }
        compose.waitForIdle()

        compose.onNodeWithContentDescription("Favorite").performTouchInput { longClick() }
        compose.onNodeWithTag("reaction_bubble_compact", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("emoji_picker_cell_👍", useUnmergedTree = true).performClick()

        assertEquals("👍", selected)
        compose.onNodeWithTag("reaction_bubble_compact", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test fun flickingReactionBubbleExpandsItsScrollableGrid() {
        val custom = (0..30).map { index ->
            me.foxtails.palustris.domain.CustomEmoji(
                shortcode = "custom$index",
                animatedUrl = null,
                staticUrl = null,
                submissionValue = ":custom$index:",
            )
        }
        val post = Post(postId("expanded-reaction"), account, "Reaction bubble", 0, Audience.Public)
        var selected: String? = null
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                PalustrisApp(
                    account = account,
                    feedState = FeedState(
                        posts = listOf(post),
                        ownedPosts = listOf(OwnedPost(account.id, post)),
                        actions = setOf(PostAction.React),
                    ),
                    emojiCapabilities = EmojiCapabilities(
                        catalog = CapabilityStatus.Supported,
                        reactionMutation = CapabilityStatus.Supported,
                    ),
                    emojiCatalogState = me.foxtails.palustris.ui.emoji.EmojiCatalogState(items = custom),
                    onReaction = { _, choice -> selected = choice.submissionValue },
                )
            }
        }
        compose.waitForIdle()

        compose.onNodeWithContentDescription("Favorite").performTouchInput { longClick() }
        compose.onNodeWithTag("emoji_picker_cell_👍", useUnmergedTree = true)
            .performTouchInput { swipeUp() }
        compose.onNodeWithTag("reaction_bubble_expanded", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("reaction_bubble_grid", useUnmergedTree = true).assert(hasScrollAction())
        assertNull(selected)
    }

    @Test fun downwardFlickOverCompactEmojiCellExpandsReactionBubble() {
        val post = Post(postId("expanded-reaction-down"), account, "Reaction bubble", 0, Audience.Public)
        var selected: String? = null
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                PalustrisApp(
                    account = account,
                    feedState = FeedState(
                        posts = listOf(post),
                        ownedPosts = listOf(OwnedPost(account.id, post)),
                        actions = setOf(PostAction.React),
                    ),
                    emojiCapabilities = EmojiCapabilities(
                        reactionMutation = CapabilityStatus.Supported,
                    ),
                    onReaction = { _, choice -> selected = choice.submissionValue },
                )
            }
        }
        compose.waitForIdle()

        compose.onNodeWithContentDescription("Favorite").performTouchInput { longClick() }
        compose.onNodeWithTag("emoji_picker_cell_👍", useUnmergedTree = true)
            .performTouchInput { swipeDown() }
        compose.onNodeWithTag("reaction_bubble_expanded", useUnmergedTree = true).assertIsDisplayed()
        assertNull(selected)
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
