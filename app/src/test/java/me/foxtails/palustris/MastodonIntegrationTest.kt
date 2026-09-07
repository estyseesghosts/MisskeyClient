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
import me.foxtails.palustris.domain.UpdateProfileRequest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
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
                .put("sensitive", true)
                .put("favourites_count", 4)
                .put("favourited", true)
                .put("media_attachments", JSONArray().put(JSONObject()
                    .put("type", "image")
                .put("url", "https://example.org/photo.jpg")
                .put("preview_url", "https://example.org/photo-small.jpg")
                .put("description", "A photo")
                .put("sensitive", false)))
                .put("poll", JSONObject().put("options", JSONArray().put(JSONObject().put("title", "Yes").put("votes_count", 3))))
        }, origin)

        assertEquals("Hello @bob\nTea & cake", post.text)
        assertEquals(Audience.Direct, post.audience)
        assertEquals("Warning", post.contentWarning)
        assertEquals("A photo", post.attachments.single().description)
        assertTrue(post.attachments.single().sensitive)
        assertEquals("image/*", post.attachments.single().mimeType)
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
        assertEquals("/api/v2/search?q=hello+world", server.takeRequest().path)
    }

    @Test
    fun sourceUpdatesMastodonProfileWithPatch() = runBlocking {
        server.enqueue(MockResponse().setBody(localAccount.toString()))
        val account = source().updateProfile(UpdateProfileRequest("New name", "New bio"))
        assertEquals("Alice", account.displayName)
        val request = server.takeRequest()
        assertEquals("PATCH", request.method)
        assertEquals("/api/v1/accounts/update_credentials", request.path)
        assertEquals("Bearer token", request.getHeader("Authorization"))
        val body = request.body.readUtf8()
        assertTrue(body.contains("display_name=New%20name"))
        assertTrue(body.contains("note=New%20bio"))
    }

    @Test
    fun sourceRejectsUnsupportedCreateFieldsBeforeNetworkRequests() = runBlocking {
        val requestOrigin = server.url("/").toString().removeSuffix("/")
        val unsupported = listOf(
            CreatePostRequest("text", quoteOf = EntityId(requestOrigin, "quoted")),
            CreatePostRequest("text", attachments = listOf(me.foxtails.palustris.domain.Attachment("https://example.org/photo.jpg", "image/jpeg", null))),
            CreatePostRequest("text", poll = me.foxtails.palustris.domain.PollRequest(listOf("yes", "no"))),
        )

        unsupported.forEach { request ->
            assertThrows(SourceError.Unsupported::class.java) {
                runBlocking { source().create(request) }
            }
        }
        assertEquals(0, server.requestCount)
    }

    @Test
    fun mapperHandlesMastodonQuoteEnvelopeStates() {
        val quoted = status("quoted").put("content", "<p>Quoted</p>")
        val accepted = status("accepted").put("quote", JSONObject()
            .put("state", "accepted")
            .put("quoted_status", quoted))
        assertEquals("quoted", MastodonMapper.post(accepted, origin).quote?.id?.value)

        listOf("pending", "rejected", "deleted", "blocked", "unknown").forEach { state ->
            val unavailable = status("$state-quote").put("quote", JSONObject()
                .put("state", state)
                .put("quoted_status", quoted))
            assertNull(MastodonMapper.post(unavailable, origin).quote)
        }
    }

    @Test
    fun paginationRejectsForeignHostsChangedPortsAndSchemeChangesBeforeSendingBearer() = runBlocking {
        MockWebServer().use { other ->
            server.enqueue(MockResponse().setBody("[${status("newest")}]"))
            val source = source()
            val first = source.timeline(me.foxtails.palustris.domain.Timeline.Home)
            val invalidCursors = listOf(
                other.url("/api/v1/timelines/home?max_id=1").toString(),
                "${origin.replace(Regex(":\\d+$"), ":${other.port}")}/api/v1/timelines/home?max_id=1",
                "https://${server.hostName}:${server.port}/api/v1/timelines/home?max_id=1",
            )
            invalidCursors.forEach { cursor ->
                try {
                    source.timeline(me.foxtails.palustris.domain.Timeline.Home, cursor)
                    throw AssertionError("Invalid pagination URL should be rejected")
                } catch (_: SourceError.Unsupported) {
                    // Expected: bearer credentials must not be sent to this URL.
                }
            }
            assertEquals("newest", first.items.single().id.value)
            assertEquals(0, other.requestCount)
            assertEquals(1, server.requestCount)
        }
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
