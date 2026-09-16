package me.foxtails.palustris.ui.settings

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelectable
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import me.foxtails.palustris.domain.AppLanguage
import me.foxtails.palustris.ui.settings.LanguageSettingsScreen
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Every catalog row stays reachable and selectable on compact screens with large fonts. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-420dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LanguageSettingsScreenTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val labels = listOf(
        "System default",
        "English",
        "Deutsch",
        "Español",
        "Español (España)",
        "Español (Latinoamérica)",
        "Français",
        "हिन्दी",
        "日本語",
        "한국어",
        "Português (Brasil)",
        "Português (Portugal)",
        "粵語（香港）",
        "中文",
        "简体中文（中国大陆）",
        "繁體中文（臺灣）",
        "Русский",
        "Bahasa Indonesia",
    )

    @Test
    fun everyRowIsReachableOnCompactScreensWithLargeFonts() {
        compose.activity.resources.configuration.fontScale = 2.0f
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                LanguageSettingsScreen(selected = AppLanguage.English, onSelected = {})
            }
        }

        assertEquals(AppLanguage.entries.size, labels.size)
        labels.forEachIndexed { index, label ->
            compose.onNode(hasScrollAction()).performScrollToIndex(index)
            compose.onNodeWithText(label).assertIsDisplayed()
        }
    }

    @Test
    fun everyComposedRowExposesSingleSelectionSemantics() {
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                LanguageSettingsScreen(selected = AppLanguage.Japanese, onSelected = {})
            }
        }

        labels.forEachIndexed { index, label ->
            compose.onNode(hasScrollAction()).performScrollToIndex(index)
            compose.onNodeWithText(label).assertIsDisplayed().assertIsSelectable()
        }
        compose.onNode(hasScrollAction()).performScrollToIndex(labels.indexOf("日本語"))
        compose.onNodeWithText("日本語").assertIsSelected()
        compose.onAllNodes(isSelected()).assertCountEquals(1)
        compose.onNode(hasScrollAction()).performScrollToIndex(labels.indexOf("English"))
        compose.onNodeWithText("English").assertIsNotSelected()
    }

    @Test
    fun selectingARowReportsTheExactRegionalLanguage() {
        var selected: AppLanguage? = null
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                LanguageSettingsScreen(selected = AppLanguage.English, onSelected = { selected = it })
            }
        }

        compose.onNode(hasScrollAction()).performScrollToIndex(labels.indexOf("Português (Brasil)"))
        compose.onNodeWithText("Português (Brasil)").performClick()
        compose.runOnIdle { assertEquals(AppLanguage.PortugueseBrazil, selected) }

        compose.onNode(hasScrollAction()).performScrollToIndex(labels.indexOf("Español (Latinoamérica)"))
        compose.onNodeWithText("Español (Latinoamérica)").performClick()
        compose.runOnIdle { assertEquals(AppLanguage.SpanishLatinAmerica, selected) }
    }
}
