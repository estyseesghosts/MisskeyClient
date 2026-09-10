package me.foxtails.palustris

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import me.foxtails.palustris.data.notifications.InMemoryNotificationStore
import me.foxtails.palustris.data.notifications.NotificationRepository
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.EntityId
import me.foxtails.palustris.domain.Event
import me.foxtails.palustris.domain.Notification
import me.foxtails.palustris.domain.NotificationAcknowledgement
import me.foxtails.palustris.domain.NotificationActivity
import me.foxtails.palustris.domain.NotificationCheckpoint
import me.foxtails.palustris.domain.NotificationDeliveryState
import me.foxtails.palustris.domain.NotificationPage
import me.foxtails.palustris.domain.NotificationPageDirection
import me.foxtails.palustris.domain.NotificationQuery
import me.foxtails.palustris.domain.NotificationReadStatus
import me.foxtails.palustris.domain.NotificationSyncToken
import me.foxtails.palustris.domain.NotificationSyncCompleteness
import me.foxtails.palustris.domain.NotificationUnreadState
import me.foxtails.palustris.domain.Protocol
import me.foxtails.palustris.domain.SocialEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotEquals
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

    @Test
    fun acknowledgementSeparatesServerReadAndAndroidPresentationState() = runBlocking {
        val repository = NotificationRepository(InMemoryNotificationStore())
        val token = NotificationSyncToken(account, 1)
        repository.activate(token)
        val item = notification("one", NotificationActivity.Mention)
        repository.ingest(token, NotificationPage(listOf(item), unreadState = NotificationUnreadState.Exact(1)))

        assertTrue(repository.markPresented(token, item.id))
        assertTrue(repository.acknowledge(
            token,
            NotificationAcknowledgement(account, NotificationUnreadState.None, 20),
        ))

        val readState = repository.observe(account).value.items.single().readState
        assertEquals(NotificationReadStatus.Read, readState.status)
        assertTrue(readState.serverAcknowledged)
        assertTrue(readState.androidPresented)
    }

    @Test
    fun androidDismissalIsPersistedWithoutMarkingNotificationRead() = runBlocking {
        val store = InMemoryNotificationStore()
        val repository = NotificationRepository(store)
        val token = NotificationSyncToken(account, 1)
        repository.activate(token)
        val item = notification("swiped", NotificationActivity.Mention)
        repository.ingest(token, NotificationPage(listOf(item), unreadState = NotificationUnreadState.Exact(1)))

        assertTrue(repository.markAndroidDismissed(account, item.id))

        val readState = repository.observe(account).value.items.single().readState
        assertEquals(NotificationReadStatus.Unknown, readState.status)
        assertFalse(readState.serverAcknowledged)
        assertFalse(readState.androidPresented)
        assertTrue(readState.androidDismissed)
        assertTrue(NotificationRepository(store).observe(account).value.items.single().readState.androidDismissed)
    }

    @Test
    fun localDismissalIsAStableTombstoneAcrossRepositoryRecreation() = runBlocking {
        val store = InMemoryNotificationStore()
        val first = NotificationRepository(store)
        val token = NotificationSyncToken(account, 1)
        first.activate(token)
        val item = notification("dismissed", NotificationActivity.Follow)
        first.establishBaseline(token, NotificationPage(
            items = listOf(item),
            checkpoint = NotificationCheckpoint(account, NotificationQuery()),
        ))
        first.dismissFromInbox(token, item.id, remoteApplied = false)

        val restarted = NotificationRepository(store)
        restarted.activate(token)
        restarted.establishBaseline(token, NotificationPage(
            items = listOf(item),
            checkpoint = NotificationCheckpoint(account, NotificationQuery()),
        ))

        assertTrue(restarted.observe(account).value.items.isEmpty())
        assertTrue(item.id in restarted.observe(account).value.dismissedIds)
    }

    @Test
    fun olderAndNewerWritesAdvanceOnlyTheirOwnBoundary() = runBlocking {
        val repository = NotificationRepository(InMemoryNotificationStore())
        val token = NotificationSyncToken(account, 1)
        val query = NotificationQuery()
        repository.activate(token)
        repository.establishBaseline(token, NotificationPage(
            items = listOf(notification("initial", NotificationActivity.Mention)),
            checkpoint = NotificationCheckpoint(
                account,
                query,
                newest = me.foxtails.palustris.domain.NotificationCursor("newest-1"),
                oldest = me.foxtails.palustris.domain.NotificationCursor("oldest-1"),
                capturedAtEpochMillis = 1,
            ),
            direction = NotificationPageDirection.Initial,
        ))
        repository.ingestOlderPage(token, NotificationPage(
            items = listOf(notification("older", NotificationActivity.Mention)),
            olderCursor = me.foxtails.palustris.domain.NotificationCursor("oldest-2"),
            checkpoint = NotificationCheckpoint(
                account,
                query,
                newest = me.foxtails.palustris.domain.NotificationCursor("should-not-win"),
                oldest = me.foxtails.palustris.domain.NotificationCursor("oldest-2"),
                capturedAtEpochMillis = 2,
            ),
            direction = NotificationPageDirection.Older,
        ))
        assertEquals(
            "newest-1",
            repository.checkpoint(account, query)?.newest?.value,
        )
        assertEquals("oldest-2", repository.checkpoint(account, query)?.oldest?.value)

        repository.ingestNewerPage(token, NotificationPage(
            items = listOf(notification("newer", NotificationActivity.Mention)),
            newerCursor = me.foxtails.palustris.domain.NotificationCursor("newest-2"),
            checkpoint = NotificationCheckpoint(
                account,
                query,
                newest = me.foxtails.palustris.domain.NotificationCursor("newest-2"),
                oldest = me.foxtails.palustris.domain.NotificationCursor("should-not-win"),
                capturedAtEpochMillis = 3,
            ),
            direction = NotificationPageDirection.Newer,
        ))
        assertEquals("newest-2", repository.checkpoint(account, query)?.newest?.value)
        assertEquals("oldest-2", repository.checkpoint(account, query)?.oldest?.value)
    }

    @Test
    fun streamArrivalPreservesEachCachedRowsReadState() = runBlocking {
        val repository = NotificationRepository(InMemoryNotificationStore())
        val token = NotificationSyncToken(account, 1)
        val alreadyRead = notification("read", NotificationActivity.Follow).copy(
            readState = me.foxtails.palustris.domain.NotificationReadState(NotificationReadStatus.Read),
        )
        repository.activate(token)
        repository.establishBaseline(token, NotificationPage(listOf(alreadyRead)))

        assertTrue(repository.applyStreamEvent(token, Event(
            account,
            SocialEvent.NotificationReceived(notification("new", NotificationActivity.Mention)),
        )))

        assertEquals(
            NotificationReadStatus.Read,
            repository.observe(account).value.items.single { it.id == alreadyRead.id }.readState.status,
        )
    }

    @Test
    fun olderHistoryAndOverlappingPagesDoNotCreateAudibleDeliveries() = runBlocking {
        val repository = NotificationRepository(InMemoryNotificationStore())
        val token = NotificationSyncToken(account, 1)
        val query = NotificationQuery()
        val baseline = notification("baseline", NotificationActivity.Mention)
        repository.activate(token)
        repository.establishBaseline(token, NotificationPage(
            items = listOf(baseline),
            checkpoint = NotificationCheckpoint(account, query),
        ))

        repository.ingestOlderPage(token, NotificationPage(
            items = listOf(notification("older", NotificationActivity.Follow)),
            olderCursor = me.foxtails.palustris.domain.NotificationCursor("older-next"),
            checkpoint = NotificationCheckpoint(
                account,
                query,
                oldest = me.foxtails.palustris.domain.NotificationCursor("older-next"),
            ),
            direction = NotificationPageDirection.Older,
        ))
        repository.ingestNewerPage(token, NotificationPage(
            items = listOf(baseline, notification("newer", NotificationActivity.Reply)),
            newerCursor = me.foxtails.palustris.domain.NotificationCursor("newer-next"),
            checkpoint = NotificationCheckpoint(
                account,
                query,
                newest = me.foxtails.palustris.domain.NotificationCursor("newer-next"),
            ),
            direction = NotificationPageDirection.Newer,
        ))

        assertEquals(listOf("newer"), repository.pendingDeliveries(account).map { it.notificationId.value })
    }

    @Test
    fun filteredQueriesKeepIndependentCheckpointsAndCompleteness() = runBlocking {
        val repository = NotificationRepository(InMemoryNotificationStore())
        val token = NotificationSyncToken(account, 1)
        val mentions = NotificationQuery(setOf(me.foxtails.palustris.domain.NotificationCategory.Mentions))
        val social = NotificationQuery(setOf(me.foxtails.palustris.domain.NotificationCategory.Social))
        repository.activate(token)
        repository.establishBaseline(token, NotificationPage(
            items = listOf(notification("mention", NotificationActivity.Mention)),
            checkpoint = NotificationCheckpoint(account, mentions, oldest = me.foxtails.palustris.domain.NotificationCursor("mentions-old")),
        ))
        repository.ingestOlderPage(token, NotificationPage(
            items = listOf(notification("social", NotificationActivity.Follow)),
            olderCursor = me.foxtails.palustris.domain.NotificationCursor("social-old"),
            checkpoint = NotificationCheckpoint(account, social, oldest = me.foxtails.palustris.domain.NotificationCursor("social-old")),
            direction = NotificationPageDirection.Older,
        ))

        assertEquals("mentions-old", repository.checkpoint(account, mentions)?.oldest?.value)
        assertEquals("social-old", repository.checkpoint(account, social)?.oldest?.value)
        assertEquals(1, repository.observeInbox(account, mentions).first().items.size)
        assertEquals(1, repository.observeInbox(account, social).first().items.size)
    }

    @Test
    fun olderContinuationMovesFromIncompleteToTerminalWithoutRestoringTheCursor() = runBlocking {
        val repository = NotificationRepository(InMemoryNotificationStore())
        val token = NotificationSyncToken(account, 1)
        val query = NotificationQuery()
        repository.activate(token)
        repository.establishBaseline(token, NotificationPage(
            items = listOf(notification("baseline", NotificationActivity.Mention)),
            checkpoint = NotificationCheckpoint(account, query, oldest = me.foxtails.palustris.domain.NotificationCursor("old-1")),
        ))
        repository.ingestOlderPage(token, NotificationPage(
            items = listOf(notification("older", NotificationActivity.Mention)),
            olderCursor = me.foxtails.palustris.domain.NotificationCursor("old-2"),
            checkpoint = NotificationCheckpoint(account, query, oldest = me.foxtails.palustris.domain.NotificationCursor("old-2")),
            direction = NotificationPageDirection.Older,
        ))
        assertEquals(NotificationSyncCompleteness.Incomplete, repository.checkpoint(account, query)?.completeness)

        repository.ingestOlderPage(token, NotificationPage(
            items = emptyList(),
            checkpoint = NotificationCheckpoint(account, query),
            direction = NotificationPageDirection.Older,
            reachedBoundary = true,
        ))
        assertEquals(NotificationSyncCompleteness.Complete, repository.checkpoint(account, query)?.completeness)
        assertEquals(null, repository.checkpoint(account, query)?.oldest)
    }

    @Test
    fun streamDuplicatesCreateOneDeliveryAndDismissalSurvivesRedelivery() = runBlocking {
        val repository = NotificationRepository(InMemoryNotificationStore())
        val token = NotificationSyncToken(account, 1)
        val query = NotificationQuery()
        val baseline = notification("baseline", NotificationActivity.Mention)
        val incoming = notification("stream", NotificationActivity.Reply)
        repository.activate(token)
        repository.establishBaseline(token, NotificationPage(
            items = listOf(baseline),
            checkpoint = NotificationCheckpoint(account, query),
        ))

        val event = Event(account, SocialEvent.NotificationReceived(incoming))
        assertTrue(repository.applyStreamEvent(token, event))
        assertTrue(repository.applyStreamEvent(token, event))
        assertEquals(listOf("stream"), repository.pendingDeliveries(account).map { it.notificationId.value })

        assertTrue(repository.dismissFromInbox(token, incoming.id, remoteApplied = false))
        assertTrue(repository.applyStreamEvent(token, event))
        assertTrue(repository.observe(account).value.items.none { it.id == incoming.id })
        assertTrue(repository.pendingDeliveries(account).isEmpty())
    }

    @Test
    fun expiredDeliveryClaimCanBeRecoveredAndOldClaimCannotFinishIt() = runBlocking {
        val repository = NotificationRepository(InMemoryNotificationStore())
        val token = NotificationSyncToken(account, 1)
        val query = NotificationQuery()
        val incoming = notification("incoming", NotificationActivity.Mention)
        repository.activate(token)
        repository.establishBaseline(token, NotificationPage(
            items = listOf(notification("baseline", NotificationActivity.Mention)),
            checkpoint = NotificationCheckpoint(account, query),
        ))
        repository.ingestNewerPage(token, NotificationPage(
            items = listOf(incoming),
            checkpoint = NotificationCheckpoint(account, query),
            direction = NotificationPageDirection.Newer,
        ))

        val first = repository.claimDelivery(token, incoming.id, nowEpochMillis = 100L)
        assertNotNull(first)
        assertTrue(repository.pendingDeliveries(account, nowEpochMillis = first!!.claimExpiresAtEpochMillis - 1).isEmpty())
        assertFalse(repository.finishDelivery(
            token,
            incoming.id,
            NotificationDeliveryState.Presented,
            claimId = "stale-claim",
        ))

        val recovered = repository.claimDelivery(
            token,
            incoming.id,
            nowEpochMillis = first.claimExpiresAtEpochMillis,
        )
        assertNotNull(recovered)
        assertNotEquals(first.claimId, recovered!!.claimId)
        assertTrue(repository.finishDelivery(
            token,
            incoming.id,
            NotificationDeliveryState.Presented,
            claimId = recovered.claimId,
        ))
        assertTrue(repository.pendingDeliveries(account).isEmpty())
    }

    @Test
    fun notificationRepositoryIsApplicationSingleton() {
        assertNotNull(NotificationRepository::class.java.getAnnotation(javax.inject.Singleton::class.java))
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
