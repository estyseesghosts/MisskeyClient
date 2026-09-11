package me.foxtails.palustris.data.emoji

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface EmojiCacheDao {
    @Query("SELECT * FROM emoji_catalog_snapshot WHERE accountKey = :accountKey")
    fun catalogSnapshot(accountKey: String): EmojiCatalogSnapshotEntity?

    @Query("SELECT * FROM emoji_catalog_entry WHERE accountKey = :accountKey ORDER BY catalogPosition ASC")
    fun catalogEntries(accountKey: String): List<EmojiCatalogEntryEntity>

    @Query("DELETE FROM emoji_catalog_entry WHERE accountKey = :accountKey")
    fun deleteCatalogEntries(accountKey: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertCatalogSnapshot(snapshot: EmojiCatalogSnapshotEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertCatalogEntries(entries: List<EmojiCatalogEntryEntity>)

    @Transaction
    fun replaceCatalog(
        accountKey: String,
        refreshedAtEpochMillis: Long,
        entries: List<EmojiCatalogEntryEntity>,
    ) {
        deleteCatalogEntries(accountKey)
        insertCatalogEntries(entries)
        insertCatalogSnapshot(EmojiCatalogSnapshotEntity(accountKey, refreshedAtEpochMillis))
    }

    @Query("DELETE FROM emoji_catalog_entry WHERE accountKey = :accountKey")
    fun deleteCatalog(accountKey: String)

    @Query("DELETE FROM emoji_catalog_snapshot WHERE accountKey = :accountKey")
    fun deleteCatalogSnapshot(accountKey: String)

    @Transaction
    fun deleteAccountCatalog(accountKey: String) {
        deleteCatalog(accountKey)
        deleteCatalogSnapshot(accountKey)
    }

    @Query("SELECT * FROM emoji_asset_url WHERE canonicalUrl = :canonicalUrl")
    fun assetUrl(canonicalUrl: String): EmojiAssetUrlEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAssetUrl(mapping: EmojiAssetUrlEntity)

    @Query("DELETE FROM emoji_asset_url WHERE canonicalUrl = :canonicalUrl")
    fun deleteAssetUrl(canonicalUrl: String)

    @Query("SELECT * FROM emoji_asset WHERE contentHash = :contentHash")
    fun asset(contentHash: String): EmojiAssetEntity?

    @Query("SELECT * FROM emoji_asset")
    fun assets(): List<EmojiAssetEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAsset(asset: EmojiAssetEntity)

    @Query("DELETE FROM emoji_asset WHERE contentHash = :contentHash")
    fun deleteAsset(contentHash: String)

    @Query(
        "SELECT * FROM emoji_asset WHERE NOT EXISTS " +
            "(SELECT 1 FROM emoji_asset_url WHERE emoji_asset_url.contentHash = emoji_asset.contentHash) " +
            "ORDER BY lastUsedEpochMillis ASC",
    )
    fun unreferencedAssets(): List<EmojiAssetEntity>
}
