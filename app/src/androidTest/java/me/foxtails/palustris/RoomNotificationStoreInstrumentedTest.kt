package me.foxtails.palustris

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import me.foxtails.palustris.data.notifications.FileNotificationStore
import me.foxtails.palustris.data.notifications.LegacyNotificationFileImporter
import me.foxtails.palustris.data.notifications.NotificationRepositoryState
import me.foxtails.palustris.data.notifications.RoomNotificationStore
import me.foxtails.palustris.data.notifications.db.NotificationDatabase
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.EntityId
import me.foxtails.palustris.domain.NotificationUnreadState
import me.foxtails.palustris.domain.Protocol
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomNotificationStoreInstrumentedTest {
    private lateinit var database: NotificationDatabase
    private lateinit var store: RoomNotificationStore
    private val account = AccountId(Connection("https://fixture.example", Protocol.MISSKEY), "receiver")

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, NotificationDatabase::class.java).build()
        val legacyStore = FileNotificationStore(context)
        store = RoomNotificationStore(database, LegacyNotificationFileImporter(context, legacyStore))
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun stateRoundTripsThroughRoomAndDeleteRemovesIt() {
        val dismissed = EntityId(account.connection.origin, "dismissed")
        val state = NotificationRepositoryState(
            unreadState = NotificationUnreadState.Exact(2),
            dismissedIds = setOf(dismissed),
        )

        store.write(account, state)

        val restored = store.read(account)
        assertEquals(NotificationUnreadState.Exact(2), restored?.unreadState)
        assertEquals(setOf(dismissed), restored?.dismissedIds)

        store.delete(account)
        assertEquals(null, store.read(account))
    }
}
