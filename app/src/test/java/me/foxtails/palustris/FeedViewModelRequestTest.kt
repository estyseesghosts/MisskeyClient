package me.foxtails.palustris

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.Audience
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.EntityId
import me.foxtails.palustris.domain.OwnedPost
import me.foxtails.palustris.domain.Page
import me.foxtails.palustris.domain.Post
import me.foxtails.palustris.domain.Protocol
import me.foxtails.palustris.domain.ServerCapabilities
import me.foxtails.palustris.domain.SocialSource
import me.foxtails.palustris.domain.Timeline
import me.foxtails.palustris.data.notifications.NotificationSyncOrchestrator
import me.foxtails.palustris.ui.FeedViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FeedViewModelRequestTest {
    private val connection = Connection("https://example.org", Protocol.MISSKEY)
    private val accountId = AccountId(connection, "owner")
    private val author = Account(AccountId(connection, "author"), "Author", "@author@example.org")

    private fun post(id: String) = Post(
        id = EntityId(connection.origin, id),
        author = author,
        text = id,
        publishedAtEpochMillis = 0,
        audience = Audience.Public,
    )

    private fun rows(model: FeedViewModel): List<String> =
        model.feed.value.posts.map { it.id.value }

    /** A source whose timeline requests wait for an explicit release that survives cancellation. */
    private class GatedSource : SocialSource {
        override val capabilities = ServerCapabilities(timelines = setOf(Timeline.Home))
        val requests = mutableListOf<Pair<Timeline, String?>>()
        private val pending = ArrayDeque<CompletableDeferred<Page<Post>>>()

        override suspend fun timeline(timeline: Timeline, cursor: String?): Page<Post> {
            requests += timeline to cursor
            val gate = CompletableDeferred<Page<Post>>()
            pending += gate
            // NonCancellable so a superseded request still reaches the owner's epoch guard.
            return withContext(NonCancellable) { gate.await() }
        }

        override suspend fun searchHashtag(tag: String, cursor: String?): Page<Post> = Page(emptyList())

        fun complete(index: Int, page: Page<Post>) { pending[index].complete(page) }
        fun fail(index: Int, error: Exception) { pending[index].completeExceptionally(error) }
    }

    @Test
    fun acceptedPageMergesIntoCurrentRowsAndKeepsMutations() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val coordinator = NotificationSyncOrchestrator()
        try {
            val source = GatedSource()
            val model = FeedViewModel(accountId, source, coordinator)
            advanceUntilIdle()
            source.complete(0, Page(listOf(post("a")), nextCursor = "c1"))
            advanceUntilIdle()
            assertEquals(listOf("a"), rows(model))

            model.applyExternalPost(OwnedPost(accountId, post("a").copy(favourited = true)))

            model.loadMore()
            advanceUntilIdle()
            source.complete(1, Page(listOf(post("a"), post("b")), nextCursor = "c2"))
            advanceUntilIdle()

            assertEquals(listOf("a", "b"), rows(model))
            assertTrue(model.feed.value.ownedPosts.first { it.post.id.value == "a" }.post.favourited)
            assertEquals("c2", model.feed.value.nextCursor)
        } finally {
            coordinator.close()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun refreshAdvancesTheRequestEpochAndPagingKeepsIt() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val coordinator = NotificationSyncOrchestrator()
        try {
            val source = GatedSource()
            val model = FeedViewModel(accountId, source, coordinator)
            advanceUntilIdle()
            source.complete(0, Page(listOf(post("a")), nextCursor = "c1"))
            advanceUntilIdle()
            val firstEpoch = model.feed.value.requestEpoch

            model.refresh()
            advanceUntilIdle()
            source.complete(1, Page(listOf(post("b")), nextCursor = "c2"))
            advanceUntilIdle()
            val secondEpoch = model.feed.value.requestEpoch
            assertTrue(secondEpoch > firstEpoch)

            model.loadMore()
            advanceUntilIdle()
            source.complete(2, Page(listOf(post("b"), post("c")), nextCursor = "c3"))
            advanceUntilIdle()

            assertEquals(secondEpoch, model.feed.value.requestEpoch)
            assertEquals(listOf("b", "c"), rows(model))
        } finally {
            coordinator.close()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun failedTimelineChangeCarriesTheNewRequestEpoch() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val failing = object : SocialSource {
            override val capabilities = ServerCapabilities(timelines = setOf(Timeline.Home, Timeline.Local))
            override suspend fun timeline(timeline: Timeline, cursor: String?): Page<Post> {
                if (timeline == Timeline.Local) throw java.io.IOException("timeline unavailable")
                return Page(listOf(post("a")), nextCursor = "c1")
            }
            override suspend fun searchHashtag(tag: String, cursor: String?): Page<Post> = Page(emptyList())
        }
        val coordinator = NotificationSyncOrchestrator()
        try {
            val model = FeedViewModel(accountId, failing, coordinator)
            advanceUntilIdle()
            val firstEpoch = model.feed.value.requestEpoch

            model.refresh(Timeline.Local)
            advanceUntilIdle()

            assertTrue(model.feed.value.requestEpoch > firstEpoch)
            assertEquals(Timeline.Home, model.feed.value.timeline)
        } finally {
            coordinator.close()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun staleRefreshSuccessCannotReplaceNewerRefresh() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val coordinator = NotificationSyncOrchestrator()
        try {
            val source = GatedSource()
            val model = FeedViewModel(accountId, source, coordinator)
            advanceUntilIdle()
            source.complete(0, Page(listOf(post("a")), nextCursor = "c1"))
            advanceUntilIdle()

            model.refresh()
            model.refresh()
            advanceUntilIdle()
            source.complete(1, Page(listOf(post("new")), nextCursor = "c2"))
            advanceUntilIdle()
            source.complete(0, Page(listOf(post("old")), nextCursor = "cold"))
            advanceUntilIdle()

            assertEquals(listOf("new"), rows(model))
            assertEquals("c2", model.feed.value.nextCursor)
        } finally {
            coordinator.close()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun staleRefreshFailureDoesNotPublishAnError() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val coordinator = NotificationSyncOrchestrator()
        try {
            val source = GatedSource()
            val model = FeedViewModel(accountId, source, coordinator)
            advanceUntilIdle()
            source.complete(0, Page(listOf(post("a")), nextCursor = "c1"))
            advanceUntilIdle()

            model.refresh()
            model.refresh()
            advanceUntilIdle()
            source.complete(1, Page(listOf(post("new")), nextCursor = "c2"))
            advanceUntilIdle()
            source.fail(0, java.io.IOException("old failure"))
            advanceUntilIdle()

            assertEquals(listOf("new"), rows(model))
            assertNull(model.feed.value.error)
            assertFalse(model.feed.value.loading)
        } finally {
            coordinator.close()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun stopRejectsLateRefreshCompletion() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val coordinator = NotificationSyncOrchestrator()
        try {
            val source = GatedSource()
            val model = FeedViewModel(accountId, source, coordinator)
            advanceUntilIdle()
            model.stop()
            source.complete(0, Page(listOf(post("late")), nextCursor = "c1"))
            advanceUntilIdle()

            assertTrue(rows(model).isEmpty())
            assertTrue(model.feed.value.ownedPosts.isEmpty())
            assertNull(model.feed.value.error)
        } finally {
            coordinator.close()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun repeatedCursorStopsAutomaticPaging() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val coordinator = NotificationSyncOrchestrator()
        try {
            val source = GatedSource()
            val model = FeedViewModel(accountId, source, coordinator)
            advanceUntilIdle()
            source.complete(0, Page(listOf(post("a")), nextCursor = "c1"))
            advanceUntilIdle()

            model.loadMore()
            advanceUntilIdle()
            source.complete(1, Page(listOf(post("b")), nextCursor = "c1"))
            advanceUntilIdle()

            assertNull(model.feed.value.nextCursor)
            assertEquals(listOf("a", "b"), rows(model))
        } finally {
            coordinator.close()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun acceptedCursorCycleStopsPaging() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val coordinator = NotificationSyncOrchestrator()
        try {
            val source = GatedSource()
            val model = FeedViewModel(accountId, source, coordinator)
            advanceUntilIdle()
            source.complete(0, Page(listOf(post("a")), nextCursor = "c1"))
            advanceUntilIdle()

            model.loadMore()
            advanceUntilIdle()
            source.complete(1, Page(listOf(post("b")), nextCursor = "c2"))
            advanceUntilIdle()
            assertEquals("c2", model.feed.value.nextCursor)

            model.loadMore()
            advanceUntilIdle()
            source.complete(2, Page(listOf(post("c")), nextCursor = "c1"))
            advanceUntilIdle()

            assertNull(model.feed.value.nextCursor)
            assertEquals(listOf("a", "b", "c"), rows(model))
        } finally {
            coordinator.close()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun queuedPagingCallsReserveOneSlot() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val coordinator = NotificationSyncOrchestrator()
        try {
            val source = GatedSource()
            val model = FeedViewModel(accountId, source, coordinator)
            advanceUntilIdle()
            source.complete(0, Page(listOf(post("a")), nextCursor = "c1"))
            advanceUntilIdle()

            model.loadMore()
            model.loadMore()
            advanceUntilIdle()

            assertEquals(2, source.requests.size)
            source.complete(1, Page(listOf(post("b")), nextCursor = "c2"))
            advanceUntilIdle()
            assertEquals(listOf("a", "b"), rows(model))
        } finally {
            coordinator.close()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun failedPageKeepsCursorForARetry() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val coordinator = NotificationSyncOrchestrator()
        try {
            val source = GatedSource()
            val model = FeedViewModel(accountId, source, coordinator)
            advanceUntilIdle()
            source.complete(0, Page(listOf(post("a")), nextCursor = "c1"))
            advanceUntilIdle()

            model.loadMore()
            advanceUntilIdle()
            source.fail(1, java.io.IOException("page failed"))
            advanceUntilIdle()

            assertEquals(listOf("a"), rows(model))
            assertEquals("c1", model.feed.value.nextCursor)

            model.loadMore()
            advanceUntilIdle()
            source.complete(2, Page(listOf(post("b")), nextCursor = "c2"))
            advanceUntilIdle()

            assertEquals(listOf("a", "b"), rows(model))
            assertEquals("c2", model.feed.value.nextCursor)
        } finally {
            coordinator.close()
            Dispatchers.resetMain()
        }
    }
}
