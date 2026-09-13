package me.foxtails.palustris.ui.components

import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import me.foxtails.palustris.MainActivity
import me.foxtails.palustris.ui.PalustrisTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-420dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PillActionTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun actionUsesAccessibleButtonSemanticsInLightAndDarkThemes() {
        var clicked = false
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                PalustrisTheme {
                    PillAction("repost?", onClick = { clicked = true })
                }
            }
        }
        compose.onNodeWithContentDescription("repost?").assertIsDisplayed().performClick()
        compose.runOnIdle { assertTrue(clicked) }
    }
}
