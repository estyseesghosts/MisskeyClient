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
import me.foxtails.palustris.domain.CapabilityStatus
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.EmojiCapabilities
import me.foxtails.palustris.domain.EmojiChoice
import me.foxtails.palustris.domain.EntityId
import me.foxtails.palustris.domain.OwnedPost
import me.foxtails.palustris.domain.Post
import me.foxtails.palustris.domain.PostAction
import me.foxtails.palustris.domain.PostActionResult
import me.foxtails.palustris.domain.PostInteractionCounts
import me.foxtails.palustris.domain.PrimaryFavouriteCapability
import me.foxtails.palustris.domain.PrimaryFavouriteMode
import me.foxtails.palustris.domain.Protocol
import me.foxtails.palustris.domain.Reaction
import me.foxtails.palustris.domain.ReactionSelectionMode
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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

    @Test
    fun staleOrForeignActivationIsIgnored() = runTest {
        val source = FakeSource(context = ThreadContext(focal = focal.post))
        val model = PostThreadViewModel(account, source, sessionRevision = 8L)

        model.activate(OwnedPost(account, focal.post, 7L), supportsComments = true)
        model.activate(
            OwnedPost(AccountId(Connection(origin, Protocol.MASTODON), "other"), focal.post, 8L),
            supportsComments = true,
        )
        advanceUntilIdle()

        assertEquals(0, source.threadCalls)
        assertEquals(PostThreadPhase.Inactive, model.state.value.phase)
    }

    @Test
    fun replacementThreadDropsStaleActionCompletion() = runTest {
        val other = owned("other")
        val source = GatedThreadSource()
        source.contextFor = { focalId ->
            if (focalId == other.post.id) {
                ThreadContext(focal = other.post, ancestors = listOf(focal.post))
            } else {
                ThreadContext(focal = focal.post)
            }
        }
        val model = PostThreadViewModel(account, source, sessionRevision = 8L)

        model.activate(focal, supportsComments = true)
        advanceUntilIdle()
        model.react(model.state.value.focal!!, EmojiChoice("👍", "👍", null))
        advanceUntilIdle()
        assertEquals(1, source.reactedCalls.size)

        model.activate(other, supportsComments = true)
        advanceUntilIdle()
        // The old post is present again as the new thread's ancestor. A late result from
        // the replaced thread must not modify it.
        source.completeReact(0)
        advanceUntilIdle()

        assertTrue(model.state.value.ancestors.single().post.selectedReactions.isEmpty())
        assertTrue(model.state.value.pendingActions.isEmpty())
    }

    @Test
    fun failedSecondReactionRestoresPreviousConfirmedOverlay() = runTest {
        val source = GatedThreadSource()
        val model = PostThreadViewModel(account, source, sessionRevision = 8L)

        model.activate(focal, supportsComments = true)
        advanceUntilIdle()
        model.react(model.state.value.focal!!, EmojiChoice("👍", "👍", null))
        advanceUntilIdle()
        source.completeReact(0)
        advanceUntilIdle()
        assertEquals(listOf("👍"), model.state.value.focal!!.post.selectedReactions.map { it.submissionValue })

        model.react(model.state.value.focal!!, EmojiChoice("😂", "😂", null))
        advanceUntilIdle()
        source.completeRemove(0)
        advanceUntilIdle()
        source.failReact(1, java.io.IOException("add failed"))
        advanceUntilIdle()
        // The server never stored either reaction. Only the restored confirmed overlay
        // can keep the first selection across the refresh.
        model.refresh()
        advanceUntilIdle()

        assertEquals(listOf("👍"), model.state.value.focal!!.post.selectedReactions.map { it.submissionValue })
    }

    @Test
    fun confirmedReactionRemovalSurvivesRefresh() = runTest {
        val reacted = focal.post.copy(
            myReaction = "👍",
            selectedReactions = listOf(EmojiChoice("👍", "👍", null)),
            reactions = listOf(Reaction("👍", 1, selected = true)),
        )
        // The server still returns the removed reaction. The confirmed overlay must win.
        val source = GatedThreadSource(context = ThreadContext(focal = reacted))
        val model = PostThreadViewModel(account, source, sessionRevision = 8L)

        model.activate(OwnedPost(account, reacted, 8L), supportsComments = true)
        advanceUntilIdle()
        model.react(model.state.value.focal!!, EmojiChoice("👍", "👍", null))
        advanceUntilIdle()
        source.completeRemove(0)
        advanceUntilIdle()
        model.refresh()
        advanceUntilIdle()

        assertNull(model.state.value.focal!!.post.myReaction)
        assertTrue(model.state.value.focal!!.post.selectedReactions.isEmpty())
    }

    @Test
    fun failedReactionFavoriteLeavesNoOptimisticOverlay() = runTest {
        val source = GatedThreadSource(primaryFavouriteMode = PrimaryFavouriteMode.Reaction)
        val model = PostThreadViewModel(account, source, sessionRevision = 8L)

        model.activate(focal, supportsComments = true)
        advanceUntilIdle()
        model.favorite(model.state.value.focal!!)
        advanceUntilIdle()
        source.failFavourite(0, java.io.IOException("favorite down"))
        advanceUntilIdle()
        model.refresh()
        advanceUntilIdle()

        assertNull(model.state.value.focal!!.post.myReaction)
        assertFalse(model.state.value.focal!!.post.favourited)
        assertTrue(model.state.value.focal!!.post.selectedReactions.isEmpty())
    }

    @Test
    fun acknowledgedFavoriteOverlayRetiresAfterRefresh() = runTest {
        val source = GatedThreadSource()
        val model = PostThreadViewModel(account, source, sessionRevision = 8L)

        model.activate(focal, supportsComments = true)
        advanceUntilIdle()
        model.favorite(model.state.value.focal!!)
        advanceUntilIdle()
        source.completeFavourite(0, PostActionResult(selected = true, createdRepostId = null))
        advanceUntilIdle()

        // The server confirms the favorite, so the overlay retires.
        source.context = ThreadContext(focal = focal.post.copy(
            favourited = true,
            interactionCounts = PostInteractionCounts(favouriteCount = 1),
        ))
        model.refresh()
        advanceUntilIdle()
        // A later remote change is no longer hidden by the stale overlay.
        source.context = ThreadContext(focal = focal.post)
        model.refresh()
        advanceUntilIdle()

        assertFalse(model.state.value.focal!!.post.favourited)
    }

    @Test
    fun absentExternalReactionProjectionKeepsConfirmedState() = runTest {
        val reacted = focal.post.copy(
            myReaction = "🎉",
            selectedReactions = listOf(EmojiChoice("🎉", "🎉", null)),
            reactions = listOf(Reaction("🎉", 2, selected = true)),
            interactionCounts = PostInteractionCounts(reactionCount = 2),
        )
        val source = GatedThreadSource(context = ThreadContext(focal = reacted))
        val model = PostThreadViewModel(account, source, sessionRevision = 8L)

        model.activate(OwnedPost(account, reacted, 8L), supportsComments = true)
        advanceUntilIdle()
        model.applyExternalPost(OwnedPost(account, reacted.copy(
            myReaction = null,
            selectedReactions = emptyList(),
            reactions = emptyList(),
            interactionCounts = PostInteractionCounts(),
        ), 8L))

        assertEquals("🎉", model.state.value.focal!!.post.myReaction)
        assertEquals(listOf("🎉"), model.state.value.focal!!.post.reactions.map { it.emoji })
    }

    @Test
    fun authoritativeEmptyExternalProjectionClearsState() = runTest {
        val reacted = focal.post.copy(
            myReaction = "🎉",
            selectedReactions = listOf(EmojiChoice("🎉", "🎉", null)),
            reactions = listOf(Reaction("🎉", 2, selected = true)),
            interactionCounts = PostInteractionCounts(reactionCount = 2),
        )
        val source = GatedThreadSource(context = ThreadContext(focal = reacted))
        val model = PostThreadViewModel(account, source, sessionRevision = 8L)

        model.activate(OwnedPost(account, reacted, 8L), supportsComments = true)
        advanceUntilIdle()
        model.applyExternalPost(OwnedPost(account, reacted.copy(
            myReaction = null,
            selectedReactions = emptyList(),
            reactions = emptyList(),
            interactionCounts = PostInteractionCounts(reactionCount = 0),
        ), 8L))

        assertNull(model.state.value.focal!!.post.myReaction)
        assertTrue(model.state.value.focal!!.post.reactions.isEmpty())
    }

    @Test
    fun externalProjectionDoesNotEmitToTheUpdateListener() = runTest {
        val source = GatedThreadSource(context = ThreadContext(focal = focal.post))
        val model = PostThreadViewModel(account, source, sessionRevision = 8L)
        var emissions = 0

        model.setPostUpdateListener { emissions += 1 }
        model.activate(focal, supportsComments = true)
        advanceUntilIdle()
        val baseline = emissions
        model.applyExternalPost(OwnedPost(account, focal.post.copy(favourited = true), 8L))

        assertEquals(baseline, emissions)
        assertTrue(model.state.value.focal!!.post.favourited)
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

    private class GatedThreadSource(
        var context: ThreadContext = ThreadContext(focal = Post(
            EntityId("https://example.org", "focal"),
            Account(AccountId(Connection("https://example.org", Protocol.MASTODON), "viewer"), "Viewer", "@viewer@example.org"),
            "focal",
            0L,
            Audience.Public,
        )),
        private val primaryFavouriteMode: PrimaryFavouriteMode = PrimaryFavouriteMode.Native,
    ) : SocialSource {
        override val capabilities = ServerCapabilities(
            actions = setOf(PostAction.Reply, PostAction.Reshare, PostAction.Favorite, PostAction.React, PostAction.Bookmark),
            primaryFavourite = PrimaryFavouriteCapability(mode = primaryFavouriteMode),
            emoji = EmojiCapabilities(selectionMode = ReactionSelectionMode.Single),
            threads = CapabilityStatus.Supported,
        )
        var contextFor: ((EntityId) -> ThreadContext)? = null
        var threadCalls = 0
        val reactedCalls = mutableListOf<Pair<EntityId, EmojiChoice>>()
        val removedCalls = mutableListOf<Pair<EntityId, EmojiChoice>>()
        private val reactPending = ArrayDeque<CompletableDeferred<Unit>>()
        private val removePending = ArrayDeque<CompletableDeferred<Unit>>()
        private val favouritePending = ArrayDeque<CompletableDeferred<PostActionResult>>()

        override suspend fun timeline(timeline: Timeline, cursor: String?): Page<Post> = Page(emptyList())

        override suspend fun threadContext(focalId: EntityId, continuation: ThreadContinuation?): ThreadContext {
            threadCalls++
            val base = contextFor?.invoke(focalId) ?: context
            return if (continuation == null) base else base.copy(continuation = null)
        }

        override suspend fun react(id: EntityId, choice: EmojiChoice) {
            reactedCalls += id to choice
            val gate = CompletableDeferred<Unit>()
            reactPending += gate
            // NonCancellable so a replaced thread still reaches the owner's guard.
            withContext(NonCancellable) { gate.await() }
        }

        override suspend fun removeReaction(id: EntityId, choice: EmojiChoice) {
            removedCalls += id to choice
            val gate = CompletableDeferred<Unit>()
            removePending += gate
            withContext(NonCancellable) { gate.await() }
        }

        override suspend fun setPrimaryFavourite(
            id: EntityId,
            favouriteEmoji: String,
            selected: Boolean,
        ): PostActionResult {
            val gate = CompletableDeferred<PostActionResult>()
            favouritePending += gate
            return withContext(NonCancellable) { gate.await() }
        }

        fun completeReact(index: Int) { reactPending[index].complete(Unit) }
        fun failReact(index: Int, error: Exception) { reactPending[index].completeExceptionally(error) }
        fun completeRemove(index: Int) { removePending[index].complete(Unit) }
        fun completeFavourite(index: Int, result: PostActionResult) { favouritePending[index].complete(result) }
        fun failFavourite(index: Int, error: Exception) { favouritePending[index].completeExceptionally(error) }
    }
}
