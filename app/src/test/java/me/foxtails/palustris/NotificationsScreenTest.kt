package me.foxtails.palustris

import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import me.foxtails.palustris.ui.MessagesScreen
import me.foxtails.palustris.ui.NotificationsUiState
import me.foxtails.palustris.ui.notifications.NotificationsScreen
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.Audience
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.EntityId
import me.foxtails.palustris.domain.Notification
import me.foxtails.palustris.domain.NotificationActivity
import me.foxtails.palustris.domain.Post
import me.foxtails.palustris.domain.Protocol
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

    @Test fun compactNotificationRowsKeepFullRefreshViewportAndFinalRowCanScrollClear() {
        val connection = Connection("https://example.org", Protocol.MASTODON)
        val receiver = Account(AccountId(connection, "receiver"), "Receiver", "@receiver@example.org")
        val notifications = (0..8).map { index ->
            val actor = Account(AccountId(connection, "actor-$index"), "Actor $index", "@actor$index@example.org")
            Notification(
                id = EntityId(connection.origin, "compact-$index"),
                accountId = receiver.id,
                createdAtEpochMillis = 0,
                activity = NotificationActivity.Favourite,
                actors = listOf(actor),
                post = Post(
                    EntityId(connection.origin, "compact-post-$index"),
                    actor,
                    "Notification $index\nA second fixture line\nA third fixture line",
                    0,
                    Audience.Public,
                ),
                rawType = "favourite",
            )
        }
        showNotifications(connected = true, notificationState = NotificationsUiState(items = notifications))

        val refresh = compose.onNodeWithTag("notification_refresh_surface", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val content = compose.onNodeWithTag("notifications_content", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        assertEquals("Pull-to-refresh should own the complete compact viewport", refresh.top, content.top, 0.5f)
        assertEquals("Pull-to-refresh should own the complete compact viewport", refresh.bottom, content.bottom, 0.5f)

        val filters = compose.onNodeWithContentDescription("Notification filters; swipe horizontally for more")
            .fetchSemanticsNode().boundsInRoot
        val underlappingRow = compose.onNodeWithTag("notification_row_compact-5", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        assertTrue("a notification row should continue behind the floating filters", underlappingRow.bottom > filters.top)

        repeat(14) {
            compose.onNodeWithTag("notifications_content", useUnmergedTree = true).performTouchInput { swipeUp() }
        }
        compose.waitForIdle()
        val finalRow = compose.onNodeWithTag("notification_row_compact-8", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        assertTrue("the final notification should clear the floating filters", finalRow.bottom <= filters.top)
        compose.onNodeWithTag("notification_row_compact-8", useUnmergedTree = true).assertIsDisplayed()
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
