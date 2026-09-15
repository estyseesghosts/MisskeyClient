package me.foxtails.palustris.data.notifications

import android.content.Context
import android.util.AtomicFile
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import me.foxtails.palustris.data.notifications.db.NotificationDatabase
import me.foxtails.palustris.data.notifications.db.NotificationStateEntity
import me.foxtails.palustris.domain.AccountId
import org.json.JSONException
import org.json.JSONObject

interface NotificationStore {
    fun read(accountId: AccountId): NotificationStoreRead
    fun write(accountId: AccountId, state: NotificationRepositoryState)
    fun delete(accountId: AccountId)
}

class InMemoryNotificationStore : NotificationStore {
    private val values = mutableMapOf<AccountId, NotificationRepositoryState>()
    override fun read(accountId: AccountId): NotificationStoreRead =
        values[accountId]?.let(NotificationStoreRead::Readable) ?: NotificationStoreRead.Absent
    override fun write(accountId: AccountId, state: NotificationRepositoryState) { values[accountId] = state }
    override fun delete(accountId: AccountId) { values.remove(accountId) }
}

class FileNotificationStore @javax.inject.Inject constructor(
    @ApplicationContext context: Context,
) : NotificationStore {
    private val directory = File(context.noBackupFilesDir, "notifications")
    override fun read(accountId: AccountId): NotificationStoreRead {
        val file = fileFor(accountId)
        if (!file.baseFile.exists()) return NotificationStoreRead.Absent
        return try {
            val json = JSONObject(String(file.readFully(), Charsets.UTF_8))
            if (json.isFutureNotificationStateVersion()) return NotificationStoreRead.Unsupported
            val state = decode(json)
            if (state.hasReceivingAccount(accountId)) {
                NotificationStoreRead.Readable(state)
            } else {
                NotificationStoreRead.Corrupt
            }
        } catch (error: JSONException) {
            NotificationStoreRead.Corrupt
        } catch (error: IllegalArgumentException) {
            NotificationStoreRead.Corrupt
        } catch (error: IOException) {
            NotificationStoreRead.Unavailable
        }
    }

    override fun write(accountId: AccountId, state: NotificationRepositoryState) {
        directory.mkdirs()
        val file = fileFor(accountId)
        val stream = file.startWrite()
        try {
            stream.write(encode(state).toString().toByteArray(Charsets.UTF_8))
            file.finishWrite(stream)
        } catch (error: Exception) {
            file.failWrite(stream)
            throw error
        }
    }

    override fun delete(accountId: AccountId) { fileFor(accountId).delete() }
    private fun fileFor(accountId: AccountId) = AtomicFile(File(directory, "${accountId.stableFileName()}.json"))
}

class RoomNotificationStore @javax.inject.Inject constructor(
    private val database: NotificationDatabase,
    private val importer: LegacyNotificationFileImporter,
) : NotificationStore {
    private val dao = database.notificationDao()

    /**
     * Restart-safe legacy import. The Room row is authoritative: when present it
     * wins over any legacy file. Otherwise the legacy file is read first, saved to
     * Room, and only then marked. A failed save or a transient legacy failure stays
     * unmarked so the next restart retries. Corrupt and future-format rows keep
     * their bytes and never reach Room. The marker and Room cannot commit
     * atomically; the ordering above keeps retries idempotent instead.
     */
    override fun read(accountId: AccountId): NotificationStoreRead = runBlocking(Dispatchers.IO) {
        val key = accountId.stableFileName()
        val row = try {
            dao.state(key)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            return@runBlocking NotificationStoreRead.Unavailable
        }
        if (row != null) return@runBlocking decodeRow(row.stateJson, accountId)
        if (importer.isMarked(accountId)) return@runBlocking NotificationStoreRead.Absent
        val legacy = try {
            importer.readLegacy(accountId)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            return@runBlocking NotificationStoreRead.Unavailable
        }
        val imported = legacy as? NotificationStoreRead.Readable
            ?: return@runBlocking legacy
        try {
            dao.saveState(NotificationStateEntity(key, encode(imported.state).toString(), System.currentTimeMillis()))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            return@runBlocking NotificationStoreRead.Unavailable
        }
        // Best effort. A missing marker never reimports over the saved Room row.
        try {
            importer.markImported(accountId)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            // Ignore. The Room row is authoritative on retry.
        }
        NotificationStoreRead.Readable(imported.state)
    }

    override fun write(accountId: AccountId, state: NotificationRepositoryState) = runBlocking(Dispatchers.IO) {
        dao.saveState(NotificationStateEntity(accountId.stableFileName(), encode(state).toString(), System.currentTimeMillis()))
    }

    override fun delete(accountId: AccountId) = runBlocking(Dispatchers.IO) {
        val key = accountId.stableFileName()
        database.runInTransaction {
            dao.deleteState(key); dao.deleteEvents(key); dao.deleteActors(key); dao.deleteGroups(key)
            dao.deleteQueryState(key); dao.deleteDismissals(key); dao.deleteDelivery(key)
            dao.deleteAcknowledgements(key); dao.deletePushRegistration(key); dao.deleteSettings(key)
        }
        // Seal the legacy file so a removed account cannot resurrect old state.
        try {
            importer.deleteLegacyForRemoval(accountId)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            // Best effort. Memory removal stays authoritative.
        }
    }

    /**
     * A malformed or foreign-owned row is corrupt, not unavailable. A newer format is unsupported,
     * not corrupt. The original row stays untouched in every non-readable case.
     */
    private fun decodeRow(json: String, accountId: AccountId): NotificationStoreRead {
        return try {
            val parsed = JSONObject(json)
            if (parsed.isFutureNotificationStateVersion()) return NotificationStoreRead.Unsupported
            val state = decode(parsed)
            if (state.hasReceivingAccount(accountId)) {
                NotificationStoreRead.Readable(state)
            } else {
                NotificationStoreRead.Corrupt
            }
        } catch (error: JSONException) {
            NotificationStoreRead.Corrupt
        } catch (error: IllegalArgumentException) {
            NotificationStoreRead.Corrupt
        }
    }
}
