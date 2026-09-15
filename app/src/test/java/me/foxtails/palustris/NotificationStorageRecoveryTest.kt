package me.foxtails.palustris

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import me.foxtails.palustris.data.notifications.NotificationIngestRequest
import me.foxtails.palustris.data.notifications.NotificationRepository
import me.foxtails.palustris.data.notifications.NotificationRepositoryState
import me.foxtails.palustris.data.notifications.NotificationStorageHealth
import me.foxtails.palustris.data.notifications.NotificationStore
import me.foxtails.palustris.data.notifications.NotificationStoreRead
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.EntityId
import me.foxtails.palustris.domain.Notification
import me.foxtails.palustris.domain.NotificationAcknowledgement
import me.foxtails.palustris.domain.NotificationActivity
import me.foxtails.palustris.domain.NotificationCheckpoint
import me.foxtails.palustris.domain.NotificationCursor
import me.foxtails.palustris.domain.NotificationPage
import me.foxtails.palustris.domain.NotificationPageDirection
import me.foxtails.palustris.domain.NotificationQuery
import me.foxtails.palustris.domain.NotificationSettings
import me.foxtails.palustris.domain.NotificationSyncToken
import me.foxtails.palustris.domain.NotificationUnreadState
import me.foxtails.palustris.domain.Page
import me.foxtails.palustris.domain.Post
import me.foxtails.palustris.domain.Protocol
import me.foxtails.palustris.domain.PushRegistration
import me.foxtails.palustris.domain.ServerCapabilities
import me.foxtails.palustris.domain.SocialSource
import me.foxtails.palustris.domain.Timeline
import me.foxtails.palustris.ui.NotificationsViewModel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Recovers one account from a corrupt or unavailable notification store without touching the
 * original bytes, while a healthy account keeps working normally.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NotificationStorageRecoveryTest {
    private val account = AccountId(Connection("https://example.org", Protocol.MISSKEY), "receiver")
    private val other = AccountId(Connection("https://other.example", Protocol.MASTODON), "second")

    @After
    fun resetMain() {
        Dispatchers.resetMain()
    }

    @Test
    fun corruptAccountBlocksMutationsAndLeavesTheStoredBytesUntouched() = runBlocking {
        val store = ScriptedStore(fallbackRead = NotificationStoreRead.Corrupt)
        val repository = NotificationRepository(store)
        val token = NotificationSyncToken(account, 1)
        repository.activate(token)

        assertEquals(NotificationStorageHealth.Recoverable, repository.observeStorageHealth(account).value)
        assertFalse(repository.establishBaseline(token, baselineRequest(), baselinePage()))
        assertFalse(repository.updateSettings(token, NotificationSettings(alertsEnabled = true)))
        assertFalse(repository.updatePushRegistration(token, registration(token)))
        assertFalse(repository.clearPushRegistration(token))
        assertFalse(repository.markSeen(token))
        assertFalse(repository.acknowledge(token, NotificationAcknowledgement(account, NotificationUnreadState.None, 1)))
        assertNull(repository.claimDelivery(token, EntityId(account.connection.origin, "one")))
        assertTrue(repository.pendingDeliveries(account).isEmpty())
        assertNull(repository.pushRegistration(account))

        assertEquals(0, store.writes)
        assertFalse(repository.retry(account))
        assertEquals(NotificationStorageHealth.Recoverable, repository.observeStorageHealth(account).value)
        assertEquals(0, store.writes)
    }

    @Test
    fun aHealthySecondAccountIsUnaffectedByTheBlockedAccount() = runBlocking {
        val store = ScriptedStore(fallbackRead = NotificationStoreRead.Corrupt)
        store.reads[other] = NotificationStoreRead.Absent
        val repository = NotificationRepository(store)
        repository.activate(NotificationSyncToken(account, 1))

        assertEquals(NotificationStorageHealth.Recoverable, repository.observeStorageHealth(account).value)

        val otherToken = NotificationSyncToken(other, 1)
        repository.activate(otherToken)
        assertEquals(NotificationStorageHealth.Healthy, repository.observeStorageHealth(other).value)
        assertTrue(repository.updateSettings(otherToken, NotificationSettings(showPreviews = true)))
        assertTrue(repository.settings(other).showPreviews)
        assertTrue(store.writes >= 1)
    }

    @Test
    fun unsupportedFutureFormatBlocksMutationsAndRetry() = runBlocking {
        val store = ScriptedStore(fallbackRead = NotificationStoreRead.Unsupported)
        val repository = NotificationRepository(store)
        val token = NotificationSyncToken(account, 1)
        repository.activate(token)

        assertEquals(NotificationStorageHealth.Unsupported, repository.observeStorageHealth(account).value)
        assertFalse(repository.establishBaseline(token, baselineRequest(), baselinePage()))
        assertFalse(repository.updateSettings(token, NotificationSettings(alertsEnabled = true)))
        assertEquals(0, store.writes)

        assertFalse(repository.retry(account))
        assertEquals(NotificationStorageHealth.Unsupported, repository.observeStorageHealth(account).value)
        assertEquals(0, store.writes)
    }

    @Test
    fun resetClearsUnsupportedStateAndRevokesTheOldGeneration() = runBlocking {
        val store = ScriptedStore(fallbackRead = NotificationStoreRead.Unsupported)
        val repository = NotificationRepository(store)
        val staleToken = NotificationSyncToken(account, 1)
        repository.activate(staleToken)
        assertEquals(NotificationStorageHealth.Unsupported, repository.observeStorageHealth(account).value)

        assertTrue(repository.reset(account))

        assertEquals(NotificationStorageHealth.Healthy, repository.observeStorageHealth(account).value)
        assertEquals(NotificationRepositoryState(), repository.observe(account).value)
        assertEquals(1, store.writes)

        // The old generation is revoked, so a late writer cannot recreate the discarded state.
        assertFalse(repository.updateSettings(staleToken, NotificationSettings(alertsEnabled = true)))
        assertEquals(1, store.writes)

        // A fresh token works against the new empty baseline.
        val freshToken = repository.currentToken(account)!!
        assertTrue(freshToken.generation > staleToken.generation)
        assertTrue(repository.updateSettings(freshToken, NotificationSettings(showPreviews = true)))
        assertTrue(repository.settings(account).showPreviews)
    }

    @Test
    fun resetLeavesASecondAccountUntouched() = runBlocking {
        val store = ScriptedStore(fallbackRead = NotificationStoreRead.Unsupported)
        store.reads[other] = NotificationStoreRead.Absent
        val repository = NotificationRepository(store)
        val otherToken = NotificationSyncToken(other, 1)
        repository.activate(otherToken)
        assertTrue(repository.updateSettings(otherToken, NotificationSettings(showPreviews = true)))

        assertTrue(repository.reset(account))

        assertEquals(NotificationStorageHealth.Healthy, repository.observeStorageHealth(other).value)
        assertTrue(repository.settings(other).showPreviews)
    }

    @Test
    fun aFailedResetLeavesTheAccountBlocked() = runBlocking {
        val store = FailingWriteStore(NotificationStoreRead.Unsupported)
        val repository = NotificationRepository(store)
        repository.activate(NotificationSyncToken(account, 1))

        assertFalse(repository.reset(account))
        assertEquals(NotificationStorageHealth.Unavailable, repository.observeStorageHealth(account).value)
    }

    @Test
    fun retryReloadsAReadableStateAndClearsTheFailure() = runBlocking {
        val store = ScriptedStore(fallbackRead = NotificationStoreRead.Unavailable)
        val repository = NotificationRepository(store)
        repository.activate(NotificationSyncToken(account, 1))
        assertEquals(NotificationStorageHealth.Unavailable, repository.observeStorageHealth(account).value)

        store.reads[account] = NotificationStoreRead.Readable(
            NotificationRepositoryState(settings = NotificationSettings(showPreviews = true)),
        )
        assertTrue(repository.retry(account))
        assertEquals(NotificationStorageHealth.Healthy, repository.observeStorageHealth(account).value)
        assertTrue(repository.settings(account).showPreviews)
    }

    @Test
    fun aHealthyRetryDoesNotReplaceCommittedInMemoryState() = runBlocking {
        val store = ScriptedStore(fallbackRead = NotificationStoreRead.Absent)
        val repository = NotificationRepository(store)
        val token = NotificationSyncToken(account, 1)
        repository.activate(token)
        assertTrue(repository.updateSettings(token, NotificationSettings(showPreviews = true)))
        val committed = repository.observe(account).value

        store.reads[account] = NotificationStoreRead.Readable(NotificationRepositoryState())
        assertTrue(repository.retry(account))

        assertEquals(committed, repository.observe(account).value)
    }

    @Test
    fun corruptStorageExposesARecoverableInboxErrorAndRetry() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ScriptedStore(fallbackRead = NotificationStoreRead.Corrupt)
        val repository = NotificationRepository(store)
        repository.activate(NotificationSyncToken(account, 1))
        val viewModel = NotificationsViewModel(account, EmptyPageSource(account), repository)
        advanceUntilIdle()

        assertTrue(viewModel.state.value.storageUnavailable)
        assertTrue(viewModel.state.value.items.isEmpty())

        store.reads[account] = NotificationStoreRead.Readable(NotificationRepositoryState())
        viewModel.refresh()
        advanceUntilIdle()

        assertFalse(viewModel.state.value.storageUnavailable)
    }

    private fun baselineRequest() =
        NotificationIngestRequest(NotificationQuery(), NotificationPageDirection.Initial)

    private fun baselinePage() = NotificationPage(
        items = listOf(
            Notification(
                id = EntityId(account.connection.origin, "one"),
                accountId = account,
                createdAtEpochMillis = 1,
                activity = NotificationActivity.Mention,
                rawType = "mention",
            ),
        ),
        checkpoint = NotificationCheckpoint(account, NotificationQuery()),
        direction = NotificationPageDirection.Initial,
    )

    private fun registration(token: NotificationSyncToken) = PushRegistration(
        accountId = token.accountId,
        generation = token.generation,
        instanceName = "instance",
    )

    private class ScriptedStore(private val fallbackRead: NotificationStoreRead) : NotificationStore {
        val reads = mutableMapOf<AccountId, NotificationStoreRead>()
        var writes = 0
        override fun read(accountId: AccountId): NotificationStoreRead = reads[accountId] ?: fallbackRead
        override fun write(accountId: AccountId, state: NotificationRepositoryState) { writes += 1 }
        override fun delete(accountId: AccountId) = Unit
    }

    private class FailingWriteStore(private val fallbackRead: NotificationStoreRead) : NotificationStore {
        override fun read(accountId: AccountId): NotificationStoreRead = fallbackRead
        override fun write(accountId: AccountId, state: NotificationRepositoryState) = error("disk full")
        override fun delete(accountId: AccountId) = Unit
    }

    private class EmptyPageSource(private val account: AccountId) : SocialSource {
        override val capabilities = ServerCapabilities(timelines = setOf(Timeline.Home))
        override suspend fun timeline(timeline: Timeline, cursor: String?): Page<Post> = Page(emptyList())
        override suspend fun notifications(
            query: NotificationQuery,
            cursor: NotificationCursor?,
        ): NotificationPage = NotificationPage(
            items = emptyList(),
            checkpoint = NotificationCheckpoint(account, query),
        )
    }
}
