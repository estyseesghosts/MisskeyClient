package me.foxtails.palustris.ui.posts

import androidx.compose.foundation.layout.Box
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import me.foxtails.palustris.MainActivity
import me.foxtails.palustris.ui.PalustrisTheme
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
class ReactionPickerGestureTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun longPressAndUpwardDragCallsExpandedOnly() {
        var compact = false
        var expanded = false
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                PalustrisTheme {
                    Box(
                        Modifier
                            .size(48.dp)
                            .testTag("reaction_gesture")
                            .reactionPickerGesture(
                                enabled = true,
                                gestureKey = "account:post:1",
                                thresholdPx = 36.dp.value,
                                onCompact = { compact = true },
                                onExpanded = { expanded = true },
                            ),
                    )
                }
            }
        }
        compose.onNodeWithTag("reaction_gesture").performTouchInput {
            down(center)
            advanceEventTime(600)
            moveBy(Offset(0f, -150f))
            up()
        }

        compose.runOnIdle {
            assertTrue(expanded)
            assertTrue(!compact)
        }
    }
}
