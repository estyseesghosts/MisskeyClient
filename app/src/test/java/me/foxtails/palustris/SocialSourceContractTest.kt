package me.foxtails.palustris

import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.runBlocking
import me.foxtails.palustris.data.misskey.MisskeyApi
import me.foxtails.palustris.domain.CapabilityProbe
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.Protocol
import me.foxtails.palustris.domain.ServerCapabilities
import me.foxtails.palustris.domain.SourceError
import me.foxtails.palustris.domain.Timeline
import me.foxtails.palustris.ui.FeedState
import me.foxtails.palustris.ui.requiresSignIn
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Protocol-neutral behavior checks shared by every SocialSource adapter. */
abstract class SocialSourceContractTest {
    protected abstract fun createSource(
        server: MockWebServer,
        capabilityProbe: CapabilityProbe?,
        clock: () -> Long,
    ): me.foxtails.palustris.domain.SocialSource

    protected abstract fun enqueueCapabilities(server: MockWebServer, timelines: Set<Timeline> = setOf(Timeline.Home))

    protected abstract fun enqueueTimelinePage(server: MockWebServer, ids: List<String>)

    @Test
    fun callingTimelineTwiceWithReturnedCursorNeverRepeatsIds() = runBlocking {
        MockWebServer().use { server ->
            enqueueCapabilities(server)
            enqueueTimelinePage(server, listOf("first"))
            enqueueTimelinePage(server, listOf("second"))
            val source = createSource(server, null, System::currentTimeMillis)

            val first = source.timeline(Timeline.Home)
            val second = source.timeline(Timeline.Home, first.nextCursor)

            assertTrue(first.nextCursor != null)
            assertTrue(first.items.map { it.id }.intersect(second.items.map { it.id }).isEmpty())
        }
    }

    @Test
    fun unauthorizedErrorRequiresSignIn() {
        assertTrue(requiresSignIn(SourceError.Unauthorized))
        assertFalse(requiresSignIn(SourceError.RateLimited))
        assertFalse(FeedState().needsSignIn)
    }

    @Test
    fun emptyPageHasNoNextCursor() = runBlocking {
        MockWebServer().use { server ->
            enqueueCapabilities(server)
            enqueueTimelinePage(server, emptyList())
            val page = createSource(server, null, System::currentTimeMillis).timeline(Timeline.Home)

            assertTrue(page.items.isEmpty())
            assertEquals(null, page.nextCursor)
        }
    }

    @Test
    fun cursorPaginationReturnsDifferentItems() = runBlocking {
        MockWebServer().use { server ->
            enqueueCapabilities(server)
            enqueueTimelinePage(server, listOf("newest"))
            enqueueTimelinePage(server, listOf("older"))
            val source = createSource(server, null, System::currentTimeMillis)

            val first = source.timeline(Timeline.Home)
            val second = source.timeline(Timeline.Home, first.nextCursor)

            assertNotEquals(first.items.single().id, second.items.single().id)
        }
    }

    @Test
    fun unsupportedTimelineReturnsSharedError() = runBlocking {
        MockWebServer().use { server ->
            enqueueCapabilities(server)
            val source = createSource(server, null, System::currentTimeMillis)

            try {
                source.timeline(Timeline.Local)
                throw AssertionError("Unsupported timeline should throw")
            } catch (error: SourceError.Unsupported) {
                assertEquals("timeline:Local", error.feature)
            }
        }
    }

    @Test
    fun capabilitiesRefreshReturnsUpdatedCapabilities() = runBlocking {
        MockWebServer().use { server ->
            val now = AtomicLong(5 * 60 * 1000L + 1)
            var probeCount = 0
            val probe = object : CapabilityProbe {
                override suspend fun probeCapabilities(connection: Connection): ServerCapabilities {
                    probeCount++
                    return ServerCapabilities(
                        timelines = if (probeCount == 1) setOf(Timeline.Home) else setOf(Timeline.Home, Timeline.Local),
                        capabilitiesLastUpdated = now.get(),
                    )
                }
            }
            enqueueTimelinePage(server, listOf("first"))
            enqueueTimelinePage(server, listOf("second"))
            val source = createSource(server, probe, now::get)

            source.timeline(Timeline.Home)
            now.addAndGet(5 * 60 * 1000L + 1)
            source.timeline(Timeline.Home)

            assertEquals(2, probeCount)
            assertTrue(Timeline.Local in source.capabilities.timelines)
        }
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
open class MisskeySourceContractTest : SocialSourceContractTest() {
    private val user = """{"id":"contract-user","username":"contract","name":"Contract","host":null}"""

    override fun createSource(
        server: MockWebServer,
        capabilityProbe: CapabilityProbe?,
        clock: () -> Long,
    ): me.foxtails.palustris.domain.SocialSource {
        val origin = server.url("/").toString().removeSuffix("/")
        val api = MisskeyApi()
        return me.foxtails.palustris.data.misskey.MisskeySource(
            origin = origin,
            token = "contract-token",
            api = api,
            accountId = me.foxtails.palustris.domain.AccountId(Connection(origin, Protocol.MISSKEY), "contract-user"),
            capabilityProbe = capabilityProbe ?: me.foxtails.palustris.data.misskey.MisskeyCapabilityProbe(api),
            clock = clock,
        )
    }

    override fun enqueueCapabilities(server: MockWebServer, timelines: Set<Timeline>) {
        server.enqueue(okhttp3.mockwebserver.MockResponse().setBody(
            org.json.JSONObject().put("version", "2026.1.0")
                .put("timelines", org.json.JSONArray(timelines.map(Timeline::name))).toString(),
        ))
    }

    override fun enqueueTimelinePage(server: MockWebServer, ids: List<String>) {
        val body = org.json.JSONArray(ids.map { id ->
            org.json.JSONObject("""{"id":"$id","createdAt":"2026-09-06T10:00:00Z","user":$user,"text":"Contract","visibility":"home"}""")
        }).toString()
        server.enqueue(okhttp3.mockwebserver.MockResponse().setBody(body))
    }
}
