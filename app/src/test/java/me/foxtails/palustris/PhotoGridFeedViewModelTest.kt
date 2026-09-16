package me.foxtails.palustris

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import me.foxtails.palustris.data.preferences.InMemoryPhotoGridPreferencesRepository
import me.foxtails.palustris.data.preferences.InMemoryPostPreferencesRepository
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.Audience
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.EntityId
import me.foxtails.palustris.domain.Page
import me.foxtails.palustris.domain.Post
import me.foxtails.palustris.domain.ServerCapabilities
import me.foxtails.palustris.domain.SocialSource
import me.foxtails.palustris.domain.Timeline
import me.foxtails.palustris.domain.timelineDisplayOrder
import me.foxtails.palustris.data.notifications.NotificationSyncOrchestrator
import me.foxtails.palustris.ui.feed.FeedViewModel
import me.foxtails.palustris.ui.PhotoGridFeed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PhotoGridFeedViewModelTest {
    private val connection = Connection("https://grid.example", me.foxtails.palustris.domain.Protocol.MISSKEY)
    private val account = AccountId(connection, "owner")
    private val author = Account(account, "Owner", "@owner@grid.example")

    @Test
    fun gridDoesNotLoadUntilRequestedAndUsesIndependentSelection() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val source = GridSource()
        val model = model(source)
        try {
            advanceUntilIdle()
            assertTrue(source.timelineCalls.none { it == Timeline.Bubble })

            model.selectPhotoGridFeed(PhotoGridFeed.TimelineFeed(Timeline.Bubble))
            advanceUntilIdle()

            assertEquals(listOf("bubble-post"), model.photoGridFeed.value.posts.map { it.post.id.value })
            assertEquals(account, model.photoGridFeed.value.posts.single().fetchedBy)
            assertEquals(7L, model.photoGridFeed.value.posts.single().sessionRevision)
            assertEquals(listOf("home-post"), model.feed.value.ownedPosts.map { it.post.id.value })
        } finally {
            model.stop()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun pagingContinuesThroughTextOnlyPagesAndStopsRepeatedCursors() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val source = GridSource().apply { bubblePages = listOf(
            Page(listOf(post("text-only")), "cursor-1"),
            Page(listOf(post("photo")), "cursor-1"),
        ) }
        val model = model(source)
        try {
            advanceUntilIdle()
            model.selectPhotoGridFeed(PhotoGridFeed.TimelineFeed(Timeline.Bubble))
            advanceUntilIdle()
            model.loadMorePhotoGrid()
            advanceUntilIdle()
            assertEquals(listOf("text-only", "photo"), model.photoGridFeed.value.posts.map { it.post.id.value })
            assertEquals(null, model.photoGridFeed.value.nextCursor)
            assertEquals(2, source.bubbleCallCount)
        } finally {
            model.stop()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun lateCanceledGridResultCannotReplaceTheSelectedFeed() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val source = GridSource()
        val delayed = CompletableDeferred<Unit>()
        source.bubbleGate = delayed
        val model = model(source)
        try {
            advanceUntilIdle()
            model.selectPhotoGridFeed(PhotoGridFeed.TimelineFeed(Timeline.Bubble))
            model.selectPhotoGridFeed(PhotoGridFeed.TimelineFeed(Timeline.Home))
            advanceUntilIdle()
            delayed.complete(Unit)
            advanceUntilIdle()
            assertEquals(PhotoGridFeed.TimelineFeed(Timeline.Home), model.photoGridFeed.value.selectedFeed)
            assertTrue(model.photoGridFeed.value.posts.none { it.post.id.value == "bubble-post" })
        } finally {
            model.stop()
            Dispatchers.resetMain()
        }
    }

    private fun model(source: GridSource) = FeedViewModel(
        account,
        source,
        NotificationSyncOrchestrator(),
        InMemoryPostPreferencesRepository(),
        InMemoryPhotoGridPreferencesRepository(),
        7L,
    )

    private fun post(id: String) = Post(
        EntityId(connection.origin, id), author, id, 0L, Audience.Public,
    )

    private inner class GridSource : SocialSource {
        override val capabilities = ServerCapabilities(timelines = timelineDisplayOrder.toSet())
        val timelineCalls = mutableListOf<Timeline>()
        var bubblePages = listOf(Page(listOf(post("bubble-post")), "bubble-cursor"))
        var bubbleCallCount = 0
        var bubbleGate: CompletableDeferred<Unit>? = null

        override suspend fun timeline(timeline: Timeline, cursor: String?): Page<Post> {
            timelineCalls += timeline
            if (timeline == Timeline.Bubble) {
                bubbleGate?.await()
                val page = bubblePages.getOrNull(bubbleCallCount++) ?: Page(emptyList())
                return page
            }
            return Page(listOf(post("home-post")))
        }
    }
}
