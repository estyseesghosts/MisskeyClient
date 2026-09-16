package me.foxtails.palustris.data.directmessages

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Adds the conversation identity column.
 *
 * Every legacy row becomes `PROVISIONAL`. The stored value cannot prove a server
 * conversation identity, so the default keeps a legacy row away from a server
 * mark-read. The next conversation list rewrites the row with the server
 * identity. The column definition must match `DirectConversationEntity` exactly,
 * or Room rejects the migrated schema.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            "ALTER TABLE direct_conversations ADD COLUMN identity TEXT NOT NULL DEFAULT 'PROVISIONAL'",
        )
    }
}

val DIRECT_MESSAGE_MIGRATIONS = arrayOf(MIGRATION_1_2)
