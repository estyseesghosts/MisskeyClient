package me.foxtails.palustris.domain

/** Supplied by an adapter and refreshed as the server's feature set changes. */
data class ServerCapabilities(
    val timelines: Set<Timeline> = emptySet(),
    val audiences: Set<Audience> = emptySet(),
    val actions: Set<PostAction> = emptySet(),
    val maxPostLength: Int? = null,
    val canPublish: Boolean = false,
    val notifications: NotificationCapabilities = NotificationCapabilities(),
    val capabilitiesLastUpdated: Long = 0,
)

enum class CapabilityStatus { Supported, Denied, Unsupported, TemporarilyUnavailable, Unknown }

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
