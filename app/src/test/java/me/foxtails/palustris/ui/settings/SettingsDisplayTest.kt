package me.foxtails.palustris.ui.settings

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import me.foxtails.palustris.data.preferences.FileAppPreferencesRepository
import me.foxtails.palustris.domain.AppBackground
import me.foxtails.palustris.domain.AppColorPalette
import me.foxtails.palustris.domain.AppColorScheme
import me.foxtails.palustris.domain.AppPreferences
import me.foxtails.palustris.domain.AppPreferencesState
import me.foxtails.palustris.ui.settings.DisplaySettingsScreen
import me.foxtails.palustris.ui.settings.SettingsHost
import me.foxtails.palustris.ui.settings.SettingsRoute
import me.foxtails.palustris.ui.settings.SettingsScreen
import me.foxtails.palustris.ui.theme.resolvedAppColorScheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-420dpi")
class SettingsDisplayTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Before
    fun clearPreferences() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        File(context.noBackupFilesDir, "app-preferences.json").delete()
        File(context.noBackupFilesDir, "app-preferences.json.new").delete()
    }

    @Test
    fun settingsRootHasOnlyOneDisplayEntry() {
        compose.activity.setContent {
            SettingsScreen(
                preferences = AppPreferences(),
                onDisplay = {},
                onNotifications = {},
                onPrivacy = {},
                onLanguage = {},
            )
        }

        compose.onAllNodesWithText("Display").assertCountEquals(1)
    }

    @Test
    fun displayScreenShowsThreeExclusiveStylesAndFourteenPaletteChoices() {
        compose.activity.setContent {
            DisplaySettingsScreen(
                preferences = AppPreferences(),
                onColorScheme = {},
                onColorPalette = {},
                onBackground = {},
                onTextSize = {},
                onFont = {},
                onRequest60Hz = {},
            )
        }

        compose.onNodeWithText("System").assertIsDisplayed()
        compose.onNodeWithText("System monochrome").assertIsDisplayed()
        compose.onNodeWithText("Colour palette").assertIsDisplayed()
        listOf(
            "Pastel red", "Pastel orange", "Pastel yellow", "Pastel green", "Pastel blue", "Pastel indigo", "Pastel violet",
            "Vibrant red", "Vibrant orange", "Vibrant yellow", "Vibrant green", "Vibrant blue", "Vibrant indigo", "Vibrant violet",
        ).forEach { compose.onNodeWithContentDescription(it).assertIsDisplayed() }
    }

    @Test
    fun backFromDisplayReturnsToSettingsAndBackFromSettingsDismissesIt() {
        var route by mutableStateOf<SettingsRoute>(SettingsRoute.Display)
        var dismissed = false
        compose.activity.setContent {
            SettingsHost(
                state = AppPreferencesState(loaded = true),
                route = route,
                onRoute = { route = it },
                onBack = { dismissed = true },
            )
        }

        compose.activity.onBackPressedDispatcher.onBackPressed()
        compose.waitForIdle()
        assertEquals(SettingsRoute.Main, route)

        compose.activity.onBackPressedDispatcher.onBackPressed()
        compose.waitForIdle()
        assertTrue(dismissed)
    }

    @Test
    fun paletteSelectionChangesPersistedPreference() {
        var selected = AppColorPalette.PastelIndigo
        var selectedScheme = AppColorScheme.System
        compose.activity.setContent {
            DisplaySettingsScreen(
                preferences = AppPreferences(colorPalette = selected),
                onColorScheme = { selectedScheme = it },
                onColorPalette = { selected = it },
                onBackground = {},
                onTextSize = {},
                onFont = {},
                onRequest60Hz = {},
            )
        }

        compose.onNodeWithContentDescription("Vibrant green").performClick()
        compose.runOnIdle {
            assertEquals(AppColorPalette.VibrantGreen, selected)
            assertEquals(AppColorScheme.Palette, selectedScheme)
        }
    }

    @Test
    fun filePreferencesRoundTripPaletteAndMigrateLegacyScheme() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repository = FileAppPreferencesRepository(context)
        repository.update { it.copy(colorPalette = AppColorPalette.VibrantViolet, colorScheme = AppColorScheme.System) }
        val reloaded = FileAppPreferencesRepository(context)
        assertEquals(AppColorPalette.VibrantViolet, reloaded.observe().first { it.loaded }.preferences.colorPalette)

        File(context.noBackupFilesDir, "app-preferences.json").writeText("{\"colorScheme\":\"Monochrome\"}")
        val legacy = FileAppPreferencesRepository(context)
        assertEquals(AppColorScheme.SystemMonochrome, legacy.observe().first { it.loaded }.preferences.colorScheme)

        File(context.noBackupFilesDir, "app-preferences.json").writeText("{\"colorScheme\":\"Palette\"}")
        val palette = FileAppPreferencesRepository(context)
        assertEquals(AppColorScheme.Palette, palette.observe().first { it.loaded }.preferences.colorScheme)
    }

    @Test
    fun systemMonochromeHasNoChroma() {
        lateinit var colors: androidx.compose.material3.ColorScheme
        compose.activity.setContent {
            colors = resolvedAppColorScheme(
                context = LocalContext.current,
                colorScheme = AppColorScheme.SystemMonochrome,
                palette = AppColorPalette.PastelRed,
                background = AppBackground.Default,
                darkTheme = false,
            )
        }

        compose.runOnIdle {
            assertEquals(colors.primary.red, colors.primary.green, 0.0001f)
            assertEquals(colors.primary.green, colors.primary.blue, 0.0001f)
            assertEquals(colors.background.red, colors.background.green, 0.0001f)
            assertNotEquals(0f, colors.primary.red)
        }
    }

    @Test
    fun systemUsesTheDeviceMaterialSchemeInsteadOfTheSelectedPalette() {
        lateinit var colors: androidx.compose.material3.ColorScheme
        lateinit var expected: androidx.compose.material3.ColorScheme
        compose.activity.setContent {
            val context = LocalContext.current
            expected = dynamicLightColorScheme(context)
            colors = resolvedAppColorScheme(
                context = context,
                colorScheme = AppColorScheme.System,
                palette = AppColorPalette.VibrantRed,
                background = AppBackground.Default,
                darkTheme = false,
            )
        }

        compose.runOnIdle {
            assertEquals(expected.primary, colors.primary)
            assertEquals(expected.background, colors.background)
            assertEquals(expected.onSurface, colors.onSurface)
        }
    }

    @Test
    fun paletteKeepsTextAndSurfaceColorsSeparateInBothModes() {
        AppColorPalette.entries.forEach { palette ->
            val light = me.foxtails.palustris.ui.theme.selectedAppColorScheme(palette, darkTheme = false)
            val dark = me.foxtails.palustris.ui.theme.selectedAppColorScheme(palette, darkTheme = true)

            listOf(light, dark).forEach { scheme ->
                listOf(
                    scheme.background to scheme.onBackground,
                    scheme.surface to scheme.onSurface,
                    scheme.surfaceVariant to scheme.onSurfaceVariant,
                    scheme.primary to scheme.onPrimary,
                    scheme.primaryContainer to scheme.onPrimaryContainer,
                    scheme.secondary to scheme.onSecondary,
                    scheme.secondaryContainer to scheme.onSecondaryContainer,
                    scheme.tertiary to scheme.onTertiary,
                    scheme.tertiaryContainer to scheme.onTertiaryContainer,
                ).forEach { (background, text) ->
                    assertTrue(colorDistance(background, text) > 0.2f)
                }
            }
        }
    }

    @Test
    fun darkBackgroundUsesNearBlackValue() {
        lateinit var colors: androidx.compose.material3.ColorScheme
        compose.activity.setContent {
            colors = resolvedAppColorScheme(
                context = LocalContext.current,
                colorScheme = AppColorScheme.System,
                palette = AppColorPalette.VibrantBlue,
                background = AppBackground.Dark,
                darkTheme = true,
            )
        }

        compose.runOnIdle { assertEquals(Color(0xFF090909), colors.background) }
    }

    private fun colorDistance(first: Color, second: Color): Float {
        fun brightness(color: Color) = color.red * 0.2126f + color.green * 0.7152f + color.blue * 0.0722f
        return kotlin.math.abs(brightness(first) - brightness(second))
    }
}
