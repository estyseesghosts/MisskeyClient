package me.foxtails.palustris.domain

data class CreatePostRequest(
    val text: String,
    val audience: Audience = Audience.Public,
    val contentWarning: String? = null,
    val replyTo: EntityId? = null,
    val attachments: List<Attachment> = emptyList(),
)
