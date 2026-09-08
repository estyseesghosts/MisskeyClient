package me.foxtails.palustris.domain

/** IDs are opaque and scoped to a connection. Never parse them as numbers or dates. */
data class EntityId(val connection: String, val value: String)

enum class Timeline { Home, Local, Social, Federated }
enum class Audience { Public, Unlisted, Followers, Direct }
enum class PostAction { Reply, Reshare, Favorite, React, Bookmark }

enum class PrimaryFavouriteMode { Native, Reaction, Unavailable }

enum class SavedPostsKind { Bookmarks, Favourites }

data class PrimaryFavouriteCapability(
    val status: CapabilityStatus = CapabilityStatus.Unknown,
    val mode: PrimaryFavouriteMode = PrimaryFavouriteMode.Unavailable,
)

data class SavedPostsCapability(
    val status: CapabilityStatus = CapabilityStatus.Unknown,
    val kind: SavedPostsKind,
)

data class Attachment(val url: String, val mimeType: String, val description: String?, val previewUrl: String? = null, val sensitive: Boolean = false)
data class Reaction(val emoji: String, val count: Int, val selected: Boolean, val imageUrl: String? = null)
data class PollOption(val text: String, val votes: Int)
data class Post(
    val id: EntityId,
    val author: Account,
    val text: String,
    val publishedAtEpochMillis: Long,
    val audience: Audience,
    val attachments: List<Attachment> = emptyList(),
    val contentWarning: String? = null,
    val resharedBy: Account? = null,
    val replyTo: EntityId? = null,
    val replyToAuthorId: AccountId? = null,
    val reactions: List<Reaction> = emptyList(),
    val availableActions: Set<PostAction> = emptySet(),
    val url: String? = null,
    val replyCount: Int = 0,
    val reshareCount: Int = 0,
    val quote: Post? = null,
    val pollOptions: List<PollOption> = emptyList(),
    val reposted: Boolean = false,
    val favourited: Boolean = false,
    val saved: Boolean = false,
    val myReaction: String? = null,
    val ownRepostId: EntityId? = null,
    /** ID accepted by post actions when the displayed row wraps another post, such as a renote. */
    val actionTargetId: EntityId? = null,
)

data class PostActionResult(
    val post: Post? = null,
    val selected: Boolean? = null,
    val count: Int? = null,
    val createdRepostId: EntityId? = null,
)

/** Cursor semantics belong to the adapter: Mastodon and Misskey paginate differently. */
data class Page<T>(val items: List<T>, val nextCursor: String? = null)

/** Unread precision is deliberately preserved across protocol adapters. */
sealed interface NotificationUnreadState {
    data class Exact(val count: Int) : NotificationUnreadState { init { require(count >= 0) } }
    data class AtLeast(val count: Int) : NotificationUnreadState { init { require(count >= 0) } }
    data object Present : NotificationUnreadState
    data object None : NotificationUnreadState
    data object Unknown : NotificationUnreadState
}

/** Every repository input is fenced to the account session that produced it. */
data class NotificationSyncToken(
    val accountId: AccountId,
    val generation: Long,
)

data class NotificationAcknowledgement(
    val accountId: AccountId,
    val readState: NotificationUnreadState,
    val acknowledgedAtEpochMillis: Long,
)

data class PushSubscriptionSpec(
    val accountId: AccountId,
    val endpoint: ValidatedUrl,
    val publicKey: String,
    val authSecret: String,
    val standardWebPush: Boolean = true,
    val alerts: Set<NotificationCategory> = setOf(NotificationCategory.All),
)

data class PushSubscription(
    val accountId: AccountId,
    val endpoint: ValidatedUrl,
    val remoteId: String? = null,
)

