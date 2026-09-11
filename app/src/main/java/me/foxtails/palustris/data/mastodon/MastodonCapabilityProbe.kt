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
import me.foxtails.palustris.domain.ReactionSelectionMode
import me.foxtails.palustris.domain.SavedPostsCapability
import me.foxtails.palustris.domain.SavedPostsKind
import me.foxtails.palustris.domain.ServerCapabilities
import me.foxtails.palustris.domain.Timeline
import org.json.JSONObject

/** Probes Mastodon instance metadata without making protocol details visible to the UI. */
class MastodonCapabilityProbe(private val api: MisskeyApi) : CapabilityProbe {
    enum class EmojiMutationProbeOutcome { Supported, Ambiguous, Failed, Unknown }

    data class VersionTriple(val major: Int, val minor: Int, val patch: Int) {
        fun atLeast(major: Int, minor: Int, patch: Int): Boolean = when {
            this.major != major -> this.major > major
            this.minor != minor -> this.minor > minor
            else -> this.patch >= patch
        }
    }

    override suspend fun probeCapabilities(connection: Connection): ServerCapabilities {
        val instance = JSONObject(api.get(connection.origin, "v2/instance").body)
        val mutationOutcome = if (hasVerifiedEmojiReactionMetadata(instance)) {
            probeEmojiReactionMutation(connection.origin)
        } else {
            EmojiMutationProbeOutcome.Unknown
        }
        return parseCapabilities(instance, mutationOutcome)
    }

    private suspend fun probeEmojiReactionMutation(origin: String): EmojiMutationProbeOutcome = try {
        api.get(origin, "v1/pleroma/statuses/1/reactions/$PROBE_EMOJI_ENCODED")
        EmojiMutationProbeOutcome.Supported
    } catch (e: CancellationException) {
        throw e
    } catch (e: ApiFailure) {
        EmojiMutationProbeOutcome.Ambiguous
    } catch (_: Exception) {
        EmojiMutationProbeOutcome.Failed
    }

    companion object {
        private const val PROBE_EMOJI_ENCODED = "%F0%9F%8E%89"

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

        /** The optional mutation probe ran successfully against the verified extension. */
        fun hasVerifiedEmojiReactionMetadata(instance: JSONObject): Boolean =
            instance.optJSONObject("pleroma")
                ?.optJSONObject("metadata")
                ?.optJSONArray("features")
                ?.let { features ->
                    (0 until features.length()).any { features.optString(it) == "pleroma_emoji_reactions" }
                }
                ?: false

        fun parseCapabilities(
            instance: JSONObject,
            emojiMutationProbe: EmojiMutationProbeOutcome = EmojiMutationProbeOutcome.Unknown,
        ): ServerCapabilities {
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
            val emoji = emojiCapabilities(instance, emojiMutationProbe)
            val actions = setOf(
                PostAction.Reply,
                PostAction.Reshare,
                PostAction.Favorite,
                PostAction.Bookmark,
            ) + if (emoji.reactionMutation == CapabilityStatus.Supported) setOf(PostAction.React) else emptySet()
            return ServerCapabilities(
                timelines = setOf(Timeline.Home, Timeline.Local, Timeline.Federated),
                audiences = setOf(Audience.Public, Audience.Unlisted, Audience.Followers, Audience.Direct),
                actions = actions,
                canPublish = true,
                profile = ProfileCapabilities(editable = editable.copy(imageDeletion = imageDeletion)),
                emoji = emoji,
                quotes = quotes,
                primaryFavourite = PrimaryFavouriteCapability(CapabilityStatus.Supported, PrimaryFavouriteMode.Native),
                savedPosts = SavedPostsCapability(CapabilityStatus.Supported, SavedPostsKind.Bookmarks),
                likedPosts = CapabilityStatus.Supported,
                capabilitiesLastUpdated = System.currentTimeMillis(),
                capabilitySchemaVersion = ServerCapabilities.CURRENT_CAPABILITY_SCHEMA_VERSION,
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

        private fun emojiCapabilities(
            instance: JSONObject,
            mutationProbe: EmojiMutationProbeOutcome,
        ): EmojiCapabilities {
            if (!hasVerifiedEmojiReactionMetadata(instance)) {
                return EmojiCapabilities(
                    catalog = CapabilityStatus.Supported,
                    reactionListing = CapabilityStatus.Unsupported,
                    reactionMutation = CapabilityStatus.Unsupported,
                    selectionMode = ReactionSelectionMode.Unknown,
                )
            }
            val mutation = when (mutationProbe) {
                EmojiMutationProbeOutcome.Supported -> CapabilityStatus.Supported
                EmojiMutationProbeOutcome.Failed, EmojiMutationProbeOutcome.Unknown -> CapabilityStatus.Unknown
                EmojiMutationProbeOutcome.Ambiguous -> CapabilityStatus.Unsupported
            }
            return EmojiCapabilities(
                catalog = CapabilityStatus.Supported,
                reactionListing = CapabilityStatus.Supported,
                reactionMutation = mutation,
                selectionMode = if (mutation == CapabilityStatus.Supported) {
                    ReactionSelectionMode.Independent
                } else {
                    ReactionSelectionMode.Unknown
                },
            )
        }

        private val LEADING_VERSION = Regex("^(\\d+)\\.(\\d+)(?:\\.(\\d+))?")
    }
}
