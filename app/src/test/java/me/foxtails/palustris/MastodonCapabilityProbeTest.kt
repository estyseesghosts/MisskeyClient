package me.foxtails.palustris

import kotlinx.coroutines.runBlocking
import me.foxtails.palustris.data.mastodon.MastodonCapabilityProbe
import me.foxtails.palustris.data.misskey.MisskeyApi
import me.foxtails.palustris.domain.CapabilityStatus
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.PostAction
import me.foxtails.palustris.domain.Protocol
import me.foxtails.palustris.domain.ReactionSelectionMode
import me.foxtails.palustris.domain.ServerCapabilities
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MastodonCapabilityProbeTest {
    @Test
    fun apiVersionEightEnablesProfileApiButNotImageDescriptions() {
        val capabilities = parse(instanceWithApiVersion(8))

        assertEquals(CapabilityStatus.Supported, capabilities.profile.editable.read)
        assertEquals(CapabilityStatus.Supported, capabilities.profile.editable.update)
        assertEquals(CapabilityStatus.Supported, capabilities.profile.editable.advancedSettings)
        assertEquals(CapabilityStatus.Supported, capabilities.profile.editable.imageUpload)
        assertEquals(CapabilityStatus.Unsupported, capabilities.profile.editable.imageDescriptions)
        assertEquals(CapabilityStatus.Supported, capabilities.profile.editable.imageDeletion)
        assertEquals(CapabilityStatus.Supported, capabilities.quotes)
    }

    @Test
    fun apiVersionNineEnablesImageDescriptions() {
        val capabilities = parse(instanceWithApiVersion(9))

        assertEquals(CapabilityStatus.Supported, capabilities.profile.editable.imageDescriptions)
        assertEquals(CapabilityStatus.Supported, capabilities.profile.editable.read)
    }

    @Test
    fun apiVersionSevenDisablesTheProfileApiAndKeepsQuotes() {
        val capabilities = parse(instanceWithApiVersion(7))

        assertEquals(CapabilityStatus.Unsupported, capabilities.profile.editable.read)
        assertEquals(CapabilityStatus.Unsupported, capabilities.profile.editable.update)
        assertEquals(CapabilityStatus.Unsupported, capabilities.profile.editable.advancedSettings)
        assertEquals(CapabilityStatus.Unsupported, capabilities.profile.editable.imageDescriptions)
        assertEquals(CapabilityStatus.Supported, capabilities.profile.editable.imageDeletion)
        assertEquals(CapabilityStatus.Supported, capabilities.quotes)
    }

    @Test
    fun machineApiVersionWinsOverConflictingHumanVersion() {
        val capabilities = parse(instanceWithApiVersion(7).put("version", "4.6.0"))

        assertEquals(CapabilityStatus.Unsupported, capabilities.profile.editable.advancedSettings)
        assertEquals(CapabilityStatus.Supported, capabilities.quotes)
    }

    @Test
    fun missingApiVersionsUsesReleaseFourSixFallback() {
        val capabilities = parse(instance("4.6.0"))

        assertEquals(CapabilityStatus.Supported, capabilities.profile.editable.read)
        assertEquals(CapabilityStatus.Supported, capabilities.profile.editable.update)
        assertEquals(CapabilityStatus.Supported, capabilities.profile.editable.advancedSettings)
        assertEquals(CapabilityStatus.Supported, capabilities.profile.editable.imageUpload)
        assertEquals(CapabilityStatus.Unknown, capabilities.profile.editable.imageDescriptions)
        assertEquals(CapabilityStatus.Supported, capabilities.profile.editable.imageDeletion)
        assertEquals(CapabilityStatus.Supported, capabilities.quotes)
    }

    @Test
    fun malformedHumanVersionLeavesAdvancedCapabilitiesUnknown() {
        listOf("", "glitch", "v4.2.0-rc1", "4", "latest").forEach { version ->
            val capabilities = parse(instance(version))

            assertEquals(version, CapabilityStatus.Unknown, capabilities.profile.editable.read)
            assertEquals(version, CapabilityStatus.Unknown, capabilities.profile.editable.advancedSettings)
            assertEquals(version, CapabilityStatus.Unknown, capabilities.profile.editable.imageDeletion)
            assertEquals(version, CapabilityStatus.Unknown, capabilities.quotes)
        }
    }

    @Test
    fun forkStyleVersionDoesNotCombineUnrelatedNumbers() {
        val capabilities = parse(instance("3.5.3 (compatible; Pleroma 2.6.50)"))

        assertEquals(CapabilityStatus.Unsupported, capabilities.profile.editable.advancedSettings)
        assertEquals(CapabilityStatus.Unsupported, capabilities.profile.editable.imageDeletion)
        assertEquals(CapabilityStatus.Unsupported, capabilities.quotes)
    }

    @Test
    fun releaseVersionBelowFourSixDisablesTheProfileApi() {
        val capabilities = parse(instance("4.2.1"))

        assertEquals(CapabilityStatus.Unsupported, capabilities.profile.editable.read)
        assertEquals(CapabilityStatus.Supported, capabilities.profile.editable.imageDeletion)
        assertEquals(CapabilityStatus.Unsupported, capabilities.quotes)
    }

    @Test
    fun everyProbeResultCarriesTheCurrentSchemaVersion() {
        assertEquals(
            ServerCapabilities.CURRENT_CAPABILITY_SCHEMA_VERSION,
            parse(instance("4.6.0")).capabilitySchemaVersion,
        )
        assertEquals(
            ServerCapabilities.CURRENT_CAPABILITY_SCHEMA_VERSION,
            parse(instance("")).capabilitySchemaVersion,
        )
    }

    @Test
    fun ordinaryMastodonSupportsCatalogWithUnknownReactionsAndNoReactAction() {
        val capabilities = parse(instance("4.6.0"))

        assertEquals(CapabilityStatus.Supported, capabilities.emoji.catalog)
        assertEquals(CapabilityStatus.Unknown, capabilities.emoji.reactionListing)
        assertEquals(CapabilityStatus.Unknown, capabilities.emoji.reactionMutation)
        assertEquals(ReactionSelectionMode.Unknown, capabilities.emoji.selectionMode)
        assertFalse(PostAction.React in capabilities.actions)
        assertTrue(PostAction.Favorite in capabilities.actions)
        assertEquals(CapabilityStatus.Supported, capabilities.likedPosts)
    }

    @Test
    fun verifiedExtensionAdvertisementAddsReactIndependentAndMutation() {
        val capabilities = MastodonCapabilityProbe.parseCapabilities(extensionInstance())

        assertEquals(CapabilityStatus.Supported, capabilities.emoji.catalog)
        assertEquals(CapabilityStatus.Supported, capabilities.emoji.reactionListing)
        assertEquals(CapabilityStatus.Supported, capabilities.emoji.reactionMutation)
        assertEquals(ReactionSelectionMode.Independent, capabilities.emoji.selectionMode)
        assertTrue(PostAction.React in capabilities.actions)
    }

    @Test
    fun missingOrMalformedAdvertisementKeepsReactionSupportUnknown() {
        val absent = extensionInstance().put("pleroma", JSONObject().put("metadata", JSONObject()))
        val wrongFeature = extensionInstance().put(
            "pleroma",
            JSONObject().put("metadata", JSONObject().put("features", org.json.JSONArray().put("pleroma_other"))),
        )
        val featuresNotAnArray = extensionInstance().put(
            "pleroma",
            JSONObject().put("metadata", JSONObject().put("features", "pleroma_emoji_reactions")),
        )
        listOf(instance("4.6.0"), absent, wrongFeature, featuresNotAnArray).forEach { instance ->
            val capabilities = MastodonCapabilityProbe.parseCapabilities(instance)

            assertEquals(CapabilityStatus.Unknown, capabilities.emoji.reactionListing)
            assertEquals(CapabilityStatus.Unknown, capabilities.emoji.reactionMutation)
            assertEquals(ReactionSelectionMode.Unknown, capabilities.emoji.selectionMode)
            assertFalse(PostAction.React in capabilities.actions)
        }
    }

    @Test
    fun metadataAbsenceSendsOneInstanceRequestWithoutAProbe() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody(instance("4.6.0").toString()))
            val origin = server.url("/").toString().removeSuffix("/")
            val capabilities = MastodonCapabilityProbe(MisskeyApi())
                .probeCapabilities(Connection(origin, Protocol.MASTODON))

            assertFalse(PostAction.React in capabilities.actions)
            assertEquals(1, server.requestCount)
            assertEquals("/api/v2/instance", server.takeRequest().path)
        }
    }

    @Test
    fun extensionAdvertisementNeedsNoMutationProbe() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody(extensionInstance().toString()))
            val origin = server.url("/").toString().removeSuffix("/")
            val capabilities = MastodonCapabilityProbe(MisskeyApi())
                .probeCapabilities(Connection(origin, Protocol.MASTODON))

            assertEquals(CapabilityStatus.Supported, capabilities.emoji.reactionMutation)
            assertTrue(PostAction.React in capabilities.actions)
            assertEquals(1, server.requestCount)
            assertEquals("/api/v2/instance", server.takeRequest().path)
        }
    }

    @Test
    fun absentV2InstanceFallsBackToV1() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(404).setBody("""{"error":"Record not found"}"""))
            server.enqueue(MockResponse().setBody(extensionInstance().toString()))
            val origin = server.url("/").toString().removeSuffix("/")
            val capabilities = MastodonCapabilityProbe(MisskeyApi())
                .probeCapabilities(Connection(origin, Protocol.MASTODON))

            assertEquals(CapabilityStatus.Supported, capabilities.emoji.reactionMutation)
            assertEquals("/api/v2/instance", server.takeRequest().path)
            assertEquals("/api/v1/instance", server.takeRequest().path)
        }
    }

    @Test
    fun metadataFailurePropagatesWithoutPublishingDefaults() = runBlocking {
        listOf(401, 429, 500).forEach { status ->
            MockWebServer().use { server ->
                server.enqueue(MockResponse().setResponseCode(status).setBody("{}"))
                val origin = server.url("/").toString().removeSuffix("/")

                assertThrows(me.foxtails.palustris.data.misskey.ApiFailure::class.java) {
                    runBlocking {
                        MastodonCapabilityProbe(MisskeyApi())
                            .probeCapabilities(Connection(origin, Protocol.MASTODON))
                    }
                }
                assertEquals(1, server.requestCount)
            }
        }
    }

    @Test
    fun leadingVersionParserRejectsMalformedValues() {
        assertEquals(
            MastodonCapabilityProbe.VersionTriple(4, 2, 0),
            MastodonCapabilityProbe.parseLeadingVersion("4.2.0"),
        )
        assertEquals(
            MastodonCapabilityProbe.VersionTriple(4, 6, 1),
            MastodonCapabilityProbe.parseLeadingVersion("4.6.1+glitch"),
        )
        assertEquals(
            MastodonCapabilityProbe.VersionTriple(3, 5, 3),
            MastodonCapabilityProbe.parseLeadingVersion("3.5.3 (compatible; Pleroma 2.6.50)"),
        )
        assertEquals(
            MastodonCapabilityProbe.VersionTriple(4, 2, 0),
            MastodonCapabilityProbe.parseLeadingVersion("4.2"),
        )
        assertNull(MastodonCapabilityProbe.parseLeadingVersion("v4.2.0"))
        assertNull(MastodonCapabilityProbe.parseLeadingVersion("glitch"))
        assertNull(MastodonCapabilityProbe.parseLeadingVersion(""))
    }

    private fun parse(instance: JSONObject): ServerCapabilities =
        MastodonCapabilityProbe.parseCapabilities(instance)

    private fun instance(version: String): JSONObject {
        val json = JSONObject()
        if (version.isNotEmpty()) json.put("version", version)
        return json
    }

    private fun instanceWithApiVersion(version: Int): JSONObject =
        instance("4.6.0").put("api_versions", JSONObject().put("mastodon", version))

    private fun extensionInstance(): JSONObject = instance("4.6.0").put(
        "pleroma",
        JSONObject().put(
            "metadata",
            JSONObject().put("features", org.json.JSONArray().put("pleroma_emoji_reactions")),
        ),
    )
}
