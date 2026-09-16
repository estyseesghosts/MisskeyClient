package me.foxtails.palustris.data.directmessages

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [DirectConversationEntity::class], version = 2, exportSchema = true)
abstract class DirectMessageDatabase : RoomDatabase() {
    abstract fun directMessageDao(): DirectMessageDao
}
