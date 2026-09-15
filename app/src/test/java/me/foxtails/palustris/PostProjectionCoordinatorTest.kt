package me.foxtails.palustris

import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.Audience
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.CreatePostRequest
import me.foxtails.palustris.domain.EntityId
import me.foxtails.palustris.domain.OwnedPost
import me.foxtails.palustris.domain.Post
import me.foxtails.palustris.domain.Protocol
import me.foxtails.palustris.ui.shell.PostProjectionCoordinator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PostProjectionCoordinatorTest {
    private val connection = Connection("https://fixture.example", Protocol.MASTODON)
    private val owner = AccountId(connection, "owner")
    private val foreign = AccountId(connection, "foreign")
    private val author = Account(owner, "Owner", "@owner@fixture.example")

    private fun owned(id: String, fetchedBy: AccountId = owner, revision: Long = 7L) = OwnedPost(
        fetchedBy = fetchedBy,
        post = Post(
            id = EntityId(connection.origin, id),
            author = author,
            text = id,
            publishedAtEpochMillis = 0L,
            audience = Audience.Public,
        ),
        sessionRevision = revision,
    )

    private class RecordingSink(private val name: String) : PostProjectionCoordinator.Sink {
        val external = mutableListOf<String>()
        var published = 0
        var replies = 0
        var quotes = 0

        override fun applyExternalPost(updated: OwnedPost) {
            external += "${updated.post.id.value}@$name"
        }

        override fun applyPublishedPost(request: CreatePostRequest) {
            published++
        }

        override fun acceptPublishedReply(created: OwnedPost) {
            replies++
        }

        override fun acceptPublishedQuote(target: EntityId?) {
            quotes++
        }
    }

    @Test
    fun externalUpdateReachesEverySinkExceptTheOrigin() {
        val coordinator = PostProjectionCoordinator(owner, 7L)
        val feed = RecordingSink("feed")
        val saved = RecordingSink("saved")
        val thread = RecordingSink("thread")
        coordinator.register(feed)
        coordinator.register(saved)
        coordinator.register(thread)

        coordinator.forwardExternalPost(feed, owned("post"))

        assertEquals(emptyList<String>(), feed.external)
        assertEquals(listOf("post@saved"), saved.external)
        assertEquals(listOf("post@thread"), thread.external)
    }

    @Test
    fun nestedForwardingIsSuppressed() {
        val coordinator = PostProjectionCoordinator(owner, 7L)
        val feed = object : PostProjectionCoordinator.Sink {
            override fun applyExternalPost(updated: OwnedPost) {
                coordinator.forwardExternalPost(this, owned("echo"))
            }
        }
        val saved = RecordingSink("saved")
        coordinator.register(feed)
        coordinator.register(saved)

        coordinator.forwardExternalPost(feed, owned("post"))

        assertEquals(listOf("post@saved"), saved.external)
    }

    @Test
    fun updatesFromAnotherAccountAreRejected() {
        val coordinator = PostProjectionCoordinator(owner, 7L)
        val saved = RecordingSink("saved")
        coordinator.register(saved)

        coordinator.forwardExternalPost(saved, owned("post", fetchedBy = foreign))

        assertTrue(saved.external.isEmpty())
    }

    @Test
    fun updatesFromAnOldRevisionAreRejected() {
        val coordinator = PostProjectionCoordinator(owner, 7L)
        val saved = RecordingSink("saved")
        coordinator.register(saved)

        coordinator.forwardExternalPost(saved, owned("post", revision = 6L))

        assertTrue(saved.external.isEmpty())
    }

    @Test
    fun duplicatePublicationIsDeliveredOnce() {
        val coordinator = PostProjectionCoordinator(owner, 7L)
        val feed = RecordingSink("feed")
        val saved = RecordingSink("saved")
        coordinator.register(feed)
        coordinator.register(saved)
        val created = owned("created")
        val request = CreatePostRequest("hello")

        coordinator.forwardPublishedPost(feed, request, created)
        coordinator.forwardPublishedPost(feed, request, created)

        assertEquals(1, saved.published)
        assertEquals(1, saved.replies)
        assertEquals(1, saved.quotes)
    }

    @Test
    fun retiredCoordinatorDeliversNothingAndAcceptsNoSinks() {
        val coordinator = PostProjectionCoordinator(owner, 7L)
        val saved = RecordingSink("saved")
        coordinator.register(saved)
        coordinator.retire()

        coordinator.forwardExternalPost(saved, owned("post"))
        coordinator.forwardPublishedPost(saved, CreatePostRequest("hello"), owned("created"))

        val late = RecordingSink("late")
        coordinator.register(late)
        coordinator.forwardExternalPost(late, owned("post"))

        assertTrue(saved.external.isEmpty())
        assertEquals(0, saved.published)
        assertTrue(late.external.isEmpty())
    }

    @Test
    fun publicationReachesEverySinkExceptTheOrigin() {
        val coordinator = PostProjectionCoordinator(owner, 7L)
        val feed = RecordingSink("feed")
        val saved = RecordingSink("saved")
        val thread = RecordingSink("thread")
        coordinator.register(feed)
        coordinator.register(saved)
        coordinator.register(thread)
        val created = owned("created")
        val request = CreatePostRequest("reply", quoteOf = EntityId(connection.origin, "quoted"))

        coordinator.forwardPublishedPost(feed, request, created)

        assertEquals(0, feed.published)
        assertEquals(0, feed.replies)
        assertEquals(1, saved.published)
        assertEquals(1, thread.published)
        assertEquals(1, thread.replies)
        assertEquals(1, thread.quotes)
    }
}
