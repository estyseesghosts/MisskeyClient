package me.foxtails.palustris

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import me.foxtails.palustris.data.notifications.FileNotificationStore
import me.foxtails.palustris.data.notifications.LegacyNotificationFileImporter
import me.foxtails.palustris.data.notifications.NotificationStoreRead
import me.foxtails.palustris.data.notifications.RoomNotificationStore
import me.foxtails.palustris.data.notifications.decode
import me.foxtails.palustris.data.notifications.db.NotificationDatabase
import me.foxtails.palustris.data.notifications.db.NotificationStateEntity
import me.foxtails.palustris.data.notifications.stableFileName
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.NotificationActivity
import me.foxtails.palustris.domain.NotificationDestination
import me.foxtails.palustris.domain.NotificationPushRegistrationState
import me.foxtails.palustris.domain.NotificationUnreadState
import me.foxtails.palustris.domain.Protocol
import me.foxtails.palustris.domain.ValidatedUrl
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The Room store must decode the frozen JSON even when the row came from an older writer.
 * The fixtures are inserted directly instead of through `RoomNotificationStore.write`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class NotificationRoomStoreFixtureTest {
    @Test
    fun roomStoreDecodesFixedJsonInsertedDirectly() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, NotificationDatabase::class.java).build()
        try {
            val accountId = AccountId(Connection("https://misskey.example", Protocol.MISSKEY), "receiver")
            runBlocking(Dispatchers.IO) {
                database.notificationDao().saveState(
                    NotificationStateEntity(accountId.stableFileName(), fixtureText("complete_current_state.json"), 1L),
                )
            }
            val store = RoomNotificationStore(database, importer(context))

            val state = (store.read(accountId) as NotificationStoreRead.Readable).state

            assertEquals(2, state.items.size)
            assertEquals(NotificationUnreadState.AtLeast(2), state.unreadState)
            assertEquals(NotificationPushRegistrationState.Connected, state.pushRegistration?.state)
        } finally {
            runBlocking(Dispatchers.IO) { database.close() }
        }
    }

    @Test
    fun roomStoreWriteRoundTripsThroughRoom() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, NotificationDatabase::class.java).build()
        try {
            val accountId = AccountId(Connection("https://misskey.example", Protocol.MISSKEY), "receiver")
            val store = RoomNotificationStore(database, importer(context))
            val original = decode(JSONObject(fixtureText("complete_current_state.json")))

            store.write(accountId, original)

            val row = runBlocking(Dispatchers.IO) { database.notificationDao().state(accountId.stableFileName()) }
            assertNotNull(row)
            assertEquals(original, decode(JSONObject(row!!.stateJson)))
        } finally {
            runBlocking(Dispatchers.IO) { database.close() }
        }
    }

    @Test
    fun roomStoreReportsBrokenJsonAsCorrupt() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, NotificationDatabase::class.java).build()
        try {
            val accountId = AccountId(Connection("https://misskey.example", Protocol.MISSKEY), "receiver")
            runBlocking(Dispatchers.IO) {
                database.notificationDao().saveState(
                    NotificationStateEntity(accountId.stableFileName(), fixtureText("malformed_broken.json"), 1L),
                )
            }
            val store = RoomNotificationStore(database, importer(context))

            assertEquals(NotificationStoreRead.Corrupt, store.read(accountId))
        } finally {
            runBlocking(Dispatchers.IO) { database.close() }
        }
    }

    @Test
    fun roomStorePreservesNestedUnknownServerDestination() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, NotificationDatabase::class.java).build()
        try {
            val accountId = AccountId(Connection("https://misskey.example", Protocol.MISSKEY), "receiver")
            runBlocking(Dispatchers.IO) {
                database.notificationDao().saveState(
                    NotificationStateEntity(accountId.stableFileName(), fixtureText("activity_variants.json"), 1L),
                )
            }
            val store = RoomNotificationStore(database, importer(context))

            val activity = (store.read(accountId) as NotificationStoreRead.Readable).state.items
                .first { it.id.value == "a-unknown" }.activity as NotificationActivity.Unknown

            assertEquals(
                NotificationDestination.Server(ValidatedUrl.https("https://misskey.example/notice/9")!!),
                activity.validatedDestination,
            )
        } finally {
            runBlocking(Dispatchers.IO) { database.close() }
        }
    }

    private fun importer(context: Context) =
        LegacyNotificationFileImporter(context, FileNotificationStore(context))

    private fun fixtureText(name: String): String {
        val stream = javaClass.getResourceAsStream("/notifications/$name")
            ?: error("Missing notification fixture: $name")
        return stream.use { String(it.readBytes(), Charsets.UTF_8) }
    }
}
