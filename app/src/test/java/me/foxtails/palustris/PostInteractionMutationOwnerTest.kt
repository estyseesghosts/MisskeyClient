package me.foxtails.palustris

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.Audience
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.EmojiCapabilities
import me.foxtails.palustris.domain.EmojiChoice
import me.foxtails.palustris.domain.EntityId
import me.foxtails.palustris.domain.OwnedPost
import me.foxtails.palustris.domain.Page
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
import me.foxtails.palustris.domain.Timeline
import me.foxtails.palustris.domain.effectiveTargetId
import me.foxtails.palustris.ui.posts.PostInteractionExecutionAuthority
import me.foxtails.palustris.ui.posts.PostInteractionMutationOwner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PostInteractionMutationOwnerTest {
    private val connection = Connection("https://example.org", Protocol.MISSKEY)
    private val accountId = AccountId(connection, "owner")
    private val author = Account(AccountId(connection, "author"), "Author", "@author@example.org")

    private fun post(
        id: String = "post",
        favourited: Boolean = false,
        saved: Boolean = false,
        reposted: Boolean = false,
        myReaction: String? = null,
        selectedReactions: List<EmojiChoice> = emptyList(),
        reactions: List<Reaction> = emptyList(),
        counts: PostInteractionCounts = PostInteractionCounts(),
        text: String = "Post",
        actionTargetId: EntityId? = null,
    ) = Post(
        id = EntityId(connection.origin, id),
        author = author,
        text = text,
        publishedAtEpochMillis = 0L,
        audience = Audience.Public,
        reactions = reactions,
        selectedReactions = selectedReactions,
        myReaction = myReaction,
        favourited = favourited,
        saved = saved,
        reposted = reposted,
        actionTargetId = actionTargetId,
        interactionCounts = counts,
    )

    private class GatedMutationSource(
        favouriteMode: PrimaryFavouriteMode = PrimaryFavouriteMode.Native,
        selectionMode: ReactionSelectionMode = ReactionSelectionMode.Single,
    ) : SocialSource {
        override val capabilities = ServerCapabilities(
            actions = setOf(PostAction.Favorite, PostAction.React, PostAction.Reshare, PostAction.Bookmark),
            primaryFavourite = PrimaryFavouriteCapability(mode = favouriteMode),
            emoji = EmojiCapabilities(selectionMode = selectionMode),
        )
        val favouriteCalls = mutableListOf<EntityId>()
        val savedCalls = mutableListOf<Pair<EntityId, Boolean>>()
        val resharedCalls = mutableListOf<EntityId>()
        val reactedCalls = mutableListOf<Pair<EntityId, EmojiChoice>>()
        val removedCalls = mutableListOf<Pair<EntityId, EmojiChoice>>()
        var postResult: Post? = null
        var gatePostRefresh = false
        private val postPending = ArrayDeque<CompletableDeferred<Post>>()
        private val favouritePending = ArrayDeque<CompletableDeferred<PostActionResult>>()
        private val savedPending = ArrayDeque<CompletableDeferred<PostActionResult>>()
        private val resharedPending = ArrayDeque<CompletableDeferred<PostActionResult>>()
        private val reactPending = ArrayDeque<CompletableDeferred<Unit>>()
        private val removePending = ArrayDeque<CompletableDeferred<Unit>>()

        override suspend fun timeline(timeline: Timeline, cursor: String?): Page<Post> = Page(emptyList())
        override suspend fun searchHashtag(tag: String, cursor: String?): Page<Post> = Page(emptyList())

        override suspend fun setPrimaryFavourite(id: EntityId, favouriteEmoji: String, selected: Boolean): PostActionResult {
            favouriteCalls += id
            val gate = CompletableDeferred<PostActionResult>()
            favouritePending += gate
            return withContext(NonCancellable) { gate.await() }
        }

        override suspend fun setSaved(id: EntityId, selected: Boolean): PostActionResult {
            savedCalls += id to selected
            val gate = CompletableDeferred<PostActionResult>()
            savedPending += gate
            return withContext(NonCancellable) { gate.await() }
        }

        override suspend fun setReshared(id: EntityId, selected: Boolean, ownRepostId: EntityId?): PostActionResult {
            resharedCalls += id
            val gate = CompletableDeferred<PostActionResult>()
            resharedPending += gate
            return withContext(NonCancellable) { gate.await() }
        }

        override suspend fun react(id: EntityId, choice: EmojiChoice) {
            reactedCalls += id to choice
            val gate = CompletableDeferred<Unit>()
            reactPending += gate
            return withContext(NonCancellable) { gate.await() }
        }

        override suspend fun removeReaction(id: EntityId, choice: EmojiChoice) {
            removedCalls += id to choice
            val gate = CompletableDeferred<Unit>()
            removePending += gate
            return withContext(NonCancellable) { gate.await() }
        }

        override suspend fun post(id: EntityId): Post {
            val configured = postResult
            if (configured != null) return configured
            if (!gatePostRefresh) throw UnsupportedOperationException("no refreshed post")
            val gate = CompletableDeferred<Post>()
            postPending += gate
            return gate.await()
        }

        fun completeFavourite(index: Int, result: PostActionResult) { favouritePending[index].complete(result) }
        fun failFavourite(index: Int, error: Exception) { favouritePending[index].completeExceptionally(error) }
        fun completeSaved(index: Int, result: PostActionResult) { savedPending[index].complete(result) }
        fun failSaved(index: Int, error: Exception) { savedPending[index].completeExceptionally(error) }
        fun completeReact(index: Int) { reactPending[index].complete(Unit) }
        fun failReact(index: Int, error: Exception) { reactPending[index].completeExceptionally(error) }
        fun completeRemove(index: Int) { removePending[index].complete(Unit) }
        fun failRemove(index: Int, error: Exception) { removePending[index].completeExceptionally(error) }
        fun completeReshared(index: Int, result: PostActionResult) { resharedPending[index].complete(result) }
    }

    private inner class RowStore {
        val rows = mutableMapOf<EntityId, Post>()
        val failures = mutableListOf<Exception>()
        fun seed(vararg posts: Post) { posts.forEach { rows[it.id] = it } }
        fun owned(id: String, revision: Long = 1L): OwnedPost =
            OwnedPost(accountId, rows[EntityId(connection.origin, id)]!!, revision)
        fun apply(owned: OwnedPost, target: EntityId, transform: (Post) -> Post) {
            rows.forEach { (id, post) ->
                val effective = post.actionTargetId ?: post.id
                if ((id == target || effective == target)) rows[id] = transform(post)
            }
        }
        fun get(id: String): Post = rows[EntityId(connection.origin, id)]!!
    }

    private fun owner(
        source: GatedMutationSource,
        store: RowStore,
        authority: PostInteractionExecutionAuthority = PostInteractionExecutionAuthority(),
        scope: kotlinx.coroutines.CoroutineScope,
    ) = PostInteractionMutationOwner(
        accountId = accountId,
        source = source,
        sessionRevision = 1L,
        scope = scope,
        isActionAvailable = { true },
        favouriteEmoji = { "❤" },
        updatePost = store::apply,
        onFailure = { store.failures += it },
        executionAuthority = authority,
    )

    @Test
    fun favoriteFailurePreservesBookmarkSuccess() = runTest {
        val source = GatedMutationSource()
        val store = RowStore()
        store.seed(post())
        val mutations = owner(source, store, scope = this)
        val owned = store.owned("post")

        mutations.favorite(owned)
        mutations.bookmark(owned)
        advanceUntilIdle()
        source.failFavourite(0, java.io.IOException("favorite down"))
        advanceUntilIdle()
        source.completeSaved(0, PostActionResult(selected = true))
        advanceUntilIdle()

        assertFalse(store.get("post").favourited)
        assertTrue(store.get("post").saved)
        assertEquals(1, store.failures.size)
    }

    @Test
    fun bookmarkFailurePreservesFavoriteSuccessInReverseOrder() = runTest {
        val source = GatedMutationSource()
        val store = RowStore()
        store.seed(post())
        val mutations = owner(source, store, scope = this)
        val owned = store.owned("post")

        mutations.favorite(owned)
        mutations.bookmark(owned)
        advanceUntilIdle()
        source.completeFavourite(0, PostActionResult(selected = true))
        advanceUntilIdle()
        // The failure arrives after the unrelated family already succeeded.
        val savedCalls = source.savedCalls.size
        source.failSaved(0, java.io.IOException("save down"))
        advanceUntilIdle()

        assertTrue(store.get("post").favourited)
        assertFalse(store.get("post").saved)
        assertEquals(savedCalls, source.savedCalls.size)
    }

    @Test
    fun nativeFavoriteAndReactionUseIndependentFamilies() = runTest {
        val source = GatedMutationSource()
        val store = RowStore()
        store.seed(post())
        val mutations = owner(source, store, scope = this)

        mutations.favorite(store.owned("post"))
        mutations.react(store.owned("post"), EmojiChoice("👍", "👍", null))
        advanceUntilIdle()

        assertEquals(1, source.favouriteCalls.size)
        assertEquals(1, source.reactedCalls.size)
        source.completeReact(0)
        advanceUntilIdle()
        source.failFavourite(0, java.io.IOException("favorite down"))
        advanceUntilIdle()

        assertFalse(store.get("post").favourited)
        assertEquals(listOf("👍"), store.get("post").selectedReactions.map { it.submissionValue })
    }

    @Test
    fun reactionFailureKeepsUnrelatedExternalUpdate() = runTest {
        val source = GatedMutationSource()
        val store = RowStore()
        store.seed(post())
        val mutations = owner(source, store, scope = this)

        mutations.react(store.owned("post"), EmojiChoice("👍", "👍", null))
        advanceUntilIdle()
        // A confirmed external update lands while the reaction waits.
        store.apply(store.owned("post"), EntityId(connection.origin, "post")) {
            it.copy(saved = true, text = "Edited")
        }
        source.failReact(0, java.io.IOException("react down"))
        advanceUntilIdle()

        assertTrue(store.get("post").saved)
        assertEquals("Edited", store.get("post").text)
        assertTrue(store.get("post").selectedReactions.isEmpty())
        assertNull(store.get("post").myReaction)
    }

    @Test
    fun newerSameFamilyProjectionSurvivesOlderFailure() = runTest {
        val source = GatedMutationSource()
        val store = RowStore()
        store.seed(post())
        val mutations = owner(source, store, scope = this)

        mutations.react(store.owned("post"), EmojiChoice("👍", "👍", null))
        advanceUntilIdle()
        // A newer confirmed projection for the same family arrives first.
        store.apply(store.owned("post"), EntityId(connection.origin, "post")) {
            it.copy(
                myReaction = "🎉",
                selectedReactions = listOf(EmojiChoice("🎉", "🎉", null)),
                reactions = listOf(Reaction("🎉", 2, selected = true)),
            )
        }
        source.failReact(0, java.io.IOException("react down"))
        advanceUntilIdle()

        assertEquals("🎉", store.get("post").myReaction)
        assertEquals(listOf("🎉"), store.get("post").selectedReactions.map { it.submissionValue })
        assertEquals(1, store.failures.size)
    }

    @Test
    fun wrappersShareOneMutationForTheSameTarget() = runTest {
        val source = GatedMutationSource()
        val store = RowStore()
        val target = EntityId(connection.origin, "post")
        store.seed(
            post(),
            post(id = "wrapper", actionTargetId = target),
        )
        val mutations = owner(source, store, scope = this)

        mutations.react(store.owned("wrapper"), EmojiChoice("👍", "👍", null))
        mutations.react(store.owned("post"), EmojiChoice("😂", "😂", null))
        advanceUntilIdle()

        assertEquals(1, source.reactedCalls.size)
        source.completeReact(0)
        advanceUntilIdle()
        assertEquals(listOf("👍"), store.get("post").selectedReactions.map { it.submissionValue })
        assertEquals(listOf("👍"), store.get("wrapper").selectedReactions.map { it.submissionValue })
    }

    @Test
    fun staleSessionWorkIsDroppedSilently() = runTest {
        val source = GatedMutationSource()
        val store = RowStore()
        store.seed(post())
        val mutations = owner(source, store, scope = this)

        mutations.favorite(store.owned("post", revision = 0L))
        advanceUntilIdle()
        assertTrue(source.favouriteCalls.isEmpty())

        mutations.favorite(store.owned("post"))
        advanceUntilIdle()
        mutations.stop()
        source.completeFavourite(0, PostActionResult(selected = true))
        advanceUntilIdle()

        // Teardown discards rows, so only silence is asserted: one request went out,
        // the late completion reconciled nothing and reported no failure.
        assertEquals(1, source.favouriteCalls.size)
        assertTrue(store.failures.isEmpty())
    }

    @Test
    fun staleServerSnapshotPreservesNewerLocalFields() = runTest {
        val source = GatedMutationSource()
        val store = RowStore()
        store.seed(post(saved = true, text = "New", counts = PostInteractionCounts(favouriteCount = 4)))
        val mutations = owner(source, store, scope = this)

        mutations.favorite(store.owned("post"))
        advanceUntilIdle()
        source.completeFavourite(
            0,
            PostActionResult(
                selected = true,
                post = post(saved = false, text = "Stale", counts = PostInteractionCounts(favouriteCount = 1)),
            ),
        )
        advanceUntilIdle()

        assertTrue(store.get("post").favourited)
        assertTrue(store.get("post").saved)
        assertEquals("New", store.get("post").text)
        assertEquals(5, store.get("post").interactionCounts.favouriteCount)
    }

    @Test
    fun partialReplacementFailureReconcilesServerState() = runTest {
        val source = GatedMutationSource()
        val store = RowStore()
        store.seed(post(
            myReaction = ":a:",
            selectedReactions = listOf(EmojiChoice(":a:", ":a:", null)),
            reactions = listOf(Reaction(":a:", 3, selected = true)),
        ))
        val mutations = owner(source, store, scope = this)
        source.postResult = post(reactions = emptyList())

        mutations.react(store.owned("post"), EmojiChoice(":b:", ":b:", null))
        advanceUntilIdle()
        source.completeRemove(0)
        advanceUntilIdle()
        source.failReact(0, java.io.IOException("add failed"))
        advanceUntilIdle()

        assertNull(store.get("post").myReaction)
        assertTrue(store.get("post").selectedReactions.isEmpty())
        assertTrue(store.get("post").reactions.isEmpty())
        assertEquals(1, store.failures.size)
    }

    @Test
    fun cancelledRefreshNeverReportsFailure() = runTest {
        val source = GatedMutationSource()
        val store = RowStore()
        store.seed(post(
            myReaction = ":a:",
            selectedReactions = listOf(EmojiChoice(":a:", ":a:", null)),
            reactions = listOf(Reaction(":a:", 3, selected = true)),
        ))
        // Gate the bounded refresh so it stays in flight until the stop below cancels it.
        source.gatePostRefresh = true
        val mutations = owner(source, store, scope = this)

        mutations.react(store.owned("post"), EmojiChoice(":b:", ":b:", null))
        advanceUntilIdle()
        source.completeRemove(0)
        advanceUntilIdle()
        source.failReact(0, java.io.IOException("add failed"))
        advanceUntilIdle()
        // The bounded refresh is in flight. Stopping cancels it. Cancellation stays
        // cancellation: no rollback update and no failure report follow.
        mutations.stop()
        advanceUntilIdle()

        assertTrue(store.failures.isEmpty())
        assertEquals(listOf(":b:"), store.get("post").selectedReactions.map { it.submissionValue })
    }

    @Test
    fun reactionFavoriteFailureLeavesNoOptimisticState() = runTest {
        val source = GatedMutationSource(favouriteMode = PrimaryFavouriteMode.Reaction)
        val store = RowStore()
        store.seed(post())
        val mutations = owner(source, store, scope = this)

        mutations.favorite(store.owned("post"))
        advanceUntilIdle()
        assertTrue(store.get("post").favourited)
        source.failFavourite(0, java.io.IOException("favorite down"))
        advanceUntilIdle()

        assertFalse(store.get("post").favourited)
        assertNull(store.get("post").myReaction)
        assertTrue(store.get("post").selectedReactions.isEmpty())
        assertTrue(store.get("post").reactions.isEmpty())
    }

    @Test
    fun failedSecondMutationRestoresFirstOverlay() = runTest {
        val source = GatedMutationSource()
        val store = RowStore()
        store.seed(post())
        val mutations = owner(source, store, scope = this)

        mutations.react(store.owned("post"), EmojiChoice(":a:", ":a:", null))
        advanceUntilIdle()
        source.completeReact(0)
        advanceUntilIdle()
        mutations.react(store.owned("post"), EmojiChoice(":b:", ":b:", null))
        advanceUntilIdle()
        // The replacement removed :a: optimistically before failing on :b:.
        source.completeRemove(0)
        advanceUntilIdle()
        source.failReact(1, java.io.IOException("add failed"))
        advanceUntilIdle()

        // The bounded refresh is unavailable here, so the guarded rollback restores :a:.
        assertEquals(listOf(":a:"), store.get("post").selectedReactions.map { it.submissionValue })
    }

    @Test
    fun crossSurfaceCallsShareOneFamilySlot() = runTest {
        val source = GatedMutationSource()
        val authority = PostInteractionExecutionAuthority()
        val feed = RowStore()
        val saved = RowStore()
        feed.seed(post())
        saved.seed(post())
        val feedMutations = owner(source, feed, authority, this)
        val savedMutations = owner(source, saved, authority, this)

        feedMutations.react(feed.owned("post"), EmojiChoice("👍", "👍", null))
        savedMutations.react(saved.owned("post"), EmojiChoice("😂", "😂", null))
        advanceUntilIdle()

        assertEquals(1, source.reactedCalls.size)
        source.completeReact(0)
        advanceUntilIdle()
        assertEquals(listOf("👍"), feed.get("post").selectedReactions.map { it.submissionValue })
        assertTrue(saved.get("post").selectedReactions.isEmpty())

        savedMutations.react(saved.owned("post"), EmojiChoice("😂", "😂", null))
        advanceUntilIdle()
        assertEquals(2, source.reactedCalls.size)
        // No mutation stays in flight: the test scope must not wait on a gated child.
        source.completeReact(1)
        advanceUntilIdle()
    }

    @Test
    fun stopReleasesTheFamilySlot() = runTest {
        val source = GatedMutationSource()
        val authority = PostInteractionExecutionAuthority()
        val first = RowStore()
        val second = RowStore()
        first.seed(post())
        second.seed(post())
        val firstMutations = owner(source, first, authority, this)
        val secondMutations = owner(source, second, authority, this)

        firstMutations.react(first.owned("post"), EmojiChoice("👍", "👍", null))
        advanceUntilIdle()
        firstMutations.stop()
        // The late completion is rejected and releases the family slot.
        source.completeReact(0)
        advanceUntilIdle()
        secondMutations.react(second.owned("post"), EmojiChoice("😂", "😂", null))
        advanceUntilIdle()

        assertEquals(2, source.reactedCalls.size)
        assertEquals("😂", source.reactedCalls.last().second.submissionValue)
        // No mutation stays in flight: the test scope must not wait on a gated child.
        source.completeReact(1)
        advanceUntilIdle()
    }

    @Test
    fun unknownCountsStayUnknown() = runTest {
        val source = GatedMutationSource()
        val store = RowStore()
        store.seed(post())
        val mutations = owner(source, store, scope = this)

        mutations.favorite(store.owned("post"))
        advanceUntilIdle()
        source.completeFavourite(
            0,
            PostActionResult(selected = true, post = post(counts = PostInteractionCounts())),
        )
        advanceUntilIdle()

        assertTrue(store.get("post").favourited)
        assertNull(store.get("post").interactionCounts.favouriteCount)
    }
}
