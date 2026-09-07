package me.foxtails.palustris

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.activity.compose.setContent
import org.junit.Before
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import me.foxtails.palustris.data.auth.AccountRef
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.Protocol
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

    private fun assertPressKeepsCornersUnchanged(contentDescription: String) {
        val before = screenBitmap()
        val beforeCorners = cornerPixels(contentDescription, before)
        compose.onNodeWithContentDescription(contentDescription).performTouchInput { down(center) }
        val pressed = screenBitmap()
        compose.onNodeWithContentDescription(contentDescription).performTouchInput { up() }
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
        compose.onNodeWithText("Mentions").performClick()
        compose.onNodeWithText("No mentions yet").assertIsDisplayed()
        screenshot("notifications")
        compose.onNodeWithContentDescription("Search").performClick()
        compose.onNodeWithText("photography").assertIsDisplayed()
        compose.onNodeWithText("Hashtags").assertIsSelected()
        compose.onNodeWithContentDescription("Profile").performClick()
        compose.onNodeWithText("No account selected").assertIsDisplayed()
        screenshot("profile")
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
