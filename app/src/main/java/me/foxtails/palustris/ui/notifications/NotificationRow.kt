package me.foxtails.palustris.ui.notifications

import android.text.format.DateUtils
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import me.foxtails.palustris.R
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.Notification
import me.foxtails.palustris.domain.NotificationActionState
import me.foxtails.palustris.domain.NotificationActivity
import me.foxtails.palustris.domain.NotificationReadStatus
import me.foxtails.palustris.ui.AccountAvatar
import me.foxtails.palustris.ui.Avatar

@Composable
fun NotificationRow(
    notification: Notification,
    actionState: NotificationActionState = NotificationActionState.Idle,
    actionError: String? = null,
    onOpen: (() -> Unit)? = null,
    onDismiss: (() -> Unit)? = null,
    onFollowRequest: ((Boolean) -> Unit)? = null,
) {
    val actor = notification.actors.firstOrNull()
    val activityLabel = notification.activity.label()
    val stateLabel = when {
        notification.readState.serverAcknowledged -> stringResource(R.string.notifications_server_acknowledged)
        notification.readState.locallySeen -> stringResource(R.string.notifications_local_seen)
        notification.readState.status == NotificationReadStatus.Unread -> stringResource(R.string.notifications_unread_present)
        else -> ""
    }
    val summary = actorSummary(notification)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("notification_row_${notification.id.value}")
            .then(onOpen?.let { callback -> Modifier.clickable(onClick = callback) } ?: Modifier)
            .semantics {
                contentDescription = "$activityLabel, $summary"
                if (stateLabel.isNotBlank()) stateDescription = stateLabel
            },
        shape = MaterialTheme.shapes.large,
        color = if (notification.readState.status == NotificationReadStatus.Unread) {
            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
    ) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.Top) {
            if (actor != null) AccountAvatar(actor, Modifier.size(44.dp))
            else Avatar(Modifier.size(44.dp), description = null)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(activityLabel, style = MaterialTheme.typography.titleSmall)
                Text(
                    summary,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    DateUtils.getRelativeTimeSpanString(
                        notification.createdAtEpochMillis,
                        System.currentTimeMillis(),
                        DateUtils.MINUTE_IN_MILLIS,
                    ).toString(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                notification.post?.let { post ->
                    if (!post.contentWarning.isNullOrBlank()) {
                        Text(post.contentWarning, style = MaterialTheme.typography.labelMedium)
                    }
                    if (post.text.isNotBlank() && post.contentWarning.isNullOrBlank()) {
                        Text(post.text, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (notification.activity is NotificationActivity.FollowRequest) {
                    onFollowRequest?.let { respond ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { respond(true) },
                                enabled = actionState != NotificationActionState.Running,
                                contentPadding = ButtonDefaults.TextButtonContentPadding,
                            ) { Text(stringResource(R.string.notifications_follow_accept)) }
                            TextButton(
                                onClick = { respond(false) },
                                enabled = actionState != NotificationActionState.Running,
                            ) { Text(stringResource(R.string.notifications_follow_reject)) }
                        }
                    }
                }
                actionError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
            onDismiss?.let { dismiss ->
                TextButton(onClick = dismiss, enabled = actionState != NotificationActionState.Running) {
                    Text(stringResource(R.string.notifications_dismiss))
                }
            }
        }
    }
}

@Composable
private fun actorSummary(notification: Notification): String {
    val actors = notification.group?.actorPreviews?.takeIf { it.isNotEmpty() } ?: notification.actors
    val names = actors.map(Account::displayName).filter(String::isNotBlank)
    val total = notification.group?.totalCount
    return when {
        names.isEmpty() -> stringResource(R.string.notifications_actorless)
        names.size == 1 && total != null && total > 1 ->
            stringResource(R.string.notifications_grouped_actors, names.first(), total - 1)
        names.size == 1 -> names.first()
        names.size == 2 && total == null ->
            stringResource(R.string.notifications_grouped_two_actors, names[0], names[1])
        total != null && total > names.size ->
            stringResource(R.string.notifications_grouped_many_actors, names[0], names[1], total - names.size)
        else -> names.take(3).joinToString(", ")
    }
}

@Composable
private fun NotificationActivity.label(): String = when (this) {
    NotificationActivity.Mention -> stringResource(R.string.notification_activity_mention)
    NotificationActivity.Reply -> stringResource(R.string.notification_activity_reply)
    NotificationActivity.Reshare -> stringResource(R.string.notification_activity_reshare)
    NotificationActivity.Quote -> stringResource(R.string.notification_activity_quote)
    NotificationActivity.Favourite -> stringResource(R.string.notification_activity_favourite)
    is NotificationActivity.EmojiReaction -> stringResource(R.string.notification_activity_reaction, reaction.fallbackText)
    NotificationActivity.Follow -> stringResource(R.string.notification_activity_follow)
    NotificationActivity.FollowRequest -> stringResource(R.string.notification_activity_follow_request)
    NotificationActivity.AcceptedRequest -> stringResource(R.string.notification_activity_accepted_request)
    NotificationActivity.SubscribedPost -> stringResource(R.string.notification_activity_subscribed_post)
    is NotificationActivity.PollResult -> stringResource(R.string.notification_activity_poll_result)
    NotificationActivity.PostUpdate -> stringResource(R.string.notification_activity_post_update)
    NotificationActivity.QuotedPostUpdate -> stringResource(R.string.notification_activity_quoted_post_update)
    is NotificationActivity.System.Moderation -> title
    is NotificationActivity.System.RelationshipChange -> title
    is NotificationActivity.System.RoleOrAchievement -> title
    is NotificationActivity.System.AppEvent -> title
    is NotificationActivity.Unknown -> fallbackText
}
