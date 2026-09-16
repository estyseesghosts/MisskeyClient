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

data class DirectConversation(
    val id: ConversationId,
    val participants: List<Account>,
    val lastPost: Post,
    val unread: Boolean,
    val rootPostId: EntityId? = null,
)
