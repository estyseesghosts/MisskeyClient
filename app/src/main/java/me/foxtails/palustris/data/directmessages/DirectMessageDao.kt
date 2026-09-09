package me.foxtails.palustris.data.directmessages

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface DirectMessageDao {
    @Query("SELECT * FROM direct_conversations WHERE accountKey = :accountKey ORDER BY lastUpdatedEpochMillis DESC")
    fun conversations(accountKey: String): List<DirectConversationEntity>

    @Query("SELECT * FROM direct_conversations WHERE accountKey = :accountKey AND conversationConnection = :connection AND conversationId = :conversationId")
    fun conversation(accountKey: String, connection: String, conversationId: String): DirectConversationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun save(value: DirectConversationEntity)

    @Query("UPDATE direct_conversations SET unread = 0 WHERE accountKey = :accountKey AND conversationConnection = :connection AND conversationId = :conversationId")
    fun markRead(accountKey: String, connection: String, conversationId: String)

    @Query("DELETE FROM direct_conversations WHERE accountKey = :accountKey")
    fun deleteAccount(accountKey: String)
}
