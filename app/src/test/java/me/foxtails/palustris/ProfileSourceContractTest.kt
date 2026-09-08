package me.foxtails.palustris

import kotlinx.coroutines.runBlocking
import me.foxtails.palustris.data.mastodon.MastodonSource
import me.foxtails.palustris.data.misskey.MisskeyApi
import me.foxtails.palustris.data.misskey.MisskeySource
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.Page
import me.foxtails.palustris.domain.Post
import me.foxtails.palustris.domain.ProfileRelationship
import me.foxtails.palustris.domain.ProfileTimelineQuery
import me.foxtails.palustris.domain.ProfileTimelineTab
import me.foxtails.palustris.domain.Protocol
import me.foxtails.palustris.domain.SourceError
import me.foxtails.palustris.domain.SocialSource
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Shared profile behavior checks run against both protocol adapters. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ProfileSourceContractTest {
    @Test
    fun mastodonAdapterSatisfiesProfileContract() = runBlocking {
        MockWebServer().use { server ->
            val origin = server.url("/").toString().removeSuffix("/")
            val target = AccountId(Connection(origin, Protocol.MASTODON), "contract-user")
            val source = MastodonSource(origin, "contract-token", MisskeyApi(), target)

            server.enqueue(mastodonPage("posts-first").addHeader(
                "Link",
                "<$origin/api/v1/accounts/contract-user/statuses?limit=40&max_id=posts-first>; rel=\"next\"",
            ))
            server.enqueue(mastodonPage("posts-older"))
            server.enqueue(mastodonPage("media", media = true))
            server.enqueue(mastodonPage("repost", repost = true))
            server.enqueue(mastodonPage("reply", reply = true))

            val firstPosts = source.profileTimeline(ProfileTimelineQuery(target, ProfileTimelineTab.Posts))
            val olderPosts = source.profileTimeline(
                ProfileTimelineQuery(target, ProfileTimelineTab.Posts),
                firstPosts.nextCursor,
            )
            val pages = mapOf(
                ProfileTimelineTab.Posts to firstPosts,
                ProfileTimelineTab.Media to source.profileTimeline(ProfileTimelineQuery(target, ProfileTimelineTab.Media)),
                ProfileTimelineTab.Reposts to source.profileTimeline(ProfileTimelineQuery(target, ProfileTimelineTab.Reposts)),
                ProfileTimelineTab.Replies to source.profileTimeline(ProfileTimelineQuery(target, ProfileTimelineTab.Replies)),
            )
            ProfileSourceContract.assertCategoryResults(
                pages = pages,
                expectedIds = mapOf(
                    ProfileTimelineTab.Posts to listOf("posts-first"),
                    ProfileTimelineTab.Media to listOf("media"),
                    ProfileTimelineTab.Reposts to listOf("repost"),
                    ProfileTimelineTab.Replies to listOf("reply"),
                ),
            )
            ProfileSourceContract.assertOpaqueContinuation(firstPosts, olderPosts, "posts-first")
            assertEquals(listOf("posts-older"), olderPosts.items.map(Post::id).map { it.value })
            ProfileSourceContract.assertForeignTargetRejected(source, target)

            server.enqueue(MockResponse().setResponseCode(401).setBody("{\"error\":\"unauthorized\"}"))
            ProfileSourceContract.assertUnauthorized(source, target)

            server.enqueue(MockResponse().setBody("[{\"id\":\"contract-user\",\"following\":false}]"))
            server.enqueue(MockResponse().setBody("{\"following\":true}"))
            server.enqueue(MockResponse().setBody("{\"following\":false}"))
            ProfileSourceContract.assertRelationshipShape(source, target)
        }
    }

    @Test
    fun misskeyAdapterSatisfiesProfileContract() = runBlocking {
        MockWebServer().use { server ->
            val origin = server.url("/").toString().removeSuffix("/")
            val target = AccountId(Connection(origin, Protocol.MISSKEY), "contract-user")
            val source = MisskeySource(
                origin = origin,
                token = "contract-token",
                api = MisskeyApi(),
                accountId = target,
            )

            server.enqueue(misskeyPage("posts-first"))
            server.enqueue(misskeyPage("posts-older"))
            server.enqueue(misskeyPage("media", media = true))
            server.enqueue(misskeyPage("repost", repost = true))
            server.enqueue(misskeyPage("reply", reply = true))

            val firstPosts = source.profileTimeline(ProfileTimelineQuery(target, ProfileTimelineTab.Posts))
            val olderPosts = source.profileTimeline(
                ProfileTimelineQuery(target, ProfileTimelineTab.Posts),
                firstPosts.nextCursor,
            )
            val pages = mapOf(
                ProfileTimelineTab.Posts to firstPosts,
                ProfileTimelineTab.Media to source.profileTimeline(ProfileTimelineQuery(target, ProfileTimelineTab.Media)),
                ProfileTimelineTab.Reposts to source.profileTimeline(ProfileTimelineQuery(target, ProfileTimelineTab.Reposts)),
                ProfileTimelineTab.Replies to source.profileTimeline(ProfileTimelineQuery(target, ProfileTimelineTab.Replies)),
            )
            ProfileSourceContract.assertCategoryResults(
                pages = pages,
                expectedIds = mapOf(
                    ProfileTimelineTab.Posts to listOf("posts-first"),
                    ProfileTimelineTab.Media to listOf("media"),
                    ProfileTimelineTab.Reposts to listOf("repost"),
                    ProfileTimelineTab.Replies to listOf("reply"),
                ),
            )
            ProfileSourceContract.assertOpaqueContinuation(firstPosts, olderPosts, "posts-first")
            assertEquals(listOf("posts-older"), olderPosts.items.map(Post::id).map { it.value })
            ProfileSourceContract.assertForeignTargetRejected(source, target)

            server.enqueue(MockResponse().setResponseCode(401).setBody("{\"error\":\"AUTH\"}"))
            ProfileSourceContract.assertUnauthorized(source, target)

            server.enqueue(MockResponse().setBody("{\"isFollowing\":false}"))
            server.enqueue(MockResponse().setBody("{}"))
            server.enqueue(MockResponse().setBody("{\"isFollowing\":true}"))
            server.enqueue(MockResponse().setBody("{}"))
            server.enqueue(MockResponse().setBody("{\"isFollowing\":false}"))
            ProfileSourceContract.assertRelationshipShape(source, target)
        }
    }
}

