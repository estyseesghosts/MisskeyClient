package me.foxtails.palustris.domain

/** IDs are opaque and scoped to a connection. Never parse them as numbers or dates. */
data class EntityId(val connection: String, val value: String)

data class Account(
    val id: AccountId,
    val displayName: String,
    val handle: String,
    val avatarUrl: String? = null,
    val biography: String = "",
    val profileFields: List<ProfileField> = emptyList(),
)

data class ProfileField(val name: String, val value: String)

data class UpdateProfileRequest(
    val displayName: String,
    val biography: String,
)

enum class Timeline { Home, Local, Social, Federated }
enum class Audience { Public, Unlisted, Followers, Direct }
enum class PostAction { Reply, Reshare, Favorite, React, Bookmark }

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
    val reactions: List<Reaction> = emptyList(),
    val availableActions: Set<PostAction> = emptySet(),
    val url: String? = null,
    val replyCount: Int = 0,
    val reshareCount: Int = 0,
    val quote: Post? = null,
    val pollOptions: List<PollOption> = emptyList(),
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

/** Transport-independent boundary implemented by individual server adapters. */
interface SocialSource {
    val capabilities: ServerCapabilities
    suspend fun timelines(): List<Timeline> = capabilities.timelines.toList()
    suspend fun timeline(timeline: Timeline, cursor: String? = null): Page<Post>
    suspend fun post(id: EntityId): Post = unsupported("post")
    suspend fun profile(id: AccountId): Account = unsupported("profile")
    suspend fun thread(rootId: EntityId): List<Post> = unsupported("thread")
    suspend fun create(post: CreatePostRequest): Post = unsupported("create")
    suspend fun updateProfile(request: UpdateProfileRequest): Account = unsupported("updateProfile")
    suspend fun delete(id: EntityId) = unsupported<Unit>("delete")
    suspend fun edit(id: EntityId, text: String): Post = unsupported("edit")
    suspend fun react(id: EntityId, emoji: String) = unsupported<Unit>("react")
    suspend fun favorite(id: EntityId) = unsupported<Unit>("favorite")
    suspend fun renote(id: EntityId) = unsupported<Unit>("renote")
    suspend fun quote(id: EntityId, text: String) = unsupported<Unit>("quote")
    suspend fun votePoll(id: EntityId, optionIndex: Int) = unsupported<Unit>("votePoll")
    suspend fun uploadMedia(file: java.io.InputStream, mimeType: String): Attachment = unsupported("uploadMedia")
    suspend fun search(query: String): List<Post> = unsupported("search")
    suspend fun searchHashtag(tag: String, cursor: String? = null): Page<Post> = unsupported("hashtag search")
    suspend fun searchAccounts(query: String): List<Account> = unsupported("account search")
    /** Legacy page shape retained for source compatibility during the adapter migration. */
    suspend fun notifications(cursor: String? = null): Page<Notification> = unsupported("notifications")
    suspend fun notifications(query: NotificationQuery, cursor: NotificationCursor? = null): NotificationPage =
        unsupported("notifications")
    suspend fun fetchNewerNotifications(
        query: NotificationQuery,
        checkpoint: NotificationCheckpoint,
    ): NotificationPage = unsupported("notifications.newer")
    suspend fun fetchOlderNotifications(
        query: NotificationQuery,
        checkpoint: NotificationCheckpoint,
    ): NotificationPage = unsupported("notifications.older")
    suspend fun notificationUnreadState(): NotificationUnreadState = unsupported("notifications.unread")
    suspend fun acknowledgeNotifications(): NotificationAcknowledgement = unsupported("notifications.acknowledge")
    suspend fun dismissNotification(id: EntityId) = unsupported<Unit>("notifications.dismiss")
    suspend fun respondToFollowRequest(targetAccountId: AccountId, accept: Boolean) =
        unsupported<Unit>("notifications.followRequest")
    suspend fun createPushSubscription(spec: PushSubscriptionSpec): PushSubscription =
        unsupported("notifications.push.create")
    suspend fun updatePushSubscription(spec: PushSubscriptionSpec): PushSubscription =
        unsupported("notifications.push.update")
    suspend fun removePushSubscription() = unsupported<Unit>("notifications.push.remove")
    suspend fun follow(id: EntityId) = unsupported<Unit>("follow")
    suspend fun mute(id: EntityId) = unsupported<Unit>("mute")
    suspend fun block(id: EntityId) = unsupported<Unit>("block")
    suspend fun streamEvents(): kotlinx.coroutines.flow.Flow<Event> = unsupported("streamEvents")
}

private suspend fun <T> unsupported(feature: String): T = throw SourceError.Unsupported(feature)
