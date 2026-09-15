package me.foxtails.palustris

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import me.foxtails.palustris.ui.*
import me.foxtails.palustris.domain.*
import me.foxtails.palustris.data.auth.AccountRef
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-420dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SignInScreenTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private fun capture(name: String) {
        compose.waitForIdle()
        compose.runOnIdle {
            val view = compose.activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            File("build/ui-screenshots/$name.png").apply { parentFile?.mkdirs() }.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
    @Test fun firstStartShowsSparseSetupAndServerEntryFillsTheField() {
        compose.waitUntil(5000) { compose.onAllNodesWithText("sign in").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("i'm new, what's this?").assertIsDisplayed()
        compose.onNodeWithText("sign in").performClick()
        compose.onNodeWithText("next").assertIsNotEnabled()
        compose.onNodeWithTag("setup_server_field").performTextInput("sharkey.world")
        compose.onNodeWithText("next").assertIsEnabled()
        compose.onNode(hasSetTextAction()).assertTextContains("sharkey.world")
        capture("sign-in")
    }
    @Test fun contentWarningsRequireExplicitReveal() {
        val account = Account(AccountId(Connection("https://example.org", Protocol.MISSKEY), "a"), "A person", "@person@example.org")
        val post = Post(EntityId("https://example.org", "p"), account, "Text hidden by a content warning", System.currentTimeMillis(), Audience.Public, contentWarning = "Spoilers")
        compose.activity.runOnUiThread { compose.activity.setContent {
            AppShellFixtures.app(account = account, home = AppShellFixtures.home(FeedState(posts = listOf(post))))
        } }
        compose.onNodeWithText("Spoilers").assertIsDisplayed()
        compose.onNodeWithText(post.text).assertDoesNotExist()
        compose.onNodeWithText("Show content").performClick()
        compose.onNodeWithText(post.text).assertIsDisplayed()
        capture("home-feed")
        compose.onNodeWithText("Hide content").performClick()
        compose.onNodeWithText(post.text).assertDoesNotExist()
    }

    @Test fun sensitiveMediaStartsConcealedUntilExplicitlyRevealed() {
        val account = Account(AccountId(Connection("https://example.org", Protocol.MISSKEY), "owner"), "Owner", "@owner@example.org")
        val post = Post(
            EntityId("https://example.org", "sensitive"),
            account,
            "A post with sensitive media",
            System.currentTimeMillis(),
            Audience.Public,
            attachments = listOf(Attachment("https://example.org/photo.jpg", "image/jpeg", "A photo", sensitive = true)),
        )
        compose.activity.runOnUiThread { compose.activity.setContent {
            AppShellFixtures.app(account = account, home = AppShellFixtures.home(FeedState(posts = listOf(post))))
        } }

        compose.onNodeWithText("Show sensitive media").assertIsDisplayed()
        compose.onNodeWithText("Open image").assertDoesNotExist()
        compose.onNodeWithText("Show sensitive media").performClick()
        compose.onNodeWithText("Open image").assertDoesNotExist()
        compose.onNodeWithContentDescription("A photo").assertExists()
    }

    @Test fun publishingKeepsDraftUntilSuccessCallback() {
        val account = Account(AccountId(Connection("https://example.org", Protocol.MISSKEY), "owner"), "Owner", "@owner@example.org")
        var complete: ((OwnedPost) -> Unit)? = null
        compose.activity.runOnUiThread { compose.activity.setContent {
            AppShellFixtures.app(
                account = account,
                home = AppShellFixtures.home(FeedState(canPublish = true)),
                draftsContract = AppShellFixtures.drafts(),
                composer = AppShellFixtures.composer(
                    FeedState(canPublish = true),
                    onPublish = { _, onSuccess -> complete = onSuccess },
                ),
            )
        } }

        compose.onNodeWithContentDescription("Compose post").performClick()
        compose.onNodeWithContentDescription("Post text").performTextInput("Keep this draft")
        compose.onNodeWithText("Publish").performClick()
        compose.onNodeWithContentDescription("Post text").assertTextContains("Keep this draft")

        compose.runOnIdle {
            complete?.invoke(
                OwnedPost(
                    account.id,
                    Post(EntityId(account.id.connection.origin, "created"), account, "", 0L, Audience.Public),
                ),
            )
        }
        compose.onNodeWithContentDescription("Post text").assertDoesNotExist()
    }

    @Test fun publishingIsDisabledUntilCapabilityAllowsIt() {
        val account = Account(AccountId(Connection("https://example.org", Protocol.MISSKEY), "owner"), "Owner", "@owner@example.org")
        compose.activity.runOnUiThread { compose.activity.setContent {
            AppShellFixtures.app(account = account, home = AppShellFixtures.home(FeedState()))
        } }

        compose.onNodeWithContentDescription("Compose post").performClick()
        compose.onNodeWithText("Publishing is disabled for this account.").assertIsDisplayed()
        compose.onNodeWithText("Publish").assertIsNotEnabled()
    }
    @Test fun feedActionsPreserveTheAccountThatFetchedThePost() {
        val fetchingAccount = Account(AccountId(Connection("https://example.org", Protocol.MISSKEY), "owner"), "Owner", "@owner@example.org")
        val author = Account(AccountId(Connection("https://example.org", Protocol.MISSKEY), "author"), "Author", "@author@example.org")
        val post = Post(EntityId("https://example.org", "post"), author, "Post", System.currentTimeMillis(), Audience.Public)
        val ownedPost = OwnedPost(fetchingAccount.id, post)
        var favoritedPost: OwnedPost? = null
        var resharedPost: OwnedPost? = null
        compose.activity.runOnUiThread { compose.activity.setContent {
            AppShellFixtures.app(
                account = fetchingAccount,
                home = AppShellFixtures.home(FeedState(posts = listOf(post), ownedPosts = listOf(ownedPost), actions = setOf(PostAction.Favorite, PostAction.Reshare))),
                postInteractions = AppShellFixtures.interactions(
                    FeedState(posts = listOf(post), ownedPosts = listOf(ownedPost), actions = setOf(PostAction.Favorite, PostAction.Reshare)),
                    onFavorite = { favoritedPost = it },
                    onRepost = { resharedPost = it },
                ),
            )
        } }

        compose.onNodeWithContentDescription("Favorite").performClick()
        compose.onNodeWithContentDescription("Repost").performClick()
        compose.onNodeWithText("repost?").performClick()
        assertEquals(fetchingAccount.id, favoritedPost?.fetchedBy)
        assertEquals(fetchingAccount.id, resharedPost?.fetchedBy)
    }

    @Test fun unsupportedActionsAreDisabledAndCannotInvokeFallbackHandlers() {
        val account = Account(AccountId(Connection("https://example.org", Protocol.MISSKEY), "owner"), "Owner", "@owner@example.org")
        val post = Post(EntityId("https://example.org", "post"), account, "Post", System.currentTimeMillis(), Audience.Public)
        val ownedPost = OwnedPost(account.id, post)
        var bookmarked = false
        compose.activity.runOnUiThread { compose.activity.setContent {
            AppShellFixtures.app(
                account = account,
                home = AppShellFixtures.home(FeedState(posts = listOf(post), ownedPosts = listOf(ownedPost))),
                postInteractions = AppShellFixtures.interactions(
                    FeedState(posts = listOf(post), ownedPosts = listOf(ownedPost)),
                    onBookmark = { bookmarked = true },
                ),
            )
        } }

        compose.onNodeWithContentDescription("Reply").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Repost").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Favorite").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Bookmark").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Share").assertIsEnabled()
        assertEquals(false, bookmarked)
    }

    @Test fun switchingAccountsRebindsDisplayedFeedTimelineAndActionOwnership() {
        val first = Account(AccountId(Connection("https://example.org", Protocol.MISSKEY), "first"), "First", "@first@example.org")
        val second = Account(AccountId(Connection("https://other.example", Protocol.MISSKEY), "second"), "Second", "@second@other.example")
        val firstPost = Post(EntityId("https://example.org", "first-post"), first, "First post", 0, Audience.Public)
        val secondPost = Post(EntityId("https://other.example", "second-post"), second, "Second post", 0, Audience.Public)
        val firstOwnedPost = OwnedPost(first.id, firstPost)
        val secondOwnedPost = OwnedPost(second.id, secondPost)
        val firstFeed = FeedState(
            posts = listOf(firstPost),
            ownedPosts = listOf(firstOwnedPost),
            timeline = Timeline.Home,
            timelines = setOf(Timeline.Home, Timeline.Local),
            canPublish = true,
            actions = setOf(PostAction.Favorite),
        )
        val secondFeed = FeedState(
            posts = listOf(secondPost),
            ownedPosts = listOf(secondOwnedPost),
            timeline = Timeline.Federated,
            timelines = setOf(Timeline.Home, Timeline.Federated),
            actions = setOf(PostAction.Reshare),
        )
        val currentAccount = mutableStateOf(first)
        val currentFeed = mutableStateOf(firstFeed)
        var actionPost: OwnedPost? = null
        compose.activity.runOnUiThread { compose.activity.setContent {
            AppShellFixtures.app(
                account = currentAccount.value,
                home = AppShellFixtures.home(currentFeed.value),
                postInteractions = AppShellFixtures.interactions(currentFeed.value, onRepost = { actionPost = it }),
                accountSwitcher = AppShellFixtures.switcher(
                    accounts = listOf(
                        AccountRef(first.id, first.handle, null, first.displayName),
                        AccountRef(second.id, second.handle, null, second.displayName),
                    ),
                    onSwitch = {
                        currentAccount.value = second
                        currentFeed.value = secondFeed
                    },
                ),
            )
        } }

        compose.onNodeWithText("First post").assertIsDisplayed()
        compose.onNodeWithContentDescription("Profile").performClick()
        compose.onNodeWithContentDescription("Profile").performTouchInput { longClick() }
        compose.onNodeWithText("Second").performClick()
        compose.onNodeWithContentDescription("Home").performClick()
        compose.onNodeWithText("Second post").assertIsDisplayed()
        compose.waitForIdle()
        compose.onNodeWithTag("home_timeline_tab_Federated").assertIsDisplayed().performClick()
        compose.onNodeWithContentDescription("Repost").performClick()
        compose.onNodeWithText("repost?").performClick()
        assertEquals(second.id, actionPost?.fetchedBy)
    }

    @Test fun accountsSheetListsAccountsAndStartsAddAccountFlow() {
        val current = Account(AccountId(Connection("https://example.org", Protocol.MISSKEY), "current"), "Current", "@current@example.org")
        val other = Account(AccountId(Connection("https://other.example", Protocol.MISSKEY), "other"), "Other", "@other@other.example")
        var addRequested = false
        var switchedTo: AccountId? = null
        compose.activity.runOnUiThread { compose.activity.setContent {
            AppShellFixtures.app(
                account = current,
                accountSwitcher = AppShellFixtures.switcher(
                    accounts = listOf(AccountRef(current.id, current.handle, null, current.displayName), AccountRef(other.id, other.handle, null, other.displayName)),
                    onAdd = { addRequested = true },
                    onSwitch = { switchedTo = it },
                ),
            )
        } }

        compose.onNodeWithContentDescription("Profile").performClick()
        compose.onNodeWithContentDescription("Profile").performTouchInput { longClick() }
        compose.onNodeWithText("Other").performClick()
        assertEquals(other.id, switchedTo)
        compose.onNodeWithContentDescription("Profile").performTouchInput { longClick() }
        compose.onNodeWithText("Add account").performClick()
        assertEquals(true, addRequested)
    }

    @Test fun reactionCountsRemainVisibleWhenSubmissionIsUnavailable() {
        val account = Account(AccountId(Connection("https://example.org", Protocol.MISSKEY), "owner"), "Owner", "@owner@example.org")
        val post = Post(
            EntityId("https://example.org", "post"), account, "Post", System.currentTimeMillis(), Audience.Public,
            reactions = listOf(Reaction("🎉", 3, false)),
        )
        val ownedPost = OwnedPost(account.id, post)
         var chosenReaction: String? = null
        compose.activity.runOnUiThread { compose.activity.setContent {
            AppShellFixtures.app(
                account = account,
                home = AppShellFixtures.home(FeedState(posts = listOf(post), ownedPosts = listOf(ownedPost), actions = setOf(PostAction.React))),
                postInteractions = AppShellFixtures.interactions(
                    FeedState(posts = listOf(post), ownedPosts = listOf(ownedPost), actions = setOf(PostAction.React)),
                    onReact = { _, emoji -> chosenReaction = emoji.submissionValue },
                ),
                emojiPresentation = AppShellFixtures.emoji(
                    capabilities = EmojiCapabilities(
                        reactionListing = CapabilityStatus.Supported,
                        reactionMutation = CapabilityStatus.Supported,
                        selectionMode = ReactionSelectionMode.Single,
                    ),
                ),
            )
        } }

        compose.onNodeWithText("🎉").assertIsDisplayed()
        compose.onNodeWithTag("reaction_count_🎉", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithContentDescription("Favorite").performTouchInput { longClick() }
        compose.onNodeWithTag("reaction_bubble_compact", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("emoji_picker_cell_🎉", useUnmergedTree = true).performClick()
        assertEquals("🎉", chosenReaction)
    }

    @Test fun introductionKeepsItsPlaceholderAction() {
        compose.activity.runOnUiThread {
            compose.activity.setContent { PalustrisTheme { SetupIntroductionPreview() } }
        }

        compose.onNodeWithText("welcome to").assertIsDisplayed()
        compose.onNodeWithText("the fediverse").assertIsDisplayed()
        compose.onNodeWithText("get started").assertIsDisplayed()
    }

    @Test fun serverFieldRejectsInternalWhitespaceWithoutChangingItsValue() {
        compose.onNodeWithText("sign in").performClick()
        compose.onNodeWithTag("setup_server_field").performTextInput("example .org")

        compose.onNodeWithText("A server address cannot contain spaces.").assertIsDisplayed()
    }

    @Test fun longPressAndUpwardDragOpensOnlyTheExpandedReactionPicker() {
        val account = Account(AccountId(Connection("https://example.org", Protocol.MISSKEY), "owner"), "Owner", "@owner@example.org")
        val post = Post(EntityId("https://example.org", "post"), account, "Post", System.currentTimeMillis(), Audience.Public)
        val ownedPost = OwnedPost(account.id, post)
        compose.activity.runOnUiThread { compose.activity.setContent {
            AppShellFixtures.app(
                account = account,
                home = AppShellFixtures.home(FeedState(posts = listOf(post), ownedPosts = listOf(ownedPost), actions = setOf(PostAction.React))),
                postInteractions = AppShellFixtures.interactions(FeedState(posts = listOf(post), ownedPosts = listOf(ownedPost), actions = setOf(PostAction.React))),
                emojiPresentation = AppShellFixtures.emoji(
                    capabilities = EmojiCapabilities(
                        reactionListing = CapabilityStatus.Supported,
                        reactionMutation = CapabilityStatus.Supported,
                        selectionMode = ReactionSelectionMode.Single,
                    ),
                ),
            )
        } }

        compose.onNodeWithContentDescription("Favorite").performTouchInput {
            down(center)
            advanceEventTime(600)
            moveBy(Offset(0f, -150f))
            up()
        }

        compose.onNodeWithTag("reaction_bubble_expanded", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("reaction_bubble_compact", useUnmergedTree = true).assertDoesNotExist()
    }
}
