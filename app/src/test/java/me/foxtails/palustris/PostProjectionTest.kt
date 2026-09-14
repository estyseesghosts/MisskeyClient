package me.foxtails.palustris

import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.Audience
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.EmojiChoice
import me.foxtails.palustris.domain.EntityId
import me.foxtails.palustris.domain.Post
import me.foxtails.palustris.domain.PostInteractionCounts
import me.foxtails.palustris.domain.Protocol
import me.foxtails.palustris.domain.Reaction
import me.foxtails.palustris.domain.mergeExternalActionFields
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PostProjectionTest {
    private val connection = Connection("https://example.org", Protocol.MISSKEY)
    private val author = Account(AccountId(connection, "author"), "Author", "@author@example.org")

    private fun post(
        id: String = "post",
        reactions: List<Reaction> = emptyList(),
        selectedReactions: List<EmojiChoice> = emptyList(),
        myReaction: String? = null,
        favourited: Boolean = false,
        saved: Boolean = false,
        counts: PostInteractionCounts = PostInteractionCounts(),
    ) = Post(
        id = EntityId(connection.origin, id),
        author = author,
        text = id,
        publishedAtEpochMillis = 0L,
        audience = Audience.Public,
        reactions = reactions,
        selectedReactions = selectedReactions,
        myReaction = myReaction,
        favourited = favourited,
        saved = saved,
        interactionCounts = counts,
    )

    @Test
    fun absentReactionDataKeepsConfirmedReactionState() {
        val existing = post(
            reactions = listOf(Reaction("👍", 1, selected = true)),
            selectedReactions = listOf(EmojiChoice("👍", "👍", null)),
            myReaction = "👍",
        )

        val merged = existing.mergeExternalActionFields(post(saved = true))

        assertEquals("👍", merged.myReaction)
        assertEquals(listOf("👍"), merged.selectedReactions.map { it.submissionValue })
        assertEquals(listOf("👍"), merged.reactions.map { it.emoji })
        assertTrue(merged.saved)
    }

    @Test
    fun authoritativeEmptyReactionsClearConfirmedState() {
        val existing = post(
            reactions = listOf(Reaction("👍", 1, selected = true)),
            selectedReactions = listOf(EmojiChoice("👍", "👍", null)),
            myReaction = "👍",
            counts = PostInteractionCounts(reactionCount = 1),
        )

        val merged = existing.mergeExternalActionFields(
            post(counts = PostInteractionCounts(reactionCount = 0)),
        )

        assertNull(merged.myReaction)
        assertTrue(merged.selectedReactions.isEmpty())
        assertTrue(merged.reactions.isEmpty())
    }

    @Test
    fun nonReactionFieldsAlwaysComeFromTheProjection() {
        val existing = post(favourited = false, saved = false)
        val incoming = post(favourited = true, saved = true)

        val merged = existing.mergeExternalActionFields(incoming)

        assertTrue(merged.favourited)
        assertTrue(merged.saved)
    }

    @Test
    fun unknownCountsAreNotInvented() {
        val existing = post(counts = PostInteractionCounts(reactionCount = null))
        val merged = existing.mergeExternalActionFields(post())

        assertNull(merged.interactionCounts.reactionCount)
    }
}
