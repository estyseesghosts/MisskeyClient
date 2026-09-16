package me.foxtails.palustris.data.notifications

import kotlinx.coroutines.runBlocking
import me.foxtails.palustris.data.notifications.InMemoryNotificationStore
import me.foxtails.palustris.data.notifications.NotificationRepository
import me.foxtails.palustris.data.notifications.NotificationSynchronizer
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.EntityId
import me.foxtails.palustris.domain.Notification
import me.foxtails.palustris.domain.NotificationAcknowledgement
import me.foxtails.palustris.domain.NotificationActivity
import me.foxtails.palustris.domain.NotificationCategory
import me.foxtails.palustris.domain.NotificationCheckpoint
import me.foxtails.palustris.domain.NotificationCursor
import me.foxtails.palustris.domain.NotificationPage
import me.foxtails.palustris.domain.NotificationPageDirection
import me.foxtails.palustris.domain.NotificationQuery
import me.foxtails.palustris.domain.NotificationSyncToken
import me.foxtails.palustris.domain.NotificationUnreadState
import me.foxtails.palustris.domain.Page
import me.foxtails.palustris.domain.Post
import me.foxtails.palustris.domain.Protocol
import me.foxtails.palustris.domain.Timeline
import me.foxtails.palustris.domain.ServerCapabilities
import me.foxtails.palustris.domain.SocialSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationSynchronizerTest {
    private val account = AccountId(Connection("https://example.org", Protocol.MASTODON), "receiver")
    private val query = NotificationQuery()
    private val mentions = NotificationQuery(setOf(NotificationCategory.Mentions))

    private fun notification(id: String) = Notification(
        id = EntityId(account.connection.origin, id),
        accountId = account,
        createdAtEpochMillis = 100,
        activity = NotificationActivity.Mention,
        actors = listOf(Account(account, "Receiver", "@receiver@example.org")),
        rawType = id,
    )

    private fun page(id: String, pageQuery: NotificationQuery = query) = NotificationPage(
        items = listOf(notification(id)),
        checkpoint = NotificationCheckpoint(account, pageQuery),
    )

    private class FakeSource(
        val baseline: NotificationPage,
        val newer: ArrayDeque<NotificationPage> = ArrayDeque(),
        val older: ArrayDeque<NotificationPage> = ArrayDeque(),
    ) : SocialSource {
        override val capabilities = ServerCapabilities()
        val queries = mutableListOf<NotificationQuery>()

        override suspend fun timeline(timeline: Timeline, cursor: String?): Page<Post> = Page(emptyList())
        override suspend fun searchHashtag(tag: String, cursor: String?): Page<Post> = Page(emptyList())

        override suspend fun notifications(
            query: NotificationQuery,
            cursor: me.foxtails.palustris.domain.NotificationCursor?,
        ): NotificationPage {
            queries += query
            return baseline
        }

        override suspend fun fetchNewerNotifications(
            query: NotificationQuery,
            checkpoint: NotificationCheckpoint,
        ): NotificationPage {
            queries += query
            return newer.removeFirst()
        }

        override suspend fun fetchOlderNotifications(
            query: NotificationQuery,
            checkpoint: NotificationCheckpoint,
        ): NotificationPage {
            queries += query
            return older.removeFirst()
        }

        override suspend fun notificationUnreadState(): NotificationUnreadState =
            NotificationUnreadState.Unknown
    }

    private fun setup(source: FakeSource): Pair<NotificationRepository, NotificationSynchronizer> {
        val repository = NotificationRepository(InMemoryNotificationStore())
        repository.activate(NotificationSyncToken(account, 1))
        return repository to NotificationSynchronizer(repository)
    }

    @Test
    fun rejectedBaselineReportsIncompleteWithoutSideEffects() = runBlocking {
        val source = FakeSource(page("foreign", mentions))
        val (repository, synchronizer) = setup(source)
        val token = NotificationSyncToken(account, 1)

        val result = synchronizer.establishBaseline(source, token, query)

        assertEquals(0, result.pages)
        assertFalse(result.complete)
        assertEquals(NotificationUnreadState.Unknown, result.unreadState)
        assertTrue(repository.observe(account).value.items.isEmpty())
        assertEquals(NotificationUnreadState.Unknown, repository.observe(account).value.unreadState)
        assertEquals(null, repository.checkpoint(account, query))
    }

    @Test
    fun catchUpStopsAtTheFirstRejectedPage() = runBlocking {
        val source = FakeSource(
            baseline = page("base"),
            newer = ArrayDeque(listOf(
                page("newer-1").copy(newerCursor = NotificationCursor("cursor-1")),
                page("rejected", mentions),
            )),
        )
        val (repository, synchronizer) = setup(source)
        val token = NotificationSyncToken(account, 1)

        assertTrue(synchronizer.establishBaseline(source, token, query).complete)
        val result = synchronizer.catchUpNewer(source, token, query)

        assertEquals(1, result.pages)
        assertFalse(result.complete)
        assertFalse(result.delayed)
        assertEquals(
            listOf("newer-1", "base"),
            repository.observe(account).value.items.map { it.id.value },
        )
    }

    @Test
    fun rejectedOlderPageKeepsCurrentRowsAndUnread() = runBlocking {
        val source = FakeSource(
            baseline = page("base").copy(
                checkpoint = NotificationCheckpoint(
                    account,
                    query,
                    oldest = NotificationCursor("old-0"),
                ),
            ),
            older = ArrayDeque(listOf(page("rejected", mentions))),
        )
        val (repository, synchronizer) = setup(source)
        val token = NotificationSyncToken(account, 1)

        assertTrue(synchronizer.establishBaseline(source, token, query).complete)
        val result = synchronizer.loadOlder(source, token, query)

        assertEquals(0, result.pages)
        assertFalse(result.complete)
        assertEquals(listOf("base"), repository.observe(account).value.items.map { it.id.value })
    }

    @Test
    fun mismatchedDirectionNeverCountsAsSuccess() = runBlocking {
        val repository = NotificationRepository(InMemoryNotificationStore())
        val token = NotificationSyncToken(account, 1)
        repository.activate(token)

        assertFalse(repository.establishBaseline(
            token,
            me.foxtails.palustris.data.notifications.NotificationIngestRequest(
                query,
                NotificationPageDirection.Initial,
            ),
            page("base").copy(direction = NotificationPageDirection.Older),
        ))
        assertTrue(repository.observe(account).value.items.isEmpty())
    }

    @Test
    fun acknowledgementStillAppliesAfterARejectedPage() = runBlocking {
        val source = FakeSource(page("foreign", mentions))
        val (repository, synchronizer) = setup(source)
        val token = NotificationSyncToken(account, 1)

        assertFalse(synchronizer.establishBaseline(source, token, query).complete)
        repository.applyAcknowledgement(token, NotificationAcknowledgement(account, NotificationUnreadState.None, 7))

        assertEquals(NotificationUnreadState.None, repository.observe(account).value.unreadState)
    }
}
