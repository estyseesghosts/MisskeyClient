package me.foxtails.palustris.data.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import me.foxtails.palustris.MainActivity
import me.foxtails.palustris.R
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.EntityId
import me.foxtails.palustris.domain.Notification
import me.foxtails.palustris.domain.NotificationActivity
import me.foxtails.palustris.ui.notifications.NotificationLaunch
import me.foxtails.palustris.ui.notifications.NotificationLaunchRouter

data class NotificationPresentation(
    val accountId: AccountId,
    val notificationId: EntityId,
    val title: String,
    val body: String,
    val channel: NotificationChannelKind,
    val tag: String,
    val group: String,
    val androidId: Int,
)

interface NotificationPresenter {
    fun present(presentation: NotificationPresentation): Boolean
    fun dismiss(accountId: AccountId, notificationId: EntityId)
}

@Singleton
class NotificationPresentationFactory @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    fun prepare(
        notification: Notification,
        showPreview: Boolean,
        channel: NotificationChannelKind,
    ): NotificationPresentation {
        val title = activityTitle(notification.activity)
        val actorLabel = notification.group?.actorPreviews?.map { it.displayName }
            ?.takeIf { it.isNotEmpty() }
            ?.joinToString()
            ?: notification.actors.map { it.displayName }.filter(String::isNotBlank).joinToString()
        val safeActorLabel = actorLabel.ifBlank { context.getString(R.string.notifications_actorless) }
        val body = if (showPreview && !notification.post?.text.isNullOrBlank()) {
            notification.post?.text.orEmpty()
        } else if (showPreview && notification.post?.contentWarning != null) {
            notification.post.contentWarning.orEmpty()
        } else {
            safeActorLabel
        }
        return NotificationPresentation(
            accountId = notification.accountId,
            notificationId = notification.id,
            title = title,
            body = body,
            channel = channel,
            tag = AndroidNotificationIds.tag(notification.accountId),
            group = AndroidNotificationIds.group(notification.accountId),
            androidId = AndroidNotificationIds.id(notification.id),
        )
    }

    private fun activityTitle(activity: NotificationActivity): String = when (activity) {
        NotificationActivity.Mention -> context.getString(R.string.notification_activity_mention)
        NotificationActivity.Reply -> context.getString(R.string.notification_activity_reply)
        NotificationActivity.Reshare -> context.getString(R.string.notification_activity_reshare)
        NotificationActivity.Quote -> context.getString(R.string.notification_activity_quote)
        NotificationActivity.Favourite -> context.getString(R.string.notification_activity_favourite)
        is NotificationActivity.EmojiReaction -> context.getString(R.string.notification_activity_reaction, activity.reaction.fallbackText)
        NotificationActivity.Follow -> context.getString(R.string.notification_activity_follow)
        NotificationActivity.FollowRequest -> context.getString(R.string.notification_activity_follow_request)
        NotificationActivity.AcceptedRequest -> context.getString(R.string.notification_activity_accepted_request)
        NotificationActivity.SubscribedPost -> context.getString(R.string.notification_activity_subscribed_post)
        is NotificationActivity.PollResult -> context.getString(R.string.notification_activity_poll_result)
        NotificationActivity.PostUpdate -> context.getString(R.string.notification_activity_post_update)
        NotificationActivity.QuotedPostUpdate -> context.getString(R.string.notification_activity_quoted_post_update)
        is NotificationActivity.System -> context.getString(R.string.notifications_detail_title)
        is NotificationActivity.Unknown -> context.getString(R.string.notifications_detail_title)
    }
}

@Singleton
class AndroidNotificationPresenter @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : NotificationPresenter {
    private val manager = NotificationManagerCompat.from(context)

    override fun present(presentation: NotificationPresentation): Boolean {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) return false
        ensureChannels()
        val pendingIntent = PendingIntent.getActivity(
            context,
            presentation.androidId,
            NotificationLaunchRouter.intentFor(
                NotificationLaunch(presentation.accountId, presentation.notificationId),
            ).setClass(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = NotificationCompat.Builder(context, channelId(presentation.channel))
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(presentation.title)
            .setContentText(presentation.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(presentation.body))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SOCIAL)
            .setGroup(presentation.group)
        return try {
            manager.notify(presentation.tag, presentation.androidId, builder.build())
            true
        } catch (_: SecurityException) {
            false
        }
    }

    override fun dismiss(accountId: AccountId, notificationId: EntityId) {
        manager.cancel(AndroidNotificationIds.tag(accountId), AndroidNotificationIds.id(notificationId))
    }

    private fun ensureChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channels = listOf(
            NotificationChannel(CHANNEL_REPLIES, context.getString(R.string.notifications_channel_replies), NotificationManager.IMPORTANCE_DEFAULT),
            NotificationChannel(CHANNEL_SOCIAL, context.getString(R.string.notifications_channel_social), NotificationManager.IMPORTANCE_DEFAULT),
            NotificationChannel(CHANNEL_ACCOUNT, context.getString(R.string.notifications_channel_account), NotificationManager.IMPORTANCE_DEFAULT),
        )
        context.getSystemService(NotificationManager::class.java).createNotificationChannels(channels)
    }

    private companion object {
        const val CHANNEL_REPLIES = "notifications.replies"
        const val CHANNEL_SOCIAL = "notifications.social"
        const val CHANNEL_ACCOUNT = "notifications.account"

        fun channelId(channel: NotificationChannelKind): String = when (channel) {
            NotificationChannelKind.RepliesAndMentions -> CHANNEL_REPLIES
            NotificationChannelKind.Social -> CHANNEL_SOCIAL
            NotificationChannelKind.Account -> CHANNEL_ACCOUNT
        }
    }
}
