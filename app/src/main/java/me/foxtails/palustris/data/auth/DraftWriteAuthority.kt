package me.foxtails.palustris.data.auth

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import me.foxtails.palustris.domain.AccountId

/**
 * Owns durable write authority for draft rows, one generation per account.
 *
 * The account lifecycle activates a writer when a session connects and revokes it on removal.
 * A draft owner captures the active generation; it does not issue one. All state changes for
 * one account run under the same per-account lock: activation, revocation, deletion, and
 * accepted writes. A store commit checks the generation under that lock, so a revoked writer
 * cannot interleave a check-then-write and recreate a draft after removal.
 *
 * No lock is held during a network request. The generation is monotonic, so an activation waits
 * for any commit that is already running before it revokes the previous writer.
 */
@Singleton
class DraftWriteAuthority @Inject constructor() {
    private val generations = ConcurrentHashMap<AccountId, AtomicLong>()
    private val locks = ConcurrentHashMap<AccountId, Mutex>()

    /** Issues the writer generation for a session activation. Revokes the previous writer. */
    suspend fun activate(accountId: AccountId): Long {
        val lock = lockFor(accountId)
        return lock.withLock {
            generations.getOrPut(accountId) { AtomicLong(0L) }.incrementAndGet()
        }
    }

    fun isCurrent(accountId: AccountId, generation: Long): Boolean =
        (generations[accountId]?.get() ?: 0L) == generation

    /**
     * Runs [block] under the account lock when [generation] is still current.
     * Returns null when a removal revoked the writer first.
     */
    suspend fun <T> commitIfCurrent(accountId: AccountId, generation: Long, block: suspend () -> T): T? {
        val lock = lockFor(accountId)
        return lock.withLock {
            if (!isCurrent(accountId, generation)) null else block()
        }
    }

    /** Revokes writers without deleting rows. Serialized with accepted writes. */
    suspend fun invalidate(accountId: AccountId) {
        val lock = lockFor(accountId)
        lock.withLock {
            generations.getOrPut(accountId) { AtomicLong(0L) }.incrementAndGet()
        }
    }

    /** Revokes writers, then deletes rows in the same serialized boundary. */
    suspend fun invalidateAndDelete(accountId: AccountId, delete: suspend () -> Unit) {
        val lock = lockFor(accountId)
        lock.withLock {
            generations.getOrPut(accountId) { AtomicLong(0L) }.incrementAndGet()
            delete()
        }
    }

    private fun lockFor(accountId: AccountId): Mutex = locks.getOrPut(accountId) { Mutex() }
}
