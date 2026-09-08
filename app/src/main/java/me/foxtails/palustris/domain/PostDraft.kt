package me.foxtails.palustris.domain

import java.time.Instant
import java.util.UUID

data class PostDraftQuotePreview(
    val authorDisplayName: String,
    val authorHandle: String,
    val text: String,
    val url: String? = null,
)

data class PostDraft(
    val id: String = UUID.randomUUID().toString(),
    val accountId: AccountId?,
    val text: String,
    val audience: Audience = Audience.Public,
    val contentWarning: String? = null,
    val replyTo: EntityId? = null,
    val quoteOf: EntityId? = null,
    val attachments: List<Attachment> = emptyList(),
    val poll: PollRequest? = null,
    val updatedAt: Long = Instant.now().toEpochMilli(),
    val quotePreview: PostDraftQuotePreview? = null,
)
