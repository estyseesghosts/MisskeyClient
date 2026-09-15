package me.foxtails.palustris.ui.posts

import javax.inject.Inject
import javax.inject.Singleton
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.EntityId

/** One action family that shares an execution slot per effective target. */
enum class PostActionFamily {
    Favorite,
    FavoriteReaction,
    Reaction,
    Reshare,
    Bookmark,
    Reply,
}

/**
 * Session-bound execution authority for post interaction families.
 *
 * Per-feature job maps cannot serialize the same target's reaction family across Home,
 * Profile, collections, and Thread. This gate is shared across surfaces: a second caller for
 * one family and effective target is rejected while the first owns the slot. The caller does
 * not wait. Slots are keyed by account and session revision, so replacement sessions never
 * block on stale owners. Feature owners keep their own rows, overlays, and membership.
 *
 * Each accepted acquisition returns the token that owns the slot. Only that token releases
 * the slot. A foreign or repeated release changes nothing.
 */
@Singleton
class PostInteractionExecutionAuthority @Inject constructor() {
    /** Opaque ownership of one reserved family slot. Only this token releases the slot. */
    class OperationToken internal constructor(
        internal val accountId: AccountId,
        internal val sessionRevision: Long,
        internal val family: PostActionFamily,
        internal val target: EntityId,
    )

    private data class Slot(
        val accountId: AccountId,
        val sessionRevision: Long,
        val family: PostActionFamily,
        val target: EntityId,
    )

    private val slots = mutableSetOf<Slot>()

    /** Reserves the family slot. Returns null when another surface owns it. */
    @Synchronized
    fun acquire(
        accountId: AccountId,
        sessionRevision: Long,
        family: PostActionFamily,
        target: EntityId,
    ): OperationToken? {
        val slot = Slot(accountId, sessionRevision, family, target)
        if (slot in slots) return null
        slots += slot
        return OperationToken(accountId, sessionRevision, family, target)
    }

    /** Releases only the slot owned by [token]. A foreign or repeated release changes nothing. */
    @Synchronized
    fun release(token: OperationToken) {
        slots -= Slot(token.accountId, token.sessionRevision, token.family, token.target)
    }
}
