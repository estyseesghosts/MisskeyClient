package me.foxtails.palustris.data.directmessages

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [DirectConversationEntity::class], version = 1, exportSchema = false)
abstract class DirectMessageDatabase : RoomDatabase() {
    abstract fun directMessageDao(): DirectMessageDao
}
