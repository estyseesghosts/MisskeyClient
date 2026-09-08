package me.foxtails.palustris.data.notifications

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import me.foxtails.palustris.domain.AccountId

/** Imports the pre-Room atomic files once; malformed rows are handled by the legacy decoder. */
@Singleton
class LegacyNotificationFileImporter @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val legacyStore: FileNotificationStore,
) {
    private val markerDirectory = File(context.noBackupFilesDir, "notifications")

    @Synchronized
    fun importIfPresent(accountId: AccountId): NotificationRepositoryState? {
        val marker = markerFor(accountId)
        if (marker.exists()) return null
        val state = legacyStore.read(accountId)
        markerDirectory.mkdirs()
        marker.writeText("imported", Charsets.UTF_8)
        return state
    }

    private fun markerFor(accountId: AccountId): File =
        File(markerDirectory, "${accountId.stableFileName()}.room-imported")
}

