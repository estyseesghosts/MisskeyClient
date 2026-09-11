package me.foxtails.palustris

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import me.foxtails.palustris.data.emoji.EmojiAssetEntity
import me.foxtails.palustris.data.emoji.EmojiAssetUrlEntity
import me.foxtails.palustris.data.emoji.EmojiCacheDatabase
import me.foxtails.palustris.data.emoji.EmojiCatalogEntryEntity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class EmojiCacheDatabaseTest {
    private lateinit var database: EmojiCacheDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            EmojiCacheDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun replacingCatalogIsAtomicAndStoresEmptySnapshots() {
        val dao = database.emojiCacheDao()
        val first = entry("account-a", "first", 0)
        val second = entry("account-a", "second", 1)
        dao.replaceCatalog("account-a", 10L, listOf(first, second))

        dao.replaceCatalog("account-a", 20L, emptyList())

        assertEquals(20L, dao.catalogSnapshot("account-a")?.refreshedAtEpochMillis)
        assertEquals(emptyList<EmojiCatalogEntryEntity>(), dao.catalogEntries("account-a"))
        assertNull(dao.catalogSnapshot("account-b"))
    }

    @Test
    fun catalogRowsAreAccountScopedAndOrdered() {
        val dao = database.emojiCacheDao()
        dao.replaceCatalog("account-a", 10L, listOf(entry("account-a", "later", 4), entry("account-a", "first", 0)))
        dao.replaceCatalog("account-b", 10L, listOf(entry("account-b", "other", 0)))

        assertEquals(listOf("first", "later"), dao.catalogEntries("account-a").map { it.shortcode })
        assertEquals(listOf("other"), dao.catalogEntries("account-b").map { it.shortcode })
    }

    @Test
    fun assetMappingsAndUnreferencedAssetsAreAvailableForCleanup() {
        val dao = database.emojiCacheDao()
        dao.insertAsset(EmojiAssetEntity("hash-a", "aa/hash-a", "image/png", 3L, 1L))
        dao.insertAsset(EmojiAssetEntity("hash-b", "bb/hash-b", null, 4L, 2L))
        dao.insertAssetUrl(EmojiAssetUrlEntity("https://cdn.example/a", "hash-a", "etag", null, 3L))

        assertEquals("hash-a", dao.assetUrl("https://cdn.example/a")?.contentHash)
        assertEquals(listOf("hash-b"), dao.unreferencedAssets().map { it.contentHash })

        dao.deleteAssetUrl("https://cdn.example/a")
        assertEquals(listOf("hash-a", "hash-b"), dao.unreferencedAssets().map { it.contentHash })
        assertNotNull(dao.asset("hash-a"))
    }

    @Test
    fun accountCatalogCanBeDeletedWithoutTouchingOtherAccounts() {
        val dao = database.emojiCacheDao()
        dao.replaceCatalog("account-a", 10L, listOf(entry("account-a", "a", 0)))
        dao.replaceCatalog("account-b", 10L, listOf(entry("account-b", "b", 0)))

        dao.deleteAccountCatalog("account-a")

        assertNull(dao.catalogSnapshot("account-a"))
        assertEquals(listOf("b"), dao.catalogEntries("account-b").map { it.shortcode })
    }

    @Test
    fun accountCatalogRemovalDoesNotDeleteSharedAssetStorage() {
        val dao = database.emojiCacheDao()
        dao.replaceCatalog("account-a", 10L, listOf(entry("account-a", "a", 0)))
        dao.insertAsset(EmojiAssetEntity("shared", "emoji/assets/sh/shared", "image/png", 3L, 1L))
        dao.insertAssetUrl(EmojiAssetUrlEntity("https://cdn.example/shared", "shared", null, null, 1L))

        dao.deleteAccountCatalog("account-a")

        assertNotNull(dao.asset("shared"))
        assertEquals("shared", dao.assetUrl("https://cdn.example/shared")?.contentHash)
    }

    private fun entry(accountKey: String, shortcode: String, position: Int) = EmojiCatalogEntryEntity(
        accountKey = accountKey,
        emojiIdentity = ":$shortcode:",
        shortcode = shortcode,
        animatedUrl = null,
        staticUrl = "https://cdn.example/$shortcode.png",
        category = null,
        aliasesJson = "[]",
        visibleInPicker = true,
        catalogPosition = position,
    )
}
