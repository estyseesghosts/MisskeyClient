package me.foxtails.palustris

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
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
import me.foxtails.palustris.domain.NotificationSyncToken
import me.foxtails.palustris.domain.Protocol
import me.foxtails.palustris.domain.ServerCapabilities
import me.foxtails.palustris.domain.SocialSource
import me.foxtails.palustris.domain.SourceError
import me.foxtails.palustris.domain.Timeline
import me.foxtails.palustris.domain.Page
import me.foxtails.palustris.domain.Post
import me.foxtails.palustris.ui.NotificationsViewModel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NotificationsViewModelTest {
    private val account = AccountId(Connection("https://example.org", Protocol.MISSKEY), "receiver")

    @After
    fun resetMain() {
        Dispatchers.resetMain()
    }

    @Test
    fun dismissRemovesRowWhenProtocolHasNoServerDismissEndpoint() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val notification = Notification(
            id = EntityId(account.connection.origin, "notification"),
            accountId = account,
            createdAtEpochMillis = 1,
            activity = NotificationActivity.Follow,
            actors = listOf(Account(account, "Actor", "@actor@example.org")),
            rawType = "follow",
        )
        val source = UnsupportedDismissSource(notification)
        val repository = NotificationRepository(InMemoryNotificationStore())
        repository.activate(NotificationSyncToken(account, 1))
        val viewModel = NotificationsViewModel(
            account,
            source,
            repository,
        )
        advanceUntilIdle()

        viewModel.dismiss(notification)
        advanceUntilIdle()

        assertTrue(viewModel.state.value.items.isEmpty())
    }

    @Test
    fun stopPreventsLatePublicationAndLaterRequests() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val notification = Notification(
            id = EntityId(account.connection.origin, "notification"),
            accountId = account,
            createdAtEpochMillis = 1,
            activity = NotificationActivity.Follow,
            actors = listOf(Account(account, "Actor", "@actor@example.org")),
            rawType = "follow",
        )
        val gate = CompletableDeferred<NotificationPage>()
        val source = GatedNotificationSource(account, notification, gate)
        val repository = NotificationRepository(InMemoryNotificationStore())
        repository.activate(NotificationSyncToken(account, 1))
        val viewModel = NotificationsViewModel(account, source, repository)
        advanceUntilIdle()

        viewModel.stop()
        gate.complete(
            NotificationPage(
                items = listOf(notification),
                checkpoint = NotificationCheckpoint(account, NotificationQuery()),
            ),
        )
        advanceUntilIdle()

        assertTrue(viewModel.state.value.items.isEmpty())
        assertNull(viewModel.state.value.error)

        viewModel.refresh()
        viewModel.loadOlder()
        advanceUntilIdle()
        assertEquals(1, source.requests)
        viewModel.stop()
    }

    @Test
    fun oldPageFailureAfterReplacementChangesNothing() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val notification = Notification(
            id = EntityId(account.connection.origin, "notification"),
            accountId = account,
            createdAtEpochMillis = 1,
            activity = NotificationActivity.Follow,
            actors = listOf(Account(account, "Actor", "@actor@example.org")),
            rawType = "follow",
        )
        val source = ScriptedNotificationSource()
        val repository = NotificationRepository(InMemoryNotificationStore())
        repository.activate(NotificationSyncToken(account, 1))
        val viewModel = NotificationsViewModel(account, source, repository)
        advanceUntilIdle()
        source.complete(0, page(notification))
        advanceUntilIdle()
        assertEquals(1, viewModel.state.value.items.size)

        viewModel.loadOlder()
        advanceUntilIdle()
        viewModel.refresh()
        advanceUntilIdle()
        source.fail(1, java.io.IOException("old page failed"))
        advanceUntilIdle()

        assertEquals(1, viewModel.state.value.items.size)
        assertNull(viewModel.state.value.error)
        assertEquals(false, viewModel.state.value.loadingMore)
        source.complete(2, page(notification))
        advanceUntilIdle()
        assertEquals(false, viewModel.state.value.loading)
    }

    @Test
    fun pagingSlotReservationRejectsOverlap() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val notification = Notification(
            id = EntityId(account.connection.origin, "notification"),
            accountId = account,
            createdAtEpochMillis = 1,
            activity = NotificationActivity.Follow,
            actors = listOf(Account(account, "Actor", "@actor@example.org")),
            rawType = "follow",
        )
        val source = ScriptedNotificationSource()
        val repository = NotificationRepository(InMemoryNotificationStore())
        repository.activate(NotificationSyncToken(account, 1))
        val viewModel = NotificationsViewModel(account, source, repository)
        advanceUntilIdle()
        source.complete(0, page(notification))
        advanceUntilIdle()

        viewModel.loadOlder()
        advanceUntilIdle()
        viewModel.loadOlder()
        advanceUntilIdle()

        // The second paging call returns at the reserved slot instead of restarting the page.
        assertEquals(listOf("refresh", "older"), source.requests)
        source.complete(1, page(notification))
        advanceUntilIdle()

        assertEquals(1, viewModel.state.value.items.size)
        assertEquals(false, viewModel.state.value.loadingMore)
        assertNull(viewModel.state.value.error)
    }

    private fun page(notification: Notification) = NotificationPage(
        items = listOf(notification),
        olderCursor = me.foxtails.palustris.domain.NotificationCursor("older-1"),
        checkpoint = NotificationCheckpoint(account, NotificationQuery()),
    )

    private class ScriptedNotificationSource : SocialSource {
        override val capabilities = ServerCapabilities(timelines = setOf(Timeline.Home))
        val requests = mutableListOf<String>()
        private val gates = mutableListOf<CompletableDeferred<NotificationPage>>()

        override suspend fun timeline(timeline: Timeline, cursor: String?): Page<Post> = Page(emptyList())

        override suspend fun notifications(query: NotificationQuery, cursor: me.foxtails.palustris.domain.NotificationCursor?): NotificationPage {
            requests += "refresh"
            return gated()
        }

        override suspend fun fetchOlderNotifications(
            query: NotificationQuery,
            checkpoint: NotificationCheckpoint,
        ): NotificationPage {
            requests += "older"
            return gated()
        }

        override suspend fun fetchNewerNotifications(
            query: NotificationQuery,
            checkpoint: NotificationCheckpoint,
        ): NotificationPage {
            requests += "newer"
            return gated()
        }

        override suspend fun dismissNotification(id: EntityId): Unit = throw SourceError.Unsupported("dismiss")

        private suspend fun gated(): NotificationPage {
            val gate = CompletableDeferred<NotificationPage>()
            gates += gate
            return gate.await()
        }

        fun complete(index: Int, page: NotificationPage) { gates[index].complete(page) }
        fun fail(index: Int, error: Exception) { gates[index].completeExceptionally(error) }
    }

    private class GatedNotificationSource(
        private val account: AccountId,
        private val notification: Notification,
        private val gate: CompletableDeferred<NotificationPage>,
    ) : SocialSource {
        var requests = 0
        override val capabilities = ServerCapabilities(timelines = setOf(Timeline.Home))

        override suspend fun timeline(timeline: Timeline, cursor: String?): Page<Post> = Page(emptyList())

        override suspend fun notifications(query: NotificationQuery, cursor: me.foxtails.palustris.domain.NotificationCursor?): NotificationPage {
            requests += 1
            return gate.await()
        }

        override suspend fun dismissNotification(id: EntityId): Unit = throw SourceError.Unsupported("dismiss")
    }

    private class UnsupportedDismissSource(
        private val notification: Notification,
    ) : SocialSource {
        override val capabilities = ServerCapabilities(timelines = setOf(Timeline.Home))

        override suspend fun timeline(timeline: Timeline, cursor: String?): Page<Post> = Page(emptyList())

        override suspend fun notifications(query: NotificationQuery, cursor: me.foxtails.palustris.domain.NotificationCursor?): NotificationPage =
            NotificationPage(
                items = listOf(notification),
                checkpoint = NotificationCheckpoint(notification.accountId, query),
            )

        override suspend fun dismissNotification(id: EntityId): Unit = throw SourceError.Unsupported("dismiss")
    }
}
