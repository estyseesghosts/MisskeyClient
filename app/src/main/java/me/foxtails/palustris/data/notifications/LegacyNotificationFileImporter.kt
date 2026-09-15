package me.foxtails.palustris.data.notifications

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import me.foxtails.palustris.domain.AccountId

/** Imports the pre-Room atomic files once. Reading never marks; the Room owner marks after save. */
@Singleton
class LegacyNotificationFileImporter @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val legacyStore: FileNotificationStore,
) {
    private val markerDirectory = File(context.noBackupFilesDir, "notifications")

    fun isMarked(accountId: AccountId): Boolean = markerFor(accountId).exists()

    /**
     * Reads the legacy file without touching the marker. A transient failure stays
     * unmarked so a later restart can retry. Cancellation propagates.
     */
    fun readLegacy(accountId: AccountId): NotificationStoreRead {
        try {
            return legacyStore.read(accountId)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            return NotificationStoreRead.Unavailable
        }
    }

    /**
     * Records a completed import. Returns false when the marker write fails; the
     * Room row stays authoritative so a missing marker never reimports over it.
     */
    fun markImported(accountId: AccountId): Boolean {
        try {
            markerDirectory.mkdirs()
            markerFor(accountId).writeText("imported", Charsets.UTF_8)
            return true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            return false
        }
    }

    /**
     * Seals a removed account. Deletes the legacy file and marks the account so an
     * old atomic file cannot resurrect local state later. Best effort.
     */
    fun deleteLegacyForRemoval(accountId: AccountId) {
        try {
            legacyStore.delete(accountId)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            // Best effort. The marker below still blocks resurrection.
        }
        markImported(accountId)
    }

    private fun markerFor(accountId: AccountId): File =
        File(markerDirectory, "${accountId.stableFileName()}.room-imported")
}

