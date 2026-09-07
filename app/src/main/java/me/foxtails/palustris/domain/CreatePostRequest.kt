package me.foxtails.palustris.domain

data class PollRequest(
    val choices: List<String>,
    val multiple: Boolean = false,
    val expiresAt: String? = null,
)

data class CreatePostRequest(
    val text: String,
    val audience: Audience = Audience.Public,
    val contentWarning: String? = null,
    val replyTo: EntityId? = null,
    val attachments: List<Attachment> = emptyList(),
    val poll: PollRequest? = null,
    val quoteOf: EntityId? = null,
)
