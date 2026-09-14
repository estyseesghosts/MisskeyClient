package me.foxtails.palustris.ui.notifications

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.CreatePostRequest
import me.foxtails.palustris.domain.Notification
import me.foxtails.palustris.domain.NotificationQuery
import me.foxtails.palustris.domain.OwnedPost
import me.foxtails.palustris.domain.SocialSource
import me.foxtails.palustris.ui.NotificationsViewModel
import me.foxtails.palustris.ui.shell.NotificationsContract
import me.foxtails.palustris.ui.shell.PostProjectionCoordinator

/**
 * Owns the notification inbox presentation for one connected session.
 *
 * The inbox model and its projection sink stay beside the notification feature. The shell receives
 * only the narrow [NotificationsContract].
 */
@Composable
fun NotificationsHost(
    accountId: AccountId,
    sessionGeneration: Long,
    sessionRevision: Long,
    source: SocialSource,
    coordinator: PostProjectionCoordinator,
): NotificationsContract {
    val model = hiltViewModel<NotificationsViewModel, NotificationsViewModel.Factory>(
        key = "notifications-$accountId-$sessionGeneration-$sessionRevision",
        creationCallback = { factory -> factory.create(accountId, source, sessionRevision) },
    )
    val sink = remember(model) {
        object : PostProjectionCoordinator.Sink {
            override fun applyExternalPost(updated: OwnedPost) { model.applyExternalPost(updated) }
            override fun applyPublishedPost(request: CreatePostRequest) { model.applyPublishedPost(request) }
        }
    }
    DisposableEffect(coordinator, sink) {
        coordinator.register(sink)
        onDispose { coordinator.unregister(sink) }
    }
    val state by model.state.collectAsStateWithLifecycle()
    val actions = remember(model) {
        object : NotificationsContract.Actions {
            override fun refresh() { model.refresh() }
            override fun loadMore() { model.loadOlder() }
            override fun markAllRead() { model.markAllRead() }
            override fun markSeen(notification: Notification?) { model.markSeen(notification?.id) }
            override fun dismiss(notification: Notification) { model.dismiss(notification) }
            override fun respondToFollowRequest(notification: Notification, accept: Boolean) {
                model.respondToFollowRequest(notification, accept)
            }
            override fun selectQuery(query: NotificationQuery) { model.selectQuery(query) }
        }
    }
    return remember(state, actions) { NotificationsContract(state, actions) }
}
