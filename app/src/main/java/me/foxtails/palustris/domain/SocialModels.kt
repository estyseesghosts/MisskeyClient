package me.foxtails.palustris.domain

/** IDs are opaque and scoped to a connection. Never parse them as numbers or dates. */
data class EntityId(val connection: String, val value: String)

data class Account(
    val id: EntityId,
    val displayName: String,
    val handle: String,
    val avatarUrl: String? = null,
    val biography: String = "",
)

enum class Timeline { Home, Local, Federated }
enum class Audience { Public, Unlisted, Followers, Direct }
enum class PostAction { Reply, Reshare, Favorite, React, Bookmark }

/** Supplied by an eventual adapter, never inferred by the UI from a server's name. */
data class ServerCapabilities(
    val timelines: Set<Timeline> = emptySet(),
    val audiences: Set<Audience> = emptySet(),
    val actions: Set<PostAction> = emptySet(),
    val maxPostLength: Int? = null,
    val canPublish: Boolean = false,
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
    suspend fun timeline(timeline: Timeline, cursor: String? = null): Page<Post>
}
