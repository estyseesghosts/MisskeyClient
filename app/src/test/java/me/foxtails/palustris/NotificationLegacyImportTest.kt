package me.foxtails.palustris

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import me.foxtails.palustris.data.notifications.FileNotificationStore
import me.foxtails.palustris.data.notifications.LegacyNotificationFileImporter
import me.foxtails.palustris.data.notifications.NotificationRepositoryState
import me.foxtails.palustris.data.notifications.NotificationStoreRead
import me.foxtails.palustris.data.notifications.RoomNotificationStore
import me.foxtails.palustris.data.notifications.db.NotificationDao
import me.foxtails.palustris.data.notifications.db.NotificationDatabase
import me.foxtails.palustris.data.notifications.db.NotificationStateEntity
import me.foxtails.palustris.data.notifications.stableFileName
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.NotificationUnreadState
import me.foxtails.palustris.domain.Protocol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Slice 03-H: legacy import is restart-safe. The Room row wins over the legacy
 * file, the marker is written only after a successful Room save, and transient
 * failures stay unmarked for retry.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class NotificationLegacyImportTest {
    private val created = mutableListOf<Pair<Context, AccountId>>()
    @Test
    fun readableImportSavesRoomThenMarks() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, NotificationDatabase::class.java).build()
        try {
            val accountId = freshAccount()
            writeLegacy(context, accountId, """{"version":2,"unread":{"kind":"exact","count":3}}""")
            val store = RoomNotificationStore(database, importer(context))

            val read = store.read(accountId)

            assertTrue(read is NotificationStoreRead.Readable)
            assertEquals(NotificationUnreadState.Exact(3), (read as NotificationStoreRead.Readable).state.unreadState)
            assertTrue(markerFor(context, accountId).exists())
            assertTrue(runBlocking(Dispatchers.IO) { database.notificationDao().state(accountId.stableFileName()) } != null)
        } finally {
            runBlocking(Dispatchers.IO) { database.close() }
            cleanFiles(context)
        }
    }

    @Test
    fun roomRowIsAuthoritativeOverLegacyFile() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, NotificationDatabase::class.java).build()
        try {
            val accountId = freshAccount()
            runBlocking(Dispatchers.IO) {
                database.notificationDao().saveState(
                    NotificationStateEntity(
                        accountId.stableFileName(),
                        """{"version":2,"unread":{"kind":"exact","count":7}}""",
                        1L,
                    ),
                )
            }
            writeLegacy(context, accountId, """{"version":2,"unread":{"kind":"exact","count":1}}""")
            val store = RoomNotificationStore(database, importer(context))

            val read = store.read(accountId) as NotificationStoreRead.Readable

            assertEquals(NotificationUnreadState.Exact(7), read.state.unreadState)
            assertEquals(
                """{"version":2,"unread":{"kind":"exact","count":1}}""",
                legacyFileFor(context, accountId).readText(),
            )
        } finally {
            runBlocking(Dispatchers.IO) { database.close() }
            cleanFiles(context)
        }
    }

    @Test
    fun crashAfterRoomCommitBeforeMarkerDoesNotOverwrite() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, NotificationDatabase::class.java).build()
        try {
            val accountId = freshAccount()
            writeLegacy(context, accountId, """{"version":2,"unread":{"kind":"exact","count":4}}""")
            val store = RoomNotificationStore(database, importer(context))
            assertTrue(store.read(accountId) is NotificationStoreRead.Readable)

            // Simulate a crash after the Room commit but before the marker write.
            markerFor(context, accountId).delete()
            writeLegacy(context, accountId, """{"version":2,"unread":{"kind":"exact","count":9}}""")

            val reread = store.read(accountId) as NotificationStoreRead.Readable

            assertEquals(NotificationUnreadState.Exact(4), reread.state.unreadState)
        } finally {
            runBlocking(Dispatchers.IO) { database.close() }
            cleanFiles(context)
        }
    }

    @Test
    fun malformedLegacyReturnsCorruptAndStaysUnmarked() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, NotificationDatabase::class.java).build()
        try {
            val accountId = freshAccount()
            writeLegacy(context, accountId, """{broken""")
            val store = RoomNotificationStore(database, importer(context))

            assertEquals(NotificationStoreRead.Corrupt, store.read(accountId))
            assertFalse(markerFor(context, accountId).exists())
            assertEquals("{broken", legacyFileFor(context, accountId).readText())
            assertEquals(NotificationStoreRead.Corrupt, store.read(accountId))
        } finally {
            runBlocking(Dispatchers.IO) { database.close() }
            cleanFiles(context)
        }
    }

    @Test
    fun futureLegacyReturnsUnsupportedAndStaysUnmarked() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, NotificationDatabase::class.java).build()
        try {
            val accountId = freshAccount()
            val future = """{"version":99,"items":[]}"""
            writeLegacy(context, accountId, future)
            val store = RoomNotificationStore(database, importer(context))

            assertEquals(NotificationStoreRead.Unsupported, store.read(accountId))
            assertFalse(markerFor(context, accountId).exists())
            assertEquals(future, legacyFileFor(context, accountId).readText())
        } finally {
            runBlocking(Dispatchers.IO) { database.close() }
            cleanFiles(context)
        }
    }

    @Test
    fun absentLegacyReturnsAbsentWithoutMarker() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, NotificationDatabase::class.java).build()
        try {
            val accountId = freshAccount()
            val store = RoomNotificationStore(database, importer(context))

            assertEquals(NotificationStoreRead.Absent, store.read(accountId))
            assertFalse(markerFor(context, accountId).exists())
        } finally {
            runBlocking(Dispatchers.IO) { database.close() }
            cleanFiles(context)
        }
    }

    @Test
    fun markerPresentBlocksReimport() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, NotificationDatabase::class.java).build()
        try {
            val accountId = freshAccount()
            writeLegacy(context, accountId, """{"version":2,"unread":{"kind":"exact","count":2}}""")
            val rawImporter = importer(context)
            assertTrue(rawImporter.markImported(accountId))
            val store = RoomNotificationStore(database, rawImporter)

            assertEquals(NotificationStoreRead.Absent, store.read(accountId))
            assertTrue(runBlocking(Dispatchers.IO) { database.notificationDao().state(accountId.stableFileName()) } == null)
        } finally {
            runBlocking(Dispatchers.IO) { database.close() }
            cleanFiles(context)
        }
    }

    @Test
    fun inaccessibleLegacyReturnsUnavailableWithoutMarker() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, NotificationDatabase::class.java).build()
        try {
            val accountId = freshAccount()
            val legacy = legacyFileFor(context, accountId)
            legacy.parentFile?.mkdirs()
            legacy.delete()
            assertTrue(legacy.mkdir())
            val store = RoomNotificationStore(database, importer(context))

            assertEquals(NotificationStoreRead.Unavailable, store.read(accountId))
            assertFalse(markerFor(context, accountId).exists())
        } finally {
            runBlocking(Dispatchers.IO) { database.close() }
            cleanFiles(context)
        }
    }

    @Test
    fun saveFailureReturnsUnavailableWithoutMarker() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val real = Room.inMemoryDatabaseBuilder(context, NotificationDatabase::class.java).build()
        try {
            val accountId = freshAccount()
            writeLegacy(context, accountId, """{"version":2,"unread":{"kind":"exact","count":5}}""")
            val failing = FailingSaveDatabase(failingSave = IOException("disk full"))
            val store = RoomNotificationStore(failing, importer(context))

            assertEquals(NotificationStoreRead.Unavailable, store.read(accountId))
            assertFalse(markerFor(context, accountId).exists())
        } finally {
            runBlocking(Dispatchers.IO) { real.close() }
            cleanFiles(context)
        }
    }

    @Test
    fun deleteSealsTheLegacyFile() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, NotificationDatabase::class.java).build()
        try {
            val accountId = freshAccount()
            writeLegacy(context, accountId, """{"version":2,"unread":{"kind":"exact","count":6}}""")
            val store = RoomNotificationStore(database, importer(context))
            assertTrue(store.read(accountId) is NotificationStoreRead.Readable)

            store.delete(accountId)

            assertTrue(markerFor(context, accountId).exists())
            assertFalse(legacyFileFor(context, accountId).exists())
            assertEquals(NotificationStoreRead.Absent, store.read(accountId))
        } finally {
            runBlocking(Dispatchers.IO) { database.close() }
            cleanFiles(context)
        }
    }

    @Test
    fun fileBackedRoomSurvivesCloseAndReopen() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "legacy-import-reopen-${UUID.randomUUID()}.db"
        context.deleteDatabase(name)
        val accountId = freshAccount()
        try {
            val first = Room.databaseBuilder(context, NotificationDatabase::class.java, name).build()
            try {
                val store = RoomNotificationStore(first, importer(context))
                store.write(accountId, NotificationRepositoryState(unreadState = NotificationUnreadState.Exact(8)))
            } finally {
                first.close()
            }
            val second = Room.databaseBuilder(context, NotificationDatabase::class.java, name).build()
            try {
                val reread = RoomNotificationStore(second, importer(context)).read(accountId)
                assertEquals(
                    NotificationUnreadState.Exact(8),
                    (reread as NotificationStoreRead.Readable).state.unreadState,
                )
            } finally {
                second.close()
            }
        } finally {
            context.deleteDatabase(name)
            cleanFiles(context)
        }
    }

    private fun freshAccount(): AccountId =
        AccountId(Connection("https://misskey.example", Protocol.MISSKEY), "legacy-${UUID.randomUUID()}").also {
            created.add(ApplicationProvider.getApplicationContext<Context>() to it)
        }

    private fun importer(context: Context) =
        LegacyNotificationFileImporter(context, FileNotificationStore(context))

    private fun legacyFileFor(context: Context, accountId: AccountId): File =
        File(File(context.noBackupFilesDir, "notifications"), "${accountId.stableFileName()}.json")

    private fun markerFor(context: Context, accountId: AccountId): File =
        File(File(context.noBackupFilesDir, "notifications"), "${accountId.stableFileName()}.room-imported")

    private fun writeLegacy(context: Context, accountId: AccountId, json: String) {
        val file = legacyFileFor(context, accountId)
        file.parentFile?.mkdirs()
        if (file.isDirectory) file.deleteRecursively()
        markerFor(context, accountId).delete()
        file.writeText(json)
    }

    private fun cleanFiles(context: Context) {
        // Delete only accounts created here. Other suites share the Robolectric directory.
        created.filter { it.first === context || it.first == context }.map { it.second }.forEach { accountId ->
            val legacy = legacyFileFor(context, accountId)
            if (legacy.isDirectory) legacy.deleteRecursively() else legacy.delete()
            markerFor(context, accountId).delete()
        }
    }

    private class FailingSaveDatabase(
        private val failingSave: IOException,
    ) : NotificationDatabase() {
        private val fakeDao = object : NotificationDao {
            override fun state(accountKey: String) = null
            override fun saveState(value: NotificationStateEntity): Unit = throw failingSave
            override fun deleteState(accountKey: String) = Unit
            override fun deleteEvents(accountKey: String) = Unit
            override fun deleteActors(accountKey: String) = Unit
            override fun deleteGroups(accountKey: String) = Unit
            override fun deleteQueryState(accountKey: String) = Unit
            override fun deleteDismissals(accountKey: String) = Unit
            override fun deleteDelivery(accountKey: String) = Unit
            override fun deleteAcknowledgements(accountKey: String) = Unit
            override fun deletePushRegistration(accountKey: String) = Unit
            override fun deleteSettings(accountKey: String) = Unit
        }

        override fun notificationDao(): NotificationDao = fakeDao

        override fun createInvalidationTracker(): androidx.room.InvalidationTracker =
            throw UnsupportedOperationException("Not used in import tests")

        override fun clearAllTables(): Unit = throw UnsupportedOperationException("Not used in import tests")
    }
}
