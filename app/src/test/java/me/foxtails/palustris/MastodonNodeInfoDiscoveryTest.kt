package me.foxtails.palustris

import kotlinx.coroutines.runBlocking
import me.foxtails.palustris.data.mastodon.MastodonCapabilityProbe
import me.foxtails.palustris.data.misskey.MisskeyApi
import me.foxtails.palustris.domain.CapabilityStatus
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.PostAction
import me.foxtails.palustris.domain.Protocol
import me.foxtails.palustris.domain.ReactionSelectionMode
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * NodeInfo discovery fallback for servers whose instance metadata omits the reaction
 * advertisement. See Chunk 03-A2 of `docs/decomposition_3/03.md`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MastodonNodeInfoDiscoveryTest {
    @Test
    fun nodeInfoAdvertisementAddsReactionsWhenInstanceMetadataLacksIt() = runBlocking {
        MockWebServer().use { server ->
            val origin = server.url("/").toString().removeSuffix("/")
            server.enqueue(MockResponse().setBody(instance("4.6.0").toString()))
            server.enqueue(MockResponse().setBody(wellKnown(origin).toString()))
            server.enqueue(MockResponse().setBody(nodeInfo("pleroma_emoji_reactions").toString()))

            val capabilities = MastodonCapabilityProbe(MisskeyApi())
                .probeCapabilities(Connection(origin, Protocol.MASTODON))

            assertEquals(CapabilityStatus.Supported, capabilities.emoji.reactionMutation)
            assertEquals(ReactionSelectionMode.Independent, capabilities.emoji.selectionMode)
            assertTrue(PostAction.React in capabilities.actions)
            assertEquals(3, server.requestCount)
            assertEquals("/api/v2/instance", server.takeRequest().path)
            val wellKnownRequest = server.takeRequest()
            assertEquals("/.well-known/nodeinfo", wellKnownRequest.path)
            assertNull(wellKnownRequest.getHeader("Authorization"))
            val documentRequest = server.takeRequest()
            assertEquals("/nodeinfo/2.1", documentRequest.path)
            assertNull(documentRequest.getHeader("Authorization"))
        }
    }

    @Test
    fun nodeInfoDiscoveryIsSkippedWhenInstanceMetadataAdvertises() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody(extensionInstance().toString()))
            val origin = server.url("/").toString().removeSuffix("/")

            val capabilities = MastodonCapabilityProbe(MisskeyApi())
                .probeCapabilities(Connection(origin, Protocol.MASTODON))

            assertEquals(CapabilityStatus.Supported, capabilities.emoji.reactionMutation)
            assertEquals(1, server.requestCount)
            assertEquals("/api/v2/instance", server.takeRequest().path)
        }
    }

    @Test
    fun nodeInfoTwentyOneIsPreferredOverTwentyZero() = runBlocking {
        MockWebServer().use { server ->
            val origin = server.url("/").toString().removeSuffix("/")
            server.enqueue(MockResponse().setBody(instance("4.6.0").toString()))
            server.enqueue(MockResponse().setBody(
                wellKnown(
                    "http://nodeinfo.diaspora.software/ns/schema/2.0" to "$origin/nodeinfo/2.0",
                    "http://nodeinfo.diaspora.software/ns/schema/2.1" to "$origin/nodeinfo/2.1",
                ).toString(),
            ))
            server.enqueue(MockResponse().setBody(nodeInfo("pleroma_emoji_reactions").toString()))

            val capabilities = MastodonCapabilityProbe(MisskeyApi())
                .probeCapabilities(Connection(origin, Protocol.MASTODON))

            assertEquals(CapabilityStatus.Supported, capabilities.emoji.reactionMutation)
            assertEquals(3, server.requestCount)
            server.takeRequest()
            server.takeRequest()
            assertEquals("/nodeinfo/2.1", server.takeRequest().path)
        }
    }

    @Test
    fun nodeInfoDocumentWithoutTheFeatureKeepsReactionsUnknown() = runBlocking {
        MockWebServer().use { server ->
            val origin = server.url("/").toString().removeSuffix("/")
            server.enqueue(MockResponse().setBody(instance("4.6.0").toString()))
            server.enqueue(MockResponse().setBody(wellKnown(origin).toString()))
            server.enqueue(MockResponse().setBody(nodeInfo("pleroma_other").toString()))

            val capabilities = MastodonCapabilityProbe(MisskeyApi())
                .probeCapabilities(Connection(origin, Protocol.MASTODON))

            assertEquals(CapabilityStatus.Unknown, capabilities.emoji.reactionMutation)
            assertFalse(PostAction.React in capabilities.actions)
            assertEquals(3, server.requestCount)
        }
    }

    @Test
    fun nodeInfoWellKnownWithoutLinksKeepsReactionsUnknown() = runBlocking {
        MockWebServer().use { server ->
            val origin = server.url("/").toString().removeSuffix("/")
            server.enqueue(MockResponse().setBody(instance("4.6.0").toString()))
            server.enqueue(MockResponse().setBody("""{"links":[]}"""))

            val capabilities = MastodonCapabilityProbe(MisskeyApi())
                .probeCapabilities(Connection(origin, Protocol.MASTODON))

            assertEquals(CapabilityStatus.Unknown, capabilities.emoji.reactionMutation)
            assertEquals(2, server.requestCount)
        }
    }

    @Test
    fun foreignOrCrossSchemeNodeInfoUrlIsRejectedBeforeTheDocumentRequest() = runBlocking {
        listOf("https://evil.example/nodeinfo/2.1", "http://evil.example/nodeinfo/2.1").forEach { href ->
            MockWebServer().use { server ->
                val origin = server.url("/").toString().removeSuffix("/")
                server.enqueue(MockResponse().setBody(instance("4.6.0").toString()))
                server.enqueue(MockResponse().setBody(wellKnown("http://nodeinfo.diaspora.software/ns/schema/2.1" to href).toString()))

                val capabilities = MastodonCapabilityProbe(MisskeyApi())
                    .probeCapabilities(Connection(origin, Protocol.MASTODON))

                assertEquals(href, CapabilityStatus.Unknown, capabilities.emoji.reactionMutation)
                assertEquals(href, 2, server.requestCount)
            }
        }
    }

    @Test
    fun credentialedAndFragmentedNodeInfoUrlsAreRejected() = runBlocking {
        listOf("http://user:pass@localhost/nodeinfo/2.1", "http://localhost/nodeinfo/2.1#section").forEach { href ->
            MockWebServer().use { server ->
                val origin = server.url("/").toString().removeSuffix("/")
                server.enqueue(MockResponse().setBody(instance("4.6.0").toString()))
                server.enqueue(MockResponse().setBody(wellKnown("http://nodeinfo.diaspora.software/ns/schema/2.1" to href).toString()))

                val capabilities = MastodonCapabilityProbe(MisskeyApi())
                    .probeCapabilities(Connection(origin, Protocol.MASTODON))

                assertEquals(href, CapabilityStatus.Unknown, capabilities.emoji.reactionMutation)
                assertEquals(href, 2, server.requestCount)
            }
        }
    }

    @Test
    fun nodeInfoWellKnownFailureKeepsReactionsUnknownWithoutThrowing() = runBlocking {
        MockWebServer().use { server ->
            val origin = server.url("/").toString().removeSuffix("/")
            server.enqueue(MockResponse().setBody(instance("4.6.0").toString()))
            server.enqueue(MockResponse().setResponseCode(500).setBody("{}"))

            val capabilities = MastodonCapabilityProbe(MisskeyApi())
                .probeCapabilities(Connection(origin, Protocol.MASTODON))

            assertEquals(CapabilityStatus.Unknown, capabilities.emoji.reactionMutation)
            assertEquals(2, server.requestCount)
        }
    }

    @Test
    fun nodeInfoDocumentFailureKeepsReactionsUnknown() = runBlocking {
        MockWebServer().use { server ->
            val origin = server.url("/").toString().removeSuffix("/")
            server.enqueue(MockResponse().setBody(instance("4.6.0").toString()))
            server.enqueue(MockResponse().setBody(wellKnown(origin).toString()))
            server.enqueue(MockResponse().setResponseCode(500).setBody("{}"))

            val capabilities = MastodonCapabilityProbe(MisskeyApi())
                .probeCapabilities(Connection(origin, Protocol.MASTODON))

            assertEquals(CapabilityStatus.Unknown, capabilities.emoji.reactionMutation)
            assertEquals(3, server.requestCount)
        }
    }

    @Test
    fun nodeInfoReadIsBounded() = runBlocking {
        MockWebServer().use { server ->
            val origin = server.url("/").toString().removeSuffix("/")
            server.enqueue(MockResponse().setBody(instance("4.6.0").toString()))
            server.enqueue(MockResponse().setBody("x".repeat(300 * 1024)))

            val capabilities = MastodonCapabilityProbe(MisskeyApi())
                .probeCapabilities(Connection(origin, Protocol.MASTODON))

            assertEquals(CapabilityStatus.Unknown, capabilities.emoji.reactionMutation)
            assertEquals(2, server.requestCount)
        }
    }

    @Test
    fun nodeInfoFeatureDetectionIsNotSoftwareNameBased() {
        val nodeInfo = JSONObject()
            .put("software", JSONObject().put("name", "pleroma"))
            .put("metadata", JSONObject().put("features", JSONArray().put("pleroma_other")))

        assertFalse(MastodonCapabilityProbe.hasNodeInfoReactionFeature(nodeInfo))
        assertTrue(
            MastodonCapabilityProbe.hasNodeInfoReactionFeature(
                nodeInfo.put("metadata", JSONObject().put("features", JSONArray().put("pleroma_emoji_reactions"))),
            ),
        )
    }

    private fun wellKnown(origin: String): JSONObject =
        wellKnown("http://nodeinfo.diaspora.software/ns/schema/2.1" to "$origin/nodeinfo/2.1")

    private fun wellKnown(vararg links: Pair<String, String>): JSONObject = JSONObject().put(
        "links",
        JSONArray().apply {
            links.forEach { (rel, href) -> put(JSONObject().put("rel", rel).put("href", href)) }
        },
    )

    private fun nodeInfo(feature: String): JSONObject = JSONObject()
        .put("version", "2.1")
        .put("software", JSONObject().put("name", "pleroma"))
        .put("metadata", JSONObject().put("features", JSONArray().put(feature)))

    private fun instance(version: String): JSONObject = JSONObject().apply {
        if (version.isNotEmpty()) put("version", version)
    }

    private fun extensionInstance(): JSONObject = instance("4.6.0").put(
        "pleroma",
        JSONObject().put(
            "metadata",
            JSONObject().put("features", JSONArray().put("pleroma_emoji_reactions")),
        ),
    )
}
