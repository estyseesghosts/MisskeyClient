package me.foxtails.palustris

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import me.foxtails.palustris.data.notifications.NotificationIngestRequest
import me.foxtails.palustris.data.notifications.NotificationRepository
import me.foxtails.palustris.data.notifications.NotificationRepositoryState
import me.foxtails.palustris.data.notifications.NotificationStorageHealth
import me.foxtails.palustris.data.notifications.NotificationStore
import me.foxtails.palustris.data.notifications.NotificationStoreRead
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.EntityId
import me.foxtails.palustris.domain.Notification
import me.foxtails.palustris.domain.NotificationAcknowledgement
import me.foxtails.palustris.domain.NotificationActivity
import me.foxtails.palustris.domain.NotificationCheckpoint
import me.foxtails.palustris.domain.NotificationPage
import me.foxtails.palustris.domain.NotificationPageDirection
import me.foxtails.palustris.domain.NotificationQuery
import me.foxtails.palustris.domain.NotificationSettings
import me.foxtails.palustris.domain.NotificationSyncToken
import me.foxtails.palustris.domain.NotificationUnreadState
import me.foxtails.palustris.domain.Protocol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A failed notification store write publishes nothing and reports failure.
 *
 * Durable acceptance is the success boundary: visible state, the returned result, and the
 * persisted bytes agree, and no failed operation is described as durably completed.
 */
class NotificationWriteFailureTest {
    private val account = AccountId(Connection("https://example.org", Protocol.MISSKEY), "receiver")

    @Test
    fun failedSettingsWriteLeavesVisibleStateUnchangedAndMarksStorageUnavailable() = runBlocking {
        val store = DurableStore()
        val repository = NotificationRepository(store)
        val token = NotificationSyncToken(account, 1)
        repository.activate(token)
        assertTrue(repository.updateSettings(token, NotificationSettings(showPreviews = true)))

        store.failWrites = true
        assertFalse(repository.updateSettings(token, NotificationSettings(alertsEnabled = true)))

        assertTrue(repository.settings(account).showPreviews)
        assertFalse(repository.settings(account).alertsEnabled)
        assertEquals(NotificationStorageHealth.Unavailable, repository.observeStorageHealth(account).value)
        assertTrue((store.read(account) as NotificationStoreRead.Readable).state.settings.showPreviews)
        assertEquals(2, store.writeAttempts)

        store.failWrites = false
        assertTrue(repository.retry(account))
        assertEquals(NotificationStorageHealth.Healthy, repository.observeStorageHealth(account).value)
        assertTrue(repository.updateSettings(token, NotificationSettings(alertsEnabled = true)))
        assertTrue(repository.settings(account).alertsEnabled)

        val restarted = NotificationRepository(store)
        assertTrue(restarted.settings(account).alertsEnabled)
    }

    @Test
    fun failedBaselineIngestionPublishesNothing() = runBlocking {
        val store = DurableStore(failWrites = true)
        val repository = NotificationRepository(store)
        val token = NotificationSyncToken(account, 1)
        repository.activate(token)

        assertFalse(repository.establishBaseline(token, baselineRequest(), baselinePage("one")))

        assertTrue(repository.observe(account).value.items.isEmpty())
        assertEquals(NotificationStorageHealth.Unavailable, repository.observeStorageHealth(account).value)
        assertEquals(NotificationStoreRead.Absent, store.read(account))
    }

    @Test
    fun failedDeliveryClaimPermitsNoPresentation() = runBlocking {
        val store = DurableStore()
        val repository = NotificationRepository(store)
        val token = NotificationSyncToken(account, 1)
        val query = NotificationQuery()
        repository.activate(token)
        assertTrue(repository.establishBaseline(token, baselineRequest(query), baselinePage("base", query)))
        assertTrue(repository.ingestNewerPage(token, newerRequest(query), newerPage("incoming", query)))

        store.failWrites = true
        assertNull(repository.claimDelivery(token, EntityId(account.connection.origin, "incoming"), nowEpochMillis = 100L))

        assertTrue(repository.pendingDeliveries(account).isEmpty())
        assertEquals(NotificationStorageHealth.Unavailable, repository.observeStorageHealth(account).value)
        val durable = (store.read(account) as NotificationStoreRead.Readable).state
        assertNull(durable.deliveries[EntityId(account.connection.origin, "incoming")]?.claimId)
    }

