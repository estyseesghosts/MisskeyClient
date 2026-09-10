package me.foxtails.palustris.ui.notifications

import android.text.format.DateUtils
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import me.foxtails.palustris.ui.motion.AnimatedStatePane
import me.foxtails.palustris.ui.motion.LocalPalustrisMotionScheme
import me.foxtails.palustris.ui.motion.springPress

@Composable
fun NotificationRow(
    notification: Notification,
    actionState: NotificationActionState = NotificationActionState.Idle,
    actionError: String? = null,
    onOpen: (() -> Unit)? = null,
    onDismiss: (() -> Unit)? = null,
    onFollowRequest: ((Boolean) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val scheme = LocalPalustrisMotionScheme.current
    val interactionSource = remember { MutableInteractionSource() }
    val actor = notification.actors.firstOrNull()
    val activityLabel = notification.activity.label()
    val stateLabel = when {
        notification.readState.serverAcknowledged -> stringResource(R.string.notifications_server_acknowledged)
        notification.readState.locallySeen -> stringResource(R.string.notifications_local_seen)
        notification.readState.status == NotificationReadStatus.Unread -> stringResource(R.string.notifications_unread_present)
        else -> ""
    }
    val summary = actorSummary(notification)
    val rowAccessibility = stringResource(R.string.notification_row_accessibility, activityLabel, summary)
    val summaryEmoji = remember(notification) {
        (notification.group?.actorPreviews?.takeIf { it.isNotEmpty() } ?: notification.actors)
            .flatMap { it.emoji.entries }
            .associate { it.toPair() }
    }
    val containerColor by animateColorAsState(
        targetValue = if (notification.readState.status == NotificationReadStatus.Unread) {
            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
        animationSpec = scheme.color,
        label = "notificationReadColor",
    )
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("notification_row_${notification.id.value}")
            .then(onOpen?.let { callback ->
                Modifier
                    .springPress(interactionSource, pressedScale = scheme.largePressedScale)
                    .clickable(
                        interactionSource = interactionSource,
                        indication = LocalIndication.current,
                        onClick = callback,
                    )
            } ?: Modifier)
            .semantics {
                contentDescription = rowAccessibility
                if (stateLabel.isNotBlank()) stateDescription = stateLabel
            },
        shape = MaterialTheme.shapes.large,
        color = containerColor,
    ) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.Top) {
            if (actor != null) AccountAvatar(actor, Modifier.size(44.dp))
            else Avatar(Modifier.size(44.dp), description = null)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (notification.activity is NotificationActivity.EmojiReaction) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        me.foxtails.palustris.ui.emoji.CustomEmojiImage(
                            emoji = notification.activity.reaction.emoji,
                            fallbackText = notification.activity.reaction.fallbackText,
                            modifier = Modifier.size(20.dp),
                            textStyle = MaterialTheme.typography.labelLarge,
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(activityLabel, style = MaterialTheme.typography.titleSmall)
                    }
                } else {
                    Text(activityLabel, style = MaterialTheme.typography.titleSmall)
                }
                AnimatedContent(
                    targetState = summary,
                    transitionSpec = {
                        if (scheme.reducedMotion) EnterTransition.None togetherWith ExitTransition.None
                        else fadeIn(scheme.fastFadeIn) togetherWith fadeOut(scheme.fastFadeOut)
                    },
                    label = "notificationSummary",
                ) { summaryState ->
                    me.foxtails.palustris.ui.emoji.InlineEmojiText(
                        summaryState,
                        summaryEmoji,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
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
                        me.foxtails.palustris.ui.emoji.InlineEmojiText(
                            post.contentWarning,
                            post.emoji,
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                    if (post.text.isNotBlank() && post.contentWarning.isNullOrBlank()) {
                        me.foxtails.palustris.ui.emoji.PostText(
                            post,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (notification.activity is NotificationActivity.FollowRequest) {
                    onFollowRequest?.let { respond ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { respond(true) },
                                enabled = actionState != NotificationActionState.Running,
                                contentPadding = ButtonDefaults.TextButtonContentPadding,
                                modifier = Modifier.width(112.dp),
                            ) {
                                AnimatedContent(
                                    targetState = actionState,
                                    transitionSpec = {
                                        if (scheme.reducedMotion) EnterTransition.None togetherWith ExitTransition.None
                                        else fadeIn(scheme.fastFadeIn) togetherWith fadeOut(scheme.fastFadeOut)
                                    },
                                    label = "followAcceptState",
                                ) { followState ->
                                    if (followState == NotificationActionState.Running) {
                                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                    } else Text(stringResource(R.string.notifications_follow_accept))
                                }
                            }
                            TextButton(
                                onClick = { respond(false) },
                                enabled = actionState != NotificationActionState.Running,
                                modifier = Modifier.width(112.dp),
                            ) { Text(stringResource(R.string.notifications_follow_reject)) }
                        }
                    }
                }
                AnimatedStatePane(
                    stateKey = actionError != null,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    actionError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
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
    NotificationActivity.DirectMessage -> stringResource(R.string.notification_activity_direct_message)
    is NotificationActivity.System.Moderation -> title
    is NotificationActivity.System.RelationshipChange -> title
    is NotificationActivity.System.RoleOrAchievement -> title
    is NotificationActivity.System.AppEvent -> title
    is NotificationActivity.Unknown -> fallbackText
}
