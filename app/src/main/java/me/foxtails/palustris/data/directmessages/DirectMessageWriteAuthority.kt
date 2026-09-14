package me.foxtails.palustris.data.directmessages

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import me.foxtails.palustris.domain.AccountId

/**
 * Owns durable write authority for direct-message rows, one generation per account.
 *
 * A repository captures its generation when it starts writing. Account removal revokes
 * writers before deleting rows, and session replacement revokes old writers before new
 * writers activate. Either order is safe: a writer either commits before the revocation
 * and the later deletion still wins, or it observes the revocation and writes nothing.
 * Browsing between conversations or accounts never revokes, so valid cache updates
 * for a still-owned account keep working.
 *
 * The generation counter is lock-free so revocation can run anywhere, including the
 * main thread during session activation. Store commits take the per-account lock, so a
 * revocation followed by deletion cannot interleave with a stale check-then-write.
 * No lock is held during network requests.
 */
@Singleton
class DirectMessageWriteAuthority @Inject constructor() {
    private val generations = ConcurrentHashMap<AccountId, AtomicLong>()
    private val locks = ConcurrentHashMap<AccountId, Mutex>()

    /** Issues the writer generation for a newly activated writer. */
    fun issue(accountId: AccountId): Long =
        generations.getOrPut(accountId) { AtomicLong(0L) }.incrementAndGet()

    fun isCurrent(accountId: AccountId, generation: Long): Boolean =
        (generations[accountId]?.get() ?: 0L) == generation

    /**
     * Runs [block] under the account lock when [generation] is still current.
     * Returns null when a removal or session replacement revoked the writer first.
     */
    suspend fun <T> commitIfCurrent(accountId: AccountId, generation: Long, block: suspend () -> T): T? {
        val lock = locks.getOrPut(accountId) { Mutex() }
        return lock.withLock {
            if (!isCurrent(accountId, generation)) null else block()
        }
    }

    /** Revokes writers without deleting rows. Later commits from them write nothing. */
    fun invalidate(accountId: AccountId) {
        generations.getOrPut(accountId) { AtomicLong(0L) }.incrementAndGet()
    }

    /** Revokes writers, then deletes rows in the same serialized boundary. */
    suspend fun invalidateAndDelete(accountId: AccountId, delete: suspend () -> Unit) {
        invalidate(accountId)
        val lock = locks.getOrPut(accountId) { Mutex() }
        lock.withLock { delete() }
    }
}
