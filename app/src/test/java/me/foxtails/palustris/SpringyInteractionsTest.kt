package me.foxtails.palustris

import androidx.activity.compose.setContent
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import me.foxtails.palustris.MainActivity
import me.foxtails.palustris.ui.motion.LocalPalustrisMotionScheme
import me.foxtails.palustris.ui.motion.MotionScaleKey
import me.foxtails.palustris.ui.motion.PalustrisMotionScheme
import me.foxtails.palustris.ui.motion.springPress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-420dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SpringyInteractionsTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun pressCompressesAndReturnsWithoutChangingClickSemantics() {
        var clicks = 0
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                CompositionLocalProvider(LocalPalustrisMotionScheme provides PalustrisMotionScheme.standard(false)) {
                    val source = remember { MutableInteractionSource() }
                    Box(
                        Modifier
                            .size(48.dp)
                            .springPress(source, pressedScale = 0.96f)
                            .clickable(
                                interactionSource = source,
                                indication = LocalIndication.current,
                                role = Role.Button,
                            ) { clicks++ }
                            .semantics {
                                contentDescription = "Springy test button"
                                role = Role.Button
                            },
                    )
                }
            }
        }
        compose.waitForIdle()
        val button = compose.onNodeWithContentDescription("Springy test button")
        button.assertIsEnabled()
        val before = button.fetchSemanticsNode().config[MotionScaleKey]
        compose.mainClock.autoAdvance = false
        try {
            button.performTouchInput { down(center) }
            compose.mainClock.advanceTimeBy(60)
            val pressed = button.fetchSemanticsNode().config[MotionScaleKey]
            assertTrue("press should compress", pressed < before)
            button.performTouchInput { cancel() }
            compose.mainClock.advanceTimeBy(500)
            assertEquals(1f, button.fetchSemanticsNode().config[MotionScaleKey], 0.001f)
        } finally {
            compose.mainClock.autoAdvance = true
        }
        button.performClick()
        assertEquals(1, clicks)
    }

    @Test
    fun disabledControlsDoNotCompressOrInvokeCallbacks() {
        var clicks = 0
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                CompositionLocalProvider(LocalPalustrisMotionScheme provides PalustrisMotionScheme.standard(false)) {
                    val source = remember { MutableInteractionSource() }
                    Box(
                        Modifier
                            .size(48.dp)
                            .springPress(source, enabled = false)
                            .clickable(
                                interactionSource = source,
                                indication = LocalIndication.current,
                                enabled = false,
                            ) { clicks++ }
                            .semantics { contentDescription = "Disabled springy button" },
                    )
                }
            }
        }
        compose.waitForIdle()
        val button = compose.onNodeWithContentDescription("Disabled springy button")
        button.assertIsNotEnabled()
        assertEquals(1f, button.fetchSemanticsNode().config[MotionScaleKey], 0.001f)
        button.performClick()
        assertEquals(0, clicks)
    }

    @Test
    fun reducedMotionSnapsPressState() {
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                CompositionLocalProvider(LocalPalustrisMotionScheme provides PalustrisMotionScheme.standard(true)) {
                    val source = remember { MutableInteractionSource() }
                    Box(
                        Modifier
                            .size(48.dp)
                            .springPress(source)
                            .clickable(
                                interactionSource = source,
                                indication = LocalIndication.current,
                            ) {}
                            .semantics { contentDescription = "Reduced motion button" },
                    )
                }
            }
        }
        compose.waitForIdle()
        val button = compose.onNodeWithContentDescription("Reduced motion button")
        compose.mainClock.autoAdvance = false
        try {
            button.performTouchInput { down(center) }
            compose.mainClock.advanceTimeBy(1)
            assertEquals(1f, button.fetchSemanticsNode().config[MotionScaleKey], 0.001f)
        } finally {
            button.performTouchInput { cancel() }
            compose.mainClock.autoAdvance = true
        }
    }
}
