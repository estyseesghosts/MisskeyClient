package me.foxtails.palustris

import kotlinx.coroutines.runBlocking
import me.foxtails.palustris.data.auth.*
import me.foxtails.palustris.data.mastodon.MastodonErrorMapper
import me.foxtails.palustris.data.misskey.*
import me.foxtails.palustris.domain.*
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MisskeyIntegrationTest : MisskeySourceContractTest() {
    private val user = JSONObject()
        .put("id", "user-a")
        .put("username", "alice")
        .put("name", "Alice")
        .put("host", JSONObject.NULL)
        .put("description", "Misskey bio")
        .put("fields", org.json.JSONArray()
            .put(JSONObject().put("name", "Website").put("value", "https://example.org"))
            .put(JSONObject().put("name", "Matrix").put("value", "@alice:example.org")))
        .toString()
    private fun note(id: String) = """{"id":"$id","createdAt":"2026-09-06T10:00:00Z","user":$user,"text":"Hello","visibility":"home"}"""

    @Test fun domainValidationRejectsCredentialsAndNonHttpsUrls() {
        assertEquals("https://misskey.io", ServerAddress.normalize(" Misskey.IO/ "))
        listOf("http://example.org", "https://user:pass@example.org", "example.org/path", "example.org?token=secret", "example.org#x", "file:///etc/passwd").forEach {
            assertThrows(IllegalArgumentException::class.java) { ServerAddress.normalize(it) }
        }
    }

    @Test fun connectionValidationRequiresNormalizedHttpsOrigin() {
        assertTrue(Connection("https://example.org", Protocol.MISSKEY).isValid())
        assertTrue(Connection("https://example.org/", Protocol.MASTODON).isValid())
        assertFalse(Connection("example.org", Protocol.MISSKEY).isValid())
        assertFalse(Connection("http://example.org", Protocol.MISSKEY).isValid())
        assertFalse(Connection("https://user:pass@example.org", Protocol.MISSKEY).isValid())
    }

    @Test fun misskeyMapperMapsRichProfileMetadataAndReplyParent() {
        val profile = JSONObject(user)
            .put("bannerUrl", "https://example.org/banner.png")
            .put("followersCount", 42)
            .put("followingCount", 17)
            .put("notesCount", 99)
            .put("isLocked", true)
            .put("isBot", false)
        val reply = JSONObject(note("reply"))
            .put("user", profile)
            .put("replyId", "parent-note")
            .put("replyUserId", "parent-user")

        val account = MisskeyMapper.account(profile, "https://example.org")
        val post = MisskeyMapper.post(reply, "https://example.org")

        assertEquals("https://example.org/banner.png", account.bannerUrl)
        assertEquals(42L, account.followersCount)
        assertEquals(17L, account.followingCount)
        assertEquals(99L, account.postsCount)
        assertTrue(account.locked)
        assertEquals("parent-user", post.replyToAuthorId?.localId)
    }

    @Test fun misskeyMapperDistinguishesExplicitEmptyQuoteRenoteFromPureRenote() {
        val quoted = JSONObject(note("quoted"))
        val quote = JSONObject(note("quote")).put("text", "").put("renote", quoted)
        val pure = JSONObject(note("pure"))
        pure.remove("text")
        pure.put("renote", quoted)

        val quotePost = MisskeyMapper.post(quote, "https://example.org")
        val purePost = MisskeyMapper.post(pure, "https://example.org")

        assertEquals("", quotePost.text)
        assertEquals(null, quotePost.resharedBy)
        assertEquals("quoted", quotePost.quote?.id?.value)
        assertEquals("user-a", purePost.resharedBy?.id?.localId)
    }

    @Test fun accountIdentityUsesOriginAndLocalIdNotProtocol() {
        val misskey = AccountId(Connection("https://example.org", Protocol.MISSKEY), "same-id")
        val mastodon = AccountId(Connection("https://example.org", Protocol.MASTODON), "same-id")
        val otherAccount = AccountId(Connection("https://example.org", Protocol.MISSKEY), "other-id")
        assertEquals(misskey, mastodon)
        assertEquals(misskey.hashCode(), mastodon.hashCode())
        assertNotEquals(misskey, otherAccount)
    }

    @Test fun callbackRequiresCorrectSessionOriginPathAndFreshness() {
        val pending = PendingLogin("https://example.org", "unique-session", 1000)
        assertTrue(AuthCallback.matches("palustris://auth/misskey?session=unique-session", pending, 2000))
        listOf("palustris://auth/misskey?session=other", "palustris://wrong/misskey?session=unique-session",
            "https://auth/misskey?session=unique-session", "palustris://auth/misskey?session=unique-session&session=unique-session").forEach {
            assertFalse(AuthCallback.matches(it, pending, 2000))
        }
        assertFalse(AuthCallback.matches("palustris://auth/misskey?session=unique-session", pending, 1_000_000))
    }

    @Test fun authRequestsNotificationAndRelationshipPermissions() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"ok":true,"token":"test-token","user":$user}"""))
            val auth = MisskeyAuth(MisskeyApi())
            val pending = PendingLogin(
                origin = server.url("/").toString().removeSuffix("/"),
                id = "test-session",
                createdAt = System.currentTimeMillis(),
                requestedAccess = setOf(AccessScope.NotificationsRead, AccessScope.NotificationsWrite, AccessScope.FollowRequests),
            )
            val url = okhttp3.HttpUrl.Companion.run { auth.browserUrl(pending).toHttpUrl() }
            assertEquals("read:account,write:account,write:notes,read:notifications,write:notifications,write:following,write:reactions,read:favorites,write:favorites", url.queryParameter("permission"))
            assertEquals("palustris://auth/misskey", url.queryParameter("callback"))
            val result = auth.complete(pending)
            assertEquals("Alice", result.account.displayName)
            assertEquals("test-token", result.token)
            assertTrue(result.canPublish)
            assertEquals(AccessStatus.Granted, result.access.status(AccessScope.NotificationsRead))
            assertEquals(AccessStatus.Granted, result.access.status(AccessScope.NotificationsWrite))
            assertEquals(AccessStatus.Granted, result.access.status(AccessScope.FollowRequests))
            val request = server.takeRequest()
            assertEquals("/api/miauth/test-session/check", request.path)
            assertEquals("POST", request.method)
            assertEquals(1, server.requestCount)
        }
    }

    @Test fun misskeyCreateMapsAudienceReplyQuoteWarningAndPoll() = runBlocking {
        MockWebServer().use { server ->
            val response = JSONObject().put("createdNote", JSONObject(note("created")))
            server.enqueue(MockResponse().setBody(response.toString()))
            val origin = server.url("/").toString().removeSuffix("/")
            val source = MisskeySource(
                origin = origin,
                token = "test-token",
                api = MisskeyApi(),
                initialCapabilities = ServerCapabilities(canPublish = true),
            )
            val request = CreatePostRequest(
                text = "A new note",
                audience = Audience.Unlisted,
                contentWarning = "Spoilers",
                replyTo = EntityId(origin, "parent"),
                poll = PollRequest(listOf("Yes", "No"), multiple = true, expiresAt = Instant.parse("2026-09-08T00:00:00Z")),
                quoteOf = EntityId(origin, "quoted"),
            )

            assertEquals("created", source.create(request).id.value)
            val body = JSONObject(server.takeRequest().body.readUtf8())
            assertEquals("test-token", body.getString("i"))
            assertEquals("A new note", body.getString("text"))
            assertEquals("home", body.getString("visibility"))
            assertEquals("Spoilers", body.getString("cw"))
            assertEquals("parent", body.getString("replyId"))
            assertEquals("quoted", body.getString("renoteId"))
            assertEquals(listOf("Yes", "No"), body.getJSONObject("poll").getJSONArray("choices").let { choices ->
                (0 until choices.length()).map(choices::getString)
            })
            assertTrue(body.getJSONObject("poll").getBoolean("multiple"))
            assertEquals(Instant.parse("2026-09-08T00:00:00Z").toEpochMilli(), body.getJSONObject("poll").getLong("expiresAt"))
        }
    }

    @Test fun misskeyCreateRejectsAttachmentsUntilMediaIdsAreSupported() = runBlocking {
        MockWebServer().use { server ->
            val origin = server.url("/").toString().removeSuffix("/")
            val attachment = Attachment("https://example.org/photo.jpg", "image/jpeg", null)
            val source = MisskeySource(origin, "test-token", MisskeyApi())

            assertThrows(SourceError.Unsupported::class.java) {
                runBlocking { source.create(CreatePostRequest("text", attachments = listOf(attachment))) }
            }
            assertEquals(0, server.requestCount)
        }
    }

    @Test fun misskeyUpdateProfileUsesAccountEndpoint() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody(user))
            val origin = server.url("/").toString().removeSuffix("/")
            val account = MisskeySource(origin, "test-token", MisskeyApi()).updateProfile(UpdateProfileRequest("New name", "New bio"))
            assertEquals("Alice", account.displayName)
            val request = server.takeRequest()
            assertEquals("/api/i/update", request.path)
            val body = JSONObject(request.body.readUtf8())
            assertEquals("test-token", body.getString("i"))
            assertEquals("New name", body.getString("name"))
            assertEquals("New bio", body.getString("description"))
        }
    }

    @Test fun misskeyLoadsProfileAndLooksUpExactHandle() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody(user))
            server.enqueue(MockResponse().setBody(user))
            val origin = server.url("/").toString().removeSuffix("/")
            val source = MisskeySource(origin, "test-token", MisskeyApi())
            val accountId = AccountId(Connection(origin, Protocol.MISSKEY), "user-a")

            val profile = source.profile(accountId)
            val result = source.searchAccounts("@alice@example.org")

            assertEquals("Misskey bio", profile.biography)
            assertEquals(listOf("Website", "Matrix"), result.single().profileFields.map { it.name })
            val profileRequest = server.takeRequest()
            assertEquals("/api/users/show", profileRequest.path)
            assertEquals("user-a", JSONObject(profileRequest.body.readUtf8()).getString("userId"))
            val lookup = server.takeRequest()
            assertEquals("/api/users/show", lookup.path)
            val lookupBody = JSONObject(lookup.body.readUtf8())
            assertEquals("alice", lookupBody.getString("username"))
            assertEquals("example.org", lookupBody.getString("host"))
        }
    }

    @Test fun misskeyProfileTimelineUsesFlagsOuterCursorAndSharedClassification() = runBlocking {
        MockWebServer().use { server ->
            val origin = server.url("/").toString().removeSuffix("/")
            val target = AccountId(Connection(origin, Protocol.MISSKEY), "user-a")
            val reply = JSONObject(note("reply"))
                .put("replyId", "parent-note")
                .put("replyUserId", "other-user")
            val media = JSONObject(note("media")).put("files", JSONArray().put(
                JSONObject().put("url", "https://example.org/photo.jpg").put("type", "image/jpeg"),
            ))
            val repost = JSONObject(note("repost"))
            repost.remove("text")
            repost.put("renote", JSONObject(note("quoted")))

            server.enqueue(MockResponse().setBody(JSONArray().put(JSONObject(note("root"))).put(repost).put(reply).toString()))
            server.enqueue(MockResponse().setBody("[]"))
            server.enqueue(MockResponse().setBody(JSONArray().put(JSONObject(note("plain"))).put(media).toString()))
            server.enqueue(MockResponse().setBody(JSONArray().put(repost).toString()))
            server.enqueue(MockResponse().setBody(JSONArray().put(reply).toString()))
            val source = MisskeySource(origin, "test-token", MisskeyApi())

            val posts = source.profileTimeline(ProfileTimelineQuery(target, ProfileTimelineTab.Posts))
            source.profileTimeline(ProfileTimelineQuery(target, ProfileTimelineTab.Posts), posts.nextCursor)
            val mediaPage = source.profileTimeline(ProfileTimelineQuery(target, ProfileTimelineTab.Media))
            val reposts = source.profileTimeline(ProfileTimelineQuery(target, ProfileTimelineTab.Reposts))
            val replies = source.profileTimeline(ProfileTimelineQuery(target, ProfileTimelineTab.Replies))

            assertEquals(listOf("root"), posts.items.map { it.id.value })
            assertEquals("reply", posts.nextCursor)
            assertEquals(listOf("media"), mediaPage.items.map { it.id.value })
            assertEquals(listOf("repost"), reposts.items.map { it.id.value })
            assertEquals(listOf("reply"), replies.items.map { it.id.value })

            val firstBody = JSONObject(server.takeRequest().body.readUtf8())
            val continuationBody = JSONObject(server.takeRequest().body.readUtf8())
            val mediaBody = JSONObject(server.takeRequest().body.readUtf8())
            val repostBody = JSONObject(server.takeRequest().body.readUtf8())
            val repliesBody = JSONObject(server.takeRequest().body.readUtf8())
            assertEquals("user-a", firstBody.getString("userId"))
            assertEquals(40, firstBody.getInt("limit"))
            assertFalse(firstBody.getBoolean("withReplies"))
            assertFalse(firstBody.getBoolean("withRenotes"))
            assertEquals("reply", continuationBody.getString("untilId"))
            assertTrue(mediaBody.getBoolean("withFiles"))
            assertFalse(mediaBody.getBoolean("withRenotes"))
            assertFalse(repostBody.getBoolean("withReplies"))
            assertTrue(repostBody.getBoolean("withRenotes"))
            assertTrue(repliesBody.getBoolean("withReplies"))
            assertFalse(repliesBody.getBoolean("withRenotes"))
        }
    }

    @Test fun misskeyProfileRelationshipFollowUnfollowRereadsState() = runBlocking {
        MockWebServer().use { server ->
            val origin = server.url("/").toString().removeSuffix("/")
            val target = AccountId(Connection(origin, Protocol.MISSKEY), "user-a")
            server.enqueue(MockResponse().setBody("{\"isFollowing\":false,\"isFollowed\":true}"))
            server.enqueue(MockResponse().setBody("{}"))
            server.enqueue(MockResponse().setBody("{\"isFollowing\":true}"))
            server.enqueue(MockResponse().setBody("{}"))
            server.enqueue(MockResponse().setBody("{\"isFollowing\":false}"))
            val source = MisskeySource(origin, "test-token", MisskeyApi())

            assertTrue(source.profileRelationship(target).followedBy)
            assertTrue(source.followProfile(target).following)
            assertFalse(source.unfollowProfile(target).following)

            val relation = server.takeRequest()
            assertEquals("/api/users/relation", relation.path)
            assertEquals("user-a", JSONObject(relation.body.readUtf8()).getString("userId"))
            assertEquals("/api/following/create", server.takeRequest().path)
            assertEquals("/api/users/relation", server.takeRequest().path)
            assertEquals("/api/following/delete", server.takeRequest().path)
            assertEquals("/api/users/relation", server.takeRequest().path)
        }
    }

    @Test fun misskeyProfileRelationshipAcceptsArrayForRemoteMastodonAccount() = runBlocking {
        MockWebServer().use { server ->
            val origin = server.url("/").toString().removeSuffix("/")
            val target = AccountId(Connection(origin, Protocol.MISSKEY), "remote-mastodon-user")
            server.enqueue(MockResponse().setBody("[{\"id\":\"remote-mastodon-user\",\"following\":true,\"followedBy\":true,\"hasPendingRequestFromYou\":false}]"))

            val relationship = MisskeySource(origin, "test-token", MisskeyApi()).profileRelationship(target)

            assertTrue(relationship.following)
            assertTrue(relationship.followedBy)
            assertEquals("/api/users/relation", server.takeRequest().path)
        }
    }

    @Test fun misskeyPinnedPostsSupportInlineNotesAndBoundedIdFanout() = runBlocking {
        MockWebServer().use { server ->
            val origin = server.url("/").toString().removeSuffix("/")
            val target = AccountId(Connection(origin, Protocol.MISSKEY), "user-a")
            val foreignUser = JSONObject(user).put("id", "user-b").put("username", "bob")
            val foreignNote = JSONObject(note("foreign")).put("user", foreignUser)
            server.enqueue(MockResponse().setBody(JSONObject(user).put(
                "pinnedNotes", JSONArray().put(JSONObject(note("inline")).put("user", JSONObject(user))).put(foreignNote),
            ).toString()))
            val source = MisskeySource(origin, "test-token", MisskeyApi())
            assertEquals(listOf("inline"), source.pinnedPosts(target).map { it.id.value })
            assertEquals("/api/users/show", server.takeRequest().path)

            server.enqueue(MockResponse().setBody(JSONObject(user).put(
                "pinnedNotes", JSONArray().put("pin-a").put("pin-b"),
            ).toString()))
            server.enqueue(MockResponse().setBody(note("pin-a")))
            server.enqueue(MockResponse().setBody(note("pin-b")))
            assertEquals(listOf("pin-a", "pin-b"), source.pinnedPosts(target).map { it.id.value })
            assertEquals("/api/users/show", server.takeRequest().path)
            assertEquals("/api/notes/show", server.takeRequest().path)
            assertEquals("/api/notes/show", server.takeRequest().path)
        }
    }

    @Test fun misskeyProfileRejectsForeignTargetOriginBeforeNetwork() = runBlocking {
        MockWebServer().use { server ->
            val origin = server.url("/").toString().removeSuffix("/")
            val foreign = AccountId(Connection("https://other.example", Protocol.MISSKEY), "user-a")
            val source = MisskeySource(origin, "test-token", MisskeyApi())

            assertThrows(SourceError.Unsupported::class.java) { runBlocking { source.profile(foreign) } }
            assertEquals(0, server.requestCount)
        }
    }

    @Test fun misskeySearchesHashtagWithOpaqueNoteCursor() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("[${note("tag-newest")}]") )
            server.enqueue(MockResponse().setBody("[${note("tag-older")}]") )
            val origin = server.url("/").toString().removeSuffix("/")
            val source = MisskeySource(origin, "test-token", MisskeyApi())

            val first = source.searchHashtag("#cats")
            val second = source.searchHashtag("cats", first.nextCursor)

            assertEquals("tag-newest", first.items.single().id.value)
            assertEquals("tag-older", second.items.single().id.value)
            val firstBody = JSONObject(server.takeRequest().body.readUtf8())
            val secondBody = JSONObject(server.takeRequest().body.readUtf8())
            assertEquals("cats", firstBody.getString("tag"))
            assertEquals("tag-newest", secondBody.getString("untilId"))
        }
    }

    @Test fun capabilityRefreshPreservesAccountPublishPermission() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"version":"2026.1.0"}"""))
            server.enqueue(MockResponse().setBody("[]"))
            val origin = server.url("/").toString().removeSuffix("/")
            val source = MisskeySource(
                origin = origin,
                token = "test-token",
                api = MisskeyApi(),
                initialCapabilities = ServerCapabilities(canPublish = true),
            )

            source.timeline(Timeline.Home)

            assertTrue(source.capabilities.canPublish)
        }
    }

    @Test fun misskeyCreateMapsEveryAudienceVisibility() = runBlocking {
        MockWebServer().use { server ->
            repeat(4) {
                server.enqueue(MockResponse().setBody(JSONObject()
                    .put("createdNote", JSONObject(note("created-$it"))).toString()))
            }
            val origin = server.url("/").toString().removeSuffix("/")
            val source = MisskeySource(origin, "test-token", MisskeyApi(), initialCapabilities = ServerCapabilities(canPublish = true))
            listOf(
                Audience.Public to "public",
                Audience.Unlisted to "home",
                Audience.Followers to "followers",
                Audience.Direct to "specified",
            ).forEach { (audience, visibility) ->
                source.create(CreatePostRequest("text", audience = audience))
                assertEquals(visibility, JSONObject(server.takeRequest().body.readUtf8()).getString("visibility"))
            }
        }
    }

    @Test fun misskeyDeleteUsesNotesDelete() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("{}"))
            val origin = server.url("/").toString().removeSuffix("/")
            MisskeySource(origin, "test-token", MisskeyApi()).delete(EntityId(origin, "note-to-delete"))

            val request = server.takeRequest()
            assertEquals("/api/notes/delete", request.path)
            val body = JSONObject(request.body.readUtf8())
            assertEquals("test-token", body.getString("i"))
            assertEquals("note-to-delete", body.getString("noteId"))
        }
    }

    @Test fun homeFeedUsesOuterRenoteCursorAndMapsSensitiveMediaAndQuotes() = runBlocking {
        MockWebServer().use { server ->
            val renote = """{"id":"outer-id","createdAt":"2026-09-06T11:00:00Z","user":$user,"text":null,"renote":${note("original-id")}}"""
            server.enqueue(MockResponse().setBody("""{"version":"2026.1.0"}"""))
            server.enqueue(MockResponse().setBody("[$renote]"))
            server.enqueue(MockResponse().setBody("[]"))
            val source = MisskeySource(server.url("/").toString().removeSuffix("/"), "test-token", MisskeyApi())
            val page = source.timeline(Timeline.Home)
            assertEquals("outer-id", page.nextCursor)
            assertEquals("outer-id", page.items.single().id.value)
            assertEquals(AccountId(Connection(server.url("/").toString().removeSuffix("/"), Protocol.MISSKEY), "user-a"), page.items.single().author.id)
            assertEquals("Alice", page.items.single().resharedBy?.displayName)
            assertEquals(Audience.Unlisted, page.items.single().audience)
            assertTrue(page.items.single().url!!.endsWith("/notes/original-id"))
            val metaRequest = server.takeRequest()
            assertEquals("/api/meta", metaRequest.path)
            assertEquals("POST", metaRequest.method)
            assertTrue(metaRequest.getHeader("Content-Type")?.startsWith("application/json") == true)
            assertEquals("{}", metaRequest.body.readUtf8())
            val first = server.takeRequest()
            assertEquals("/api/notes/timeline", first.path)
            assertEquals("test-token", JSONObject(first.body.readUtf8()).getString("i"))
            assertNull(source.timeline(Timeline.Home, page.nextCursor).nextCursor)
            assertEquals("outer-id", JSONObject(server.takeRequest().body.readUtf8()).getString("untilId"))
        }
        val json = JSONObject(note("quoted"))
            .put("cw", "Warning").put("renote", JSONObject(note("original")))
            .put("files", org.json.JSONArray("""[{"url":"https://example.org/photo.jpg","type":"image/jpeg","comment":"A photo","isSensitive":true}]"""))
        val post = MisskeyMapper.post(json, "https://example.org")
        assertEquals("Warning", post.contentWarning)
        assertTrue(post.attachments.single().sensitive)
        assertEquals("A photo", post.attachments.single().description)
        assertNotNull(post.quote)
        assertNull(post.resharedBy)
    }

    @Test fun capabilityProbeUsesMisskeyDisableFlagsForTimelines() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody(
                JSONObject().put("version", "2026.1.0")
                    .put("disableLocalTimeline", false)
                    .put("disableGlobalTimeline", true).toString(),
            ))
            val connection = Connection(server.url("/").toString().removeSuffix("/"), Protocol.MISSKEY)
            val capabilities = MisskeyCapabilityProbe(MisskeyApi()).probeCapabilities(connection)

            assertEquals(setOf(Timeline.Home, Timeline.Local, Timeline.Social), capabilities.timelines)
            assertFalse(Timeline.Federated in capabilities.timelines)
            assertEquals(setOf(PostAction.Reply, PostAction.Reshare, PostAction.Favorite, PostAction.React, PostAction.Bookmark), capabilities.actions)
        }
    }

    @Test fun misskeyTimelineRoutesUseNativeEndpoints() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"version":"2026.1.0"}"""))
            repeat(3) { server.enqueue(MockResponse().setBody("[]")) }
            val source = MisskeySource(server.url("/").toString().removeSuffix("/"), "test-token", MisskeyApi())

            source.timeline(Timeline.Local)
            source.timeline(Timeline.Social)
            source.timeline(Timeline.Federated)

            assertEquals("/api/meta", server.takeRequest().path)
            assertEquals("/api/notes/local-timeline", server.takeRequest().path)
            assertEquals("/api/notes/hybrid-timeline", server.takeRequest().path)
            assertEquals("/api/notes/global-timeline", server.takeRequest().path)
        }
    }

    @Test fun postAndThreadCombineAncestorsRootAndChildren() = runBlocking {
        MockWebServer().use { server ->
            val root = JSONObject(note("root")).put("replyId", "parent").toString()
            val parent = JSONObject(note("parent")).toString()
            val child = JSONObject(note("child")).toString()
            server.enqueue(MockResponse().setBody(root))
            server.enqueue(MockResponse().setBody(root))
            server.enqueue(MockResponse().setBody(parent))
            server.enqueue(MockResponse().setBody("[$child]"))
            val source = MisskeySource(server.url("/").toString().removeSuffix("/"), "test-token", MisskeyApi())
            val rootId = EntityId(server.url("/").toString().removeSuffix("/"), "root")

            assertEquals("root", source.post(rootId).id.value)
            val thread = source.thread(rootId)

            assertEquals(listOf("parent", "root", "child"), thread.map { it.id.value })
            assertEquals("/api/notes/show", server.takeRequest().path)
            assertEquals("/api/notes/show", server.takeRequest().path)
            assertEquals("/api/notes/show", server.takeRequest().path)
            assertEquals("/api/notes/children", server.takeRequest().path)
        }
    }

    @Test fun authenticatedPostNeverFollowsRedirects() = runBlocking {
        MockWebServer().use { server -> MockWebServer().use { destination ->
            server.enqueue(MockResponse().setResponseCode(307).addHeader("Location", destination.url("/steal")))
            try {
                MisskeyApi().post(server.url("/").toString().removeSuffix("/"), "notes/timeline", JSONObject().put("i", "test-token"))
                fail("Redirect must fail")
            } catch (e: ApiFailure) { assertEquals(307, e.status) }
            assertEquals(0, destination.requestCount)
        } }
    }

    @Test fun httpResponseExposesHeadersAndMastodonNextCursor() {
        val response = HttpResponse("body", Headers.headersOf(
            "Link", "<https://example.org/api/v1/timelines/home?max_id=10>; rel=\"next\", <https://example.org/prev>; rel=\"prev\"",
        ))
        assertEquals("body", response.body)
        assertNotNull(response.linkHeader())
        assertEquals("https://example.org/api/v1/timelines/home?max_id=10", response.linkHeaderCursor())
    }

    @Test fun mastodonCallbackAndTokenExchangeUseOpaqueStateAndCode() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"access_token":"mastodon-token","scope":"read write push"}"""))
            server.enqueue(MockResponse().setBody("""{"id":"mastodon-user","username":"alice","acct":"alice","display_name":"Alice","avatar":"https://example.org/avatar.png","note":"<p>Hello</p>"}"""))
            val origin = server.url("/").toString().removeSuffix("/")
            val pending = PendingLogin(origin, "oauth-state", System.currentTimeMillis(), Protocol.MASTODON,
                clientId = "client-id", clientSecret = "client-secret", codeVerifier = "verifier",
                codeChallenge = "challenge", authorizationCode = "auth-code")
            assertTrue(AuthCallback.matches("palustris://auth/mastodon?code=auth-code&state=oauth-state", pending, System.currentTimeMillis()))
            val auth = MastodonAuth(MisskeyApi())
            val result = auth.complete(pending)
            assertEquals("mastodon-token", result.token)
            assertEquals(Protocol.MASTODON, result.protocol)
            assertEquals(AccessStatus.Granted, result.access.status(AccessScope.Push))
            assertEquals(AccountId(Connection(origin, Protocol.MASTODON), "mastodon-user"), result.account.id)
            assertEquals("@alice@${server.hostName}", result.account.handle)
            val tokenRequest = server.takeRequest()
            assertEquals("/oauth/token", tokenRequest.path)
            val tokenBody = tokenRequest.body.readUtf8()
            assertEquals("authorization_code", tokenBody.substringAfter("grant_type=").substringBefore('&'))
            assertTrue(tokenBody.contains("code_verifier=verifier"))
            assertEquals("Bearer mastodon-token", server.takeRequest().getHeader("Authorization"))
        }
    }

    @Test fun mastodonBrowserUrlIncludesPkceWhenAvailable() {
        val pending = PendingLogin("https://example.org", "state", System.currentTimeMillis(), Protocol.MASTODON,
            clientId = "client-id", codeChallenge = "challenge")
        val url = MastodonAuth(MisskeyApi()).browserUrl(pending).toHttpUrl()
        assertEquals("/oauth/authorize", url.encodedPath)
        assertEquals("client-id", url.queryParameter("client_id"))
        assertEquals("challenge", url.queryParameter("code_challenge"))
        assertEquals("S256", url.queryParameter("code_challenge_method"))
        assertEquals("state", url.queryParameter("state"))
    }

    @Test fun protocolErrorsMapToSharedSourceErrors() {
        assertSame(SourceError.Unauthorized, MisskeyErrorMapper.map(ApiFailure(401)))
        assertSame(SourceError.RateLimited, MastodonErrorMapper.map(429))
        assertEquals("server exploded", (MastodonErrorMapper.map(500, """{"error":"server exploded"}""") as SourceError.ServerError).detail)
        assertEquals("timeline", (MisskeyErrorMapper.map(ApiFailure(404, "timeline")) as SourceError.Unsupported).feature)
    }

    @Test fun unsupportedSocialSourceOperationsUseSharedError() = runBlocking {
        val source = object : SocialSource {
            override val capabilities = ServerCapabilities()
            override suspend fun timeline(timeline: Timeline, cursor: String?): Page<Post> = Page(emptyList())
        }
        try {
            source.search("hello")
            fail("Unsupported operation should throw")
        } catch (error: SourceError.Unsupported) {
            assertEquals("search", error.feature)
        }
    }
}
