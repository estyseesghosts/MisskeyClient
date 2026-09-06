package me.foxtails.palustris

import kotlinx.coroutines.runBlocking
import me.foxtails.palustris.data.auth.*
import me.foxtails.palustris.data.misskey.*
import me.foxtails.palustris.domain.*
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MisskeyIntegrationTest {
    private val user = """{"id":"user-a","username":"alice","name":"Alice","host":null}"""
    private fun note(id: String) = """{"id":"$id","createdAt":"2026-09-06T10:00:00Z","user":$user,"text":"Hello","visibility":"home"}"""

    @Test fun domainValidationRejectsCredentialsAndNonHttpsUrls() {
        assertEquals("https://misskey.io", ServerAddress.normalize(" Misskey.IO/ "))
        listOf("http://example.org", "https://user:pass@example.org", "example.org/path", "example.org?token=secret", "example.org#x", "file:///etc/passwd").forEach {
            assertThrows(IllegalArgumentException::class.java) { ServerAddress.normalize(it) }
        }
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
            server.enqueue(MockResponse().setBody("[$renote]"))
            server.enqueue(MockResponse().setBody("[]"))
            val source = MisskeySource(server.url("/").toString().removeSuffix("/"), "test-token", MisskeyApi())
            val page = source.timeline(Timeline.Home)
            assertEquals("outer-id", page.nextCursor)
            assertEquals("outer-id", page.items.single().id.value)
            assertEquals("Alice", page.items.single().resharedBy?.displayName)
            assertEquals(Audience.Unlisted, page.items.single().audience)
            assertTrue(page.items.single().url!!.endsWith("/notes/original-id"))
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
}
