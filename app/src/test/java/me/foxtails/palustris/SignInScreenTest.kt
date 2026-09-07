package me.foxtails.palustris

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
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
    @Test fun firstStartShowsSignInAndInstanceButtonsFillTheField() {
        compose.waitUntil(5000) { compose.onAllNodesWithText("Welcome!").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Next").assertIsNotEnabled()
        compose.onNodeWithText("sharkey.world").performScrollTo().performClick()
        compose.onNodeWithText("Next").assertIsEnabled()
        compose.onNode(hasSetTextAction()).assertTextContains("sharkey.world")
        capture("sign-in")
    }
    @Test fun contentWarningsRequireExplicitReveal() {
        val account = Account(AccountId(Connection("https://example.org", Protocol.MISSKEY), "a"), "A person", "@person@example.org")
        val post = Post(EntityId("https://example.org", "p"), account, "Text hidden by a content warning", System.currentTimeMillis(), Audience.Public, contentWarning = "Spoilers")
        compose.activity.runOnUiThread { compose.activity.setContent {
            PalustrisApp(account = account, feedState = FeedState(posts = listOf(post)))
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
            PalustrisApp(account = account, feedState = FeedState(posts = listOf(post)))
        } }

        compose.onNodeWithText("Show sensitive media").assertIsDisplayed()
        compose.onNodeWithText("Open image").assertDoesNotExist()
        compose.onNodeWithText("Show sensitive media").performClick()
        compose.onNodeWithText("Open image").assertIsDisplayed()
    }

    @Test fun publishingKeepsDraftUntilSuccessCallback() {
        val account = Account(AccountId(Connection("https://example.org", Protocol.MISSKEY), "owner"), "Owner", "@owner@example.org")
        var complete: (() -> Unit)? = null
        compose.activity.runOnUiThread { compose.activity.setContent {
            PalustrisApp(
                account = account,
                feedState = FeedState(canPublish = true),
                onPublish = { _, onSuccess -> complete = onSuccess },
            )
        } }

        compose.onNodeWithContentDescription("Compose post").performClick()
        compose.onNodeWithContentDescription("Post text").performTextInput("Keep this draft")
        compose.onNodeWithText("Publish").performClick()
        compose.onNodeWithContentDescription("Post text").assertTextContains("Keep this draft")

        compose.runOnIdle { complete?.invoke() }
        compose.onNodeWithContentDescription("Post text").assertDoesNotExist()
    }

    @Test fun publishingIsDisabledUntilCapabilityAllowsIt() {
        val account = Account(AccountId(Connection("https://example.org", Protocol.MISSKEY), "owner"), "Owner", "@owner@example.org")
        compose.activity.runOnUiThread { compose.activity.setContent {
            PalustrisApp(account = account, feedState = FeedState())
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
            PalustrisApp(
                account = fetchingAccount,
                feedState = FeedState(posts = listOf(post), ownedPosts = listOf(ownedPost), actions = setOf(PostAction.Favorite, PostAction.Reshare)),
                onReact = { favoritedPost = it },
                onReshare = { resharedPost = it },
            )
        } }

        compose.onNodeWithContentDescription("Favorite").performClick()
        compose.onNodeWithContentDescription("Repost").performClick()
        assertEquals(fetchingAccount.id, favoritedPost?.fetchedBy)
        assertEquals(fetchingAccount.id, resharedPost?.fetchedBy)
    }

    @Test fun unsupportedActionsAreDisabledAndCannotInvokeFallbackHandlers() {
        val account = Account(AccountId(Connection("https://example.org", Protocol.MISSKEY), "owner"), "Owner", "@owner@example.org")
        val post = Post(EntityId("https://example.org", "post"), account, "Post", System.currentTimeMillis(), Audience.Public)
        val ownedPost = OwnedPost(account.id, post)
        var replied = false
        var bookmarked = false
        compose.activity.runOnUiThread { compose.activity.setContent {
            PalustrisApp(
                account = account,
                feedState = FeedState(posts = listOf(post), ownedPosts = listOf(ownedPost)),
                onReply = { replied = true },
                onBookmark = { bookmarked = true },
            )
        } }

        compose.onNodeWithContentDescription("Reply").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Repost").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Favorite").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Bookmark").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Share").assertIsEnabled()
        assertEquals(false, replied)
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
            PalustrisApp(
                account = currentAccount.value,
                feedState = currentFeed.value,
                accounts = listOf(
                    AccountRef(first.id, first.handle, null, first.displayName),
                    AccountRef(second.id, second.handle, null, second.displayName),
                ),
                ownedPosts = currentFeed.value.ownedPosts,
                onSwitchAccount = {
                    currentAccount.value = second
                    currentFeed.value = secondFeed
                },
                onReshare = { actionPost = it },
            )
        } }

        compose.onNodeWithText("First post").assertIsDisplayed()
        compose.onNodeWithContentDescription("Profile").performClick()
        compose.onNodeWithContentDescription("Accounts").performClick()
        compose.onNodeWithText("Second").performClick()
        compose.onNodeWithContentDescription("Home").performClick()
        compose.onNodeWithText("Second post").assertIsDisplayed()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Choose timeline").performClick()
        compose.waitForIdle()
        compose.onAllNodesWithText("Federated").onLast().assertIsDisplayed()
        compose.onNodeWithText("Local").assertDoesNotExist()
        compose.onAllNodesWithText("Federated").onLast().performClick()
        compose.onNodeWithContentDescription("Repost").performClick()
        assertEquals(second.id, actionPost?.fetchedBy)
    }

    @Test fun accountsSheetListsAccountsAndStartsAddAccountFlow() {
        val current = Account(AccountId(Connection("https://example.org", Protocol.MISSKEY), "current"), "Current", "@current@example.org")
        val other = Account(AccountId(Connection("https://other.example", Protocol.MISSKEY), "other"), "Other", "@other@other.example")
        var addRequested = false
        var switchedTo: AccountId? = null
        compose.activity.runOnUiThread { compose.activity.setContent {
            PalustrisApp(
                account = current,
                accounts = listOf(AccountRef(current.id, current.handle, null, current.displayName), AccountRef(other.id, other.handle, null, other.displayName)),
                onAddAccount = { addRequested = true },
                onSwitchAccount = { switchedTo = it },
            )
        } }

        compose.onNodeWithContentDescription("Profile").performClick()
        compose.onNodeWithContentDescription("Accounts").performClick()
        compose.onNodeWithText("Other").performClick()
        assertEquals(other.id, switchedTo)
        compose.onNodeWithContentDescription("Accounts").performClick()
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
            PalustrisApp(
                account = account,
                feedState = FeedState(posts = listOf(post), ownedPosts = listOf(ownedPost), actions = setOf(PostAction.React)),
                onReaction = { _, emoji -> chosenReaction = emoji },
            )
        } }

        compose.onNodeWithText("🎉").assertIsDisplayed()
        compose.onNodeWithContentDescription("Favorite").performTouchInput { longClick() }
        compose.onNodeWithText("Add reaction").assertDoesNotExist()
        compose.onAllNodesWithText("🎉").onLast().performClick()
        assertEquals(null, chosenReaction)
    }
}
