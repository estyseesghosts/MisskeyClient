package me.foxtails.palustris

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
