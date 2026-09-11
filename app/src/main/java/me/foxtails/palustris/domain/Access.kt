package me.foxtails.palustris.domain

/** Protocol-neutral access capabilities requested or observed for one account session. */
enum class AccessScope {
    NotificationsRead,
    NotificationsWrite,
    FollowRequests,
    Push,
    PrimaryFavouriteWrite,
    SavedPostsRead,
    SavedPostsWrite,
    LikedPostsRead,
}

enum class AccessStatus { Granted, Denied, Unknown }

data class AccessGrant(
    val requested: Set<AccessScope> = emptySet(),
    val known: Map<AccessScope, AccessStatus> = emptyMap(),
) {
    fun status(scope: AccessScope): AccessStatus = known[scope] ?: AccessStatus.Unknown
}
