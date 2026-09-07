package me.foxtails.palustris.domain

import java.net.URI

/** A notification belongs to the account that received it, not to the visible actor. */
data class Notification(
    val id: EntityId,
    val accountId: AccountId,
    val createdAtEpochMillis: Long,
    val activity: NotificationActivity,
    val actors: List<Account> = emptyList(),
    val target: NotificationTarget? = null,
    val destination: NotificationDestination? = null,
    val post: Post? = null,
    val readState: NotificationReadState = NotificationReadState(),
    val rawType: String,
    val group: NotificationGroup? = null,
) {
    val actor: Account? get() = actors.firstOrNull()

    /** Compatibility for callers that have not moved to typed activity yet. */
    val type: String get() = rawType

    /** Unknown server read state is deliberately not treated as unread. */
    @Deprecated("Use readState.status")
    val isRead: Boolean get() = readState.status == NotificationReadStatus.Read
}

sealed interface NotificationActivity {
    data object Mention : NotificationActivity
    data object Reply : NotificationActivity
    data object Reshare : NotificationActivity
    data object Quote : NotificationActivity
    data object Favourite : NotificationActivity
    data class EmojiReaction(val reaction: NotificationReaction) : NotificationActivity
    data object Follow : NotificationActivity
    data object FollowRequest : NotificationActivity
    data object AcceptedRequest : NotificationActivity
    data object SubscribedPost : NotificationActivity
    data class PollResult(val option: String? = null) : NotificationActivity
    data object PostUpdate : NotificationActivity

    sealed interface System : NotificationActivity {
        data class Moderation(val title: String, val detail: String? = null) : System
        data class RelationshipChange(val title: String, val detail: String? = null) : System
        data class RoleOrAchievement(val title: String, val detail: String? = null) : System
        data class AppEvent(val title: String, val detail: String? = null) : System
    }

    /** Unknown values remain renderable without exposing the raw protocol payload. */
    data class Unknown(
        val fallbackText: String,
        val validatedDestination: NotificationDestination? = null,
    ) : NotificationActivity
}

data class NotificationReaction(
    val identity: String,
    val fallbackText: String,
    val imageUrl: String? = null,
)

/** The canonical object affected by the activity. Wrapper posts must not replace this identity. */
sealed interface NotificationTarget {
    data class Post(val id: EntityId) : NotificationTarget
    data class Profile(val id: AccountId) : NotificationTarget
    data class Poll(val id: EntityId) : NotificationTarget
    data class Conversation(val id: EntityId) : NotificationTarget
}

sealed interface NotificationDestination {
    data class InApp(val target: NotificationTarget) : NotificationDestination
    data class Server(val url: ValidatedUrl) : NotificationDestination
}

/** An external destination accepted for navigation after scheme/authority validation. */
@ConsistentCopyVisibility
data class ValidatedUrl private constructor(val value: String) {
    companion object {
        fun https(value: String): ValidatedUrl? = runCatching {
            val uri = URI(value)
            if (uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrBlank() &&
                uri.userInfo == null && uri.fragment == null
            ) {
                ValidatedUrl(uri.toASCIIString())
            } else {
                null
            }
        }.getOrNull()
    }

    override fun toString(): String = value
}

data class NotificationGroupId(val accountId: AccountId, val value: String)

data class NotificationGroup(
    val id: NotificationGroupId,
    val actorPreviews: List<Account> = emptyList(),
    val totalCount: Int? = null,
    val actorContinuation: NotificationCursor? = null,
)

enum class NotificationReadStatus { Read, Unread, Unknown }

data class NotificationReadState(
    val status: NotificationReadStatus = NotificationReadStatus.Unknown,
    val locallySeen: Boolean = false,
)
