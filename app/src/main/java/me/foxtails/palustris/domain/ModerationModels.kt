package me.foxtails.palustris.domain

enum class ModerationListKind { Blocked, Muted, Hashtags }

data class ModerationListQuery(
    val kind: ModerationListKind,
    val direction: ModerationPageDirection = ModerationPageDirection.Older,
)

enum class ModerationPageDirection { Older, Newer }

/** A cursor is opaque above an adapter and is tied to the account and query that created it. */
data class ModerationCursor(
    val accountId: AccountId,
    val query: ModerationListQuery,
    val protocolVariant: String,
    val value: String,
)

data class ModerationAccount(
    val account: Account,
    val relationshipId: String? = null,
    val createdAtEpochMillis: Long? = null,
)

data class MutedHashtag(
    val value: String,
    val contexts: Set<String> = emptySet(),
    val expiresAtEpochMillis: Long? = null,
)

data class ReportRequest(
    val targetAccountId: AccountId,
    val postId: EntityId? = null,
    val comment: String,
)

data class ModerationPage<T>(val items: List<T>, val nextCursor: ModerationCursor? = null)