internal object ProfileSourceContract {
    fun assertCategoryResults(
        pages: Map<ProfileTimelineTab, Page<Post>>,
        expectedIds: Map<ProfileTimelineTab, List<String>>,
    ) {
        assertEquals(ProfileTimelineTab.entries.toSet(), pages.keys)
        ProfileTimelineTab.entries.forEach { tab ->
            assertEquals(expectedIds.getValue(tab), pages.getValue(tab).items.map { it.id.value })
        }
        val allIds = pages.values.flatMap { page -> page.items.map { it.id.value } }
        assertEquals("fixture rows should not duplicate IDs across categories", allIds.size, allIds.distinct().size)
    }

    fun assertOpaqueContinuation(first: Page<Post>, second: Page<Post>, expectedCursor: String) {
        assertNotNull(first.nextCursor)
        assertEquals(expectedCursor, first.nextCursor?.let { cursor ->
            when {
                cursor.contains("max_id=") -> cursor.substringAfter("max_id=")
                else -> cursor
            }
        })
        assertTrue(second.nextCursor == null || second.nextCursor != expectedCursor)
    }

    suspend fun assertForeignTargetRejected(source: SocialSource, target: AccountId) {
        val foreign = AccountId(
            Connection("https://other.example", target.connection.protocol),
            target.localId,
        )
        assertThrows(SourceError.Unsupported::class.java) {
            runBlocking {
                source.profileTimeline(ProfileTimelineQuery(foreign, ProfileTimelineTab.Posts))
            }
        }
    }

    suspend fun assertUnauthorized(source: SocialSource, target: AccountId) {
        assertThrows(SourceError.Unauthorized::class.java) {
            runBlocking {
                source.profileTimeline(ProfileTimelineQuery(target, ProfileTimelineTab.Posts))
            }
        }
    }

    suspend fun assertRelationshipShape(source: SocialSource, target: AccountId) {
        val initial = source.profileRelationship(target)
        assertEquals(target, initial.profileId)
        assertFalse(initial.following)
        val followed = source.followProfile(target)
        assertEquals(target, followed.profileId)
        assertTrue(followed.following)
        val unfollowed = source.unfollowProfile(target)
        assertEquals(target, unfollowed.profileId)
        assertFalse(unfollowed.following)
    }
}

private fun mastodonPage(
    id: String,
    media: Boolean = false,
    repost: Boolean = false,
    reply: Boolean = false,
): MockResponse {
    val target = mastodonAccount("contract-user", "contract")
    val status = mastodonStatus(id, target)
    when {
        media -> status.put("media_attachments", JSONArray().put(JSONObject()
            .put("type", "image")
            .put("url", "https://example.org/$id.jpg")))
        repost -> status.put("account", target).put(
            "reblog",
            mastodonStatus("original-$id", mastodonAccount("original-user", "original")),
        )
        reply -> status.put("in_reply_to_id", "parent").put("in_reply_to_account_id", "other-user")
    }
    return MockResponse().setBody(JSONArray().put(status).toString())
}

private fun mastodonAccount(id: String, username: String) = JSONObject()
    .put("id", id)
    .put("username", username)
    .put("acct", username)
    .put("display_name", username)

private fun mastodonStatus(id: String, account: JSONObject) = JSONObject()
    .put("id", id)
    .put("created_at", "2026-09-06T10:00:00Z")
    .put("account", account)
    .put("content", "<p>Contract</p>")
    .put("visibility", "public")

private fun misskeyPage(
    id: String,
    media: Boolean = false,
    repost: Boolean = false,
    reply: Boolean = false,
): MockResponse {
    val target = misskeyAccount("contract-user", "contract")
    val note = misskeyNote(id, target)
    when {
        media -> note.put("files", JSONArray().put(JSONObject()
            .put("type", "image/jpeg")
            .put("url", "https://example.org/$id.jpg")))
        repost -> {
            note.remove("text")
            note.put("renote", misskeyNote("original-$id", misskeyAccount("original-user", "original")))
        }
        reply -> note.put("replyId", "parent").put("replyUserId", "other-user")
    }
    return MockResponse().setBody(JSONArray().put(note).toString())
}

private fun misskeyAccount(id: String, username: String) = JSONObject()
    .put("id", id)
    .put("username", username)
    .put("name", username)
    .put("host", JSONObject.NULL)

private fun misskeyNote(id: String, account: JSONObject) = JSONObject()
    .put("id", id)
    .put("createdAt", "2026-09-06T10:00:00Z")
    .put("user", account)
    .put("text", "Contract")
    .put("visibility", "public")
