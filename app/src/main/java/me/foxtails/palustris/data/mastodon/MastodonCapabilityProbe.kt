package me.foxtails.palustris.data.mastodon

import kotlinx.coroutines.CancellationException
import me.foxtails.palustris.data.misskey.ApiFailure
import me.foxtails.palustris.data.misskey.MisskeyApi
import me.foxtails.palustris.domain.Audience
import me.foxtails.palustris.domain.CapabilityProbe
import me.foxtails.palustris.domain.CapabilityStatus
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.EditableProfileCapabilities
import me.foxtails.palustris.domain.EmojiCapabilities
import me.foxtails.palustris.domain.PostAction
import me.foxtails.palustris.domain.PrimaryFavouriteCapability
import me.foxtails.palustris.domain.PrimaryFavouriteMode
import me.foxtails.palustris.domain.ProfileCapabilities
import me.foxtails.palustris.domain.ModerationCapabilities
import me.foxtails.palustris.domain.ReactionSelectionMode
import me.foxtails.palustris.domain.SavedPostsCapability
import me.foxtails.palustris.domain.SavedPostsKind
import me.foxtails.palustris.domain.ServerCapabilities
import me.foxtails.palustris.domain.Timeline
import me.foxtails.palustris.domain.withTimelineStatuses
import org.json.JSONObject
import java.net.URI

/**
 * Probes Mastodon instance metadata without making protocol details visible to the UI.
 *
 * Reaction capability comes from a recognized extension advertisement, never from a
 * mutation request. An arbitrary probe request cannot prove mutation support: the
 * advertised extension is the only positive evidence this owner accepts.
 */
class MastodonCapabilityProbe(private val api: MisskeyApi) : CapabilityProbe {
    data class VersionTriple(val major: Int, val minor: Int, val patch: Int) {
        fun atLeast(major: Int, minor: Int, patch: Int): Boolean = when {
            this.major != major -> this.major > major
            this.minor != minor -> this.minor > minor
            else -> this.patch >= patch
        }
    }

    override suspend fun probeCapabilities(connection: Connection): ServerCapabilities {
        val instance = fetchInstanceMetadata(connection.origin)
        // NodeInfo is supplemental evidence. Skip discovery when instance metadata already
        // advertises the extension, so a supported server never pays the extra requests.
        val discovered = if (hasVerifiedEmojiReactionMetadata(instance)) {
            false
        } else {
            fetchNodeInfoReactionAdvertisement(connection.origin)
        }
        return parseCapabilities(instance, nodeInfoAdvertisesReactions = discovered)
    }

    /**
     * Fetches instance metadata with a bounded response size.
     *
     * The v2 endpoint is preferred. The v1 endpoint is a documented fallback only when the
     * v2 endpoint is absent (404). Any other failure propagates, so the caller keeps its
     * existing evidence instead of replacing verified state with a default.
     */
    suspend fun fetchInstanceMetadata(origin: String): JSONObject {
        val v2 = try {
            JSONObject(api.get(origin, "v2/instance", maxResponseBytes = MAX_INSTANCE_BYTES).body)
        } catch (e: CancellationException) {
            throw e
        } catch (e: ApiFailure) {
            if (e.status != 404) throw e
            null
        }
        if (v2 != null) return v2
        return JSONObject(api.get(origin, "v1/instance", maxResponseBytes = MAX_INSTANCE_BYTES).body)
    }

    /**
     * Discovers the reaction advertisement through NodeInfo when instance metadata lacks it.
     *
     * The well-known document and the chosen NodeInfo document stay on the validated
     * connection origin, carry no credential, follow no redirect, and are bounded in bytes.
     * At most one NodeInfo document is fetched. Every discovery failure returns false: an
     * inaccessible supplemental document is not proof that the extension is unsupported.
     */
    suspend fun fetchNodeInfoReactionAdvertisement(origin: String): Boolean {
        val wellKnown = try {
            JSONObject(api.getUrl("$origin/.well-known/nodeinfo", maxResponseBytes = MAX_NODEINFO_BYTES).body)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return false
        }
        val documentUrl = supportedNodeInfoUrl(origin, wellKnown) ?: return false
        val document = try {
            JSONObject(api.getUrl(documentUrl, maxResponseBytes = MAX_NODEINFO_BYTES).body)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return false
        }
        return hasNodeInfoReactionFeature(document)
    }


