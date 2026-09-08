package me.foxtails.palustris

import me.foxtails.palustris.data.notifications.NotificationDeliveryDecision
import me.foxtails.palustris.data.notifications.NotificationDeliveryPlanner
import me.foxtails.palustris.data.notifications.NotificationChannelKind
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.Audience
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.EntityId
import me.foxtails.palustris.domain.Notification
import me.foxtails.palustris.domain.NotificationActivity
import me.foxtails.palustris.domain.categories
import me.foxtails.palustris.domain.NotificationCategory
import me.foxtails.palustris.domain.NotificationReadState
import me.foxtails.palustris.domain.NotificationSettings
import me.foxtails.palustris.domain.Post
import me.foxtails.palustris.domain.Protocol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationDeliveryPlannerTest {
    private val planner = NotificationDeliveryPlanner()
    private val account = AccountId(Connection("https://example.org", Protocol.MASTODON), "receiver")
    private val notification = Notification(
        id = EntityId(account.connection.origin, "event"),
        accountId = account,
        createdAtEpochMillis = 1L,
        activity = NotificationActivity.Mention,
        actors = listOf(Account(account.copy(localId = "actor"), "Actor", "@actor@example.org")),
        post = Post(
            id = EntityId(account.connection.origin, "post"),
            author = Account(account.copy(localId = "actor"), "Actor", "@actor@example.org"),
            text = "private text",
            publishedAtEpochMillis = 1L,
            audience = Audience.Direct,
        ),
        readState = NotificationReadState(),
        rawType = "mention",
    )

    @Test
    fun quietHoursSuppressSoundWithoutDeletingEvent() {
        val plan = planner.plan(
            notification,
            NotificationSettings(alertsEnabled = true, quietHoursStartMinutes = 22 * 60, quietHoursEndMinutes = 7 * 60),
            permissionGranted = true,
            foreground = false,
            nowMinutes = 23 * 60,
        )

        assertEquals(NotificationDeliveryDecision.SuppressedByQuietHours, plan.decision)
    }

    @Test
    fun categoryClassifierKeepsActivityFamiliesDisjoint() {
        assertEquals(setOf(NotificationCategory.Mentions), NotificationActivity.Mention.categories())
        assertEquals(setOf(NotificationCategory.Replies), NotificationActivity.Reply.categories())
        assertEquals(setOf(NotificationCategory.Quotes), NotificationActivity.QuotedPostUpdate.categories())
        assertEquals(setOf(NotificationCategory.Polls), NotificationActivity.PollResult().categories())
        assertEquals(setOf(NotificationCategory.System), NotificationActivity.Unknown("unknown").categories())
        assertEquals(setOf(NotificationCategory.Social), NotificationActivity.PostUpdate.categories())

        val socialDisabled = planner.plan(
            notification.copy(activity = NotificationActivity.Reply),
            NotificationSettings(alertsEnabled = true, categories = setOf(NotificationCategory.Social)),
            permissionGranted = true,
            foreground = false,
        )
        assertEquals(NotificationDeliveryDecision.SuppressedBySettings, socialDisabled.decision)
    }

    @Test
    fun privateBodyIsNeverPreparedAsAPreview() {
        val plan = planner.plan(
            notification,
            NotificationSettings(alertsEnabled = true, showPreviews = true, categories = setOf(NotificationCategory.All)),
            permissionGranted = true,
            foreground = false,
        )

        assertTrue(plan.showPreview.not())
        assertFalse(plan.decision == NotificationDeliveryDecision.SuppressedBySettings)
        assertEquals(NotificationChannelKind.Conversations, plan.channel)
    }

    @Test
    fun directMessagesUseConversationChannel() {
        val plan = planner.plan(
            notification.copy(activity = NotificationActivity.DirectMessage, post = null),
            NotificationSettings(alertsEnabled = true),
            permissionGranted = true,
            foreground = false,
        )

        assertEquals(NotificationChannelKind.Conversations, plan.channel)
    }
}
