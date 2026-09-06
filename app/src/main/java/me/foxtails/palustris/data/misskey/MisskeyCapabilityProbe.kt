package me.foxtails.palustris.data.misskey

import me.foxtails.palustris.domain.CapabilityProbe
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.ServerCapabilities
import me.foxtails.palustris.domain.Timeline
import org.json.JSONObject

class MisskeyCapabilityProbe(private val api: MisskeyApi) : CapabilityProbe {
    override suspend fun probeCapabilities(connection: Connection): ServerCapabilities {
        val meta = JSONObject(api.get(connection.origin, "meta").body)
        require(meta.optString("version").isNotBlank()) { "This server did not return Misskey-compatible information." }
        return ServerCapabilities(timelines = setOf(Timeline.Home), capabilitiesLastUpdated = System.currentTimeMillis())
    }
}

data class CapabilityCacheKey(val origin: String, val accountId: me.foxtails.palustris.domain.AccountId)

class CapabilityCache {
    private val values = java.util.concurrent.ConcurrentHashMap<CapabilityCacheKey, ServerCapabilities>()

    fun get(key: CapabilityCacheKey): ServerCapabilities? = values[key]
    fun put(key: CapabilityCacheKey, value: ServerCapabilities) { values[key] = value }
    fun remove(key: CapabilityCacheKey) { values.remove(key) }
}
