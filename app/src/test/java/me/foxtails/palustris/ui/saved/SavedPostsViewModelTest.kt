package me.foxtails.palustris.ui.saved

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.withContext
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.Audience
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.EntityId
import me.foxtails.palustris.domain.Page
import me.foxtails.palustris.domain.Post
import me.foxtails.palustris.domain.SavedPostsKind
import me.foxtails.palustris.domain.ServerCapabilities
import me.foxtails.palustris.domain.SocialSource
import me.foxtails.palustris.domain.Timeline
import me.foxtails.palustris.ui.saved.SavedPostsViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SavedPostsViewModelTest {
    private val account = AccountId(Connection("https://example.org", me.foxtails.palustris.domain.Protocol.MISSKEY), "receiver")
    private val author = Account(account.copy(localId = "author"), "Author", "@author@example.org")

    @Test
    fun failedOlderPageCanBeRetriedWithoutDiscardingRows() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val source = SavedSource(account, author)
            val viewModel = SavedPostsViewModel(account, source)
            advanceUntilIdle()
            assertEquals(listOf("first"), viewModel.state.value.posts.map { it.post.id.value })

            viewModel.loadMore()
            advanceUntilIdle()
            assertNotNull(viewModel.state.value.error)
            assertEquals(listOf("first"), viewModel.state.value.posts.map { it.post.id.value })

            viewModel.loadMore()
            advanceUntilIdle()
            assertEquals(listOf("first", "second"), viewModel.state.value.posts.map { it.post.id.value })
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun independentUnsaveRequestsDoNotCancelEachOther() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val source = SavedSource(account, author, failOlderPage = false)
            val viewModel = SavedPostsViewModel(account, source)
            advanceUntilIdle()
            viewModel.unsave(viewModel.state.value.posts[0])
            viewModel.unsave(viewModel.state.value.posts[1])
            advanceUntilIdle()
            assertEquals(setOf("first", "second"), source.unsavedIds)
            assertEquals(emptyList<Post>(), viewModel.state.value.posts.map { it.post })
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun stalePageCannotReplaceNewerRefresh() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val source = GatedCollectionSource()
            val viewModel = SavedPostsViewModel(account, source)
            advanceUntilIdle()
            viewModel.refresh()
            advanceUntilIdle()
            source.completeSavedPage(1, Page(listOf(post("new")), null))
            advanceUntilIdle()
            source.completeSavedPage(0, Page(listOf(post("stale")), null))
            advanceUntilIdle()

            assertEquals(listOf("new"), viewModel.state.value.posts.map { it.post.id.value })
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun confirmedRemovalStaysHiddenAcrossRefreshAndOlderPage() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val source = SavedSource(
                account,
                author,
                failOlderPage = false,
                paged = true,
                repeatFirstOnOlderPage = true,
            )
            val viewModel = SavedPostsViewModel(account, source)
            advanceUntilIdle()

            viewModel.unsave(viewModel.state.value.posts.single())
            advanceUntilIdle()
            viewModel.refresh()
            advanceUntilIdle()
            viewModel.loadMore()
            advanceUntilIdle()

            assertTrue(viewModel.state.value.posts.isEmpty())
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun unsaveFailureKeepsRowAndShowsError() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val source = GatedCollectionSource()
            val viewModel = SavedPostsViewModel(account, source)
            advanceUntilIdle()
            source.completeSavedPage(0, Page(listOf(post("first")), null))
            advanceUntilIdle()

            viewModel.unsave(viewModel.state.value.posts.single())
            advanceUntilIdle()
            source.failSaved(0, java.io.IOException("unsave failed"))
            advanceUntilIdle()

            assertEquals(listOf("first"), viewModel.state.value.posts.map { it.post.id.value })
            assertNotNull(viewModel.state.value.error)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun stopRejectsLateCollectionPage() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val source = GatedCollectionSource()
            val viewModel = SavedPostsViewModel(account, source)
            advanceUntilIdle()
            viewModel.stop()
            source.completeSavedPage(0, Page(listOf(post("late")), null))
            advanceUntilIdle()

            assertTrue(viewModel.state.value.posts.isEmpty())
            assertNull(viewModel.state.value.error)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun pagingDoesNotStartDuringRefresh() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val source = GatedCollectionSource()
            val viewModel = SavedPostsViewModel(account, source)
            advanceUntilIdle()
            source.completeSavedPage(0, Page(listOf(post("a")), "c1"))
            advanceUntilIdle()

            viewModel.loadMore()
            advanceUntilIdle()
            viewModel.refresh()
            advanceUntilIdle()
            assertEquals(false, viewModel.state.value.loadingMore)
            source.completeSavedPage(1, Page(listOf(post("late")), "c2"))
            advanceUntilIdle()
            source.completeSavedPage(2, Page(listOf(post("a"), post("b"))))
            advanceUntilIdle()

            assertEquals(listOf("a", "b"), viewModel.state.value.posts.map { it.post.id.value })
            assertEquals(false, viewModel.state.value.loadingMore)
        } finally {
            Dispatchers.resetMain()
        }
    }

    private fun post(id: String) = Post(
        id = EntityId(account.connection.origin, id),
        author = author,
        text = id,
        publishedAtEpochMillis = 1,
        audience = Audience.Public,
    )

    private inner class GatedCollectionSource : SocialSource {
        override val capabilities = ServerCapabilities(
            savedPosts = me.foxtails.palustris.domain.SavedPostsCapability(
                me.foxtails.palustris.domain.CapabilityStatus.Supported,
                SavedPostsKind.Bookmarks,
            ),
        )
        private val savedPagePending = ArrayDeque<CompletableDeferred<Page<Post>>>()
        private val savedPending = ArrayDeque<CompletableDeferred<me.foxtails.palustris.domain.PostActionResult>>()

        override suspend fun timeline(timeline: Timeline, cursor: String?): Page<Post> = Page(emptyList())

        override suspend fun savedPosts(cursor: String?): Page<Post> {
            val gate = CompletableDeferred<Page<Post>>()
            savedPagePending += gate
            return withContext(NonCancellable) { gate.await() }
        }

        override suspend fun setSaved(
            id: EntityId,
            selected: Boolean,
        ): me.foxtails.palustris.domain.PostActionResult {
            val gate = CompletableDeferred<me.foxtails.palustris.domain.PostActionResult>()
            savedPending += gate
            return withContext(NonCancellable) { gate.await() }
        }

        fun completeSavedPage(index: Int, page: Page<Post>) { savedPagePending[index].complete(page) }
        fun failSaved(index: Int, error: Exception) { savedPending[index].completeExceptionally(error) }
    }

    private class SavedSource(
        private val account: AccountId,
        private val author: Account,
        private val failOlderPage: Boolean = true,
        private val paged: Boolean = false,
        private val repeatFirstOnOlderPage: Boolean = false,
    ) : SocialSource {
        override val capabilities = ServerCapabilities(
            savedPosts = me.foxtails.palustris.domain.SavedPostsCapability(
                me.foxtails.palustris.domain.CapabilityStatus.Supported,
                SavedPostsKind.Bookmarks,
            ),
            likedPosts = me.foxtails.palustris.domain.CapabilityStatus.Supported,
        )
        var olderAttempts = 0
        val unsavedIds = mutableSetOf<String>()
        val savedCursors = mutableListOf<String?>()

        override suspend fun timeline(timeline: Timeline, cursor: String?): Page<Post> = Page(emptyList())

        override suspend fun savedPosts(cursor: String?): Page<Post> {
            savedCursors += cursor
            return when (cursor) {
                null -> if (failOlderPage || paged) {
                Page(listOf(post("first")), "page-2")
            } else {
                Page(listOf(post("first"), post("second")), null)
            }
            else -> {
                olderAttempts += 1
                if (failOlderPage && olderAttempts == 1) error("temporary page failure")
                    Page(listOf(post(if (repeatFirstOnOlderPage) "first" else "second")), null)
                }
            }
        }

        override suspend fun setSaved(id: EntityId, selected: Boolean): me.foxtails.palustris.domain.PostActionResult {
            if (!selected) unsavedIds += id.value
            return me.foxtails.palustris.domain.PostActionResult(selected = selected)
        }

        private fun post(id: String) = Post(
            id = EntityId(account.connection.origin, id),
            author = author,
            text = id,
            publishedAtEpochMillis = 1,
            audience = Audience.Public,
        )
    }
}
