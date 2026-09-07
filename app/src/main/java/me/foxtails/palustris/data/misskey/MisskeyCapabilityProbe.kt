package me.foxtails.palustris.data.misskey

import me.foxtails.palustris.domain.CapabilityProbe
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.PostAction
import me.foxtails.palustris.domain.ServerCapabilities
import me.foxtails.palustris.domain.Timeline
import org.json.JSONObject

class MisskeyCapabilityProbe(private val api: MisskeyApi) : CapabilityProbe {
    override suspend fun probeCapabilities(connection: Connection): ServerCapabilities {
        // Misskey's HTTP API is POST-based, including the unauthenticated meta endpoint.
        val meta = JSONObject(api.post(connection.origin, "meta").body)
        require(meta.optString("version").isNotBlank()) { "This server did not return Misskey-compatible information." }
        val timelines = buildSet {
            add(Timeline.Home)
            if (!meta.optBoolean("disableLocalTimeline")) {
                add(Timeline.Local)
                add(Timeline.Social)
            }
            if (!meta.optBoolean("disableGlobalTimeline")) add(Timeline.Federated)
        }
        return ServerCapabilities(
            timelines = timelines,
            actions = setOf(PostAction.React),
            capabilitiesLastUpdated = System.currentTimeMillis(),
        )
    }
}

data class CapabilityCacheKey(val origin: String, val accountId: me.foxtails.palustris.domain.AccountId)

class CapabilityCache {
    private val values = java.util.concurrent.ConcurrentHashMap<CapabilityCacheKey, ServerCapabilities>()

    fun get(key: CapabilityCacheKey): ServerCapabilities? = values[key]
    fun put(key: CapabilityCacheKey, value: ServerCapabilities) { values[key] = value }
    fun remove(key: CapabilityCacheKey) { values.remove(key) }
}
