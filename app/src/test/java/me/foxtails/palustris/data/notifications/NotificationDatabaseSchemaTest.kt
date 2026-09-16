package me.foxtails.palustris.data.notifications

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import me.foxtails.palustris.data.notifications.db.NOTIFICATION_MIGRATIONS
import me.foxtails.palustris.data.notifications.db.NotificationDatabase
import me.foxtails.palustris.data.notifications.db.NotificationStateEntity
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Guards the committed Room schema history and the installed schema.
 *
 * The first released Room schema is version 2, so there is no version-1 database file to migrate.
 * The exported `2.json` records the current schema for future migration validation. Do not build a
 * version-1 schema from the current annotations: that would invent history that never existed.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class NotificationDatabaseSchemaTest {
    @Test
    fun exportedSchemaRecordsTheInstalledVersionAndTables() {
        val schema = javaClass.getResourceAsStream(
            "/me.foxtails.palustris.data.notifications.db.NotificationDatabase/2.json",
        ) ?: error("Missing exported Room schema for version 2")
        val database = JSONObject(schema.use { String(it.readBytes(), Charsets.UTF_8) }).getJSONObject("database")

        assertEquals(2, database.getInt("version"))
        val entities = database.getJSONArray("entities")
        val tables = (0 until entities.length())
            .map { entities.getJSONObject(it).getString("tableName") }
            .toSet()
        assertEquals(
            setOf(
                "notification_state",
                "notification_events",
                "notification_actors",
                "notification_groups",
                "notification_query_state",
                "notification_dismissals",
                "notification_delivery",
                "notification_acknowledgement",
                "push_registration",
                "notification_settings",
            ),
            tables,
        )
    }

    @Test
    fun installedSchemaOpensValidatesAndSurvivesCloseReopen() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "notification-schema-history-test.db"
        context.deleteDatabase(name)
        try {
            val first = Room.databaseBuilder(context, NotificationDatabase::class.java, name)
                .addMigrations(*NOTIFICATION_MIGRATIONS)
                .allowMainThreadQueries()
                .build()
            try {
                // Opening validates the installed schema against the generated identity hash.
                first.openHelper.writableDatabase
                runBlocking(Dispatchers.IO) {
                    first.notificationDao().saveState(
                        NotificationStateEntity("key", """{"version":2}""", 1L),
                    )
                }
            } finally {
                first.close()
            }

            val second = Room.databaseBuilder(context, NotificationDatabase::class.java, name)
                .addMigrations(*NOTIFICATION_MIGRATIONS)
                .allowMainThreadQueries()
                .build()
            try {
                second.openHelper.writableDatabase
                assertNotNull(runBlocking(Dispatchers.IO) { second.notificationDao().state("key") })
            } finally {
                second.close()
            }
        } finally {
            context.deleteDatabase(name)
        }
    }

    @Test
    fun theOnlyRegisteredMigrationUpgradesFromOneToTwo() {
        val upgrades = NOTIFICATION_MIGRATIONS.map { it.startVersion to it.endVersion }

        assertEquals(listOf(1 to 2), upgrades)
    }

    @Test
    fun registeredMigrationCreatesTheExportedStateTable() {
        val schema = javaClass.getResourceAsStream(
            "/me.foxtails.palustris.data.notifications.db.NotificationDatabase/2.json",
        ) ?: error("Missing exported Room schema for version 2")
        val entities = JSONObject(schema.use { String(it.readBytes(), Charsets.UTF_8) })
            .getJSONObject("database")
            .getJSONArray("entities")
        val stateCreateSql = (0 until entities.length())
            .map { entities.getJSONObject(it) }
            .first { it.getString("tableName") == "notification_state" }
            .getString("createSql")

        assertTrue(stateCreateSql.contains("accountKey"))
        assertTrue(stateCreateSql.contains("stateJson"))
        assertTrue(stateCreateSql.contains("updatedAtEpochMillis"))
    }
}
