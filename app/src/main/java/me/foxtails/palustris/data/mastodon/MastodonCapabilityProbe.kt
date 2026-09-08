package me.foxtails.palustris.data.mastodon

import me.foxtails.palustris.data.misskey.MisskeyApi
import me.foxtails.palustris.domain.Audience
import me.foxtails.palustris.domain.CapabilityProbe
import me.foxtails.palustris.domain.CapabilityStatus
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.PostAction
import me.foxtails.palustris.domain.PrimaryFavouriteCapability
import me.foxtails.palustris.domain.PrimaryFavouriteMode
import me.foxtails.palustris.domain.SavedPostsCapability
import me.foxtails.palustris.domain.SavedPostsKind
import me.foxtails.palustris.domain.ServerCapabilities
import me.foxtails.palustris.domain.Timeline
import org.json.JSONObject

/** Probes Mastodon instance metadata without making protocol details visible to the UI. */
class MastodonCapabilityProbe(private val api: MisskeyApi) : CapabilityProbe {
    override suspend fun probeCapabilities(connection: Connection): ServerCapabilities {
        val version = JSONObject(api.get(connection.origin, "v2/instance").body)
            .optString("version")
            .takeIf(String::isNotBlank)
        return capabilitiesForVersion(version)
    }

    companion object {
        fun capabilitiesForVersion(version: String?): ServerCapabilities = ServerCapabilities(
            timelines = setOf(Timeline.Home, Timeline.Local, Timeline.Federated),
            audiences = setOf(Audience.Public, Audience.Unlisted, Audience.Followers, Audience.Direct),
            actions = setOf(PostAction.Reply, PostAction.Reshare, PostAction.Favorite, PostAction.Bookmark),
            canPublish = true,
            quotes = when {
                version == null -> CapabilityStatus.Unknown
                supportsQuotes(version) -> CapabilityStatus.Supported
                else -> CapabilityStatus.Unsupported
            },
            primaryFavourite = PrimaryFavouriteCapability(CapabilityStatus.Supported, PrimaryFavouriteMode.Native),
            savedPosts = SavedPostsCapability(CapabilityStatus.Supported, SavedPostsKind.Bookmarks),
            capabilitiesLastUpdated = System.currentTimeMillis(),
        )

        fun supportsQuotes(version: String): Boolean {
            val numbers = Regex("\\d+").findAll(version).map { it.value.toInt() }.toList()
            if (numbers.size < 2) return false
            val current = listOf(numbers[0], numbers[1], numbers.getOrElse(2) { 0 })
            return current[0] > 4 || (current[0] == 4 && current[1] >= 5)
        }
    }
}
