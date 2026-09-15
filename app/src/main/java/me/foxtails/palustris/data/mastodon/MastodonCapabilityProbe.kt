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
        return parseCapabilities(instance)
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

    companion object {
        /** Bounds the instance metadata read. The document is small on supported servers. */
        private const val MAX_INSTANCE_BYTES = 512 * 1024L

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
                    (0 until features.length()).any { features.optString(it) == "pleroma_emoji_reactions" }
                }
                ?: false

        fun parseCapabilities(instance: JSONObject): ServerCapabilities {
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
            val emoji = emojiCapabilities(instance)
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
         * A recognized advertisement proves listing and mutation support at the server
         * level with independent selection. A missing or malformed advertisement stays
         * Unknown: absence is not a verified endpoint rejection, and it must never
         * masquerade as proof of support.
         */
        private fun emojiCapabilities(instance: JSONObject): EmojiCapabilities {
            val advertised = hasVerifiedEmojiReactionMetadata(instance)
            return EmojiCapabilities(
                catalog = CapabilityStatus.Supported,
                reactionListing = if (advertised) CapabilityStatus.Supported else CapabilityStatus.Unknown,
                reactionMutation = if (advertised) CapabilityStatus.Supported else CapabilityStatus.Unknown,
                selectionMode = if (advertised) ReactionSelectionMode.Independent else ReactionSelectionMode.Unknown,
            )
        }

        private val LEADING_VERSION = Regex("^(\\d+)\\.(\\d+)(?:\\.(\\d+))?")
    }
}
