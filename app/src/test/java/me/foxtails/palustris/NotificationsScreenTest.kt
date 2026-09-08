package me.foxtails.palustris

import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import me.foxtails.palustris.ui.MessagesScreen
import me.foxtails.palustris.ui.NotificationsUiState
import me.foxtails.palustris.ui.notifications.NotificationsScreen
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.EntityId
import me.foxtails.palustris.domain.Notification
import me.foxtails.palustris.domain.NotificationActivity
import me.foxtails.palustris.domain.Protocol
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
        notificationState: NotificationsUiState = NotificationsUiState(),
        onRefresh: () -> Unit = {},
    ) {
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                NotificationsScreen(
                    connected = connected,
                    compactLayout = compactLayout,
                    accountIdentity = accountIdentity,
                    notificationState = notificationState,
                    onRefreshNotifications = onRefresh,
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

        val labels = listOf("Replies", "Reposts", "Followers", "Likes")
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
            "Replies" to "Replies",
            "Reposts" to "Reposts",
            "Followers" to "Followers",
            "Likes" to "Likes",
        )

        cases.forEach { (label, _) ->
            showNotifications()
            compose.onNodeWithContentDescription(label).performClick().assertIsSelected()
            compose.onNodeWithText("Try another filter or pull down to refresh.").assertIsDisplayed()
            compose.onNodeWithText("All caught up").assertDoesNotExist()
            compose.onNodeWithContentDescription(label).performClick().assertIsNotSelected()
            compose.onNodeWithText("All caught up").assertIsDisplayed()
            listOf("Replies", "Reposts", "Followers", "Likes").forEach { filter ->
                compose.onNodeWithText(filter).assertIsNotSelected()
            }
        }
    }

    @Test fun selectingAnotherFilterSwitchesDirectly() {
        showNotifications()

        compose.onNodeWithContentDescription("Replies").performClick()
        compose.onNodeWithContentDescription("Reposts").performClick().assertIsSelected()
        compose.onNodeWithContentDescription("Replies").assertIsNotSelected()
        compose.onNodeWithText("Try another filter or pull down to refresh.").assertIsDisplayed()
    }

    @Test fun tappingTheSelectedFilterClearsBackToAllNotifications() {
        showNotifications()

        compose.onNodeWithContentDescription("Replies").performClick().assertIsSelected()
        compose.onNodeWithContentDescription("Replies").performClick().assertIsNotSelected()
        listOf("Replies", "Reposts", "Followers", "Likes").forEach { label ->
            compose.onNodeWithText(label).assertIsNotSelected()
        }
        compose.onNodeWithText("All caught up").assertIsDisplayed()
    }

    @Test fun followersFilterShowsOnlyFollowActivity() {
        val connection = Connection("https://example.org", Protocol.MISSKEY)
        val accountId = AccountId(connection, "receiver")
        val actor = Account(AccountId(connection, "actor"), "Actor", "@actor@example.org")
        val notifications = NotificationsUiState(
            items = listOf(
                Notification(
                    id = EntityId(connection.origin, "follow"),
                    accountId = accountId,
                    createdAtEpochMillis = 2,
                    activity = NotificationActivity.Follow,
                    actors = listOf(actor),
                    rawType = "follow",
                ),
                Notification(
                    id = EntityId(connection.origin, "like"),
                    accountId = accountId,
                    createdAtEpochMillis = 1,
                    activity = NotificationActivity.Favourite,
                    actors = listOf(actor),
                    rawType = "favourite",
                ),
            ),
        )
        showNotifications(connected = true, notificationState = notifications)

        compose.onNodeWithText("Followers").performClick().assertIsSelected()
        compose.onNodeWithText("Followed you").assertIsDisplayed()
        compose.onNodeWithText("Liked your post").assertDoesNotExist()
    }

    @Test fun changingAccountIdentityResetsTransientFilter() {
        showNotifications(accountIdentity = "https://example.org\u0000alice")
        compose.onNodeWithContentDescription("Likes").performClick().assertIsSelected()

        showNotifications(accountIdentity = "https://example.org\u0000bob")
        compose.onNodeWithContentDescription("Likes").assertIsNotSelected()
        compose.onNodeWithText("All caught up").assertIsDisplayed()
    }

    @Test fun notificationRowUsesScreenSpecificScrollableSemantics() {
        showNotifications(connected = true)

        compose.onNodeWithContentDescription("Notification filters; swipe horizontally for more")
            .assert(hasScrollAction())
        compose.onNodeWithContentDescription("Notification filters; swipe horizontally for more")
            .assertIsDisplayed()
        compose.onNodeWithText("Notifications").assertIsDisplayed()
    }

    @Test fun emptyInboxCanTriggerPullToRefresh() {
        var refreshes = 0
        showNotifications(onRefresh = { refreshes++ })

        compose.onNodeWithTag("notifications_content").performTouchInput {
            swipeDown(startY = top + 4f, endY = bottom - 4f)
        }
        compose.waitForIdle()

        assertTrue("empty notification inbox should support pull-to-refresh", refreshes > 0)
    }

    @Test fun directMessagesDoesNotRenderNotificationFilters() {
        showMessages()

        compose.onNodeWithText("Direct messages coming soon").assertIsDisplayed()
        compose.onNodeWithContentDescription("Notification filters; swipe horizontally for more").assertDoesNotExist()
        listOf("Replies", "Reposts", "Followers", "Likes").forEach { label ->
            compose.onNodeWithText(label).assertDoesNotExist()
        }
    }
}
