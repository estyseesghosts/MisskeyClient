package me.foxtails.palustris.ui.posts

import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.Audience
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.EmojiChoice
import me.foxtails.palustris.domain.EntityId
import me.foxtails.palustris.domain.Post
import me.foxtails.palustris.domain.Protocol
import me.foxtails.palustris.ui.hasVisibleInteractionSelection
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PostInteractionIndicatorTest {
    private val account = Account(
        AccountId(Connection("https://example.org", Protocol.MISSKEY), "person"),
        "Person",
        "@person@example.org",
    )

    private fun post(
        favourited: Boolean = false,
        myReaction: String? = null,
        selectedReactions: List<EmojiChoice> = emptyList(),
    ) = Post(EntityId(account.id.connection.origin, "post"), account, "Post", 0L, Audience.Public,
        favourited = favourited,
        myReaction = myReaction,
        selectedReactions = selectedReactions,
    )

    @Test
    fun indicatorUsesAnyNormalizedSelectedField() {
        assertTrue(post(favourited = true).hasVisibleInteractionSelection())
        assertTrue(post(myReaction = "👍").hasVisibleInteractionSelection())
        assertTrue(post(selectedReactions = listOf(EmojiChoice("🎉", "🎉"))).hasVisibleInteractionSelection())
        assertFalse(post().hasVisibleInteractionSelection())
    }
}
