package me.foxtails.palustris

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import me.foxtails.palustris.ui.*
import me.foxtails.palustris.domain.*
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
        val account = Account(EntityId("https://example.org", "a"), "A person", "@person@example.org")
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
}
