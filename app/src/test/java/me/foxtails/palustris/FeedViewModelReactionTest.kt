package me.foxtails.palustris

import kotlinx.coroutines.CompletableDeferred
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
import me.foxtails.palustris.domain.CapabilityStatus
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.CustomEmoji
import me.foxtails.palustris.domain.EmojiCapabilities
import me.foxtails.palustris.domain.EmojiChoice
import me.foxtails.palustris.domain.EntityId
import me.foxtails.palustris.domain.OwnedPost
import me.foxtails.palustris.domain.Page
import me.foxtails.palustris.domain.Post
import me.foxtails.palustris.domain.PostAction
import me.foxtails.palustris.domain.Reaction
import me.foxtails.palustris.domain.ReactionSelectionMode
import me.foxtails.palustris.domain.ServerCapabilities
import me.foxtails.palustris.domain.SocialSource
import me.foxtails.palustris.domain.Timeline
import me.foxtails.palustris.domain.ValidatedUrl
import me.foxtails.palustris.ui.AccountSyncCoordinator
import me.foxtails.palustris.ui.FeedViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FeedViewModelReactionTest {
    private val connection = Connection("https://example.org", me.foxtails.palustris.domain.Protocol.MISSKEY)
    private val accountId = AccountId(connection, "owner")
    private val author = Account(AccountId(connection, "author"), "Author", "@author@example.org")
    private val blob = CustomEmoji(
        shortcode = "blob",
        animatedUrl = ValidatedUrl.https("https://cdn.example/blob.png"),
        staticUrl = ValidatedUrl.https("https://cdn.example/blob.png"),
        submissionValue = ":blob:",
    )

    private fun post(
        reactions: List<Reaction> = emptyList(),
        selectedReactions: List<EmojiChoice> = emptyList(),
        myReaction: String? = null,
    ) = Post(
        id = EntityId(connection.origin, "post"),
        author = author,
        text = "Post",
        publishedAtEpochMillis = 0,
        audience = Audience.Public,
        reactions = reactions,
        selectedReactions = selectedReactions,
        myReaction = myReaction,
    )

    private class ReactionSource(
        private val base: Post,
        val selectionMode: ReactionSelectionMode,
    ) : SocialSource {
        override val capabilities = ServerCapabilities(
            timelines = setOf(Timeline.Home),
            actions = setOf(PostAction.React, PostAction.Favorite),
            emoji = EmojiCapabilities(
                catalog = CapabilityStatus.Supported,
                reactionListing = CapabilityStatus.Supported,
                reactionMutation = CapabilityStatus.Supported,
                selectionMode = selectionMode,
            ),
        )
        val added = mutableListOf<Pair<EntityId, EmojiChoice>>()
        val removed = mutableListOf<Pair<EntityId, EmojiChoice>>()
        var reactGate: CompletableDeferred<Unit>? = null
        var reactError: Exception? = null

        override suspend fun timeline(timeline: Timeline, cursor: String?): Page<Post> = Page(listOf(base))

        override suspend fun searchHashtag(tag: String, cursor: String?): Page<Post> = Page(listOf(base))

        override suspend fun react(id: EntityId, choice: EmojiChoice) {
            reactGate?.await()
            reactError?.let { throw it }
            added += id to choice
        }

        override suspend fun removeReaction(id: EntityId, choice: EmojiChoice) {
            removed += id to choice
        }
    }

    private fun current(model: FeedViewModel): Post = model.feed.value.ownedPosts.single { it.fetchedBy == accountId }.post

    @Test
    fun singleModeReplaceRemovesPreviousReactionBeforeAddingTheNewOne() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val coordinator = AccountSyncCoordinator()
        try {
            val source = ReactionSource(
                post(
                    reactions = listOf(
                        Reaction(":blob:", 4, selected = true, emojiMetadata = blob),
                        Reaction("👍", 2, selected = false),
                    ),
                    selectedReactions = listOf(EmojiChoice(":blob:", ":blob:", blob)),
                    myReaction = ":blob:",
                ),
                ReactionSelectionMode.Single,
            )
            val model = FeedViewModel(accountId, source, coordinator)
            advanceUntilIdle()

            model.react(model.feed.value.ownedPosts.single(), EmojiChoice("👍", "👍", null))
            val optimistic = current(model)
            assertEquals(listOf(":blob:", "👍"), optimistic.reactions.map { it.emoji })
            assertEquals(3, optimistic.reactions.first { it.emoji == ":blob:" }.count)
            assertEquals(3, optimistic.reactions.first { it.emoji == "👍" }.count)
            assertFalse(optimistic.reactions.first { it.emoji == ":blob:" }.selected)
            assertEquals(listOf("👍"), optimistic.selectedReactions.map { it.submissionValue })
            assertEquals("👍", optimistic.myReaction)
            advanceUntilIdle()

            assertEquals(listOf(":blob:"), source.removed.map { it.second.submissionValue })
            assertEquals(listOf("👍"), source.added.map { it.second.submissionValue })
            val actionTarget = EntityId(connection.origin, "post")
            assertEquals(actionTarget, source.removed.single().first)
            assertEquals(actionTarget, source.added.single().first)
        } finally {
            coordinator.close()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun independentModeTogglesOnlyTheChosenReaction() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val coordinator = AccountSyncCoordinator()
        try {
            val source = ReactionSource(
                post(
                    reactions = listOf(Reaction(":blob:", 1, selected = true, emojiMetadata = blob)),
                    selectedReactions = listOf(EmojiChoice(":blob:", ":blob:", blob)),
                    myReaction = ":blob:",
                ),
                ReactionSelectionMode.Independent,
            )
            val model = FeedViewModel(accountId, source, coordinator)
            advanceUntilIdle()

            val owned = model.feed.value.ownedPosts.single()
            model.react(model.feed.value.ownedPosts.single(), EmojiChoice("👍", "👍", null))
            val optimistic = current(model)
            assertEquals(setOf(":blob:", "👍"), optimistic.selectedReactions.map { it.submissionValue }.toSet())
            assertEquals(1, optimistic.reactions.first { it.emoji == ":blob:" }.count)
            advanceUntilIdle()
            assertTrue(source.removed.isEmpty())
            assertEquals(listOf("👍"), source.added.map { it.second.submissionValue })

            model.react(model.feed.value.ownedPosts.single(), EmojiChoice("👍", "👍", null))
            advanceUntilIdle()
            assertEquals(listOf("👍"), source.removed.map { it.second.submissionValue })
            val afterDeselect = current(model)
            assertEquals(listOf(":blob:"), afterDeselect.selectedReactions.map { it.submissionValue })
            assertTrue(afterDeselect.reactions.none { it.emoji == "👍" })
        } finally {
            coordinator.close()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun deselectingTheSelectedReactionRemovesIt() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val coordinator = AccountSyncCoordinator()
        try {
            val source = ReactionSource(
                post(
                    reactions = listOf(Reaction(":blob:", 2, selected = true, emojiMetadata = blob)),
                    selectedReactions = listOf(EmojiChoice(":blob:", ":blob:", blob)),
                    myReaction = ":blob:",
                ),
                ReactionSelectionMode.Single,
            )
            val model = FeedViewModel(accountId, source, coordinator)
            advanceUntilIdle()

            model.react(model.feed.value.ownedPosts.single(), EmojiChoice(":blob:", ":blob:", blob))
            val optimistic = current(model)
            assertEquals(1, optimistic.reactions.first { it.emoji == ":blob:" }.count)
            assertTrue(optimistic.selectedReactions.isEmpty())
            assertNull(optimistic.myReaction)
            advanceUntilIdle()
            assertEquals(listOf(":blob:"), source.removed.map { it.second.submissionValue })
            assertTrue(source.added.isEmpty())
        } finally {
            coordinator.close()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun failedMutationRollsBackToThePreviousPost() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val coordinator = AccountSyncCoordinator()
        try {
            val source = ReactionSource(post(), ReactionSelectionMode.Single)
            val model = FeedViewModel(accountId, source, coordinator)
            advanceUntilIdle()

            source.reactError = java.io.IOException("network down")
            model.react(model.feed.value.ownedPosts.single(), EmojiChoice("👍", "👍", null))
            advanceUntilIdle()

            assertEquals(current(model), post())
            assertNotNull(model.feed.value.error)
        } finally {
            coordinator.close()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun duplicateTapsSuppressUntilTheInFlightActionCompletes() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val coordinator = AccountSyncCoordinator()
        try {
            val source = ReactionSource(post(), ReactionSelectionMode.Single)
            source.reactGate = CompletableDeferred()
            val model = FeedViewModel(accountId, source, coordinator)
            advanceUntilIdle()
            val owned = model.feed.value.ownedPosts.single()

            model.react(owned, EmojiChoice("👍", "👍", null))
            model.react(owned, EmojiChoice("😂", "😂", null))
            advanceUntilIdle()
            assertTrue(source.added.isEmpty())
            source.reactGate?.complete(Unit)
            advanceUntilIdle()
            assertEquals(listOf("👍"), source.added.map { it.second.submissionValue })
        } finally {
            coordinator.close()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun unicodeReactionOnExistingRowIncrementsItsCount() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val coordinator = AccountSyncCoordinator()
        try {
            val source = ReactionSource(
                post(reactions = listOf(Reaction("❤️", 1, selected = true))),
                ReactionSelectionMode.Single,
            )
            val model = FeedViewModel(accountId, source, coordinator)
            advanceUntilIdle()

            model.react(model.feed.value.ownedPosts.single(), EmojiChoice("❤️", "❤️", null))
            val optimistic = current(model)
            assertEquals(2, optimistic.reactions.single().count)
            advanceUntilIdle()
        } finally {
            coordinator.close()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun reactionSelectionsSurviveWithImageMetadata() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val coordinator = AccountSyncCoordinator()
        try {
            val source = ReactionSource(
                post(reactions = listOf(Reaction(":blob:", 1, selected = true, emojiMetadata = blob))),
                ReactionSelectionMode.Single,
            )
            val model = FeedViewModel(accountId, source, coordinator)
            advanceUntilIdle()

            model.react(model.feed.value.ownedPosts.single(), EmojiChoice(":blob:", ":blob:", blob))
            val optimistic = current(model)
            assertEquals(blob, optimistic.reactions.first { it.emoji == ":blob:" }.emojiMetadata)
            advanceUntilIdle()
        } finally {
            coordinator.close()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun hashtagSearchReactionMutationUpdatesTheSearchResultRow() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val coordinator = AccountSyncCoordinator()
        try {
            val source = ReactionSource(post(), ReactionSelectionMode.Single)
            val model = FeedViewModel(accountId, source, coordinator)
            advanceUntilIdle()

            model.search("#cats")
            advanceUntilIdle()
            val searchPost = model.feed.value.accountSearch.posts.single()
            model.react(OwnedPost(accountId, searchPost), EmojiChoice("👍", "👍", null))

            assertEquals("👍", model.feed.value.accountSearch.posts.single().myReaction)
            advanceUntilIdle()
        } finally {
            coordinator.close()
            Dispatchers.resetMain()
        }
    }
}
