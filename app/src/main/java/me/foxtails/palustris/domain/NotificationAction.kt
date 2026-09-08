package me.foxtails.palustris.domain

sealed interface NotificationAction {
    data class Open(val notificationId: EntityId) : NotificationAction
    data class Dismiss(val notificationId: EntityId) : NotificationAction
    data class RespondToFollowRequest(val actorId: AccountId, val accept: Boolean) : NotificationAction
    data object AcknowledgeAll : NotificationAction
}

enum class NotificationActionState { Idle, Running, Succeeded, Failed }

data class NotificationActionResult(
    val action: NotificationAction,
    val state: NotificationActionState,
    val errorCategory: String? = null,
)

