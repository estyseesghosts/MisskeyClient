package me.foxtails.palustris

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import me.foxtails.palustris.data.notifications.InMemoryNotificationStore
import me.foxtails.palustris.data.notifications.NotificationIngestRequest
import me.foxtails.palustris.data.notifications.NotificationRepository
import me.foxtails.palustris.data.notifications.NotificationRepositoryState
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.EntityId
import me.foxtails.palustris.domain.NotificationCursor
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
import me.foxtails.palustris.domain.NotificationDestination
import me.foxtails.palustris.domain.ValidatedUrl
import me.foxtails.palustris.domain.Post
import me.foxtails.palustris.domain.PostInteractionCounts
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

        assertTrue(repository.establishBaseline(token, ingestRequest(page), page))
        assertTrue(repository.markSeen(token, notification.id))
        val unreadPage = page.copy(
            items = listOf(notification.copy(
                readState = notification.readState.copy(status = NotificationReadStatus.Unread),
            )),
        )
        assertTrue(repository.establishBaseline(token, ingestRequest(unreadPage), unreadPage))

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

        val late = NotificationPage(listOf(notification("late", NotificationActivity.Follow)))
        assertFalse(repository.establishBaseline(token, ingestRequest(late), late))
        assertTrue(repository.observe(account).value.items.isEmpty())
    }

    @Test
    fun acknowledgementSeparatesServerReadAndAndroidPresentationState() = runBlocking {
        val repository = NotificationRepository(InMemoryNotificationStore())
        val token = NotificationSyncToken(account, 1)
        repository.activate(token)
        val item = notification("one", NotificationActivity.Mention)
        val first = NotificationPage(listOf(item), unreadState = NotificationUnreadState.Exact(1))
        repository.establishBaseline(token, ingestRequest(first), first)

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
        val swiped = NotificationPage(listOf(item), unreadState = NotificationUnreadState.Exact(1))
        repository.establishBaseline(token, ingestRequest(swiped), swiped)

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
        first.establishBaseline(token, baselineRequest(), NotificationPage(
            items = listOf(item),
            checkpoint = NotificationCheckpoint(account, NotificationQuery()),
        ))
        first.dismissFromInbox(token, item.id, remoteApplied = false)

        val restarted = NotificationRepository(store)
        restarted.activate(token)
        restarted.establishBaseline(token, baselineRequest(), NotificationPage(
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
        repository.establishBaseline(token, baselineRequest(query), NotificationPage(
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
        repository.ingestOlderPage(token, olderRequest(query), NotificationPage(
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

        repository.ingestNewerPage(token, newerRequest(query), NotificationPage(
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
        repository.establishBaseline(token, baselineRequest(), NotificationPage(listOf(alreadyRead)))

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
        repository.establishBaseline(token, baselineRequest(query), NotificationPage(
            items = listOf(baseline),
            checkpoint = NotificationCheckpoint(account, query),
        ))

        repository.ingestOlderPage(token, olderRequest(query), NotificationPage(
            items = listOf(notification("older", NotificationActivity.Follow)),
            olderCursor = me.foxtails.palustris.domain.NotificationCursor("older-next"),
            checkpoint = NotificationCheckpoint(
                account,
                query,
                oldest = me.foxtails.palustris.domain.NotificationCursor("older-next"),
            ),
            direction = NotificationPageDirection.Older,
        ))
        repository.ingestNewerPage(token, newerRequest(query), NotificationPage(
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
        repository.establishBaseline(token, baselineRequest(mentions), NotificationPage(
            items = listOf(notification("mention", NotificationActivity.Mention)),
            checkpoint = NotificationCheckpoint(account, mentions, oldest = me.foxtails.palustris.domain.NotificationCursor("mentions-old")),
        ))
        repository.ingestOlderPage(token, olderRequest(social), NotificationPage(
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
        repository.establishBaseline(token, baselineRequest(query), NotificationPage(
            items = listOf(notification("baseline", NotificationActivity.Mention)),
            checkpoint = NotificationCheckpoint(account, query, oldest = me.foxtails.palustris.domain.NotificationCursor("old-1")),
        ))
        repository.ingestOlderPage(token, olderRequest(query), NotificationPage(
            items = listOf(notification("older", NotificationActivity.Mention)),
            olderCursor = me.foxtails.palustris.domain.NotificationCursor("old-2"),
            checkpoint = NotificationCheckpoint(account, query, oldest = me.foxtails.palustris.domain.NotificationCursor("old-2")),
            direction = NotificationPageDirection.Older,
        ))
        assertEquals(NotificationSyncCompleteness.Incomplete, repository.checkpoint(account, query)?.completeness)

        repository.ingestOlderPage(token, olderRequest(query), NotificationPage(
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
        repository.establishBaseline(token, baselineRequest(query), NotificationPage(
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
        repository.establishBaseline(token, baselineRequest(query), NotificationPage(
            items = listOf(notification("baseline", NotificationActivity.Mention)),
            checkpoint = NotificationCheckpoint(account, query),
        ))
        repository.ingestNewerPage(token, newerRequest(query), NotificationPage(
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

    @Test
    fun notificationPostPersistencePreservesAvailableInteractionCounts() = runBlocking {
        val store = InMemoryNotificationStore()
        val first = NotificationRepository(store)
        val token = NotificationSyncToken(account, 1)
        val post = Post(
            id = EntityId(account.connection.origin, "post"),
            author = Account(account, "Author", "@author@example.org"),
            text = "Post",
            publishedAtEpochMillis = 1,
            audience = me.foxtails.palustris.domain.Audience.Public,
            interactionCounts = PostInteractionCounts(
                favouriteCount = 0,
                reactionCount = 2,
                repostCount = 3,
                quoteRepostCount = 0,
                replyCount = 4,
            ),
        )
        first.activate(token)
        first.establishBaseline(token, baselineRequest(), NotificationPage(listOf(notification("post", NotificationActivity.Mention, post))))

        val restored = NotificationRepository(store).observe(account).value.items.single().post

        assertEquals(post.interactionCounts, restored?.interactionCounts)
    }

    @Test
    fun unknownActivityPreservesValidatedNestedDestination() = runBlocking {
        val store = InMemoryNotificationStore()
        val repository = NotificationRepository(store)
        val token = NotificationSyncToken(account, 1)
        repository.activate(token)
        val destination = NotificationDestination.Server(
            ValidatedUrl.https("https://example.org/activity/1")!!,
        )
        repository.establishBaseline(token, baselineRequest(), NotificationPage(
            listOf(notification("unknown", NotificationActivity.Unknown("Unknown", destination))),
        ))

        val restored = NotificationRepository(store).observe(account).value.items.single()
        val activity = restored.activity as NotificationActivity.Unknown
        assertEquals(destination, activity.validatedDestination)
    }

    @Test
    fun foreignCheckpointAndEntityOriginAreRejectedWithoutPublication() = runBlocking {
        val repository = NotificationRepository(InMemoryNotificationStore())
        val token = NotificationSyncToken(account, 1)
        repository.activate(token)
        val foreignAccount = AccountId(Connection("https://foreign.example", Protocol.MISSKEY), "receiver")

        assertFalse(repository.establishBaseline(token, baselineRequest(), NotificationPage(
            items = emptyList(),
            checkpoint = NotificationCheckpoint(foreignAccount, NotificationQuery()),
        )))
        assertFalse(repository.establishBaseline(token, baselineRequest(), NotificationPage(
            items = listOf(notification("foreign", NotificationActivity.Mention).copy(
                id = EntityId("https://foreign.example", "foreign"),
            )),
        )))
        assertTrue(repository.observe(account).value.items.isEmpty())
    }

    @Test
    fun mismatchedCheckpointQueryIsRejectedBothDirections() = runBlocking {
        val repository = NotificationRepository(InMemoryNotificationStore())
        val token = NotificationSyncToken(account, 1)
        val all = NotificationQuery()
        val mentions = NotificationQuery(setOf(me.foxtails.palustris.domain.NotificationCategory.Mentions))
        repository.activate(token)
        assertTrue(repository.establishBaseline(token, baselineRequest(all), NotificationPage(
            items = listOf(notification("base", NotificationActivity.Mention)),
            checkpoint = NotificationCheckpoint(account, all),
        )))

        assertFalse(repository.ingestNewerPage(token, newerRequest(all), NotificationPage(
            items = listOf(notification("foreign-query", NotificationActivity.Mention)),
            checkpoint = NotificationCheckpoint(account, mentions),
            direction = NotificationPageDirection.Newer,
        )))
        assertFalse(repository.ingestOlderPage(token, olderRequest(mentions), NotificationPage(
            items = listOf(notification("foreign-query", NotificationActivity.Mention)),
            checkpoint = NotificationCheckpoint(account, all),
            direction = NotificationPageDirection.Older,
        )))
        assertEquals(listOf("base"), repository.observe(account).value.items.map { it.id.value })
        assertEquals(null, repository.checkpoint(account, mentions))
    }

    @Test
    fun queryFieldVariationsAreRejectedIndependently() = runBlocking {
        val repository = NotificationRepository(InMemoryNotificationStore())
        val token = NotificationSyncToken(account, 1)
        val requested = NotificationQuery()
        repository.activate(token)
        assertTrue(repository.establishBaseline(token, baselineRequest(requested), NotificationPage(
            items = listOf(notification("base", NotificationActivity.Mention)),
            checkpoint = NotificationCheckpoint(account, requested),
        )))

        val limitVariant = NotificationQuery(limit = 10)
        val groupedVariant = NotificationQuery(grouped = true)
        assertFalse(repository.ingestNewerPage(token, newerRequest(requested), NotificationPage(
            items = listOf(notification("limit", NotificationActivity.Mention)),
            checkpoint = NotificationCheckpoint(account, limitVariant),
            direction = NotificationPageDirection.Newer,
        )))
        assertFalse(repository.ingestNewerPage(token, newerRequest(requested), NotificationPage(
            items = listOf(notification("grouped", NotificationActivity.Mention)),
            checkpoint = NotificationCheckpoint(account, groupedVariant),
            direction = NotificationPageDirection.Newer,
        )))
        assertEquals(listOf("base"), repository.observe(account).value.items.map { it.id.value })
    }

    @Test
    fun emptyPageWithForeignQueryIsRejected() = runBlocking {
        val repository = NotificationRepository(InMemoryNotificationStore())
        val token = NotificationSyncToken(account, 1)
        repository.activate(token)
        assertFalse(repository.establishBaseline(token, baselineRequest(), NotificationPage(
            items = emptyList(),
            checkpoint = NotificationCheckpoint(
                account,
                NotificationQuery(setOf(me.foxtails.palustris.domain.NotificationCategory.Mentions)),
            ),
        )))
        assertTrue(repository.observe(account).value.items.isEmpty())
        assertEquals(null, repository.checkpoint(account, NotificationQuery()))
    }

    @Test
    fun checkpointFreePageIsAcceptedOnlyUnderItsExplicitQuery() = runBlocking {
        val repository = NotificationRepository(InMemoryNotificationStore())
        val token = NotificationSyncToken(account, 1)
        val mentions = NotificationQuery(setOf(me.foxtails.palustris.domain.NotificationCategory.Mentions))
        repository.activate(token)
        val page = NotificationPage(
            items = listOf(notification("free", NotificationActivity.Mention)),
            unreadState = NotificationUnreadState.Exact(2),
        )
        assertTrue(repository.establishBaseline(token, baselineRequest(mentions), page))
        assertEquals(listOf("free"), repository.observe(account).value.items.map { it.id.value })
        assertEquals(NotificationUnreadState.Exact(2), repository.observe(account).value.unreadState)
        assertNotNull(repository.checkpoint(account, mentions))
        assertEquals(null, repository.checkpoint(account, NotificationQuery()))
    }

    @Test
    fun staleSameQueryBoundaryIsRejectedWithoutMovingCursors() = runBlocking {
        val repository = NotificationRepository(InMemoryNotificationStore())
        val token = NotificationSyncToken(account, 1)
        val query = NotificationQuery()
        repository.activate(token)
        assertTrue(repository.establishBaseline(token, baselineRequest(query), NotificationPage(
            items = listOf(notification("base", NotificationActivity.Mention)),
            checkpoint = NotificationCheckpoint(
                account,
                query,
                oldest = NotificationCursor("old-0"),
            ),
        )))
        assertTrue(repository.ingestOlderPage(token, olderRequest(query, NotificationCursor("old-0")), NotificationPage(
            items = listOf(notification("older-1", NotificationActivity.Mention)),
            olderCursor = NotificationCursor("old-1"),
            checkpoint = NotificationCheckpoint(account, query),
            direction = NotificationPageDirection.Older,
        )))
        assertFalse(repository.ingestOlderPage(token, olderRequest(query, NotificationCursor("old-0")), NotificationPage(
            items = listOf(notification("stale", NotificationActivity.Mention)),
            olderCursor = NotificationCursor("old-0"),
            checkpoint = NotificationCheckpoint(account, query),
            direction = NotificationPageDirection.Older,
        )))
        assertFalse(repository.ingestNewerPage(token, newerRequest(query, NotificationCursor("bogus")), NotificationPage(
            items = listOf(notification("stale-new", NotificationActivity.Mention)),
            checkpoint = NotificationCheckpoint(account, query),
            direction = NotificationPageDirection.Newer,
        )))
        assertEquals("old-1", repository.checkpoint(account, query)?.olderContinuation?.value)
        assertTrue(repository.observe(account).value.items.none { it.id.value == "stale" })
    }

    @Test
    fun restoredMismatchedCheckpointEntryCannotSupplyACursor() = runBlocking {
        val store = InMemoryNotificationStore()
        val mentions = NotificationQuery(setOf(me.foxtails.palustris.domain.NotificationCategory.Mentions))
        store.write(
            account,
            NotificationRepositoryState(checkpoints = mapOf(
                NotificationQuery().stableKey to NotificationCheckpoint(account, mentions),
            )),
        )
        val repository = NotificationRepository(store)
        assertEquals(null, repository.checkpoint(account, NotificationQuery()))
        assertEquals(null, repository.checkpoint(account, mentions))
    }

    @Test
    fun rejectedIngestionChangesNeitherMemoryNorDurableState() = runBlocking {
        val store = InMemoryNotificationStore()
        val repository = NotificationRepository(store)
        val token = NotificationSyncToken(account, 1)
        val query = NotificationQuery()
        repository.activate(token)
        assertTrue(repository.establishBaseline(token, baselineRequest(query), NotificationPage(
            items = listOf(notification("base", NotificationActivity.Mention)),
            checkpoint = NotificationCheckpoint(account, query, capturedAtEpochMillis = 10),
            unreadState = NotificationUnreadState.Exact(1),
        )))
        val memoryBefore = repository.observe(account).value
        val durableBefore = store.read(account)

        assertFalse(repository.ingestNewerPage(token, newerRequest(query), NotificationPage(
            items = listOf(notification("rejected", NotificationActivity.Mention)),
            newerCursor = NotificationCursor("rejected-next"),
            checkpoint = NotificationCheckpoint(
                account,
                NotificationQuery(setOf(me.foxtails.palustris.domain.NotificationCategory.Mentions)),
                capturedAtEpochMillis = 99,
            ),
            unreadState = NotificationUnreadState.Exact(5),
            direction = NotificationPageDirection.Newer,
        )))

        val memoryAfter = repository.observe(account).value
        val durableAfter = store.read(account)
        assertEquals(memoryBefore.items.map { it.id }, memoryAfter.items.map { it.id })
        assertEquals(memoryBefore.unreadState, memoryAfter.unreadState)
        assertEquals(memoryBefore.checkpoints, memoryAfter.checkpoints)
        assertEquals(memoryBefore.deliveries, memoryAfter.deliveries)
        assertEquals(memoryBefore.lastSyncedAtEpochMillis, memoryAfter.lastSyncedAtEpochMillis)
        assertEquals(durableBefore?.items?.map { it.id }, durableAfter?.items?.map { it.id })
        assertEquals(durableBefore?.unreadState, durableAfter?.unreadState)
        assertEquals(durableBefore?.checkpoints, durableAfter?.checkpoints)
        assertEquals(durableBefore?.deliveries, durableAfter?.deliveries)
        assertEquals(durableBefore?.lastSyncedAtEpochMillis, durableAfter?.lastSyncedAtEpochMillis)
        assertTrue(repository.pendingDeliveries(account).isEmpty())
    }

    private fun ingestRequest(page: NotificationPage) =
        NotificationIngestRequest(page.checkpoint?.query ?: NotificationQuery(), NotificationPageDirection.Initial)

    private fun baselineRequest(query: NotificationQuery = NotificationQuery()) =
        NotificationIngestRequest(query, NotificationPageDirection.Initial)

    private fun newerRequest(
        query: NotificationQuery = NotificationQuery(),
        expectedContinuation: NotificationCursor? = null,
    ) = NotificationIngestRequest(query, NotificationPageDirection.Newer, expectedContinuation)

    private fun olderRequest(
        query: NotificationQuery = NotificationQuery(),
        expectedContinuation: NotificationCursor? = null,
    ) = NotificationIngestRequest(query, NotificationPageDirection.Older, expectedContinuation)

    private fun notification(id: String, activity: NotificationActivity, post: Post? = null) = Notification(
        id = EntityId(account.connection.origin, id),
        accountId = account,
        createdAtEpochMillis = 100,
        activity = activity,
        actors = listOf(Account(account, "Receiver", "@receiver@example.org")),
        post = post,
        rawType = id,
    )
}
