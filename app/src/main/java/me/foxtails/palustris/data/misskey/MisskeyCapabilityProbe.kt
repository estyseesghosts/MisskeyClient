package me.foxtails.palustris.data.misskey

import me.foxtails.palustris.domain.CapabilityProbe
import me.foxtails.palustris.domain.Audience
import me.foxtails.palustris.domain.CapabilityStatus
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.EditableProfileCapabilities
import me.foxtails.palustris.domain.EmojiCapabilities
import me.foxtails.palustris.domain.NotificationCapabilities
import me.foxtails.palustris.domain.PostAction
import me.foxtails.palustris.domain.PrimaryFavouriteCapability
import me.foxtails.palustris.domain.PrimaryFavouriteMode
import me.foxtails.palustris.domain.ProfileCapabilities
import me.foxtails.palustris.domain.ReactionSelectionMode
import me.foxtails.palustris.domain.SavedPostsCapability
import me.foxtails.palustris.domain.SavedPostsKind
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
            audiences = setOf(Audience.Public, Audience.Unlisted, Audience.Followers, Audience.Direct),
            actions = setOf(PostAction.Reply, PostAction.Reshare, PostAction.Favorite, PostAction.React, PostAction.Bookmark),
            quotes = CapabilityStatus.Supported,
            primaryFavourite = PrimaryFavouriteCapability(CapabilityStatus.Supported, PrimaryFavouriteMode.Reaction),
            savedPosts = SavedPostsCapability(CapabilityStatus.Supported, SavedPostsKind.Favourites),
            profile = ProfileCapabilities(
                editable = EditableProfileCapabilities(
                    read = CapabilityStatus.Supported,
                    update = CapabilityStatus.Supported,
                ),
            ),
            emoji = EmojiCapabilities(
                catalog = CapabilityStatus.Supported,
                reactionListing = CapabilityStatus.Supported,
                reactionMutation = CapabilityStatus.Supported,
                selectionMode = ReactionSelectionMode.Single,
            ),
            notifications = NotificationCapabilities(
                webPush = meta.optString("swPublickey").takeIf(String::isNotBlank)
                    ?.let { CapabilityStatus.Supported }
                    ?: CapabilityStatus.Unsupported,
            ),
            capabilitiesLastUpdated = System.currentTimeMillis(),
            capabilitySchemaVersion = ServerCapabilities.CURRENT_CAPABILITY_SCHEMA_VERSION,
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
