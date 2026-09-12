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
import me.foxtails.palustris.domain.withTimelineStatuses
import org.json.JSONObject

class MisskeyCapabilityProbe(
    private val api: MisskeyApi,
    private val token: String? = null,
) : CapabilityProbe {
    override suspend fun probeCapabilities(connection: Connection): ServerCapabilities {
        // Misskey's HTTP API is POST-based, including the unauthenticated meta endpoint.
        val meta = JSONObject(api.post(connection.origin, "meta").body)
        require(meta.optString("version").isNotBlank()) { "This server did not return Misskey-compatible information." }
        val policies = token?.takeIf(String::isNotBlank)?.let { credential ->
            JSONObject(api.post(connection.origin, "i", JSONObject().put("i", credential)).body)
                .optJSONObject("policies")
        }
        val localFallback = if (meta.has("disableLocalTimeline")) {
            if (meta.optBoolean("disableLocalTimeline")) CapabilityStatus.Unsupported else CapabilityStatus.Supported
        } else {
            CapabilityStatus.Supported
        }
        val globalFallback = if (meta.has("disableGlobalTimeline")) {
            if (meta.optBoolean("disableGlobalTimeline")) CapabilityStatus.Unsupported else CapabilityStatus.Supported
        } else {
            CapabilityStatus.Supported
        }
        val statuses = linkedMapOf(
            Timeline.Home to CapabilityStatus.Supported,
            Timeline.Local to policyStatus(policies, "ltlAvailable", localFallback),
            Timeline.Social to policyStatus(policies, "ltlAvailable", localFallback),
            Timeline.Federated to policyStatus(policies, "gtlAvailable", globalFallback),
        )
        if (token?.isNotBlank() == true) {
            statuses[Timeline.Bubble] = probeBubble(connection.origin, token, policies)
        }
        return ServerCapabilities(
            timelineStatuses = statuses,
            audiences = setOf(Audience.Public, Audience.Unlisted, Audience.Followers, Audience.Direct),
            actions = setOf(PostAction.Reply, PostAction.Reshare, PostAction.Favorite, PostAction.React, PostAction.Bookmark),
            quotes = CapabilityStatus.Supported,
            primaryFavourite = PrimaryFavouriteCapability(CapabilityStatus.Supported, PrimaryFavouriteMode.Reaction),
            savedPosts = SavedPostsCapability(CapabilityStatus.Supported, SavedPostsKind.Favourites),
            likedPosts = CapabilityStatus.Supported,
            threads = CapabilityStatus.Supported,
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
        ).withTimelineStatuses(statuses)
    }

    private suspend fun probeBubble(
        origin: String,
        token: String,
        policies: JSONObject?,
    ): CapabilityStatus = try {
        api.post(origin, "notes/bubble-timeline", JSONObject().put("i", token).put("limit", 1))
        policyStatus(policies, "btlAvailable", CapabilityStatus.Supported)
    } catch (e: ApiFailure) {
        when (e.code?.uppercase()) {
            "BTL_DISABLED" -> CapabilityStatus.Denied
            "NOT_SUPPORTED" -> CapabilityStatus.Unsupported
            else -> when {
                e.status == 404 -> CapabilityStatus.Unsupported
                e.status >= 500 || e.status == 429 -> CapabilityStatus.TemporarilyUnavailable
                else -> throw e
            }
        }
    } catch (_: java.io.IOException) {
        CapabilityStatus.TemporarilyUnavailable
    }

    private fun policyStatus(
        policies: JSONObject?,
        key: String,
        fallback: CapabilityStatus,
    ): CapabilityStatus = if (policies?.has(key) == true && !policies.isNull(key)) {
        if (policies.optBoolean(key)) CapabilityStatus.Supported else CapabilityStatus.Denied
    } else {
        fallback
    }
}

data class CapabilityCacheKey(val origin: String, val accountId: me.foxtails.palustris.domain.AccountId)

class CapabilityCache {
    private val values = java.util.concurrent.ConcurrentHashMap<CapabilityCacheKey, ServerCapabilities>()

    fun get(key: CapabilityCacheKey): ServerCapabilities? = values[key]
    fun put(key: CapabilityCacheKey, value: ServerCapabilities) { values[key] = value }
    fun remove(key: CapabilityCacheKey) { values.remove(key) }
}