    companion object {
        /** Bounds the instance metadata read. The document is small on supported servers. */
        private const val MAX_INSTANCE_BYTES = 512 * 1024L

        /** Bounds each NodeInfo discovery read. */
        private const val MAX_NODEINFO_BYTES = 256 * 1024L

        /** Parses only a leading major.minor.patch value; fork suffixes are ignored. */
        fun parseLeadingVersion(value: String): VersionTriple? {
            val match = LEADING_VERSION.find(value.trim()) ?: return null
            if (match.range.first != 0) return null
            return VersionTriple(
                match.groupValues[1].toInt(),
                match.groupValues[2].toInt(),
                match.groupValues.getOrNull(3)?.toIntOrNull() ?: 0,
            )
        }

        /**
         * True when instance metadata advertises the recognized reaction extension.
         * The shape is fixture-confirmed. A malformed or partial shape returns false.
         */
        fun hasVerifiedEmojiReactionMetadata(instance: JSONObject): Boolean =
            instance.optJSONObject("pleroma")
                ?.optJSONObject("metadata")
                ?.optJSONArray("features")
                ?.let { features ->
                    (0 until features.length()).any { features.optString(it) == EMOJI_REACTION_FEATURE }
                }
                ?: false

        /**
         * True when a NodeInfo document advertises the recognized reaction extension.
         * Pleroma and Akkoma publish the feature list under the top-level `metadata` object.
         * Software names are not evidence.
         */
        fun hasNodeInfoReactionFeature(nodeInfo: JSONObject): Boolean =
            nodeInfo.optJSONObject("metadata")
                ?.optJSONArray("features")
                ?.let { features ->
                    (0 until features.length()).any { features.optString(it) == EMOJI_REACTION_FEATURE }
                }
                ?: false

        fun parseCapabilities(instance: JSONObject, nodeInfoAdvertisesReactions: Boolean = false): ServerCapabilities {
            val machineVersion = instance.optJSONObject("api_versions")?.opt("mastodon")?.let { value ->
                when (value) {
                    is Number -> value.toInt().takeIf { it >= 0 }
                    is String -> value.toIntOrNull()?.takeIf { it >= 0 }
                    else -> null
                }
            }
            val releaseVersion = parseLeadingVersion(instance.optString("version"))
            val editable = when {
                machineVersion != null -> editableForMachineApi(machineVersion)
                releaseVersion != null -> editableForReleaseVersion(releaseVersion)
                else -> EditableProfileCapabilities()
            }
            val imageDeletion = when {
                machineVersion != null -> CapabilityStatus.Supported
                releaseVersion != null -> if (releaseVersion.atLeast(4, 2, 0)) {
                    CapabilityStatus.Supported
                } else {
                    CapabilityStatus.Unsupported
                }
                else -> CapabilityStatus.Unknown
            }
            val quotes = when {
                machineVersion != null -> if (machineVersion >= 7) {
                    CapabilityStatus.Supported
                } else {
                    CapabilityStatus.Unsupported
                }
                releaseVersion != null -> if (releaseVersion.atLeast(4, 5, 0)) {
                    CapabilityStatus.Supported
                } else {
                    CapabilityStatus.Unsupported
                }
                else -> CapabilityStatus.Unknown
            }
            val emoji = emojiCapabilities(instance, nodeInfoAdvertisesReactions)
            val actions = setOf(
                PostAction.Reply,
                PostAction.Reshare,
                PostAction.Favorite,
                PostAction.Bookmark,
            ) + if (emoji.reactionMutation == CapabilityStatus.Supported) setOf(PostAction.React) else emptySet()
            return ServerCapabilities(
                audiences = setOf(Audience.Public, Audience.Unlisted, Audience.Followers, Audience.Direct),
                actions = actions,
                canPublish = true,
                profile = ProfileCapabilities(editable = editable.copy(imageDeletion = imageDeletion)),
                emoji = emoji,
                quotes = quotes,
                primaryFavourite = PrimaryFavouriteCapability(CapabilityStatus.Supported, PrimaryFavouriteMode.Native),
                savedPosts = SavedPostsCapability(CapabilityStatus.Supported, SavedPostsKind.Bookmarks),
                likedPosts = CapabilityStatus.Supported,
                threads = CapabilityStatus.Supported,
                moderation = ModerationCapabilities(
                    read = CapabilityStatus.Supported,
                    write = CapabilityStatus.Supported,
                    blocked = CapabilityStatus.Supported,
                    muted = CapabilityStatus.Supported,
                    hashtags = CapabilityStatus.Unsupported,
                ),
                capabilitiesLastUpdated = System.currentTimeMillis(),
                capabilitySchemaVersion = ServerCapabilities.CURRENT_CAPABILITY_SCHEMA_VERSION,
            ).withTimelineStatuses(
                mapOf(
                    Timeline.Home to CapabilityStatus.Supported,
                    Timeline.Local to CapabilityStatus.Supported,
                    Timeline.Social to CapabilityStatus.Unsupported,
                    Timeline.Bubble to CapabilityStatus.Unsupported,
                    Timeline.Federated to CapabilityStatus.Supported,
                ),
            )
        }

        private fun editableForMachineApi(api: Int): EditableProfileCapabilities = EditableProfileCapabilities(
            read = if (api >= 8) CapabilityStatus.Supported else CapabilityStatus.Unsupported,
            update = if (api >= 8) CapabilityStatus.Supported else CapabilityStatus.Unsupported,
            advancedSettings = if (api >= 8) CapabilityStatus.Supported else CapabilityStatus.Unsupported,
            imageDescriptions = if (api >= 9) CapabilityStatus.Supported else CapabilityStatus.Unsupported,
            imageUpload = if (api >= 8) CapabilityStatus.Supported else CapabilityStatus.Unsupported,
            imageDeletion = CapabilityStatus.Supported,
        )

        private fun editableForReleaseVersion(version: VersionTriple): EditableProfileCapabilities {
            val api8 = if (version.atLeast(4, 6, 0)) CapabilityStatus.Supported else CapabilityStatus.Unsupported
            return EditableProfileCapabilities(
                read = api8,
                update = api8,
                advancedSettings = api8,
                imageUpload = api8,
                imageDeletion = if (version.atLeast(4, 2, 0)) {
                    CapabilityStatus.Supported
                } else {
                    CapabilityStatus.Unsupported
                },
            )
        }

        /**
         * Reaction capabilities come from the advertisement alone.
         *
         * A recognized instance advertisement or a recognized NodeInfo advertisement proves
         * listing and mutation support at the server level with independent selection. A
         * missing or malformed advertisement stays Unknown: absence is not a verified
         * endpoint rejection, and it must never masquerade as proof of support.
         */
        private fun emojiCapabilities(instance: JSONObject, nodeInfoAdvertised: Boolean): EmojiCapabilities {
            val advertised = hasVerifiedEmojiReactionMetadata(instance) || nodeInfoAdvertised
            return EmojiCapabilities(
                catalog = CapabilityStatus.Supported,
                reactionListing = if (advertised) CapabilityStatus.Supported else CapabilityStatus.Unknown,
                reactionMutation = if (advertised) CapabilityStatus.Supported else CapabilityStatus.Unknown,
                selectionMode = if (advertised) ReactionSelectionMode.Independent else ReactionSelectionMode.Unknown,
            )
        }

        /**
         * Chooses at most one same-origin NodeInfo document from the well-known links.
         * NodeInfo 2.1 is preferred. A foreign, credentialed, or fragmented URL is rejected
         * before any request.
         */
        private fun supportedNodeInfoUrl(origin: String, wellKnown: JSONObject): String? {
            val links = wellKnown.optJSONArray("links") ?: return null
            val candidates = (0 until links.length()).mapNotNull { index ->
                val link = links.optJSONObject(index) ?: return@mapNotNull null
                val href = link.optString("href")
                if (href.isBlank()) null else link.optString("rel") to href
            }
            val chosen = candidates.firstOrNull { it.first.endsWith("/2.1") }?.second
                ?: candidates.firstOrNull { it.first.endsWith("/2.0") }?.second
                ?: return null
            return sameOriginUrl(origin, chosen)
        }

        /**
         * Returns the normalized URL only when it is same-origin with the connection origin.
         *
         * The connection origin is a validated HTTPS origin without credentials, a path, a
         * query, or a fragment. Matching the candidate scheme to the origin scheme therefore
         * keeps discovery HTTPS-only in production. This function still rejects foreign
         * hosts, credentialed URLs, and fragments explicitly.
         */
        private fun sameOriginUrl(origin: String, candidate: String): String? {
            val base = runCatching { URI(origin) }.getOrNull() ?: return null
            val target = runCatching { URI(candidate) }.getOrNull() ?: return null
            if (!target.scheme.equals(base.scheme, ignoreCase = true)) return null
            if (target.host.isNullOrBlank()) return null
            if (target.userInfo != null) return null
            if (target.fragment != null) return null
            if (!target.host.equals(base.host, ignoreCase = true)) return null
            if (effectivePort(target) != effectivePort(base)) return null
            return target.toASCIIString()
        }

        private fun effectivePort(uri: URI): Int = when {
            uri.port != -1 -> uri.port
            uri.scheme.equals("https", ignoreCase = true) -> 443
            uri.scheme.equals("http", ignoreCase = true) -> 80
            else -> -1
        }

        private val EMOJI_REACTION_FEATURE = "pleroma_emoji_reactions"

        private val LEADING_VERSION = Regex("^(\\d+)\\.(\\d+)(?:\\.(\\d+))?")
    }
}
