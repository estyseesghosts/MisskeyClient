package me.foxtails.palustris

import kotlinx.coroutines.runBlocking
import me.foxtails.palustris.data.notifications.InMemoryNotificationStore
import me.foxtails.palustris.data.notifications.NotificationRepository
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.EntityId
import me.foxtails.palustris.domain.Notification
import me.foxtails.palustris.domain.NotificationActivity
import me.foxtails.palustris.domain.NotificationCheckpoint
import me.foxtails.palustris.domain.NotificationPage
import me.foxtails.palustris.domain.NotificationQuery
import me.foxtails.palustris.domain.NotificationReadStatus
import me.foxtails.palustris.domain.NotificationSyncToken
import me.foxtails.palustris.domain.NotificationUnreadState
import me.foxtails.palustris.domain.Protocol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationRepositoryTest {
    private val account = AccountId(Connection("https://example.org", Protocol.MISSKEY), "receiver")

    @Test
    fun duplicatePagesMergeWithoutLosingLocalSeenOrUnreadPrecision() = runBlocking {
        val repository = NotificationRepository(InMemoryNotificationStore())
        val token = NotificationSyncToken(account, 1)
        repository.activate(token)
        val notification = notification("one", NotificationActivity.Reply)
        val page = NotificationPage(
            items = listOf(notification),
            checkpoint = NotificationCheckpoint(account, NotificationQuery(), capturedAtEpochMillis = 10),
            unreadState = NotificationUnreadState.Exact(1),
        )

        assertTrue(repository.ingest(token, page))
        assertTrue(repository.markSeen(token, notification.id))
        val unreadPage = page.copy(
            items = listOf(notification.copy(
                readState = notification.readState.copy(status = NotificationReadStatus.Unread),
            )),
        )
        assertTrue(repository.ingest(token, unreadPage))

        val state = repository.observe(account).value
        assertEquals(1, state.items.size)
        assertEquals(NotificationReadStatus.Unread, state.items.single().readState.status)
        assertTrue(state.items.single().readState.locallySeen)
        assertEquals(NotificationUnreadState.Exact(1), state.unreadState)
    }

    @Test
    fun invalidatedGenerationCannotRecreateRemovedAccountRows() = runBlocking {
        val repository = NotificationRepository(InMemoryNotificationStore())
        val token = NotificationSyncToken(account, 4)
        repository.activate(token)
        repository.invalidate(account, token.generation)

        assertFalse(repository.ingest(token, NotificationPage(listOf(notification("late", NotificationActivity.Follow)))))
        assertTrue(repository.observe(account).value.items.isEmpty())
    }

    private fun notification(id: String, activity: NotificationActivity) = Notification(
        id = EntityId(account.connection.origin, id),
        accountId = account,
        createdAtEpochMillis = 100,
        activity = activity,
        actors = listOf(Account(account, "Receiver", "@receiver@example.org")),
        post = null,
        rawType = id,
    )
}
