package me.foxtails.palustris.data.emoji

import androidx.room.Entity
import androidx.room.Index

@Entity(tableName = "emoji_catalog_snapshot")
data class EmojiCatalogSnapshotEntity(
    @androidx.room.PrimaryKey val accountKey: String,
    val refreshedAtEpochMillis: Long,
)

@Entity(
    tableName = "emoji_catalog_entry",
    primaryKeys = ["accountKey", "emojiIdentity"],
    indices = [Index(value = ["accountKey", "catalogPosition"])],
)
data class EmojiCatalogEntryEntity(
    val accountKey: String,
    val emojiIdentity: String,
    val shortcode: String,
    val animatedUrl: String?,
    val staticUrl: String?,
    val category: String?,
    val aliasesJson: String,
    val visibleInPicker: Boolean,
    val catalogPosition: Int,
)

@Entity(
    tableName = "emoji_asset_url",
    indices = [Index(value = ["contentHash"])],
)
data class EmojiAssetUrlEntity(
    @androidx.room.PrimaryKey val canonicalUrl: String,
    val contentHash: String,
    val etag: String?,
    val lastModified: String?,
    val lastCheckedEpochMillis: Long,
)

@Entity(
    tableName = "emoji_asset",
    indices = [Index(value = ["lastUsedEpochMillis"])],
)
data class EmojiAssetEntity(
    @androidx.room.PrimaryKey val contentHash: String,
    val relativePath: String,
    val mimeType: String?,
    val byteSize: Long,
    val lastUsedEpochMillis: Long,
)
