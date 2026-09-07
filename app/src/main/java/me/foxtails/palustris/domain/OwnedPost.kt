package me.foxtails.palustris.domain

/** Associates a merged timeline row with the account whose session fetched it. */
data class OwnedPost(val fetchedBy: AccountId, val post: Post)
