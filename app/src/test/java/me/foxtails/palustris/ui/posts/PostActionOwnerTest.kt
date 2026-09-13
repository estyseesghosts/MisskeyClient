@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package me.foxtails.palustris.ui.posts

import androidx.compose.ui.geometry.Rect
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.advanceUntilIdle
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.Audience
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.EntityId
import me.foxtails.palustris.domain.OwnedPost
import me.foxtails.palustris.domain.ProfileRelationship
import me.foxtails.palustris.domain.ServerCapabilities
import me.foxtails.palustris.domain.SocialSource
import me.foxtails.palustris.domain.Protocol
import me.foxtails.palustris.domain.Page
import me.foxtails.palustris.domain.Timeline
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PostActionOwnerTest {
    private val connection = Connection("https://example.org", Protocol.MISSKEY)
    private val ownerId = AccountId(connection, "owner")
    private val authorId = AccountId(connection, "author")
    private val author = Account(authorId, "Author", "@author@example.org")

    @Test
    fun openRejectsAStaleAccountSession() = runTest {
        val owner = PostActionOwner(ownerId, 2L, FakeSource(), this)

        owner.open(post(OwnedPost(ownerId, post())), Rect(0f, 0f, 1f, 1f))
        owner.open(post(OwnedPost(ownerId, post(), sessionRevision = 3L)), Rect.Zero)

        assertNull(owner.target)
        assertNull(owner.relationship.target)
    }

    @Test
    fun mutationUsesTheEffectivePostAuthorAndPublishesTheConfirmedState() = runTest {
        var changed = 0
        val source = FakeSource()
        val owner = PostActionOwner(ownerId, 2L, source, this) { changed++ }
        val owned = OwnedPost(ownerId, post(), sessionRevision = 2L)

        owner.open(owned, Rect.Zero)
        advanceUntilIdle()
        owner.mutate(RelationshipMutation.Mute)
        owner.mutate(RelationshipMutation.Unmute)
        advanceUntilIdle()
        owner.mutate(RelationshipMutation.Unmute)
        advanceUntilIdle()

        assertEquals(listOf(authorId, authorId), source.muteTargets)
        assertEquals(listOf(true, false), source.muteValues)
        assertEquals(2, changed)
        assertTrue(!owner.relationship.relationship!!.muting)
        assertNull(owner.relationship.mutation)
    }

    private fun post(owned: OwnedPost? = null): OwnedPost = owned ?: OwnedPost(ownerId, post())

    private fun post(): me.foxtails.palustris.domain.Post = me.foxtails.palustris.domain.Post(
        id = EntityId(connection.origin, "post"),
        author = author,
        text = "post",
        publishedAtEpochMillis = 0L,
        audience = Audience.Public,
    )

    private class FakeSource : SocialSource {
        override val capabilities: ServerCapabilities = ServerCapabilities()
        val muteTargets = mutableListOf<AccountId>()
        val muteValues = mutableListOf<Boolean>()

        override suspend fun timeline(timeline: Timeline, cursor: String?): Page<me.foxtails.palustris.domain.Post> =
            error("not used")

        override suspend fun profileRelationship(id: AccountId) = ProfileRelationship(id)

        override suspend fun setMuted(id: AccountId, muted: Boolean): ProfileRelationship {
            muteTargets += id
            muteValues += muted
            return ProfileRelationship(id, muting = muted)
        }
    }
}