    @Test
    fun failedDismissalStaysRetryableWithoutServerAcknowledgement() = runBlocking {
        val store = DurableStore()
        val repository = NotificationRepository(store)
        val token = NotificationSyncToken(account, 1)
        repository.activate(token)
        val item = notification("one", NotificationActivity.Mention)
        assertTrue(repository.establishBaseline(
            token,
            baselineRequest(),
            baselinePage("one"),
        ))

        store.failWrites = true
        assertFalse(repository.dismissFromInbox(token, item.id, remoteApplied = false))

        assertEquals(listOf(item.id), repository.observe(account).value.items.map { it.id })
        assertFalse(repository.observe(account).value.items.single().readState.serverAcknowledged)
        assertTrue(repository.observe(account).value.dismissedIds.isEmpty())
        assertFalse(repository.markAndroidDismissed(account, item.id))
        assertFalse(repository.observe(account).value.items.single().readState.androidDismissed)
        assertEquals(NotificationStorageHealth.Unavailable, repository.observeStorageHealth(account).value)

        store.failWrites = false
        assertTrue(repository.retry(account))
        assertTrue(repository.dismissFromInbox(token, item.id, remoteApplied = false))
        assertTrue(repository.observe(account).value.items.isEmpty())
    }

    @Test
    fun failedAcknowledgementAfterRemoteSuccessNeedsRetryNotRepetition() = runBlocking {
        val store = DurableStore()
        val repository = NotificationRepository(store)
        val token = NotificationSyncToken(account, 1)
        repository.activate(token)
        assertTrue(repository.establishBaseline(token, baselineRequest(), baselinePage("one")))

        store.failWrites = true
        assertFalse(repository.applyAcknowledgement(
            token,
            NotificationAcknowledgement(account, NotificationUnreadState.None, 20),
        ))

        assertFalse(repository.observe(account).value.items.single().readState.serverAcknowledged)
        assertEquals(NotificationStorageHealth.Unavailable, repository.observeStorageHealth(account).value)
        // The repository issues one write attempt; it never repeats the remote acknowledgement.
        assertEquals(2, store.writeAttempts)
    }

    @Test
    fun concurrentMutationsSerializeWithoutLostUpdates() = runBlocking {
        val store = DurableStore()
        val repository = NotificationRepository(store)
        val token = NotificationSyncToken(account, 1)
        repository.activate(token)
        assertTrue(repository.establishBaseline(token, baselineRequest(), baselinePage("one", items = listOf("a", "b"))))

        val first = async(Dispatchers.Default) {
            repository.markLocallySeen(token, setOf(EntityId(account.connection.origin, "a")))
        }
        val second = async(Dispatchers.Default) {
            repository.markLocallySeen(token, setOf(EntityId(account.connection.origin, "b")))
        }
        assertTrue(first.await())
        assertTrue(second.await())

        val seen = repository.observe(account).value.items
            .filter { it.readState.locallySeen }.map { it.id.value }.toSet()
        assertEquals(setOf("a", "b"), seen)
        val durableSeen = (store.read(account) as NotificationStoreRead.Readable).state.items
            .filter { it.readState.locallySeen }.map { it.id.value }.toSet()
        assertEquals(setOf("a", "b"), durableSeen)
    }

