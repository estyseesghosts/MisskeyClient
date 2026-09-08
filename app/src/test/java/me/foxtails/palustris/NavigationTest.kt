package me.foxtails.palustris

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.ViewGroup
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.activity.compose.setContent
import org.junit.Before
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import me.foxtails.palustris.data.auth.AccountRef
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.Audience
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.EntityId
import me.foxtails.palustris.domain.Protocol
import me.foxtails.palustris.domain.Post
import me.foxtails.palustris.ui.FeedState
import me.foxtails.palustris.ui.PalustrisApp
import org.junit.After
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
class NavigationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Before fun previewShell() { compose.activity.runOnUiThread { compose.activity.setContent { PalustrisApp() } } }

    @After fun clearDraft() {
        compose.activity.getSharedPreferences("local_draft", Context.MODE_PRIVATE).edit().clear().commit()
    }

    private fun screenshot(name: String) {
        compose.waitForIdle()
        val file = File("build/ui-screenshots/$name.png")
        file.parentFile?.mkdirs()
        compose.runOnIdle {
            val view = compose.activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    private fun screenBitmap(): Bitmap {
        lateinit var bitmap: Bitmap
        compose.runOnIdle {
            val view = compose.activity.window.decorView
            bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
        }
        return bitmap
    }

    private fun cornerPixels(contentDescription: String, bitmap: Bitmap): List<Int> {
        val bounds = compose.onNodeWithContentDescription(contentDescription).fetchSemanticsNode().boundsInRoot
        val left = bounds.left.toInt()
        val top = bounds.top.toInt()
        val right = bounds.right.toInt() - 1
        val bottom = bounds.bottom.toInt() - 1
        val inset = 4
        return listOf(
            bitmap.getPixel(left + inset, top + inset),
            bitmap.getPixel(right - inset, top + inset),
            bitmap.getPixel(left + inset, bottom - inset),
            bitmap.getPixel(right - inset, bottom - inset),
        )
    }

    private fun pressSample(contentDescription: String, bitmap: Bitmap): Int {
        val bounds = compose.onNodeWithContentDescription(contentDescription).fetchSemanticsNode().boundsInRoot
        val x = bounds.center.x.toInt()
        val y = bounds.top.toInt() + 8
        return bitmap.getPixel(x, y)
    }

    private fun bounds(contentDescription: String) =
        compose.onNodeWithContentDescription(contentDescription).fetchSemanticsNode().boundsInRoot

    private fun assertPressKeepsCornersUnchanged(contentDescription: String) {
        val before = screenBitmap()
        val beforeCorners = cornerPixels(contentDescription, before)
        compose.onNodeWithContentDescription(contentDescription).performTouchInput { down(center) }
        val pressed = screenBitmap()
        compose.onNodeWithContentDescription(contentDescription).performTouchInput { cancel() }
        assertNotEquals("$contentDescription press did not render an indication", pressSample(contentDescription, before), pressSample(contentDescription, pressed))
        assertEquals("$contentDescription press changed a rounded corner", beforeCorners, cornerPixels(contentDescription, pressed))
    }

    @Test fun navigationRetainsSearchAndSelectedTimeline() {
        screenshot("home")
        compose.onAllNodesWithContentDescription("Choose timeline").onFirst().performClick()
        compose.onNodeWithText("Local").performClick()
        compose.onNodeWithText("Local posts will appear here when an account is connected.").assertIsDisplayed()
        compose.onNodeWithContentDescription("Search").performClick()
        compose.onNodeWithText("Hashtags").performClick()
        screenshot("search")
        compose.onNode(hasSetTextAction()).performTextInput("photography")
        compose.onNodeWithContentDescription("Notifications").performClick()
        compose.onNodeWithText("All caught up").assertIsDisplayed()
        screenshot("notifications")
        compose.onNodeWithContentDescription("Search").performClick()
        compose.onNodeWithText("photography").assertIsDisplayed()
        compose.onNodeWithText("Hashtags").assertIsSelected()
        compose.onNodeWithContentDescription("Profile").performClick()
        compose.onNodeWithText("No account selected").assertIsDisplayed()
        screenshot("profile")
    }

    @Test fun compactNotificationsDockSitsAboveNavigation() {
        compose.onNodeWithContentDescription("Notifications").performClick()
        compose.waitForIdle()

        val chips = bounds("Notification filters; swipe horizontally for more")
        val action = bounds("Direct messages")
        val navigation = bounds("Home")
        val content = compose.onNodeWithText("All caught up").fetchSemanticsNode().boundsInRoot
        val density = compose.activity.resources.displayMetrics.density

        assertTrue("notification chips should be above the contextual action", chips.bottom < action.top)
        assertTrue("notification chips should be above the navigation pill", chips.bottom < navigation.top)
        assertTrue("notification chips should keep compact side margins", chips.left / density >= 16f)
        assertTrue("notification chips should keep compact side margins", chips.right / density <= 411f - 16f)
        assertTrue("notification placeholder should remain above the dock", content.bottom <= chips.top)
        compose.onNodeWithContentDescription("Notification filters; swipe horizontally for more").assert(hasScrollAction())
    }

    @Test fun compactHomeSelectorTrailsNavigationAndLeavesNoDestinationSlot() {
        compose.waitForIdle()
        val selector = bounds("Choose timeline")
        val action = bounds("Compose post")
        val homeDestinationBefore = bounds("Home")
        val density = compose.activity.resources.displayMetrics.density

        assertEquals(168f, selector.width / density, 1f)
        assertEquals(60f, selector.height / density, 1f)
        assertTrue("selector should be below the content top", selector.top > 96f * density)
        assertTrue("selector should be above the navigation action", selector.bottom < action.top)
        assertEquals("selector should trail the navigation assembly", action.right, selector.right, density)

        compose.onNodeWithContentDescription("Search").performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Choose timeline").assertDoesNotExist()
        assertEquals(homeDestinationBefore.left, bounds("Home").left, density)
        assertEquals(homeDestinationBefore.top, bounds("Home").top, density)
        assertEquals(homeDestinationBefore.right, bounds("Home").right, density)
        assertEquals(homeDestinationBefore.bottom, bounds("Home").bottom, density)
    }

    @Test fun compactSearchDockSitsAboveNavigation() {
        compose.onNodeWithContentDescription("Search").performClick()
        compose.waitForIdle()

        val field = bounds("Search field")
        val chips = bounds("Search categories; swipe horizontally for more")
        val action = bounds("Alternate search")
        assertTrue("search field should be above navigation", field.bottom < action.top)
        assertTrue("search chips should be above the search field", chips.bottom <= field.top)
        val density = compose.activity.resources.displayMetrics.density
        assertTrue("search field should keep the compact navigation side margins", field.left / density >= 16f)
        assertTrue("search field should keep the compact navigation side margins", field.right / density <= 411f - 16f)
    }

    @Test fun compactProfileDockSitsAboveNavigationAndResetsForAnotherProfile() {
        val connection = Connection("https://example.org", Protocol.MASTODON)
        val alice = Account(AccountId(connection, "alice"), "Alice Profile", "@alice@example.org")
        val bob = Account(AccountId(connection, "bob"), "Bob Profile", "@bob@example.org", biography = "Bob's biography")
        val post = Post(EntityId("https://example.org", "bob-post"), bob, "Bob's post", 0, Audience.Public)
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                PalustrisApp(account = alice, feedState = FeedState(posts = listOf(post)))
            }
        }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Profile").performClick()
        compose.waitForIdle()

        val categories = bounds("Profile categories; swipe horizontally for more")
        val action = bounds("Edit profile")
        val density = compose.activity.resources.displayMetrics.density
        assertTrue("profile categories should be above navigation", categories.bottom < action.top)
        assertTrue("profile categories should keep compact side margins", categories.left / density >= 16f)
        assertTrue("profile categories should keep compact side margins", categories.right / density <= 411f - 16f)
        compose.onNodeWithContentDescription("Profile categories; swipe horizontally for more").assert(hasScrollAction())
        compose.onNodeWithText("Posts").assertIsSelected()
        compose.onNodeWithText("Media").performClick()
        compose.onNodeWithText("Media").assertIsSelected()

        compose.onNodeWithContentDescription("Home").performClick()
        compose.onNodeWithContentDescription("Profile").performClick()
        compose.onNodeWithText("Media").assertIsSelected()

        compose.onNodeWithContentDescription("Home").performClick()
        compose.onNodeWithText("Bob Profile").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Bob's biography").assertIsDisplayed()
        compose.onNodeWithText("Posts").assertIsSelected()
        compose.onNodeWithText("Media").assertIsNotSelected()
    }

    @Test fun searchDockNeverCrossesNavigationDuringKeyboardDismissal() {
        compose.onNodeWithContentDescription("Search").performClick()
        val density = compose.activity.resources.displayMetrics.density
        val systemBottom = (24 * density).toInt()
        var previousBottom = 0f
        for (keyboardDp in listOf(360, 300, 200, 120, 100, 80, 40, 0)) {
            compose.runOnIdle {
                val content = compose.activity.findViewById<ViewGroup>(android.R.id.content)
                val insets = WindowInsetsCompat.Builder()
                    .setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.of(0, 0, 0, systemBottom))
                    .setInsetsIgnoringVisibility(WindowInsetsCompat.Type.navigationBars(), Insets.of(0, 0, 0, systemBottom))
                    .setInsets(WindowInsetsCompat.Type.ime(), Insets.of(0, 0, 0, (keyboardDp * density).toInt()))
                    .setVisible(WindowInsetsCompat.Type.ime(), keyboardDp > 0)
                    .build()
                ViewCompat.dispatchApplyWindowInsets(content.getChildAt(0), insets)
            }
            compose.waitForIdle()
            val field = bounds("Search field")
            val navigation = bounds("Alternate search")
            assertTrue("field crossed navigation at IME height $keyboardDp", field.bottom < navigation.top)
            assertTrue("field bounced upward at IME height $keyboardDp", field.bottom >= previousBottom)
            if (keyboardDp == 360) {
                assertTrue("test must actually move the field above the keyboard", navigation.top - field.bottom > 150 * density)
            }
            previousBottom = field.bottom
        }
    }

    @Test fun scrollingHidesAndRestoresCompactControlsAndLeavesFinalPostReachable() {
        val account = Account(
            AccountId(Connection("https://example.org", Protocol.MASTODON), "scrolling"),
            "Scrolling account",
            "@scrolling@example.org",
        )
        val posts = (0..14).map { index ->
            Post(
                EntityId("https://example.org", "scroll-$index"),
                account,
                if (index == 14) "Final post" else "Post $index with enough content to make the feed scroll.",
                0,
                Audience.Public,
            )
        }
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                PalustrisApp(account = account, feedState = FeedState(posts = posts))
            }
        }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Choose timeline").assertIsDisplayed()
        compose.onNodeWithContentDescription("Compose post").assertIsDisplayed()

        compose.onNode(hasScrollAction()).performTouchInput { swipeUp() }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Choose timeline").assertDoesNotExist()
        compose.onNodeWithContentDescription("Compose post").assertDoesNotExist()

        compose.onNode(hasScrollAction()).performTouchInput { swipeDown() }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Choose timeline").assertIsDisplayed()
        compose.onNodeWithContentDescription("Compose post").assertIsDisplayed()

        repeat(12) {
            compose.onNode(hasScrollAction()).performTouchInput { swipeUp() }
        }
        compose.waitForIdle()
        compose.onNodeWithText("Final post").assertIsDisplayed()
    }

    @Test fun homeAndSelectedSearchIndicationsStayRounded() {
        assertPressKeepsCornersUnchanged("Choose timeline")

        compose.onNodeWithContentDescription("Search").performClick()
        compose.onNodeWithContentDescription("Search").assertIsSelected()
        assertPressKeepsCornersUnchanged("Search")
    }

    @Test fun draftsSurviveActivityRecreationAndCanBeDeleted() {
        compose.onNodeWithContentDescription("Compose post").performClick()
        compose.onNodeWithContentDescription("Post text").performTextInput("A draft stored only on this device.")
        screenshot("compose")
        compose.onNodeWithText("Save draft").performClick()
        compose.activityRule.scenario.recreate()
        compose.activity.runOnUiThread { compose.activity.setContent { PalustrisApp() } }
        compose.onNodeWithContentDescription("Profile").performClick()
        compose.onNodeWithContentDescription("Drafts").performClick()
        compose.onNodeWithText("A draft stored only on this device.").assertIsDisplayed()
        compose.onNodeWithText("Delete draft").performClick()
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithText("A draft stored only on this device.").assertIsDisplayed()
        compose.onNodeWithText("Delete draft").performClick()
        compose.onNodeWithText("Delete", substring = false).performClick()
        compose.onNodeWithText("No drafts yet").assertIsDisplayed()
    }

    @Test fun closingComposerAutosavesUnsavedText() {
        compose.onNodeWithContentDescription("Compose post").performClick()
        compose.onNodeWithContentDescription("Post text").performTextInput("Unsaved")
        compose.onNodeWithContentDescription("Close composer").performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Profile").performClick()
        compose.onNodeWithContentDescription("Drafts").performClick()
        compose.onNodeWithText("Unsaved").assertIsDisplayed()
    }

    @Test fun contextualActionsFollowSelectedDestination() {
        compose.onNodeWithContentDescription("Compose post").assertIsEnabled()
        compose.onNodeWithContentDescription("Edit profile").assertDoesNotExist()

        compose.onNodeWithContentDescription("Search").performClick()
        compose.onNodeWithContentDescription("Alternate search").assertIsEnabled().performClick()
        compose.onNodeWithText("Alternate search").assertIsDisplayed()

        compose.onNodeWithContentDescription("Notifications").performClick()
        compose.onNodeWithContentDescription("Direct messages").assertIsEnabled().performClick()
        compose.onNodeWithText("Direct messages coming soon").assertIsDisplayed()
        compose.onAllNodesWithContentDescription("Notifications").onLast().assertIsEnabled().performClick()
        compose.onNodeWithText("All caught up").assertIsDisplayed()

        compose.onNodeWithContentDescription("Profile").performClick()
        compose.onNodeWithContentDescription("Edit profile").assertIsEnabled().performClick()
        compose.onNodeWithContentDescription("Profile").assertIsSelected()
    }

    @Test fun profileAvatarLongPressOpensExistingAccountSwitcher() {
        val current = Account(
            AccountId(Connection("https://example.org", Protocol.MASTODON), "current"),
            "Current account",
            "@current@example.org",
            "https://example.org/current.png",
        )
        val other = Account(
            AccountId(Connection("https://other.example", Protocol.MISSKEY), "other"),
            "Other account",
            "@other@other.example",
        )
        var switchedTo: AccountId? = null
        compose.activity.runOnUiThread { compose.activity.setContent {
            PalustrisApp(
                account = current,
                accounts = listOf(
                    AccountRef(current.id, current.handle, current.avatarUrl, current.displayName),
                    AccountRef(other.id, other.handle, other.avatarUrl, other.displayName),
                ),
                onSwitchAccount = { switchedTo = it },
            )
        } }

        compose.onNodeWithContentDescription("Profile").performClick()
        compose.onNodeWithContentDescription("Profile").assertIsSelected()
        compose.onNodeWithText("Other account").assertDoesNotExist()

        compose.onNodeWithContentDescription("Profile").performTouchInput { longClick() }
        compose.onNodeWithText("Other account").assertIsDisplayed()
        compose.onNodeWithText("@other@other.example").assertIsDisplayed()
        compose.onNodeWithText("Other account").performClick()
        assertEquals(other.id, switchedTo)
        compose.onNodeWithContentDescription("Profile").assertIsSelected()
    }
}
