package me.foxtails.palustris.ui.navigation

import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.EntityId
import me.foxtails.palustris.domain.ValidatedUrl

/** Typed destinations keep account selection and notification launches together. */
sealed interface AppRoute {
    data class Post(val accountId: AccountId, val postId: EntityId) : AppRoute
    data class Profile(val accountId: AccountId, val profileId: AccountId) : AppRoute
    data class Poll(val accountId: AccountId, val pollId: EntityId) : AppRoute
    data class Conversation(val accountId: AccountId, val conversationId: EntityId) : AppRoute
    data class NotificationDetail(val accountId: AccountId, val notificationId: EntityId) : AppRoute
    data class OpenOnServer(
        val accountId: AccountId,
        val notificationId: EntityId,
        val url: ValidatedUrl,
    ) : AppRoute
    data class AccountUnavailable(val accountId: AccountId, val notificationId: EntityId) : AppRoute
}
