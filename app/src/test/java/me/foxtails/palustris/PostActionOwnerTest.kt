package me.foxtails.palustris

import androidx.compose.ui.geometry.Rect
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.Audience
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.EntityId
import me.foxtails.palustris.domain.OwnedPost
import me.foxtails.palustris.domain.Page
import me.foxtails.palustris.domain.Post
import me.foxtails.palustris.domain.ProfileRelationship
import me.foxtails.palustris.domain.Protocol
import me.foxtails.palustris.domain.ServerCapabilities
import me.foxtails.palustris.domain.SocialSource
import me.foxtails.palustris.domain.Timeline
import me.foxtails.palustris.ui.posts.PostActionOwner
import me.foxtails.palustris.ui.posts.PostReportState
import me.foxtails.palustris.ui.posts.RelationshipMutation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** A retired popup dismisses and loses its authority. */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class PostActionOwnerTest {
    private val connection = Connection("https://popup.example", Protocol.MASTODON)
    private val accountId = AccountId(connection, "owner")
    private val authorId = AccountId(connection, "author")
    private val author = Account(authorId, "Author", "@author@popup.example")

    private class RelationshipSource : SocialSource {
        override val capabilities = ServerCapabilities()
        override suspend fun timeline(timeline: Timeline, cursor: String?): Page<Post> =
            Page(emptyList(), null)

        override suspend fun profileRelationship(id: AccountId): ProfileRelationship =
            ProfileRelationship(profileId = id)
    }

    private fun owned(revision: Long = 7L) = OwnedPost(
        fetchedBy = accountId,
        post = Post(
            id = EntityId(connection.origin, "post"),
            author = author,
            text = "Post",
            publishedAtEpochMillis = 0L,
            audience = Audience.Public,
        ),
        sessionRevision = revision,
    )

    @Test
    fun retireDismissesTheOpenPopup() = runTest {
        val owner = PostActionOwner(accountId, 7L, RelationshipSource(), this)
        owner.open(owned(), Rect.Zero)
        advanceUntilIdle()
        assertNotNull(owner.target)

        owner.retire()

        assertNull(owner.target)
    }

    @Test
    fun retiredOwnerRejectsOpen() = runTest {
        val owner = PostActionOwner(accountId, 7L, RelationshipSource(), this)
        owner.retire()

        owner.open(owned(), Rect.Zero)
        advanceUntilIdle()

        assertNull(owner.target)
    }

    @Test
    fun retiredOwnerRejectsMutationsAndReports() = runTest {
        val owner = PostActionOwner(accountId, 7L, RelationshipSource(), this)
        owner.open(owned(), Rect.Zero)
        advanceUntilIdle()
        assertNotNull(owner.relationship.relationship)
        owner.retire()

        owner.mutate(RelationshipMutation.Follow)
        owner.submitReport("spam")
        advanceUntilIdle()

        assertNull(owner.relationship.mutation)
        assertEquals(PostReportState(), owner.report)
    }
}
