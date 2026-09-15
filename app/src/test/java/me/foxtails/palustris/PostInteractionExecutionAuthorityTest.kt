package me.foxtails.palustris

import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.EntityId
import me.foxtails.palustris.domain.Protocol
import me.foxtails.palustris.ui.posts.PostActionFamily
import me.foxtails.palustris.ui.posts.PostInteractionExecutionAuthority
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** Typed family slots reject busy callers and release only the owning token. */
class PostInteractionExecutionAuthorityTest {
    private val account = AccountId(Connection("https://authority.example", Protocol.MISSKEY), "owner")
    private val target = EntityId("https://authority.example", "post")

    @Test
    fun busyFamilyRejectsTheSecondCaller() {
        val authority = PostInteractionExecutionAuthority()

        val first = authority.acquire(account, 7L, PostActionFamily.Reaction, target)
        val second = authority.acquire(account, 7L, PostActionFamily.Reaction, target)

        assertNotNull(first)
        assertNull(second)
    }

    @Test
    fun otherFamiliesProceedWhileOneFamilyIsOwned() {
        val authority = PostInteractionExecutionAuthority()

        val reaction = authority.acquire(account, 7L, PostActionFamily.Reaction, target)
        val reshare = authority.acquire(account, 7L, PostActionFamily.Reshare, target)

        assertNotNull(reaction)
        assertNotNull(reshare)
    }

    @Test
    fun releaseFreesTheSlotForTheNextCaller() {
        val authority = PostInteractionExecutionAuthority()

        val first = authority.acquire(account, 7L, PostActionFamily.Reaction, target)!!
        authority.release(first)
        val second = authority.acquire(account, 7L, PostActionFamily.Reaction, target)

        assertNotNull(second)
    }

    @Test
    fun foreignReleaseKeepsTheOwnedSlot() {
        val authority = PostInteractionExecutionAuthority()
        val other = EntityId("https://authority.example", "other")

        val owned = authority.acquire(account, 7L, PostActionFamily.Reaction, target)!!
        val foreign = authority.acquire(account, 7L, PostActionFamily.Reaction, other)!!
        authority.release(foreign)

        assertNull(authority.acquire(account, 7L, PostActionFamily.Reaction, target))
        authority.release(owned)
    }

    @Test
    fun repeatedReleaseIsANoOp() {
        val authority = PostInteractionExecutionAuthority()

        val token = authority.acquire(account, 7L, PostActionFamily.Bookmark, target)!!
        authority.release(token)
        authority.release(token)

        assertNotNull(authority.acquire(account, 7L, PostActionFamily.Bookmark, target))
    }
}
