package me.foxtails.palustris.data.misskey

import me.foxtails.palustris.domain.CapabilityProbe
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.ServerCapabilities
import me.foxtails.palustris.domain.Timeline
import org.json.JSONArray
import org.json.JSONObject

class MisskeyCapabilityProbe(private val api: MisskeyApi) : CapabilityProbe {
    override suspend fun probeCapabilities(connection: Connection): ServerCapabilities {
        val meta = JSONObject(api.get(connection.origin, "meta").body)
        require(meta.optString("version").isNotBlank()) { "This server did not return Misskey-compatible information." }
        val timelines = meta.optJSONArray("timelines")?.let(::parseTimelines) ?: setOf(Timeline.Home)
        return ServerCapabilities(timelines = timelines, capabilitiesLastUpdated = System.currentTimeMillis())
    }

    private fun parseTimelines(values: JSONArray): Set<Timeline> = buildSet {
        for (index in 0 until values.length()) {
            runCatching { add(Timeline.valueOf(values.getString(index))) }
        }
    }
}

data class CapabilityCacheKey(val origin: String, val accountId: me.foxtails.palustris.domain.AccountId)

class CapabilityCache {
    private val values = java.util.concurrent.ConcurrentHashMap<CapabilityCacheKey, ServerCapabilities>()

    fun get(key: CapabilityCacheKey): ServerCapabilities? = values[key]
    fun put(key: CapabilityCacheKey, value: ServerCapabilities) { values[key] = value }
    fun remove(key: CapabilityCacheKey) { values.remove(key) }
}
