package me.foxtails.palustris.data.directmessages

import androidx.room.Entity

@Entity(
    tableName = "direct_conversations",
    primaryKeys = ["accountKey", "conversationConnection", "conversationId"],
)
data class DirectConversationEntity(
    val accountKey: String,
    val conversationConnection: String,
    val conversationId: String,
    val protocol: String,
    val rootPostConnection: String?,
    val rootPostId: String?,
    val participantJson: String,
    val lastPostJson: String,
    val threadJson: String,
    val lastUpdatedEpochMillis: Long,
    val unread: Boolean,
)
