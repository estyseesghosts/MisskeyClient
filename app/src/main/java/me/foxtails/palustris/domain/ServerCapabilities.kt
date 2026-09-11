package me.foxtails.palustris.domain

/** Supplied by an adapter and refreshed as the server's feature set changes. */
data class ServerCapabilities(
    val timelines: Set<Timeline> = emptySet(),
    val audiences: Set<Audience> = emptySet(),
    val actions: Set<PostAction> = emptySet(),
    val maxPostLength: Int? = null,
    val canPublish: Boolean = false,
    val notifications: NotificationCapabilities = NotificationCapabilities(),
    val profile: ProfileCapabilities = ProfileCapabilities(),
    val emoji: EmojiCapabilities = EmojiCapabilities(),
    val capabilitiesLastUpdated: Long = 0,
    val quotes: CapabilityStatus = CapabilityStatus.Unknown,
    val primaryFavourite: PrimaryFavouriteCapability = PrimaryFavouriteCapability(),
    val savedPosts: SavedPostsCapability? = null,
    val likedPosts: CapabilityStatus = CapabilityStatus.Unknown,
    /** Bumps when capability shapes change; older snapshots force a fresh probe. */
    val capabilitySchemaVersion: Int = CURRENT_CAPABILITY_SCHEMA_VERSION,
) {
    companion object {
        const val CURRENT_CAPABILITY_SCHEMA_VERSION = 2
    }
}

enum class CapabilityStatus { Supported, Denied, Unsupported, TemporarilyUnavailable, Unknown }

data class ProfileCapabilities(
    val details: CapabilityStatus = CapabilityStatus.Unknown,
    val timelines: CapabilityStatus = CapabilityStatus.Unknown,
    val relationships: CapabilityStatus = CapabilityStatus.Unknown,
    val followActions: CapabilityStatus = CapabilityStatus.Unknown,
    val pinnedPosts: CapabilityStatus = CapabilityStatus.Unknown,
    val editable: EditableProfileCapabilities = EditableProfileCapabilities(),
)

data class EditableProfileCapabilities(
    val read: CapabilityStatus = CapabilityStatus.Unknown,
    val update: CapabilityStatus = CapabilityStatus.Unknown,
    val advancedSettings: CapabilityStatus = CapabilityStatus.Unknown,
    val imageDescriptions: CapabilityStatus = CapabilityStatus.Unknown,
    val imageUpload: CapabilityStatus = CapabilityStatus.Unknown,
    val imageDeletion: CapabilityStatus = CapabilityStatus.Unknown,
)

enum class ReactionSelectionMode { Single, Independent, Unknown }

data class EmojiCapabilities(
    val catalog: CapabilityStatus = CapabilityStatus.Unknown,
    val reactionListing: CapabilityStatus = CapabilityStatus.Unknown,
    val reactionMutation: CapabilityStatus = CapabilityStatus.Unknown,
    val selectionMode: ReactionSelectionMode = ReactionSelectionMode.Unknown,
)

enum class NotificationReadSemantics { PerNotification, AccountWide, TimelineMarker, Unknown }

enum class NotificationUnreadPrecision { Exact, LowerBound, Boolean, Unknown }

enum class NotificationCategory { All, Mentions, Replies, Quotes, Social, Polls, System }

data class NotificationCapabilities(
    val listing: CapabilityStatus = CapabilityStatus.Unknown,
    val supportedCategories: Set<NotificationCategory> = emptySet(),
    val readSemantics: NotificationReadSemantics = NotificationReadSemantics.Unknown,
    val unreadCountPrecision: NotificationUnreadPrecision = NotificationUnreadPrecision.Unknown,
    val grouping: CapabilityStatus = CapabilityStatus.Unknown,
    val dismissal: CapabilityStatus = CapabilityStatus.Unknown,
    val policyManagement: CapabilityStatus = CapabilityStatus.Unknown,
    val followRequestActions: CapabilityStatus = CapabilityStatus.Unknown,
    val streaming: CapabilityStatus = CapabilityStatus.Unknown,
    val webPush: CapabilityStatus = CapabilityStatus.Unknown,
)

data class PushProviderInfo(
    val status: CapabilityStatus,
    val vapidPublicKey: String? = null,
)

/**
 * Resolves a feature's usable state without treating a failed request as proof of server absence.
 */
fun effectiveCapabilityStatus(
    server: CapabilityStatus,
    access: AccessStatus,
    implemented: Boolean,
): CapabilityStatus = when {
    server == CapabilityStatus.Unsupported -> CapabilityStatus.Unsupported
    access == AccessStatus.Denied || server == CapabilityStatus.Denied -> CapabilityStatus.Denied
    server == CapabilityStatus.TemporarilyUnavailable -> CapabilityStatus.TemporarilyUnavailable
    !implemented -> CapabilityStatus.Unsupported
    server == CapabilityStatus.Unknown || access == AccessStatus.Unknown -> CapabilityStatus.Unknown
    else -> CapabilityStatus.Supported
}
