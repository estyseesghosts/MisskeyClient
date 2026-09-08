package me.foxtails.palustris.data.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
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
import me.foxtails.palustris.domain.Notification
import me.foxtails.palustris.domain.NotificationActivity

/** Local Android presentation boundary for future push and foreground delivery paths. */
interface NotificationPresenter {
    fun present(notification: Notification): Boolean
    fun dismiss(notification: Notification)
}

@Singleton
class AndroidNotificationPresenter @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : NotificationPresenter {
    private val manager = NotificationManagerCompat.from(context)

    override fun present(notification: Notification): Boolean {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) return false
        ensureChannels()
        val activityLabel = notification.activity.notificationLabel()
        val actorLabel = notification.actors.joinToString { it.displayName }
            .ifBlank { "Activity from your server" }
        val pendingIntent = PendingIntent.getActivity(
            context,
            stableId(notification),
            Intent(context, MainActivity::class.java).apply {
                putExtra(EXTRA_ACCOUNT_ORIGIN, notification.accountId.connection.origin)
                putExtra(EXTRA_ACCOUNT_LOCAL_ID, notification.accountId.localId)
                putExtra(EXTRA_NOTIFICATION_ID, notification.id.value)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = NotificationCompat.Builder(context, channelFor(notification.activity))
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(activityLabel)
            .setContentText(actorLabel)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SOCIAL)
            .setGroup(groupFor(notification.accountId))
        return try {
            manager.notify(groupFor(notification.accountId), stableId(notification), builder.build())
            true
        } catch (_: SecurityException) {
            false
        }
    }

    override fun dismiss(notification: Notification) {
        manager.cancel(groupFor(notification.accountId), stableId(notification))
    }

    private fun ensureChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channels = listOf(
            NotificationChannel(
                CHANNEL_SOCIAL,
                "Social activity",
                NotificationManager.IMPORTANCE_DEFAULT,
            ),
            NotificationChannel(
                CHANNEL_ACCOUNT,
                "Account activity",
                NotificationManager.IMPORTANCE_DEFAULT,
            ),
        )
        context.getSystemService(NotificationManager::class.java).createNotificationChannels(channels)
    }

    private companion object {
        const val CHANNEL_SOCIAL = "notifications.social"
        const val CHANNEL_ACCOUNT = "notifications.account"
        const val EXTRA_ACCOUNT_ORIGIN = "notification.account.origin"
        const val EXTRA_ACCOUNT_LOCAL_ID = "notification.account.localId"
        const val EXTRA_NOTIFICATION_ID = "notification.id"

        fun channelFor(activity: NotificationActivity): String = when (activity) {
            NotificationActivity.Follow,
            NotificationActivity.FollowRequest,
            NotificationActivity.AcceptedRequest,
            is NotificationActivity.System,
            -> CHANNEL_ACCOUNT
            else -> CHANNEL_SOCIAL
        }

        fun groupFor(accountId: AccountId): String =
            "notifications:${accountId.connection.origin}:${accountId.localId}"

        fun stableId(notification: Notification): Int =
            (notification.id.connection + "\u0000" + notification.id.value).hashCode()
    }
}

private fun NotificationActivity.notificationLabel(): String = when (this) {
    NotificationActivity.Mention -> "Mentioned you"
    NotificationActivity.Reply -> "Replied to you"
    NotificationActivity.Reshare -> "Reposted your post"
    NotificationActivity.Quote -> "Quoted your post"
    NotificationActivity.Favourite -> "Liked your post"
    is NotificationActivity.EmojiReaction -> "Reacted with ${reaction.fallbackText}"
    NotificationActivity.Follow -> "Followed you"
    NotificationActivity.FollowRequest -> "Requested to follow you"
    NotificationActivity.AcceptedRequest -> "Accepted your follow request"
    NotificationActivity.SubscribedPost -> "Posted something new"
    is NotificationActivity.PollResult -> "Your poll ended"
    NotificationActivity.PostUpdate -> "Updated a post"
    NotificationActivity.QuotedPostUpdate -> "Updated a quoted post"
    is NotificationActivity.System.Moderation -> title
    is NotificationActivity.System.RelationshipChange -> title
    is NotificationActivity.System.RoleOrAchievement -> title
    is NotificationActivity.System.AppEvent -> title
    is NotificationActivity.Unknown -> fallbackText
}
