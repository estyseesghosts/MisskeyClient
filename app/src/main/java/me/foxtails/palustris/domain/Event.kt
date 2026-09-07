package me.foxtails.palustris.domain

/** Typed events crossing the source boundary; protocol stream names stay inside adapters. */
sealed interface SocialEvent {
    data class NotificationReceived(val notification: Notification) : SocialEvent
    data class NotificationReadChanged(
        val accountId: AccountId,
        val state: NotificationReadState,
    ) : SocialEvent
    data class PostChanged(val post: Post) : SocialEvent
    data class Other(val kind: String) : SocialEvent
}

data class Event(
    val accountId: AccountId,
    val payload: SocialEvent,
)
