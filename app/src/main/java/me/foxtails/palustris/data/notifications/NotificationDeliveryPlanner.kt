package me.foxtails.palustris.data.notifications

import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject
import me.foxtails.palustris.domain.Audience
import me.foxtails.palustris.domain.Notification
import me.foxtails.palustris.domain.NotificationDeliveryState
import me.foxtails.palustris.domain.NotificationSettings
import me.foxtails.palustris.domain.matchesCategory

enum class NotificationDeliveryDecision {
    Present,
    AlreadyPresented,
    SuppressedBySettings,
    SuppressedByQuietHours,
    PermissionRequired,
}

data class NotificationDeliveryPlan(
    val notification: Notification,
    val decision: NotificationDeliveryDecision,
    val showPreview: Boolean,
    val channel: NotificationChannelKind,
)

enum class NotificationChannelKind { RepliesAndMentions, Social, Account }

/** Pure delivery policy: inbox ingestion is never dropped for presentation reasons. */
class NotificationDeliveryPlanner @Inject constructor() {
    fun plan(
        notification: Notification,
        settings: NotificationSettings,
        permissionGranted: Boolean,
        foreground: Boolean,
        nowMinutes: Int = LocalTime.now().toSecondOfDay() / 60,
    ): NotificationDeliveryPlan {
        val channel = when (notification.activity) {
            me.foxtails.palustris.domain.NotificationActivity.Mention,
            me.foxtails.palustris.domain.NotificationActivity.Reply,
            -> NotificationChannelKind.RepliesAndMentions
            me.foxtails.palustris.domain.NotificationActivity.Follow,
            me.foxtails.palustris.domain.NotificationActivity.FollowRequest,
            me.foxtails.palustris.domain.NotificationActivity.AcceptedRequest,
            is me.foxtails.palustris.domain.NotificationActivity.System,
            -> NotificationChannelKind.Account
            else -> NotificationChannelKind.Social
        }
        val decision = when {
            notification.readState.androidPresented || notification.readState.androidDismissed ->
                NotificationDeliveryDecision.AlreadyPresented
            !settings.alertsEnabled || !categoryEnabled(notification, settings) || foreground ->
                NotificationDeliveryDecision.SuppressedBySettings
            !permissionGranted -> NotificationDeliveryDecision.PermissionRequired
            isQuietHours(settings, nowMinutes) -> NotificationDeliveryDecision.SuppressedByQuietHours
            else -> NotificationDeliveryDecision.Present
        }
        return NotificationDeliveryPlan(
            notification = notification,
            decision = decision,
            showPreview = settings.showPreviews && notification.post?.audience == Audience.Public,
            channel = channel,
        )
    }

    private fun categoryEnabled(notification: Notification, settings: NotificationSettings): Boolean =
        settings.categories.any(notification.activity::matchesCategory)

    private fun isQuietHours(settings: NotificationSettings, nowMinutes: Int): Boolean {
        val start = settings.quietHoursStartMinutes ?: return false
        val end = settings.quietHoursEndMinutes ?: return false
        return if (start <= end) nowMinutes in start until end else nowMinutes >= start || nowMinutes < end
    }
}
