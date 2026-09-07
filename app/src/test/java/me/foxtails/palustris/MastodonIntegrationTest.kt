package me.foxtails.palustris

import java.io.ByteArrayInputStream
import kotlinx.coroutines.runBlocking
import me.foxtails.palustris.data.mastodon.MastodonMapper
import me.foxtails.palustris.data.mastodon.MastodonSource
import me.foxtails.palustris.data.misskey.MisskeyApi
import me.foxtails.palustris.domain.Audience
import me.foxtails.palustris.domain.CreatePostRequest
import me.foxtails.palustris.domain.EntityId
import me.foxtails.palustris.domain.PostAction
import me.foxtails.palustris.domain.SourceError
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MastodonIntegrationTest {
    private val origin get() = server.url("/").toString().removeSuffix("/")
    private lateinit var server: MockWebServer

    private val localAccount = JSONObject()
        .put("id", "local-user")
        .put("username", "alice")
        .put("acct", "alice")
        .put("display_name", "Alice")
        .put("avatar", "https://example.org/avatar.png")
        .put("note", "<p>Bio &amp; details</p>")

    @Before
    fun startServer() {
        server = MockWebServer().also { it.start() }
    }

    @After
    fun stopServer() {
        server.shutdown()
    }

    @Test
    fun mapperConvertsHtmlVisibilityMediaPollAndActions() {
        val post = MastodonMapper.post(status("status-1").apply {
            put("content", "<p>Hello <span class=\"h-card\"><a href=\"https://example.org/@bob\">@bob</a></span><br>Tea &amp; cake</p>")
                .put("visibility", "direct")
                .put("spoiler_text", "Warning")
                .put("favourites_count", 4)
                .put("favourited", true)
                .put("media_attachments", JSONArray().put(JSONObject()
                    .put("type", "image")
                .put("url", "https://example.org/photo.jpg")
                .put("preview_url", "https://example.org/photo-small.jpg")
                .put("description", "A photo")
                .put("sensitive", true)))
                .put("poll", JSONObject().put("options", JSONArray().put(JSONObject().put("title", "Yes").put("votes_count", 3))))
        }, origin)

        assertEquals("Hello @bob\nTea & cake", post.text)
        assertEquals(Audience.Direct, post.audience)
        assertEquals("Warning", post.contentWarning)
        assertEquals("A photo", post.attachments.single().description)
        assertTrue(post.attachments.single().sensitive)
        assertEquals("Yes", post.pollOptions.single().text)
        assertTrue(PostAction.Favorite in post.availableActions)
        assertFalse(PostAction.React in post.availableActions)
        assertEquals("Bio & details", MastodonMapper.account(localAccount, origin).biography)
    }

    @Test
    fun mapperPreservesReblogAsResharedPost() {
        val resharedBy = JSONObject(localAccount.toString()).put("id", "resharer").put("username", "bob").put("acct", "bob")
            .put("display_name", "Bob")
        val outer = status("boost-1").put("account", resharedBy).put("reblog", status("original-1"))

        val post = MastodonMapper.post(outer, origin)

        assertEquals("boost-1", post.id.value)
        assertEquals("local-user", post.author.id.localId)
        assertEquals("Bob", post.resharedBy?.displayName)
        assertEquals("Original", post.text)
    }

    @Test
    fun sourceUsesLinkCursorAndBearerTimelineRequest() = runBlocking {
        server.enqueue(MockResponse().setBody("[${status("newest")}]" ).addHeader(
            "Link", "<$origin/api/v1/timelines/home?max_id=newest>; rel=\"next\"",
        ))
        server.enqueue(MockResponse().setBody("[${status("older")}]"))
        val source = source()

        val first = source.timeline(me.foxtails.palustris.domain.Timeline.Home)
        val second = source.timeline(me.foxtails.palustris.domain.Timeline.Home, first.nextCursor)

        assertEquals("newest", first.items.single().id.value)
        assertEquals("older", second.items.single().id.value)
        val firstRequest = server.takeRequest()
        val secondRequest = server.takeRequest()
        assertEquals("/api/v1/timelines/home", firstRequest.path)
        assertEquals("/api/v1/timelines/home?max_id=newest", secondRequest.path)
        assertEquals("Bearer token", secondRequest.getHeader("Authorization"))
    }

    @Test
    fun sourceMapsCreateFavoriteRenoteNotificationsMediaAndSearch() = runBlocking {
        server.enqueue(MockResponse().setBody(status("created").toString()))
        server.enqueue(MockResponse().setBody("{}"))
        server.enqueue(MockResponse().setBody("{}"))
        server.enqueue(MockResponse().setBody("[${notification("notification-1")}]"))
        server.enqueue(MockResponse().setBody(JSONObject()
            .put("id", "media-1")
            .put("type", "image")
            .put("url", "https://example.org/uploaded.jpg")
            .put("preview_url", "https://example.org/uploaded-small.jpg")
            .toString()))
        server.enqueue(MockResponse().setBody(JSONObject().put("statuses", JSONArray().put(status("found"))).toString()))
        val source = source()

        val created = source.create(CreatePostRequest(
            text = "Created",
            audience = Audience.Followers,
            contentWarning = "CW",
            replyTo = EntityId(origin, "parent"),
        ))
        source.favorite(EntityId(origin, "favorite-1"))
        source.renote(EntityId(origin, "renote-1"))
        val notifications = source.notifications()
        val attachment = source.uploadMedia(ByteArrayInputStream("bytes".toByteArray()), "image/jpeg")
        val search = source.search("hello world")

        assertEquals("created", created.id.value)
        val createRequest = server.takeRequest()
        val createBody = createRequest.body.readUtf8()
        assertEquals("Bearer token", createRequest.getHeader("Authorization"))
        assertTrue(createBody.contains("visibility=private"))
        assertTrue(createBody.contains("spoiler_text=CW"))
        assertTrue(createBody.contains("in_reply_to_id=parent"))
        val favoriteRequest = server.takeRequest()
        val renoteRequest = server.takeRequest()
        assertEquals("/api/v1/statuses/favorite-1/favourite", favoriteRequest.path)
        assertEquals("Bearer token", favoriteRequest.getHeader("Authorization"))
        assertEquals("/api/v1/statuses/renote-1/reblog", renoteRequest.path)
        assertEquals("Bearer token", renoteRequest.getHeader("Authorization"))
        assertEquals("notification-1", notifications.items.single().id.value)
        assertEquals("https://example.org/uploaded.jpg", attachment.url)
        assertEquals("found", search.single().id.value)
        assertEquals("/api/v1/notifications", server.takeRequest().path)
        val uploadRequest = server.takeRequest()
        assertEquals("/api/v1/media", uploadRequest.path)
        assertEquals("Bearer token", uploadRequest.getHeader("Authorization"))
        assertTrue(uploadRequest.body.readUtf8().contains("bytes"))
        assertEquals("/api/v1/search?q=hello+world", server.takeRequest().path)
    }

    @Test
    fun sourceMapsMastodonErrors() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(404).setBody("{\"error\":\"missing\"}"))

        try {
            source().post(EntityId(origin, "missing"))
            throw AssertionError("Missing status should fail")
        } catch (error: SourceError.Unsupported) {
            assertEquals("requested feature", error.feature)
        }
    }

    private fun source() = MastodonSource(origin, "token", MisskeyApi())

    private fun status(id: String) = JSONObject()
        .put("id", id)
        .put("created_at", "2026-09-06T10:00:00Z")
        .put("account", localAccount)
        .put("content", "<p>Original</p>")
        .put("visibility", "public")

    private fun notification(id: String) = JSONObject()
        .put("id", id)
        .put("type", "mention")
        .put("created_at", "2026-09-06T10:00:00Z")
        .put("account", localAccount)
        .put("status", status("notification-status"))
}
