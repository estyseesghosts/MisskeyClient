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
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MisskeyIntegrationTest : MisskeySourceContractTest() {
    private val user = """{"id":"user-a","username":"alice","name":"Alice","host":null}"""
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

    @Test fun authChecksSessionAndUsesOnlyReadAccountPermission() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"ok":true,"token":"test-token","user":$user}"""))
            val auth = MisskeyAuth(MisskeyApi())
            val pending = PendingLogin(server.url("/").toString().removeSuffix("/"), "test-session", System.currentTimeMillis())
            val url = okhttp3.HttpUrl.Companion.run { auth.browserUrl(pending).toHttpUrl() }
            assertEquals("read:account", url.queryParameter("permission"))
            assertEquals("palustris://auth/misskey", url.queryParameter("callback"))
            val result = auth.complete(pending)
            assertEquals("Alice", result.account.displayName)
            assertEquals("test-token", result.token)
            val request = server.takeRequest()
            assertEquals("/api/miauth/test-session/check", request.path)
            assertEquals("POST", request.method)
            assertEquals(1, server.requestCount)
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
            assertEquals(setOf(PostAction.React), capabilities.actions)
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
            server.enqueue(MockResponse().setBody("""{"access_token":"mastodon-token"}"""))
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
