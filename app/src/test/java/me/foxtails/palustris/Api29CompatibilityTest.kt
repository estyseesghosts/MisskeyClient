package me.foxtails.palustris

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import me.foxtails.palustris.ui.PalustrisTheme
import me.foxtails.palustris.ui.motion.palustrisMotionScheme
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], qualifiers = "w411dp-h891dp-notnight-420dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class Api29CompatibilityTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun lightThemeUsesAStaticColorScheme() {
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                PalustrisTheme {
                    Text("API 29 light theme")
                }
            }
        }

        compose.onNodeWithText("API 29 light theme").assertIsDisplayed()
    }

    @Test
    @Config(sdk = [29], qualifiers = "w411dp-h891dp-night-420dpi")
    fun darkThemeUsesAStaticColorScheme() {
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                PalustrisTheme {
                    Text("API 29 dark theme")
                }
            }
        }

        compose.onNodeWithText("API 29 dark theme").assertIsDisplayed()
    }

    @Test
    fun preTiramisuMotionUsesTheNormalAnimationScale() {
        var reducedMotion = true
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                reducedMotion = palustrisMotionScheme().reducedMotion
                Text("API 29 motion")
            }
        }

        compose.onNodeWithText("API 29 motion").assertIsDisplayed()
        compose.runOnIdle { assertFalse(reducedMotion) }
    }
}
