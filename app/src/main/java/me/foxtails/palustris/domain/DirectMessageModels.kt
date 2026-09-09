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

data class DirectConversation(
    val id: ConversationId,
    val participants: List<Account>,
    val lastPost: Post,
    val unread: Boolean,
    val rootPostId: EntityId? = null,
)
