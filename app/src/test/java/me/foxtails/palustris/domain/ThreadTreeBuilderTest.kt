package me.foxtails.palustris.domain

import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.Audience
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.EntityId
import me.foxtails.palustris.domain.OwnedPost
import me.foxtails.palustris.domain.Post
import me.foxtails.palustris.domain.Protocol
import me.foxtails.palustris.domain.ThreadTreeBuilder
import me.foxtails.palustris.domain.effectiveTargetId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThreadTreeBuilderTest {
    private val origin = "https://example.org"
    private val connection = Connection(origin, Protocol.MISSKEY)
    private val viewer = AccountId(connection, "viewer")

    @Test
    fun preservesFirstSeenSiblingOrderAndAttachesByExplicitParent() {
        val focal = owned("root")
        val child = owned("child", replyTo = "reply")
        val reply = owned("reply", replyTo = "root")
        val other = owned("other", replyTo = "root")

        val tree = ThreadTreeBuilder.build(focal, descendants = listOf(child, reply, other))

        assertEquals(listOf("reply", "child", "other"), tree.replies.map { it.post.id.value })
        assertEquals(EntityId(origin, "reply"), tree.replies[1].parentId)
        assertEquals(2, tree.replies[1].semanticDepth)
        assertEquals(2, tree.replies[1].visualDepth)
        assertTrue(tree.replies[0].connector.verifiedParent)
    }

    @Test
    fun removesDuplicatesAndKeepsMissingParentBranchesSeparate() {
        val focal = owned("root")
        val missing = owned("missing-child", replyTo = "deleted")
        val nested = owned("nested", replyTo = "missing-child")

        val tree = ThreadTreeBuilder.build(
            focal,
            descendants = listOf(missing, missing.copy(post = missing.post.copy(text = "later")), nested),
        )

        assertEquals(listOf("missing-child", "nested"), tree.disconnected.map { it.post.id.value })
        assertTrue(tree.disconnected.first().parentMissing)
        assertEquals("missing-child", tree.disconnected.first().post.text)
        assertEquals(1, tree.disconnected[1].semanticDepth)
        assertFalse(tree.replies.any { it.post.id.value == "missing-child" })
    }

    @Test
    fun breaksSelfAndMultiPostCyclesWithoutRecursion() {
        val focal = owned("root")
        val self = owned("self", replyTo = "self")
        val a = owned("a", replyTo = "b")
        val b = owned("b", replyTo = "a")

        val tree = ThreadTreeBuilder.build(focal, descendants = listOf(self, a, b))

        assertEquals(listOf("self", "a", "b"), tree.disconnected.map { it.post.id.value })
        assertTrue(tree.disconnected.all { it.semanticDepth >= 0 })
        assertTrue(tree.disconnected.all { it.stableKey.contains(origin) })
    }

    @Test
    fun stableKeysIncludeFetchingAccountAndSessionRevision() {
        val focal = owned("root")
        val post = owned("post", replyTo = "root", revision = 4)
        val otherAccount = post.copy(fetchedBy = AccountId(connection, "other"))
        val otherRevision = post.copy(sessionRevision = 5)

        val first = ThreadTreeBuilder.build(focal, descendants = listOf(post)).allRows
        val second = ThreadTreeBuilder.build(focal, descendants = listOf(otherAccount)).allRows
        val third = ThreadTreeBuilder.build(focal, descendants = listOf(otherRevision)).allRows

        assertFalse(first.single().stableKey == second.single().stableKey)
        assertFalse(first.single().stableKey == third.single().stableKey)
    }

    @Test
    fun effectiveTargetUsesUnderlyingResharePost() {
        val wrapper = owned("wrapper").copy(post = owned("original").post.copy(
            id = EntityId(origin, "wrapper"),
            actionTargetId = EntityId(origin, "original"),
        ))

        assertEquals(EntityId(origin, "original"), wrapper.effectiveTargetId())
    }

    private fun owned(id: String, replyTo: String? = null, revision: Long = 1L): OwnedPost {
        val entityId = EntityId(origin, id)
        return OwnedPost(
            fetchedBy = viewer,
            sessionRevision = revision,
            post = Post(
                id = entityId,
                author = Account(viewer, "Viewer", "@viewer@example.org"),
                text = id,
                publishedAtEpochMillis = 0L,
                audience = Audience.Public,
                replyTo = replyTo?.let { EntityId(origin, it) },
            ),
        )
    }
}
