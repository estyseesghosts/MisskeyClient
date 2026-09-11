package me.foxtails.palustris

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
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
import me.foxtails.palustris.ui.SavedPostsCollection
import me.foxtails.palustris.ui.SavedPostsViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
    fun likesAndBookmarksKeepIndependentCursors() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val source = SavedSource(account, author, failOlderPage = false, paged = true)
            val bookmarks = SavedPostsViewModel(account, source, SavedPostsCollection.Bookmarks)
            val likes = SavedPostsViewModel(account, source, SavedPostsCollection.Likes)
            advanceUntilIdle()

            bookmarks.loadMore()
            likes.loadMore()
            advanceUntilIdle()

            assertEquals(listOf(null, "page-2"), source.savedCursors)
            assertEquals(listOf(null, "likes-2"), source.likedCursors)
            assertEquals(listOf("first", "second"), bookmarks.state.value.posts.map { it.post.id.value })
            assertEquals(listOf("liked-first", "liked-second"), likes.state.value.posts.map { it.post.id.value })
        } finally {
            Dispatchers.resetMain()
        }
    }

    private class SavedSource(
        private val account: AccountId,
        private val author: Account,
        private val failOlderPage: Boolean = true,
        private val paged: Boolean = false,
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
        val likedCursors = mutableListOf<String?>()

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
                    Page(listOf(post("second")), null)
                }
            }
        }

        override suspend fun likedPosts(cursor: String?): Page<Post> {
            likedCursors += cursor
            return when (cursor) {
                null -> Page(listOf(post("liked-first")), "likes-2")
                else -> Page(listOf(post("liked-second")), null)
            }
        }

        override suspend fun unsave(id: EntityId) {
            unsavedIds += id.value
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
