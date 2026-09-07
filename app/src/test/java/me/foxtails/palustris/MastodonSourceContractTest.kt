package me.foxtails.palustris

import me.foxtails.palustris.data.mastodon.MastodonSource
import me.foxtails.palustris.data.misskey.MisskeyApi
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.CapabilityProbe
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.Protocol
import me.foxtails.palustris.domain.ServerCapabilities
import me.foxtails.palustris.domain.Timeline
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONArray
import org.json.JSONObject
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MastodonSourceContractTest : SocialSourceContractTest() {
    private val account = JSONObject()
        .put("id", "contract-user")
        .put("username", "contract")
        .put("acct", "contract")
        .put("display_name", "Contract")
        .put("note", "")

    override fun createSource(
        server: MockWebServer,
        capabilityProbe: CapabilityProbe?,
        clock: () -> Long,
    ): me.foxtails.palustris.domain.SocialSource {
        val origin = server.url("/").toString().removeSuffix("/")
        return MastodonSource(
            origin = origin,
            token = "contract-token",
            api = MisskeyApi(),
            accountId = AccountId(Connection(origin, Protocol.MASTODON), "contract-user"),
            initialCapabilities = ServerCapabilities(timelines = setOf(Timeline.Home)),
            capabilityProbe = capabilityProbe,
            clock = clock,
        )
    }

    override fun enqueueCapabilities(server: MockWebServer, timelines: Set<Timeline>) = Unit

    override fun enqueueTimelinePage(server: MockWebServer, ids: List<String>) {
        val body = JSONArray(ids.map { id ->
            JSONObject()
                .put("id", id)
                .put("created_at", "2026-09-06T10:00:00Z")
                .put("account", account)
                .put("content", "<p>Contract</p>")
                .put("visibility", "public")
        }).toString()
        val response = MockResponse().setBody(body)
        if (ids.isNotEmpty()) {
            val origin = server.url("/").toString().removeSuffix("/")
            response.addHeader("Link", "<$origin/api/v1/timelines/home?max_id=${ids.last()}>; rel=\"next\"")
        }
        server.enqueue(response)
    }
}
