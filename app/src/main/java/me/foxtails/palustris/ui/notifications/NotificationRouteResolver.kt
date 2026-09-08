package me.foxtails.palustris.ui.notifications

import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.Notification
import me.foxtails.palustris.domain.NotificationDestination
import me.foxtails.palustris.domain.NotificationTarget
import me.foxtails.palustris.ui.navigation.AppRoute

object NotificationRouteResolver {
    fun detail(accountId: AccountId, notificationId: me.foxtails.palustris.domain.EntityId): AppRoute {
        return if (notificationId.connection == accountId.connection.origin) {
            AppRoute.NotificationDetail(accountId, notificationId)
        } else {
            AppRoute.AccountUnavailable(accountId, notificationId)
        }
    }

    fun resolve(notification: Notification): AppRoute {
        val accountId = notification.accountId
        return when (val destination = notification.destination) {
            is NotificationDestination.InApp -> when (val target = destination.target) {
                is NotificationTarget.Post -> AppRoute.Post(accountId, target.id)
                is NotificationTarget.Profile -> AppRoute.Profile(accountId, target.id)
                is NotificationTarget.Poll -> AppRoute.Poll(accountId, target.id)
                is NotificationTarget.Conversation -> AppRoute.Conversation(accountId, target.id)
            }
            is NotificationDestination.Server -> AppRoute.OpenOnServer(accountId, notification.id, destination.url)
            null -> AppRoute.NotificationDetail(accountId, notification.id)
        }
    }
}
