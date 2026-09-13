package me.foxtails.palustris.ui.posts

import androidx.compose.ui.geometry.Rect
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.Audience
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.EntityId
import me.foxtails.palustris.domain.OwnedPost
import me.foxtails.palustris.domain.Post
import me.foxtails.palustris.domain.Protocol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PostRepostConfirmationOwnerTest {
    private val account = Account(
        AccountId(Connection("https://example.org", Protocol.MISSKEY), "owner"),
        "Owner",
        "@owner@example.org",
    )

    private fun post(reposted: Boolean = false) = OwnedPost(
        fetchedBy = account.id,
        post = Post(EntityId(account.id.connection.origin, "post"), account, "Post", 0L, Audience.Public, reposted = reposted),
        sessionRevision = 4L,
    )

    @Test
    fun confirmationUsesTheLatestMatchingStateAndClearsPendingState() {
        val owner = PostRepostConfirmationOwner()
        var result: OwnedPost? = null
        val target = post()
        owner.request(target, Rect(0f, 0f, 48f, 48f))

        owner.confirm(target, onReshare = { result = it })

        assertEquals(target, result)
        assertNull(owner.pending)
    }

    @Test
    fun projectedStateChangeMakesConfirmationStale() {
        val owner = PostRepostConfirmationOwner()
        var called = false
        val target = post()
        owner.request(target, Rect.Zero)
        owner.reconcile(post(reposted = true))

        owner.confirm(post(reposted = true), onReshare = { called = true })

        assertEquals(false, called)
        assertNull(owner.pending)
    }

    @Test
    fun undoConfirmationUsesTheRepostedState() {
        val owner = PostRepostConfirmationOwner()
        var result: OwnedPost? = null
        val target = post(reposted = true)
        owner.request(target, Rect.Zero)

        owner.confirm(target, onReshare = { result = it })

        assertEquals(true, result?.post?.reposted)
    }
}
