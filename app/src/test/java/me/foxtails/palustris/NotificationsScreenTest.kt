package me.foxtails.palustris

import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import me.foxtails.palustris.ui.MessagesScreen
import me.foxtails.palustris.ui.NotificationsScreen
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
class NotificationsScreenTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private fun showNotifications(
        connected: Boolean = false,
        accountIdentity: String = "preview",
        compactLayout: Boolean = true,
    ) {
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                NotificationsScreen(
                    connected = connected,
                    compactLayout = compactLayout,
                    accountIdentity = accountIdentity,
                )
            }
        }
        compose.waitForIdle()
    }

    private fun showMessages() {
        compose.activity.runOnUiThread { compose.activity.setContent { MessagesScreen() } }
        compose.waitForIdle()
    }

    @Test fun defaultStateShowsAllNotificationsAndRequiredChipOrder() {
        showNotifications()

        val labels = listOf("Replies", "Reposts", "Likes")
        labels.forEach { label ->
            compose.onNodeWithText(label).assertIsDisplayed().assertIsNotSelected()
        }
        assertTrue(
            "notification chips should retain the requested order",
            labels.map { compose.onNodeWithText(it).fetchSemanticsNode().boundsInRoot.left }
                .zipWithNext().all { (left, right) -> left < right },
        )
        compose.onNodeWithText("All caught up").assertIsDisplayed()
        compose.onNodeWithText("Activity from people you follow will appear here.").assertIsDisplayed()
        compose.onNodeWithText("All", substring = false).assertDoesNotExist()
    }

    @Test fun selectingEachFilterShowsItsPlaceholderAndSecondTapRestoresAll() {
        val cases = listOf(
            "Replies" to ("Replies coming soon" to "Replies to you will appear here."),
            "Reposts" to ("Reposts coming soon" to "Reposts of your posts will appear here."),
            "Likes" to ("Likes coming soon" to "Likes on your posts will appear here."),
        )

        cases.forEach { (label, copy) ->
            showNotifications()
            compose.onNodeWithText(label).performClick().assertIsSelected()
            compose.onNodeWithText(copy.first).assertIsDisplayed()
            compose.onNodeWithText(copy.second).assertIsDisplayed()
            compose.onNodeWithText("All caught up").assertDoesNotExist()
            compose.onNodeWithText(label).performClick().assertIsNotSelected()
            compose.onNodeWithText("All caught up").assertIsDisplayed()
            listOf("Replies", "Reposts", "Likes").forEach { filter ->
                compose.onNodeWithText(filter).assertIsNotSelected()
            }
        }
    }

    @Test fun selectingAnotherFilterSwitchesDirectly() {
        showNotifications()

        compose.onNodeWithText("Replies").performClick()
        compose.onNodeWithText("Reposts").performClick().assertIsSelected()
        compose.onNodeWithText("Replies").assertIsNotSelected()
        compose.onNodeWithText("Replies coming soon").assertDoesNotExist()
        compose.onNodeWithText("Reposts coming soon").assertIsDisplayed()
    }

    @Test fun tappingTheSelectedFilterClearsBackToAllNotifications() {
        showNotifications()

        compose.onNodeWithText("Replies").performClick().assertIsSelected()
        compose.onNodeWithText("Replies").performClick().assertIsNotSelected()
        listOf("Replies", "Reposts", "Likes").forEach { label ->
            compose.onNodeWithText(label).assertIsNotSelected()
        }
        compose.onNodeWithText("All caught up").assertIsDisplayed()
    }

    @Test fun changingAccountIdentityResetsTransientFilter() {
        showNotifications(accountIdentity = "https://example.org\u0000alice")
        compose.onNodeWithText("Likes").performClick().assertIsSelected()

        showNotifications(accountIdentity = "https://example.org\u0000bob")
        compose.onNodeWithText("Likes").assertIsNotSelected()
        compose.onNodeWithText("All caught up").assertIsDisplayed()
    }

    @Test fun notificationRowUsesScreenSpecificScrollableSemantics() {
        showNotifications(connected = true)

        compose.onNodeWithContentDescription("Notification filters; swipe horizontally for more")
            .assert(hasScrollAction())
        compose.onNodeWithContentDescription("Notification filters; swipe horizontally for more")
            .assertIsDisplayed()
        compose.onNodeWithText("Notifications coming soon").assertIsDisplayed()
    }

    @Test fun directMessagesDoesNotRenderNotificationFilters() {
        showMessages()

        compose.onNodeWithText("Direct messages coming soon").assertIsDisplayed()
        compose.onNodeWithContentDescription("Notification filters; swipe horizontally for more").assertDoesNotExist()
        listOf("Replies", "Reposts", "Likes").forEach { label ->
            compose.onNodeWithText(label).assertDoesNotExist()
        }
    }
}
