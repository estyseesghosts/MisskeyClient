package me.foxtails.palustris

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.Audience
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.EntityId
import me.foxtails.palustris.domain.OwnedPost
import me.foxtails.palustris.domain.Post
import me.foxtails.palustris.domain.PostAction
import me.foxtails.palustris.domain.PostInteractionCounts
import me.foxtails.palustris.domain.PostActionResult
import me.foxtails.palustris.domain.Protocol
import me.foxtails.palustris.domain.ServerCapabilities
import me.foxtails.palustris.domain.SocialSource
import me.foxtails.palustris.domain.ThreadContext
import me.foxtails.palustris.domain.ThreadContinuation
import me.foxtails.palustris.domain.ThreadSessionKey
import me.foxtails.palustris.domain.Page
import me.foxtails.palustris.domain.Timeline
import me.foxtails.palustris.ui.thread.PostThreadPhase
import me.foxtails.palustris.ui.thread.PostThreadViewModel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PostThreadViewModelTest {
    private val origin = "https://example.org"
    private val account = AccountId(Connection(origin, Protocol.MASTODON), "viewer")
    private val focal = owned("focal")

    @Before fun setUp() { Dispatchers.setMain(StandardTestDispatcher()) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun excludedOriginKeepsFocalVisibleWithoutRequestingThread() = runTest {
        val source = FakeSource()
        val model = PostThreadViewModel(account, source, sessionRevision = 8L)

        model.activate(focal, supportsComments = false)
        advanceUntilIdle()

        assertEquals(0, source.threadCalls)
        assertEquals(PostThreadPhase.Inactive, model.state.value.phase)
        assertEquals("focal", model.state.value.focal?.post?.id?.value)
    }

    @Test
    fun successfulAcquisitionBuildsAncestorsAndRepliesOnce() = runTest {
        val source = FakeSource(
            context = ThreadContext(
                focal = focal.post,
                ancestors = listOf(owned("ancestor", replyTo = "focal").post),
                descendants = listOf(owned("reply", replyTo = "focal").post),
            ),
        )
        val model = PostThreadViewModel(account, source, sessionRevision = 8L)

        model.activate(focal, supportsComments = true)
        advanceUntilIdle()
        model.activate(focal, supportsComments = true)
        advanceUntilIdle()

        assertEquals(1, source.threadCalls)
        assertEquals(PostThreadPhase.Content, model.state.value.phase)
        assertEquals(listOf("ancestor"), model.state.value.ancestors.map { it.post.id.value })
        assertEquals(listOf("reply"), model.state.value.rows.map { it.post.id.value })
    }

    @Test
    fun continuationIsBoundToTheActiveSession() = runTest {
        val key = ThreadSessionKey(account, 8L, focal.post.id)
        val source = FakeSource(context = ThreadContext(focal = focal.post, continuation = ThreadContinuation(key, "more")))
        val model = PostThreadViewModel(account, source, sessionRevision = 8L)

        model.activate(focal, supportsComments = true)
        advanceUntilIdle()
        model.continueAcquisition()
        advanceUntilIdle()

        assertEquals(2, source.threadCalls)
        assertTrue(source.continuationCalls)
    }

    @Test
    fun confirmedFavoriteSurvivesAReplacementRefresh() = runTest {
        val source = FakeSource(context = ThreadContext(focal = focal.post))
        val model = PostThreadViewModel(account, source, sessionRevision = 8L)

        model.activate(focal, supportsComments = true)
        advanceUntilIdle()
        model.favorite(model.state.value.focal!!)
        advanceUntilIdle()
        model.refresh()
        advanceUntilIdle()

        assertTrue(model.state.value.focal!!.post.favourited)
    }

    @Test
    fun publishedReplyIncrementsKnownReplyCount() = runTest {
        val parent = focal.copy(post = focal.post.copy(
            interactionCounts = PostInteractionCounts(replyCount = 2),
        ))
        val source = FakeSource(context = ThreadContext(focal = parent.post))
        val model = PostThreadViewModel(account, source, sessionRevision = 8L)

        model.activate(parent, supportsComments = true)
        advanceUntilIdle()
        model.acceptPublishedReply(owned("published", replyTo = "focal"))

        assertEquals(3, model.state.value.focal!!.post.interactionCounts.replyCount)
    }

    @Test
    fun publishedReplyKeepsUnavailableReplyCountUnavailable() = runTest {
        val source = FakeSource(context = ThreadContext(focal = focal.post))
        val model = PostThreadViewModel(account, source, sessionRevision = 8L)

        model.activate(focal, supportsComments = true)
        advanceUntilIdle()
        model.acceptPublishedReply(owned("published", replyTo = "focal"))

        assertEquals(null, model.state.value.focal!!.post.interactionCounts.replyCount)
    }

    private fun owned(id: String, replyTo: String? = null): OwnedPost {
        val author = Account(account, "Viewer", "@viewer@example.org")
        return OwnedPost(
            account,
            Post(
                id = EntityId(origin, id),
                author = author,
                text = id,
                publishedAtEpochMillis = 0L,
                audience = Audience.Public,
                replyTo = replyTo?.let { EntityId(origin, it) },
            ),
            sessionRevision = 8L,
        )
    }

    private class FakeSource(
        var context: ThreadContext = ThreadContext(focal = Post(
            EntityId("https://example.org", "focal"),
            Account(AccountId(Connection("https://example.org", Protocol.MASTODON), "viewer"), "Viewer", "@viewer@example.org"),
            "focal",
            0L,
            Audience.Public,
        )),
    ) : SocialSource {
        override val capabilities = ServerCapabilities(
            actions = setOf(PostAction.Reply, PostAction.Reshare, PostAction.Favorite, PostAction.Bookmark),
            threads = me.foxtails.palustris.domain.CapabilityStatus.Supported,
        )
        var threadCalls = 0
        var continuationCalls = false

        override suspend fun timeline(timeline: Timeline, cursor: String?): Page<Post> = Page(emptyList())

        override suspend fun threadContext(focalId: EntityId, continuation: ThreadContinuation?): ThreadContext {
            threadCalls++
            continuationCalls = continuation != null
            return if (continuation == null) context else context.copy(continuation = null)
        }

        override suspend fun setPrimaryFavourite(id: EntityId, favouriteEmoji: String, selected: Boolean) =
            PostActionResult(selected = selected)
    }
}
