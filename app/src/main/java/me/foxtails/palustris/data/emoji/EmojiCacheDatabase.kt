package me.foxtails.palustris.data.emoji

import androidx.room.Database
import androidx.room.RoomDatabase

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
}
