package me.foxtails.palustris.data.directmessages

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Guards the direct-message Room schema history.
 *
 * Version 1 was released without an identity column. Every version-1 row must
 * migrate to `PROVISIONAL`, because the stored value cannot prove a server
 * conversation identity. Do not reinterpret a legacy value by string shape.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DirectMessageDatabaseSchemaTest {
    @Test
    fun exportedSchemaRecordsVersionTwoAndTheIdentityColumn() {
        val entities = exportedEntities()
        val conversation = entities.first { it.getString("tableName") == "direct_conversations" }
        val identity = conversation.getJSONArray("fields")
            .let { fields -> (0 until fields.length()).map(fields::getJSONObject) }
            .first { it.getString("columnName") == "identity" }

        assertTrue(conversation.getString("createSql").contains("identity` TEXT NOT NULL DEFAULT 'PROVISIONAL'"))
        assertEquals("TEXT", identity.getString("affinity"))
        assertTrue(identity.getBoolean("notNull"))
        assertEquals("'PROVISIONAL'", identity.getString("defaultValue"))
    }

    @Test
    fun theOnlyRegisteredMigrationUpgradesFromOneToTwo() {
        val upgrades = DIRECT_MESSAGE_MIGRATIONS.map { it.startVersion to it.endVersion }

        assertEquals(listOf(1 to 2), upgrades)
    }

    @Test
    fun aLegacyRowMigratesToAProvisionalIdentity() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "dm-schema-history-test.db"
        context.deleteDatabase(name)
        try {
            val legacy = context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null)
            try {
                legacy.execSQL(
                    "CREATE TABLE IF NOT EXISTS `direct_conversations` (`accountKey` TEXT NOT NULL, " +
                        "`conversationConnection` TEXT NOT NULL, `conversationId` TEXT NOT NULL, " +
                        "`protocol` TEXT NOT NULL, `rootPostConnection` TEXT, `rootPostId` TEXT, " +
                        "`participantJson` TEXT NOT NULL, `lastPostJson` TEXT NOT NULL, `threadJson` TEXT NOT NULL, " +
                        "`lastUpdatedEpochMillis` INTEGER NOT NULL, `unread` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`accountKey`, `conversationConnection`, `conversationId`))",
                )
                legacy.execSQL(
                    "INSERT INTO `direct_conversations` VALUES ('key', 'https://example.org', 'legacy', 'MASTODON', " +
                        "NULL, NULL, '[]', '{}', '[]', 0, 0)",
                )
                legacy.version = 1
            } finally {
                legacy.close()
            }

            val database = Room.databaseBuilder(context, DirectMessageDatabase::class.java, name)
                .addMigrations(*DIRECT_MESSAGE_MIGRATIONS)
                .allowMainThreadQueries()
                .build()
            try {
                // Opening runs the migration and validates the migrated schema.
                database.openHelper.writableDatabase
                val row = runBlocking(Dispatchers.IO) {
                    database.directMessageDao().conversation("key", "https://example.org", "legacy")
                }

                assertNotNull(row)
                assertEquals("PROVISIONAL", row?.identity)
            } finally {
                database.close()
            }
        } finally {
            context.deleteDatabase(name)
        }
    }

    private fun exportedEntities(): List<JSONObject> {
        val schema = javaClass.getResourceAsStream(
            "/me.foxtails.palustris.data.directmessages.DirectMessageDatabase/2.json",
        ) ?: error("Missing exported Room schema for version 2")
        val database = JSONObject(schema.use { String(it.readBytes(), Charsets.UTF_8) })
            .getJSONObject("database")

        assertEquals(2, database.getInt("version"))
        val entities = database.getJSONArray("entities")
        return (0 until entities.length()).map(entities::getJSONObject)
    }
}
