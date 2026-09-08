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
    val capabilitiesLastUpdated: Long = 0,
)

enum class CapabilityStatus { Supported, Denied, Unsupported, TemporarilyUnavailable, Unknown }

data class ProfileCapabilities(
    val details: CapabilityStatus = CapabilityStatus.Unknown,
    val timelines: CapabilityStatus = CapabilityStatus.Unknown,
    val relationships: CapabilityStatus = CapabilityStatus.Unknown,
    val followActions: CapabilityStatus = CapabilityStatus.Unknown,
    val pinnedPosts: CapabilityStatus = CapabilityStatus.Unknown,
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
