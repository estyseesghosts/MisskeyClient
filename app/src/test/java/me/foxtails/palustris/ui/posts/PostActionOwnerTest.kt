@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package me.foxtails.palustris.ui.posts

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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PostActionOwnerTest {
    private val sessionConnection = Connection("https://example.org", Protocol.MISSKEY)
    private val sessionOwnerId = AccountId(sessionConnection, "owner")
    private val sessionAuthorId = AccountId(sessionConnection, "author")
    private val sessionAuthor = Account(sessionAuthorId, "Author", "@author@example.org")

    private val popupConnection = Connection("https://popup.example", Protocol.MASTODON)
    private val popupAccountId = AccountId(popupConnection, "owner")
    private val popupAuthorId = AccountId(popupConnection, "author")
    private val popupAuthor = Account(popupAuthorId, "Author", "@author@popup.example")

    @Test
    fun openRejectsAStaleAccountSession() = runTest {
        val owner = PostActionOwner(sessionOwnerId, 2L, FakeSource(), this)

        owner.open(post(OwnedPost(sessionOwnerId, post())), Rect(0f, 0f, 1f, 1f))
        owner.open(post(OwnedPost(sessionOwnerId, post(), sessionRevision = 3L)), Rect.Zero)

        assertNull(owner.target)
        assertNull(owner.relationship.target)
    }

    @Test
    fun mutationUsesTheEffectivePostAuthorAndPublishesTheConfirmedState() = runTest {
        var changed = 0
        val source = FakeSource()
        val owner = PostActionOwner(sessionOwnerId, 2L, source, this, onRelationshipChanged = { changed++ })
        val owned = OwnedPost(sessionOwnerId, post(), sessionRevision = 2L)

        owner.open(owned, Rect.Zero)
        advanceUntilIdle()
        owner.mutate(RelationshipMutation.Mute)
        owner.mutate(RelationshipMutation.Unmute)
        advanceUntilIdle()
        owner.mutate(RelationshipMutation.Unmute)
        advanceUntilIdle()

        assertEquals(listOf(sessionAuthorId, sessionAuthorId), source.muteTargets)
        assertEquals(listOf(true, false), source.muteValues)
        assertEquals(2, changed)
        assertTrue(!owner.relationship.relationship!!.muting)
        assertNull(owner.relationship.mutation)
    }

    @Test
    fun retireDismissesTheOpenPopup() = runTest {
        val owner = PostActionOwner(popupAccountId, 7L, RelationshipSource(), this)
        owner.open(popupOwned(), Rect.Zero)
        advanceUntilIdle()
        assertNotNull(owner.target)

        owner.retire()

        assertNull(owner.target)
    }

    @Test
    fun retiredOwnerRejectsOpen() = runTest {
        val owner = PostActionOwner(popupAccountId, 7L, RelationshipSource(), this)
        owner.retire()

        owner.open(popupOwned(), Rect.Zero)
        advanceUntilIdle()

        assertNull(owner.target)
    }

    @Test
    fun retiredOwnerRejectsMutationsAndReports() = runTest {
        val owner = PostActionOwner(popupAccountId, 7L, RelationshipSource(), this)
        owner.open(popupOwned(), Rect.Zero)
        advanceUntilIdle()
        assertNotNull(owner.relationship.relationship)
        owner.retire()

        owner.mutate(RelationshipMutation.Follow)
        owner.submitReport("spam")
        advanceUntilIdle()

        assertNull(owner.relationship.mutation)
        assertEquals(PostReportState(), owner.report)
    }

    private fun post(owned: OwnedPost? = null): OwnedPost = owned ?: OwnedPost(sessionOwnerId, post())

    private fun post(): Post = Post(
        id = EntityId(sessionConnection.origin, "post"),
        author = sessionAuthor,
        text = "post",
        publishedAtEpochMillis = 0L,
        audience = Audience.Public,
    )

    private fun popupOwned(revision: Long = 7L) = OwnedPost(
        fetchedBy = popupAccountId,
        post = Post(
            id = EntityId(popupConnection.origin, "post"),
            author = popupAuthor,
            text = "Post",
            publishedAtEpochMillis = 0L,
            audience = Audience.Public,
        ),
        sessionRevision = revision,
    )

    private class FakeSource : SocialSource {
        override val capabilities: ServerCapabilities = ServerCapabilities()
        val muteTargets = mutableListOf<AccountId>()
        val muteValues = mutableListOf<Boolean>()

        override suspend fun timeline(timeline: Timeline, cursor: String?): Page<Post> =
            error("not used")

        override suspend fun profileRelationship(id: AccountId) = ProfileRelationship(id)

        override suspend fun setMuted(id: AccountId, muted: Boolean): ProfileRelationship {
            muteTargets += id
            muteValues += muted
            return ProfileRelationship(id, muting = muted)
        }
    }

    private class RelationshipSource : SocialSource {
        override val capabilities = ServerCapabilities()
        override suspend fun timeline(timeline: Timeline, cursor: String?): Page<Post> =
            Page(emptyList(), null)

        override suspend fun profileRelationship(id: AccountId): ProfileRelationship =
            ProfileRelationship(profileId = id)
    }
}
