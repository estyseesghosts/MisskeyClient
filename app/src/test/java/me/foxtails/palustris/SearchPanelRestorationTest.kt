package me.foxtails.palustris

import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import me.foxtails.palustris.ui.PalustrisApp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-420dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SearchPanelRestorationTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun restoredPhotoGridModeRestoresItsNavigationIcon() {
        val restorationTester = StateRestorationTester(compose)
        restorationTester.setContent { PalustrisApp() }
        compose.onNodeWithContentDescription("Search").performClick()
        compose.onNodeWithContentDescription("Photo grid").performClick()

        restorationTester.emulateSavedInstanceStateRestore()

        compose.onNodeWithContentDescription("Photo grid").assertIsSelected()
    }
}
