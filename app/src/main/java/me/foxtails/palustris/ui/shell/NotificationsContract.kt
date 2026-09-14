package me.foxtails.palustris.ui.shell

import me.foxtails.palustris.domain.Notification
import me.foxtails.palustris.domain.NotificationQuery
import me.foxtails.palustris.ui.NotificationsUiState

/**
 * Notification inbox presentation.
 *
 * The inbox state and inbox commands share one owner. The pending launch route is a shell
 * navigation handoff and is deliberately not part of this contract. [Empty] is an inert preview
 * value.
 */
data class NotificationsContract(
    val state: NotificationsUiState,
    val actions: Actions,
) {
    interface Actions {
        fun refresh()
        fun loadMore()
        fun markAllRead()
        fun markSeen(notification: Notification?)
        fun dismiss(notification: Notification)
        fun respondToFollowRequest(notification: Notification, accept: Boolean)
        fun selectQuery(query: NotificationQuery)
    }

    companion object {
        val Empty = NotificationsContract(NotificationsUiState(), NotificationsEmptyActions)
    }
}

private object NotificationsEmptyActions : NotificationsContract.Actions {
    override fun refresh() = Unit
    override fun loadMore() = Unit
    override fun markAllRead() = Unit
    override fun markSeen(notification: Notification?) = Unit
    override fun dismiss(notification: Notification) = Unit
    override fun respondToFollowRequest(notification: Notification, accept: Boolean) = Unit
    override fun selectQuery(query: NotificationQuery) = Unit
}
