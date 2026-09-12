package me.foxtails.palustris.domain

/** Associates a merged timeline row with the account and session that fetched it. */
data class OwnedPost(
    val fetchedBy: AccountId,
    val post: Post,
    val sessionRevision: Long = 0L,
)
