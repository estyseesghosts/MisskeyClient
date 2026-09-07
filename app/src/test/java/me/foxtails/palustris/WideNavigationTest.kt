package me.foxtails.palustris

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.performClick
import me.foxtails.palustris.ui.PalustrisApp
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w800dp-h1000dp-420dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WideNavigationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Before fun showShell() {
        compose.activity.runOnUiThread { compose.activity.setContent { PalustrisApp() } }
    }

    @After fun clearDraft() {
        compose.activity.getSharedPreferences("local_draft", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test fun wideLightLayoutShowsNavigationRail() {
        assertRailAndComposer()
    }

    @Test
    @Config(qualifiers = "w800dp-h1000dp-night-420dpi")
    fun wideDarkLayoutShowsNavigationRail() {
        assertRailAndComposer()
    }

    private fun assertRailAndComposer() {
        compose.waitForIdle()
        compose.runOnIdle {
            val view = compose.activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            File("build/ui-screenshots/wide-${if (view.context.resources.configuration.isNightModeActive) "dark" else "light"}.png")
                .apply { parentFile?.mkdirs() }
                .outputStream()
                .use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        compose.onAllNodesWithText("Home").onLast().assertIsDisplayed()
        compose.onNodeWithText("Search").assertIsDisplayed()
        compose.onNodeWithText("Notifications").assertIsDisplayed()
        compose.onNodeWithText("Profile").assertIsDisplayed()
        compose.onNodeWithContentDescription("Compose post").performClick()
        compose.onNodeWithText("New post").assertIsDisplayed()
    }
}
