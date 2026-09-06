package me.foxtails.palustris.domain

/** Supplied by an adapter and refreshed as the server's feature set changes. */
data class ServerCapabilities(
    val timelines: Set<Timeline> = emptySet(),
    val audiences: Set<Audience> = emptySet(),
    val actions: Set<PostAction> = emptySet(),
    val maxPostLength: Int? = null,
    val canPublish: Boolean = false,
    val capabilitiesLastUpdated: Long = 0,
)
