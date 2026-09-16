package me.foxtails.palustris.domain

/** A conversation identifier is opaque and scoped to the connection that issued it. */
data class ConversationId(
    val connection: String,
    val value: String,
)

data class DirectMessageRequest(
    val recipients: List<AccountId>,
    val text: String,
    val replyTo: EntityId? = null,
)

/**
 * A neutral thread lookup. [conversationId] identifies the conversation.
 * [anchor] is a known post in that conversation. The two are separate identity
 * spaces: a conversation ID is never treated as a status ID.
 */
data class DirectThreadRequest(
    val conversationId: ConversationId,
    val anchor: EntityId,
)

/**
 * Provenance of a conversation identifier.
 *
 * [Verified] means the server issued the identity in a conversation response.
 * [Provisional] means the client built a local placeholder from a sent post value
 * because no server conversation was known yet. Only [Verified] identity may
 * reach a server mark-read request. Do not infer identity from the identifier
 * string shape.
 */
enum class ConversationIdentity { Verified, Provisional }

data class DirectConversation(
    val id: ConversationId,
    val participants: List<Account>,
    val lastPost: Post,
    val unread: Boolean,
    val rootPostId: EntityId? = null,
    val identity: ConversationIdentity,
)
