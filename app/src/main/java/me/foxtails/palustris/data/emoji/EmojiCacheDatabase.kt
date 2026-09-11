package me.foxtails.palustris.data.emoji

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import java.io.File

@Database(
    entities = [
        EmojiCatalogSnapshotEntity::class,
        EmojiCatalogEntryEntity::class,
        EmojiAssetUrlEntity::class,
        EmojiAssetEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class EmojiCacheDatabase : RoomDatabase() {
    abstract fun emojiCacheDao(): EmojiCacheDao

    companion object {
        @Volatile private var instance: EmojiCacheDatabase? = null

        fun get(context: Context): EmojiCacheDatabase = instance ?: synchronized(this) {
            instance ?: run {
                val directory = File(context.noBackupFilesDir, "emoji").apply { mkdirs() }
                Room.databaseBuilder(
                    context.applicationContext,
                    EmojiCacheDatabase::class.java,
                    File(directory, "emoji-cache.db").absolutePath,
                ).build().also { instance = it }
            }
        }
    }
}
