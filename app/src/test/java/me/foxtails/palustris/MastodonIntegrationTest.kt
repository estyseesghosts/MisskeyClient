package me.foxtails.palustris

import java.io.ByteArrayInputStream
import kotlinx.coroutines.runBlocking
import me.foxtails.palustris.data.mastodon.MastodonMapper
import me.foxtails.palustris.data.mastodon.MastodonSource
import me.foxtails.palustris.data.misskey.MisskeyApi
import me.foxtails.palustris.domain.Audience
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.CreatePostRequest
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.EntityId
import me.foxtails.palustris.domain.NotificationQuery
import me.foxtails.palustris.domain.PostAction
import me.foxtails.palustris.domain.ProfileTimelineQuery
import me.foxtails.palustris.domain.ProfileTimelineTab
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
        .put("header", "https://example.org/banner.png")
        .put("followers_count", 42)
        .put("following_count", 17)
        .put("statuses_count", 99)
        .put("locked", true)
        .put("bot", false)
        .put("fields", JSONArray()
            .put(JSONObject().put("name", "Website").put("value", "<a href=\"https://example.org\">example.org</a>"))
            .put(JSONObject().put("name", "Matrix").put("value", "@alice:example.org")))

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
        assertEquals(listOf("Website", "Matrix"), MastodonMapper.account(localAccount, origin).profileFields.map { it.name })
        assertEquals("example.org", MastodonMapper.account(localAccount, origin).profileFields.first().value)
        assertEquals("https://example.org/banner.png", MastodonMapper.account(localAccount, origin).bannerUrl)
        assertEquals(42L, MastodonMapper.account(localAccount, origin).followersCount)
        assertEquals(17L, MastodonMapper.account(localAccount, origin).followingCount)
        assertEquals(99L, MastodonMapper.account(localAccount, origin).postsCount)
        assertTrue(MastodonMapper.account(localAccount, origin).locked)
    }

    @Test
    fun mapperPreservesHashtagAndPhraseLinksAsMarkdown() {
        val post = MastodonMapper.post(status("links").put(
            "content", "<p>[#tag](https://example.org/tags/tag) and <a href=\"https://example.org/guide\">the guide</a></p>",
        ), origin)

        assertEquals("[#tag](https://example.org/tags/tag) and [the guide](https://example.org/guide)", post.text)
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
    fun mapperMapsReplyParentAccountAndKeepsQuoteAsAuthoredPost() {
        val status = status("reply").put("in_reply_to_id", "parent").put("in_reply_to_account_id", "parent-user")
            .put("quoted_status", status("quoted").put("account", localAccount))

        val post = MastodonMapper.post(status, origin)

        assertEquals("parent-user", post.replyToAuthorId?.localId)
        assertEquals("quoted", post.quote?.id?.value)
        assertEquals(null, post.resharedBy)
    }

    @Test
    fun mapperTreatsExplicitNullReplyFieldsAsNoReply() {
        val post = MastodonMapper.post(status("top-level")
            .put("in_reply_to_id", JSONObject.NULL)
            .put("in_reply_to_account_id", JSONObject.NULL), origin)

        assertNull(post.replyTo)
        assertNull(post.replyToAuthorId)
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
    fun sourceSearchesHashtagWithBearerAndLinkCursor() = runBlocking {
        server.enqueue(MockResponse().setBody("[${status("tag-newest")}]").addHeader(
            "Link", "<$origin/api/v1/timelines/tag/cats?limit=40&max_id=tag-newest>; rel=\"next\"",
        ))
        server.enqueue(MockResponse().setBody("[${status("tag-older")}]"))
        val source = source()

        val first = source.searchHashtag("#cats")
        val second = source.searchHashtag("cats", first.nextCursor)

        assertEquals("tag-newest", first.items.single().id.value)
        assertEquals("tag-older", second.items.single().id.value)
        val firstRequest = server.takeRequest()
        val secondRequest = server.takeRequest()
        assertEquals("/api/v1/timelines/tag/cats?limit=40", firstRequest.path)
        assertEquals("/api/v1/timelines/tag/cats?limit=40&max_id=tag-newest", secondRequest.path)
        assertEquals("Bearer token", firstRequest.getHeader("Authorization"))
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
        val notifications = source.notifications(NotificationQuery())
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
        assertEquals(AccountId(Connection(origin, me.foxtails.palustris.domain.Protocol.MASTODON), "local-user"), notifications.items.single().accountId)
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
    fun sourceLoadsMastodonProfileAndLooksUpExactHandle() = runBlocking {
        server.enqueue(MockResponse().setBody(localAccount.toString()))
        server.enqueue(MockResponse().setBody(localAccount.toString()))
        val source = source()

        val profile = source.profile(AccountId(me.foxtails.palustris.domain.Connection(origin, me.foxtails.palustris.domain.Protocol.MASTODON), "local-user"))
        val result = source.searchAccounts("@alice@example.org")

        assertEquals("Bio & details", profile.biography)
        assertEquals("Matrix", result.single().profileFields[1].name)
        val profileRequest = server.takeRequest()
        assertEquals("/api/v1/accounts/local-user", profileRequest.path)
        assertEquals("Bearer token", profileRequest.getHeader("Authorization"))
        val lookupRequest = server.takeRequest()
        assertEquals("/api/v1/accounts/lookup?acct=alice%40example.org", lookupRequest.path)
        assertEquals("Bearer token", lookupRequest.getHeader("Authorization"))
    }

    @Test
    fun profileMapsOneHopMovedDestinationWithoutAnotherRequest() = runBlocking {
        val destination = account("new-user", "newalice", "New Alice")
            .put("acct", "newalice@remote.example")
            .put("moved", account("third-user", "third", "Third"))
        server.enqueue(MockResponse().setBody(JSONObject(localAccount.toString()).put("moved", destination).toString()))

        val profile = source().profile(AccountId(Connection(origin, me.foxtails.palustris.domain.Protocol.MASTODON), "local-user"))

        assertEquals("new-user", profile.movedTo?.id?.localId)
        assertEquals("New Alice", profile.movedTo?.displayName)
        assertEquals("@newalice@remote.example", profile.movedTo?.handle)
        assertEquals("https://example.org/avatar.png", profile.movedTo?.avatarUrl)
        assertNull(profile.movedTo?.movedTo)
        assertEquals(1, server.requestCount)
        assertEquals("/api/v1/accounts/local-user", server.takeRequest().path)
    }

    @Test
    fun mapperIgnoresMissingNullAndMalformedMovedDestinations() {
        assertNull(MastodonMapper.account(localAccount, origin).movedTo)
        assertNull(MastodonMapper.account(JSONObject(localAccount.toString()).put("moved", JSONObject.NULL), origin).movedTo)
        assertNull(MastodonMapper.account(JSONObject(localAccount.toString()).put("moved", JSONObject().put("username", "broken")), origin).movedTo)
        assertNull(MastodonMapper.account(JSONObject(localAccount.toString()).put("moved", JSONObject()
            .put("id", "").put("username", "")), origin).movedTo)
    }

    @Test
    fun profileTimelineUsesSafeAccountPathAndFiltersMixedStatusesLocally() = runBlocking {
        val target = AccountId(Connection(origin, me.foxtails.palustris.domain.Protocol.MASTODON), "local-user")
        val other = account("other-user", "other", "Other")
        val root = status("root").put("in_reply_to_id", JSONObject.NULL).put("in_reply_to_account_id", JSONObject.NULL)
        val media = status("media").put("media_attachments", JSONArray().put(JSONObject()
            .put("type", "image").put("url", "https://example.org/photo.jpg")))
        val reply = status("reply").put("in_reply_to_id", "parent").put("in_reply_to_account_id", "other-user")
        val selfReply = status("self-reply").put("in_reply_to_id", "parent").put("in_reply_to_account_id", "local-user")
        val boost = status("boost").put("account", localAccount).put("reblog", status("original").put("account", other))
        val quote = status("quote").put("quoted_status", status("quoted").put("account", other))
        server.enqueue(MockResponse().setBody(JSONArray().put(root).put(media).put(reply).put(selfReply).put(boost).put(quote).toString()))

        val page = source().profileTimeline(ProfileTimelineQuery(target, ProfileTimelineTab.Posts))

        assertEquals(setOf("root", "media", "quote"), page.items.map { it.id.value }.toSet())
        assertEquals(0, page.items.count { it.id.value == "boost" })
        assertEquals("/api/v1/accounts/local-user/statuses", server.takeRequest().requestUrl?.encodedPath)
    }

    @Test
    fun profileTimelineSendsCategoryHintsAndLimit() = runBlocking {
        val target = AccountId(Connection(origin, me.foxtails.palustris.domain.Protocol.MASTODON), "local-user")
        ProfileTimelineTab.entries.forEach { tab ->
            server.enqueue(MockResponse().setBody("[${status("${tab.name}-row")} ]"))
        }
        val source = source()

        ProfileTimelineTab.entries.forEach { tab ->
            source.profileTimeline(ProfileTimelineQuery(target, tab))
        }

        val expected = mapOf(
            ProfileTimelineTab.Posts to mapOf("exclude_replies" to "true", "exclude_reblogs" to "true"),
            ProfileTimelineTab.Media to mapOf("only_media" to "true", "exclude_reblogs" to "true"),
            ProfileTimelineTab.Reposts to mapOf("exclude_replies" to "true", "exclude_reblogs" to "false"),
            ProfileTimelineTab.Replies to mapOf("exclude_replies" to "false", "exclude_reblogs" to "true"),
        )
        ProfileTimelineTab.entries.forEach { tab ->
            val request = server.takeRequest()
            val url = request.requestUrl ?: error("Missing request URL")
            assertEquals("40", url.queryParameter("limit"))
            expected.getValue(tab).forEach { (name, value) -> assertEquals(value, url.queryParameter(name)) }
        }
    }

    @Test
    fun profileTimelineReusesValidatedLinkCursorAndRejectsForeignCursor() = runBlocking {
        val target = AccountId(Connection(origin, me.foxtails.palustris.domain.Protocol.MASTODON), "local-user")
        server.enqueue(MockResponse().setBody("[${status("newest")}] ").addHeader(
            "Link", "<$origin/api/v1/accounts/local-user/statuses?limit=40&max_id=newest>; rel=\"next\"",
        ))
        server.enqueue(MockResponse().setBody("[${status("older")}]"))
        val source = source()

        val first = source.profileTimeline(ProfileTimelineQuery(target, ProfileTimelineTab.Posts))
        val second = source.profileTimeline(ProfileTimelineQuery(target, ProfileTimelineTab.Posts), first.nextCursor)

        assertEquals("newest", first.items.single().id.value)
        assertEquals("older", second.items.single().id.value)
        val initialRequest = server.takeRequest()
        val continuationRequest = server.takeRequest()
        assertEquals("/api/v1/accounts/local-user/statuses?limit=40&exclude_replies=true&exclude_reblogs=true", initialRequest.path)
        assertEquals("/api/v1/accounts/local-user/statuses?limit=40&max_id=newest", continuationRequest.path)
        assertEquals("Bearer token", continuationRequest.getHeader("Authorization"))

        MockWebServer().use { foreign ->
            val foreignCursor = foreign.url("/api/v1/accounts/local-user/statuses?max_id=foreign").toString()
            assertThrows(SourceError.Unsupported::class.java) {
                runBlocking {
                    source.profileTimeline(ProfileTimelineQuery(target, ProfileTimelineTab.Posts), foreignCursor)
                }
            }
            assertEquals(2, server.requestCount)
        }
    }

    @Test
    fun profileRelationshipFollowUnfollowAndPinnedPostsUseTargetBoundEndpoints() = runBlocking {
        val target = AccountId(Connection(origin, me.foxtails.palustris.domain.Protocol.MASTODON), "local-user")
        server.enqueue(MockResponse().setBody("[{\"id\":\"local-user\",\"following\":false,\"followed_by\":true,\"requested\":false}]"))
        server.enqueue(MockResponse().setBody("{\"following\":true,\"followed_by\":true,\"requested\":false}"))
        server.enqueue(MockResponse().setBody("{\"following\":false,\"followed_by\":true,\"requested\":false}"))
        server.enqueue(MockResponse().setBody("[${status("pinned")},${status("foreign-pinned").put("account", account("other-user", "other", "Other"))}]"))
        server.enqueue(MockResponse().setResponseCode(404).setBody("{\"error\":\"unsupported\"}"))
        val source = source()

        assertTrue(source.profileRelationship(target).followedBy)
        assertTrue(source.followProfile(target).following)
        assertFalse(source.unfollowProfile(target).following)
        assertEquals(listOf("pinned"), source.pinnedPosts(target).map { it.id.value })
        assertTrue(source.pinnedPosts(target).isEmpty())

        assertEquals("/api/v1/accounts/relationships?id%5B%5D=local-user", server.takeRequest().path)
        assertEquals("/api/v1/accounts/local-user/follow", server.takeRequest().path)
        assertEquals("/api/v1/accounts/local-user/unfollow", server.takeRequest().path)
        assertEquals("/api/v1/accounts/local-user/statuses?pinned=true&limit=40", server.takeRequest().path)
        assertEquals("/api/v1/accounts/local-user/statuses?pinned=true&limit=40", server.takeRequest().path)
    }

    @Test
    fun profileDetailsEscapesOpaqueAccountIdPathSegment() = runBlocking {
        val id = "segment/with?query"
        val accountId = AccountId(Connection(origin, me.foxtails.palustris.domain.Protocol.MASTODON), id)
        server.enqueue(MockResponse().setBody(JSONObject(localAccount.toString()).put("id", id).toString()))

        source().profile(accountId)

        assertEquals("/api/v1/accounts/segment%2Fwith%3Fquery", server.takeRequest().path)
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

    private fun source() = MastodonSource(
        origin = origin,
        token = "token",
        api = MisskeyApi(),
        accountId = AccountId(Connection(origin, me.foxtails.palustris.domain.Protocol.MASTODON), "local-user"),
    )

    private fun status(id: String) = JSONObject()
        .put("id", id)
        .put("created_at", "2026-09-06T10:00:00Z")
        .put("account", localAccount)
        .put("content", "<p>Original</p>")
        .put("visibility", "public")

    private fun account(id: String, username: String, displayName: String) =
        JSONObject(localAccount.toString())
            .put("id", id)
            .put("username", username)
            .put("acct", username)
            .put("display_name", displayName)

    private fun notification(id: String) = JSONObject()
        .put("id", id)
        .put("type", "mention")
        .put("created_at", "2026-09-06T10:00:00Z")
        .put("account", localAccount)
        .put("status", status("notification-status"))
}
