package me.foxtails.palustris.ui.posts

import javax.inject.Inject
import javax.inject.Singleton
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.EntityId

/**
 * Session-bound execution authority for post interaction families.
 *
 * Per-feature job maps cannot serialize the same target's reaction family across Home,
 * Profile, collections, and Thread. This gate is shared across surfaces: the second
 * caller for one family and effective target waits while the first owns the slot.
 * Slots are keyed by account and session revision, so replacement sessions never block
 * on stale owners. Feature owners keep their own rows, overlays, and membership.
 */
@Singleton
class PostInteractionExecutionAuthority @Inject constructor() {
    private data class Slot(
        val accountId: AccountId,
        val sessionRevision: Long,
        val family: String,
        val target: EntityId,
    )

    private val slots = mutableSetOf<Slot>()

    /** Reserves the family slot. Returns false when another surface owns it. */
    @Synchronized
    fun acquire(accountId: AccountId, sessionRevision: Long, family: String, target: EntityId): Boolean {
        val slot = Slot(accountId, sessionRevision, family, target)
        if (slot in slots) return false
        slots += slot
        return true
    }

    @Synchronized
    fun release(accountId: AccountId, sessionRevision: Long, family: String, target: EntityId) {
        slots -= Slot(accountId, sessionRevision, family, target)
    }
}
