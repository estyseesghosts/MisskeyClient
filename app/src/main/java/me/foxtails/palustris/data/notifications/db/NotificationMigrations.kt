package me.foxtails.palustris.data.notifications.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Explicit schema history is kept even though the first released Room schema is version 2. */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("CREATE TABLE IF NOT EXISTS notification_state (accountKey TEXT NOT NULL PRIMARY KEY, stateJson TEXT NOT NULL, updatedAtEpochMillis INTEGER NOT NULL)")
    }
}

val NOTIFICATION_MIGRATIONS = arrayOf(MIGRATION_1_2)

