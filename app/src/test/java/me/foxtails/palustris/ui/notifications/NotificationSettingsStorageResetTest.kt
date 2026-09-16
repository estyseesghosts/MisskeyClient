package me.foxtails.palustris.ui.notifications

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import me.foxtails.palustris.ui.notifications.NotificationSettingsScreen
import me.foxtails.palustris.ui.notifications.NotificationSettingsUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The destructive reset stays behind an explicit confirmation on the settings surface. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-420dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NotificationSettingsStorageResetTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun confirmingTheResetInvokesTheActionOnce() {
        var resets = 0
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                NotificationSettingsScreen(
                    state = NotificationSettingsUiState(storageUnavailable = true),
                    onResetStorage = { resets += 1 },
                )
            }
        }

        compose.onNodeWithText("Reset local data").assertIsDisplayed().performClick()
        compose.onNodeWithText("Reset stored notifications?").assertIsDisplayed()
        compose.onNodeWithText("Reset").performClick()

        compose.runOnIdle { assertEquals(1, resets) }
    }

    @Test
    fun cancellingTheResetInvokesNoAction() {
        var resets = 0
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                NotificationSettingsScreen(
                    state = NotificationSettingsUiState(storageUnavailable = true),
                    onResetStorage = { resets += 1 },
                )
            }
        }

        compose.onNodeWithText("Reset local data").performClick()
        compose.onNodeWithText("Cancel").performClick()

        compose.runOnIdle { assertEquals(0, resets) }
    }
}
