package me.foxtails.palustris.data.notifications

import android.content.Context
import android.util.AtomicFile
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import me.foxtails.palustris.data.notifications.db.NotificationDatabase
import me.foxtails.palustris.data.notifications.db.NotificationStateEntity
import me.foxtails.palustris.domain.AccountId
import org.json.JSONObject

interface NotificationStore {
    fun read(accountId: AccountId): NotificationRepositoryState?
    fun write(accountId: AccountId, state: NotificationRepositoryState)
    fun delete(accountId: AccountId)
}

class InMemoryNotificationStore : NotificationStore {
    private val values = mutableMapOf<AccountId, NotificationRepositoryState>()
    override fun read(accountId: AccountId): NotificationRepositoryState? = values[accountId]
    override fun write(accountId: AccountId, state: NotificationRepositoryState) { values[accountId] = state }
    override fun delete(accountId: AccountId) { values.remove(accountId) }
}

class FileNotificationStore @javax.inject.Inject constructor(
    @ApplicationContext context: Context,
) : NotificationStore {
    private val directory = File(context.noBackupFilesDir, "notifications")
    override fun read(accountId: AccountId): NotificationRepositoryState? = runCatching {
        val file = fileFor(accountId)
        if (!file.baseFile.exists()) return null
        decode(JSONObject(String(file.readFully(), Charsets.UTF_8)))
    }.getOrNull()
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
    override fun read(accountId: AccountId): NotificationRepositoryState? = runBlocking(Dispatchers.IO) {
        val key = accountId.stableFileName()
        dao.state(key)?.let { return@runBlocking decode(JSONObject(it.stateJson)) }
        importer.importIfPresent(accountId)?.also { imported ->
            dao.saveState(NotificationStateEntity(key, encode(imported).toString(), System.currentTimeMillis()))
        }
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
    }
}
