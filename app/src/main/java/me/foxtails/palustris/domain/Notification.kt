package me.foxtails.palustris.domain

data class Notification(
    val id: EntityId,
    val type: String,
    val account: Account,
    val post: Post? = null,
    val createdAtEpochMillis: Long = 0,
)
