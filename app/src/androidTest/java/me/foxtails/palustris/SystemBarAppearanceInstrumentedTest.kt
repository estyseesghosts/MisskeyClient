package me.foxtails.palustris

import android.content.res.Configuration
import android.graphics.Color
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.core.view.WindowCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import me.foxtails.palustris.ui.PalustrisTheme
import me.foxtails.palustris.ui.SystemBars
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SystemBarAppearanceInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun navigationBarModeUsesBlackViewerAndRestoresThemeAppearance() {
        var viewerOpen by mutableStateOf(false)
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                PalustrisTheme {
                    SystemBars(mediaViewerOpen = viewerOpen)
                    Box(Modifier.fillMaxSize()) {
                        Button(onClick = { viewerOpen = true }) { Text("Open viewer") }
                        if (viewerOpen) Button(onClick = { viewerOpen = false }) { Text("Close viewer") }
                    }
                }
            }
        }
        compose.waitForIdle()

        val window = compose.activity.window
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        assertFalse(window.isNavigationBarContrastEnforced)
        assertEquals(!isNightMode(), controller.isAppearanceLightNavigationBars)

        compose.onNodeWithText("Open viewer").performClick()
        compose.waitForIdle()
        assertEquals(Color.BLACK, window.navigationBarColor)
        assertTrue(!controller.isAppearanceLightNavigationBars)

        compose.onNodeWithText("Close viewer").assertIsDisplayed().performClick()
        compose.waitForIdle()
        assertEquals(!isNightMode(), controller.isAppearanceLightNavigationBars)
    }

    private fun isNightMode() = (compose.activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
        Configuration.UI_MODE_NIGHT_YES
}
