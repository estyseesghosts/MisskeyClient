package me.foxtails.palustris.domain

/** IDs are opaque and scoped to a connection. Never parse them as numbers or dates. */
data class EntityId(val connection: String, val value: String)

data class Account(
    val id: AccountId,
    val displayName: String,
    val handle: String,
    val avatarUrl: String? = null,
    val biography: String = "",
)

enum class Timeline { Home, Local, Federated }
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

/** Transport-independent boundary implemented by individual server adapters. */
interface SocialSource {
    val capabilities: ServerCapabilities
    suspend fun timelines(): List<Timeline> = capabilities.timelines.toList()
    suspend fun timeline(timeline: Timeline, cursor: String? = null): Page<Post>
    suspend fun post(id: EntityId): Post = unsupported("post")
    suspend fun thread(rootId: EntityId): List<Post> = unsupported("thread")
    suspend fun create(post: CreatePostRequest): Post = unsupported("create")
    suspend fun delete(id: EntityId) = unsupported<Unit>("delete")
    suspend fun edit(id: EntityId, text: String): Post = unsupported("edit")
    suspend fun react(id: EntityId, emoji: String) = unsupported<Unit>("react")
    suspend fun favorite(id: EntityId) = unsupported<Unit>("favorite")
    suspend fun renote(id: EntityId) = unsupported<Unit>("renote")
    suspend fun quote(id: EntityId, text: String) = unsupported<Unit>("quote")
    suspend fun votePoll(id: EntityId, optionIndex: Int) = unsupported<Unit>("votePoll")
    suspend fun uploadMedia(file: java.io.InputStream, mimeType: String): Attachment = unsupported("uploadMedia")
    suspend fun search(query: String): List<Post> = unsupported("search")
    suspend fun notifications(cursor: String? = null): Page<Notification> = unsupported("notifications")
    suspend fun follow(id: EntityId) = unsupported<Unit>("follow")
    suspend fun mute(id: EntityId) = unsupported<Unit>("mute")
    suspend fun block(id: EntityId) = unsupported<Unit>("block")
    suspend fun streamEvents(): kotlinx.coroutines.flow.Flow<Event> = unsupported("streamEvents")
}

private suspend fun <T> unsupported(feature: String): T = throw SourceError.Unsupported(feature)