    @Test
    fun revocationDuringPersistenceDiscardsTheWrite() = runBlocking {
        val store = GatedStore()
        val repository = NotificationRepository(store)
        val token = NotificationSyncToken(account, 1)
        repository.activate(token)
        assertTrue(repository.establishBaseline(token, baselineRequest(), baselinePage("one")))

        store.gateWrites = true
        val pending = async(Dispatchers.Default) { repository.markSeen(token) }
        assertTrue(store.writeEntered.await(10, TimeUnit.SECONDS))
        repository.invalidate(account, token.generation)
        store.releaseWrite.countDown()
        assertFalse(pending.await())

        assertFalse(repository.observe(account).value.items.single().readState.locallySeen)
        assertEquals(NotificationStorageHealth.Healthy, repository.observeStorageHealth(account).value)
    }

    @Test
    fun failedDeleteKeepsRemovalNonFatal() = runBlocking {
        val store = DurableStore(failDeletes = true)
        val repository = NotificationRepository(store)
        val token = NotificationSyncToken(account, 1)
        repository.activate(token)
        assertTrue(repository.updateSettings(token, NotificationSettings(showPreviews = true)))

        repository.remove(account)

        assertNull(repository.currentToken(account))
        assertEquals(1, store.deleteAttempts)
    }

    private fun baselineRequest(query: NotificationQuery = NotificationQuery()) =
        NotificationIngestRequest(query, NotificationPageDirection.Initial)

    private fun newerRequest(query: NotificationQuery = NotificationQuery()) =
        NotificationIngestRequest(query, NotificationPageDirection.Newer)

    private fun baselinePage(
        id: String,
        query: NotificationQuery = NotificationQuery(),
        items: List<String> = listOf(id),
    ) = NotificationPage(
        items = items.map { notification(it, NotificationActivity.Mention) },
        checkpoint = NotificationCheckpoint(account, query),
        direction = NotificationPageDirection.Initial,
    )

    private fun newerPage(id: String, query: NotificationQuery) = NotificationPage(
        items = listOf(notification(id, NotificationActivity.Mention)),
        checkpoint = NotificationCheckpoint(account, query),
        direction = NotificationPageDirection.Newer,
    )

    private fun notification(id: String, activity: NotificationActivity) = Notification(
        id = EntityId(account.connection.origin, id),
        accountId = account,
        createdAtEpochMillis = 100,
        activity = activity,
        actors = listOf(Account(account, "Receiver", "@receiver@example.org")),
        rawType = id,
    )

    private class DurableStore(
        var failWrites: Boolean = false,
        private val failDeletes: Boolean = false,
    ) : NotificationStore {
        private val durable = mutableMapOf<AccountId, NotificationRepositoryState>()
        var writeAttempts = 0
        var deleteAttempts = 0
        override fun read(accountId: AccountId): NotificationStoreRead =
            durable[accountId]?.let(NotificationStoreRead::Readable) ?: NotificationStoreRead.Absent
        override fun write(accountId: AccountId, state: NotificationRepositoryState) {
            writeAttempts += 1
            if (failWrites) error("disk full")
            durable[accountId] = state
        }
        override fun delete(accountId: AccountId) {
            deleteAttempts += 1
            if (failDeletes) error("disk gone")
            durable.remove(accountId)
        }
    }

    private class GatedStore : NotificationStore {
        private val durable = mutableMapOf<AccountId, NotificationRepositoryState>()
        var gateWrites = false
        val writeEntered = CountDownLatch(1)
        val releaseWrite = CountDownLatch(1)
        override fun read(accountId: AccountId): NotificationStoreRead =
            durable[accountId]?.let(NotificationStoreRead::Readable) ?: NotificationStoreRead.Absent
        override fun write(accountId: AccountId, state: NotificationRepositoryState) {
            if (gateWrites) {
                writeEntered.countDown()
                check(releaseWrite.await(10, TimeUnit.SECONDS)) { "Write gate timed out." }
            }
            durable[accountId] = state
        }
        override fun delete(accountId: AccountId) { durable.remove(accountId) }
    }
}
