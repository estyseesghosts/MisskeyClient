package me.foxtails.palustris.ui.notifications

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.foxtails.palustris.R
import me.foxtails.palustris.domain.Notification
import me.foxtails.palustris.ui.EmptyState
import me.foxtails.palustris.ui.openExternal
import me.foxtails.palustris.ui.navigation.AppRoute

@Composable
fun NotificationDetailScreen(
    route: AppRoute,
    items: List<Notification>,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    when (route) {
        is AppRoute.NotificationSettings -> EmptyState(
            me.foxtails.palustris.ui.AppIcons.Notifications,
            stringResource(R.string.notifications_detail_title),
            stringResource(R.string.notifications_target_unavailable),
            modifier,
        )
        is AppRoute.AccountUnavailable -> EmptyState(
            me.foxtails.palustris.ui.AppIcons.Unavailable,
            stringResource(R.string.notifications_account_unavailable_title),
            stringResource(R.string.notifications_account_unavailable_subtitle),
            modifier,
        )
        is AppRoute.OpenOnServer -> {
            val notification = items.firstOrNull { it.id == route.notificationId }
            Column(
                modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(stringResource(R.string.notifications_detail_title), style = MaterialTheme.typography.headlineSmall)
                notification?.let { Text(it.activityLabel(), modifier = Modifier.padding(top = 12.dp)) }
                Button(
                    onClick = { openExternal(context, route.url.value) },
                    modifier = Modifier.padding(top = 20.dp),
                ) { Text(stringResource(R.string.notifications_open_on_server)) }
            }
        }
        is AppRoute.NotificationDetail -> {
            val notification = items.firstOrNull { it.id == route.notificationId }
            if (notification == null) {
                EmptyState(
                    me.foxtails.palustris.ui.AppIcons.Notifications,
                    stringResource(R.string.notifications_detail_title),
                    stringResource(R.string.notifications_detail_not_cached),
                    modifier,
                )
            } else {
                NotificationRow(notification, onOpen = {})
            }
        }
        is AppRoute.Post,
        is AppRoute.Profile,
        is AppRoute.Poll,
        is AppRoute.Conversation,
        -> {
            val notification = items.firstOrNull { candidate ->
                when (route) {
                    is AppRoute.Post -> candidate.target == me.foxtails.palustris.domain.NotificationTarget.Post(route.postId)
                    is AppRoute.Profile -> candidate.target == me.foxtails.palustris.domain.NotificationTarget.Profile(route.profileId)
                    is AppRoute.Poll -> candidate.target == me.foxtails.palustris.domain.NotificationTarget.Poll(route.pollId)
                    is AppRoute.Conversation -> candidate.target == me.foxtails.palustris.domain.NotificationTarget.Conversation(route.conversationId)
                    else -> false
                }
            }
            if (notification == null) {
                EmptyState(
                    me.foxtails.palustris.ui.AppIcons.Notifications,
                    stringResource(R.string.notifications_detail_title),
                    stringResource(R.string.notifications_target_unavailable),
                    modifier,
                )
            } else {
                NotificationRow(notification, onOpen = {})
            }
        }
    }
}

@Composable
private fun Notification.activityLabel(): String = when (activity) {
        me.foxtails.palustris.domain.NotificationActivity.Mention -> stringResource(R.string.notification_activity_mention)
        me.foxtails.palustris.domain.NotificationActivity.Reply -> stringResource(R.string.notification_activity_reply)
        me.foxtails.palustris.domain.NotificationActivity.Reshare -> stringResource(R.string.notification_activity_reshare)
        me.foxtails.palustris.domain.NotificationActivity.Quote -> stringResource(R.string.notification_activity_quote)
        me.foxtails.palustris.domain.NotificationActivity.Favourite -> stringResource(R.string.notification_activity_favourite)
        is me.foxtails.palustris.domain.NotificationActivity.EmojiReaction -> stringResource(R.string.notification_activity_reaction, activity.reaction.fallbackText)
    else -> stringResource(R.string.notifications_detail_title)
}
