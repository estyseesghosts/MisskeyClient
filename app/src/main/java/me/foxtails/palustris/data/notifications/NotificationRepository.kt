package me.foxtails.palustris.data.notifications

import javax.inject.Inject
import javax.inject.Singleton
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import me.foxtails.palustris.di.IoDispatcher
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.EntityId
import me.foxtails.palustris.domain.Notification
import me.foxtails.palustris.domain.NotificationAcknowledgement
import me.foxtails.palustris.domain.matchesCategory
import me.foxtails.palustris.domain.NotificationCheckpoint
import me.foxtails.palustris.domain.NotificationDeliveryRecord
import me.foxtails.palustris.domain.NotificationDeliveryState
import me.foxtails.palustris.domain.Event
import me.foxtails.palustris.domain.SocialEvent
import me.foxtails.palustris.domain.NotificationPage
import me.foxtails.palustris.domain.NotificationPageDirection
import me.foxtails.palustris.domain.NotificationQuery
import me.foxtails.palustris.domain.NotificationReadStatus
import me.foxtails.palustris.domain.NotificationSettings
import me.foxtails.palustris.domain.PushRegistration
import me.foxtails.palustris.domain.NotificationSyncCompleteness
import me.foxtails.palustris.domain.NotificationSyncToken
import me.foxtails.palustris.domain.NotificationUnreadState

/**
 * One account-scoped merge point for REST pages, cache state, local visibility, and unread knowledge.
 * Writes require the source generation that produced them, so late requests cannot recreate removed state.
 */
@Singleton
class NotificationRepository @Inject constructor(
    private val store: NotificationStore,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    constructor() : this(InMemoryNotificationStore())

    private val states = mutableMapOf<AccountId, MutableStateFlow<NotificationRepositoryState>>()
    private val storageHealth = mutableMapOf<AccountId, MutableStateFlow<NotificationStorageHealth>>()
    private val generations = mutableMapOf<AccountId, Long>()
    private val writeLocks = mutableMapOf<AccountId, Mutex>()

    @Synchronized
    fun observe(accountId: AccountId): StateFlow<NotificationRepositoryState> =
        stateForLocked(accountId).asStateFlow()

    /**
     * Health is account-local. A corrupt or unavailable account receives a stable failure that
     * blocks writes until [retry] reloads a readable state or finds no state. Other accounts are
     * not affected.
     */
    @Synchronized
    fun observeStorageHealth(accountId: AccountId): StateFlow<NotificationStorageHealth> {
        // Materialize the state entry so the first health read also classifies the stored value.
        stateForLocked(accountId)
        return healthForLocked(accountId).asStateFlow()
    }

    fun observeInbox(accountId: AccountId, query: NotificationQuery): Flow<NotificationInboxSnapshot> =
        observe(accountId).map { state ->
            val checkpoint = validatedCheckpoint(state, accountId, query)
            NotificationInboxSnapshot(
                items = state.items.filter { it.matches(query) },
                unreadState = state.unreadState,
                checkpoint = checkpoint,
                lastSyncedAtEpochMillis = state.lastSyncedAtEpochMillis,
                hasIncompleteSync = checkpoint?.completeness == NotificationSyncCompleteness.Incomplete ||
                    checkpoint?.completeness == NotificationSyncCompleteness.Gap,
            )
        }

    fun observeUnread(accountId: AccountId): Flow<NotificationUnreadState> =
        observe(accountId).map { it.unreadState }

    @Synchronized
    fun checkpoint(accountId: AccountId, query: NotificationQuery): NotificationCheckpoint? {
        val state = stateForLocked(accountId).value
        return validatedCheckpoint(state, accountId, query)
    }

    /**
     * Restored entries are validated before use. A map entry under one query must not
     * supply a checkpoint that carries another query or another account. Invalid entries
     * are ignored without changing the persisted format.
     */
    private fun validatedCheckpoint(
        state: NotificationRepositoryState,
        accountId: AccountId,
        query: NotificationQuery,
    ): NotificationCheckpoint? =
        state.checkpoints[query.stableKey]
            ?.takeIf { it.query == query && it.accountId == accountId }
            ?: state.checkpoint?.takeIf { it.query == query && it.accountId == accountId }

    @Synchronized
    fun activate(token: NotificationSyncToken) {
        val current = generations[token.accountId]
        if (current == null || token.generation >= current) generations[token.accountId] = token.generation
        stateForLocked(token.accountId)
    }

    @Synchronized
    fun currentToken(accountId: AccountId): NotificationSyncToken? = generations[accountId]?.let {
        NotificationSyncToken(accountId, it)
    }

    /** Rehydrates a worker token only when the durable registration still belongs to this session. */
    @Synchronized
    fun recoverToken(accountId: AccountId, sessionRevision: Long): NotificationSyncToken? {
        val registration = stateForLocked(accountId).value.pushRegistration
        if (registration != null && registration.sessionRevision != 0L &&
            registration.sessionRevision != sessionRevision
        ) return null
        val generation = maxOf(
            generations[accountId] ?: 0L,
            registration?.generation ?: 0L,
            sessionRevision,
        )
        val token = NotificationSyncToken(accountId, generation)
        activate(token)
        return token
    }

    @Synchronized
    fun invalidate(accountId: AccountId, generation: Long) {
        val next = maxOf(generations[accountId] ?: 0L, generation + 1L)
        generations[accountId] = next
    }

    /**
     * Reloads persisted state for one account after a recoverable read failure.
     *
     * A healthy account returns immediately, so a normal refresh never replaces committed
     * in-memory state. A blocked account re-reads storage and replaces its empty in-memory state
     * only after a successful load. The original bytes stay untouched on another failure. This
     * path issues no side effects and no network work. The read uses the same synchronous store
     * boundary as [observe].
     */
    fun retry(accountId: AccountId): Boolean {
        if (!synchronized(this) { storageBlockedLocked(accountId) }) return true
        val read = store.read(accountId)
        return synchronized(this) {
            val health = read.toStorageHealth()
            if (health == NotificationStorageHealth.Healthy) {
                stateForLocked(accountId).value =
                    (read as? NotificationStoreRead.Readable)?.state ?: NotificationRepositoryState()
            }
            healthForLocked(accountId).value = health
            health == NotificationStorageHealth.Healthy
        }
    }

    /**
     * Discards one account's local notification state and returns `true` on success.
     *
     * The approved user-visible policy is development-only discard. Unreadable or newer-format
     * state is removed instead of migrated. The account generation advances first, so a late
     * writer from the old state cannot recreate it. Only notification-local state changes:
     * authentication secrets and other accounts are never touched.
     *
     * Writing an empty readable state serves three purposes. It prevents the legacy file importer
     * from re-importing old data, it clears stored settings, dismissals, and delivery history, and
     * it lets the next page run as a baseline without alerts. A failed write leaves the account
     * blocked so a later reset can retry.
     */
    @Synchronized
    fun reset(accountId: AccountId): Boolean {
        invalidate(accountId, generations[accountId] ?: 0L)
        return try {
            val empty = NotificationRepositoryState()
            store.write(accountId, empty)
            states.getOrPut(accountId) { MutableStateFlow(empty) }.value = empty
            healthForLocked(accountId).value = NotificationStorageHealth.Healthy
            true
        } catch (error: Exception) {
            healthForLocked(accountId).value = NotificationStorageHealth.Unavailable
            false
        }
    }

    suspend fun establishBaseline(
        token: NotificationSyncToken,
        request: NotificationIngestRequest,
        page: NotificationPage,
    ): Boolean = applyPage(token, request, page, baselineEstablished = true)

    suspend fun ingestNewerPage(
        token: NotificationSyncToken,
        request: NotificationIngestRequest,
        page: NotificationPage,
    ): Boolean = applyPage(token, request, page)

    suspend fun ingestOlderPage(
        token: NotificationSyncToken,
        request: NotificationIngestRequest,
        page: NotificationPage,
    ): Boolean = applyPage(token, request, page)

    private suspend fun applyPage(
        token: NotificationSyncToken,
        request: NotificationIngestRequest,
        page: NotificationPage,
        baselineEstablished: Boolean = false,
    ): Boolean {
        val direction = request.direction
        return commitWrite(token) { state ->
            if (page.direction != direction) return@commitWrite null
            if (page.checkpoint?.accountId?.let { it != token.accountId } == true ||
                page.items.any {
                    it.accountId != token.accountId ||
                        it.id.connection != token.accountId.connection.origin
                }
            ) return@commitWrite null
            // The caller query owns validation. A claimed checkpoint query must equal it,
            // even for empty pages. Checkpoint-free pages are accepted only under the
            // explicit requested query, never a borrowed one.
            if (page.checkpoint?.query?.let { it != request.query } == true) return@commitWrite null
            val query = request.query
            val previousCheckpoint = validatedCheckpoint(state, token.accountId, query)
            if (!checkBoundaryLocked(request, previousCheckpoint)) return@commitWrite null
            val previous = state.items.associateBy(Notification::id)
            val incoming = page.items
            val merged = (incoming + state.items)
                .distinctBy(Notification::id)
                .map { item -> item.mergeReadState(previous[item.id]?.readState) }
                .filterNot { it.id in state.dismissedIds }
                .sortedWith(compareByDescending<Notification> { it.createdAtEpochMillis }.thenByDescending { it.id.value })
                .take(MAX_ITEMS)
            val pageCheckpoint = page.checkpoint
            val nextCheckpoint = mergeNotificationCheckpoint(
                previous = previousCheckpoint,
                page = page,
                pageCheckpoint = pageCheckpoint,
                accountId = token.accountId,
                query = query,
                direction = direction,
                baselineEstablished = baselineEstablished,
            )
            val checkpoints = state.checkpoints + (query.stableKey to nextCheckpoint)
            state.copy(
                items = merged,
                unreadState = page.unreadState.takeIf { it !is NotificationUnreadState.Unknown } ?: state.unreadState,
                checkpoint = if (query.isAll) nextCheckpoint else state.checkpoint,
                checkpoints = checkpoints,
                lastSyncedAtEpochMillis = nextCheckpoint.capturedAtEpochMillis.takeIf { it > 0 }
                    ?: state.lastSyncedAtEpochMillis,
                deliveries = updateNotificationDeliveryOutbox(
                    state,
                    incoming,
                    previousCheckpoint,
                    baselineEstablished,
                    direction,
                ),
            ) to Unit
        } != null
    }

    /**
     * Rejects a same-query page whose input boundary already moved. Older and newer
     * boundaries stay independent: an older page never validates against the newer
     * boundary and conversely. Baselines carry no input boundary.
     */
    private fun checkBoundaryLocked(
        request: NotificationIngestRequest,
        previous: NotificationCheckpoint?,
    ): Boolean {
        val expected = request.expectedContinuation ?: return true
        val current = when (request.direction) {
            NotificationPageDirection.Newer -> previous?.newerContinuation
            NotificationPageDirection.Older -> previous?.olderContinuation ?: previous?.oldest
            NotificationPageDirection.Initial -> return true
        }
        return current == expected
    }

    suspend fun updateUnreadState(token: NotificationSyncToken, unreadState: NotificationUnreadState): Boolean =
        commitWrite(token) { state ->
            state.copy(unreadState = unreadState) to Unit
        } != null

    suspend fun applyStreamEvent(token: NotificationSyncToken, event: Event): Boolean {
        if (event.accountId != token.accountId) return false
        return commitWrite(token) { current ->
            val updated = when (val payload = event.payload) {
                is SocialEvent.NotificationReceived -> {
                    val incoming = payload.notification
                    if (incoming.accountId != token.accountId || incoming.id.connection != token.accountId.connection.origin) {
                        current
                    } else {
                        val previous = current.items.associateBy(Notification::id)
                        val merged = (listOf(incoming) + current.items)
                            .distinctBy(Notification::id)
                            .map { item -> item.mergeReadState(previous[item.id]?.readState) }
                            .filterNot { it.id in current.dismissedIds }
                            .sortedWith(compareByDescending<Notification> { it.createdAtEpochMillis }.thenByDescending { it.id.value })
                            .take(MAX_ITEMS)
                        val deliveries = if (current.checkpoints.values.any { it.baselineEstablished } &&
                            incoming.id !in current.deliveries && incoming.id !in current.dismissedIds
                        ) {
                            current.deliveries + (incoming.id to NotificationDeliveryRecord(
                                accountId = incoming.accountId,
                                notificationId = incoming.id,
                                androidTag = AndroidNotificationIds.tag(incoming.accountId),
                                androidId = AndroidNotificationIds.id(incoming.id),
                            ))
                        } else current.deliveries
                        current.copy(items = merged, deliveries = deliveries)
                    }
                }
                is SocialEvent.NotificationReadChanged -> {
                    current.copy(
                        items = current.items.map { item -> item.copy(readState = item.readState.copy(
                            status = payload.state.status,
                            serverAcknowledged = payload.state.serverAcknowledged || item.readState.serverAcknowledged,
                        )) },
                        unreadState = if (payload.state.status == NotificationReadStatus.Read) NotificationUnreadState.None else current.unreadState,
                    )
                }
                else -> current
            }
            updated to Unit
        } != null
    }

    suspend fun markLocallySeen(token: NotificationSyncToken, ids: Set<EntityId>): Boolean =
        commitWrite(token) { current ->
            current.copy(items = current.items.map { item ->
                if (item.id in ids) item.copy(readState = item.readState.copy(locallySeen = true)) else item
            }) to Unit
        } != null

    suspend fun markSeen(token: NotificationSyncToken, id: EntityId? = null): Boolean =
        commitWrite(token) { current ->
            val items = current.items.map { item ->
                if (id == null || item.id == id) item.copy(readState = item.readState.copy(locallySeen = true)) else item
            }
            current.copy(items = items) to Unit
        } != null

    suspend fun markPresented(token: NotificationSyncToken, id: EntityId): Boolean =
        commitWrite(token) { current ->
            val items = current.items.map { item ->
                if (item.id == id) item.copy(readState = item.readState.copy(androidPresented = true)) else item
            }
            current.copy(items = items) to Unit
        } != null

    /**
     * Records only the Android surface dismissal; it never acknowledges the source notification.
     *
     * The dismissal publishes only after the store accepts it, so a failed write stays
     * retryable without ever becoming server acknowledgement.
     */
    suspend fun markAndroidDismissed(accountId: AccountId, id: EntityId): Boolean {
        val lock = lockFor(accountId)
        return lock.withLock {
            val next = synchronized(this@NotificationRepository) {
                val state = states[accountId]?.value ?: return@withLock false
                if (healthForLocked(accountId).value != NotificationStorageHealth.Healthy) return@withLock false
                if (state.items.none { it.id == id }) return@withLock false
                state.copy(items = state.items.map { item ->
                    if (item.id == id) item.copy(readState = item.readState.copy(androidDismissed = true)) else item
                })
            }
            try {
                withContext(ioDispatcher) { store.write(accountId, next) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                synchronized(this@NotificationRepository) {
                    healthForLocked(accountId).value = NotificationStorageHealth.Unavailable
                }
                return@withLock false
            }
            return@withLock synchronized(this@NotificationRepository) {
                val holder = states[accountId] ?: return@synchronized false
                if (healthForLocked(accountId).value != NotificationStorageHealth.Healthy) return@synchronized false
                holder.value = next
                true
            }
        }
    }

    suspend fun acknowledge(
        token: NotificationSyncToken,
        acknowledgement: NotificationAcknowledgement,
    ): Boolean {
        // A remote acknowledgement followed by a local write failure needs reconciliation,
        // not blind command repetition. A false return leaves the remote result untouched and
        // surfaces the storage failure, so the next sync re-reads server state after retry.
        return commitWrite(token) { current ->
            if (acknowledgement.accountId != token.accountId) return@commitWrite null
            val items = when (acknowledgement.readState) {
                NotificationUnreadState.None,
                is NotificationUnreadState.Exact,
                -> current.items.map { item ->
                    item.copy(readState = item.readState.copy(
                        status = NotificationReadStatus.Read,
                        serverAcknowledged = true,
                    ))
                }
                else -> current.items.map { item ->
                    item.copy(readState = item.readState.copy(serverAcknowledged = true))
                }
            }
            current.copy(items = items, unreadState = acknowledgement.readState) to Unit
        } != null
    }

    suspend fun applyAcknowledgement(
        token: NotificationSyncToken,
        acknowledgement: NotificationAcknowledgement,
    ): Boolean = acknowledge(token, acknowledgement)

    suspend fun dismiss(token: NotificationSyncToken, id: EntityId): Boolean {
        return dismissFromInbox(token, id, remoteApplied = false)
    }

    suspend fun dismissFromInbox(token: NotificationSyncToken, id: EntityId, remoteApplied: Boolean): Boolean {
        return commitWrite(token) { current ->
            if (id.connection != token.accountId.connection.origin) return@commitWrite null
            current.copy(
                items = current.items.filterNot { it.id == id },
                dismissedIds = current.dismissedIds + id,
                deliveries = current.deliveries - id,
            ) to Unit
        } != null
    }

    /**
     * Claims one pending delivery only when the claim is durable. A null return means the
     * caller must not present the notification: either no claimable record exists or the
     * store rejected the write.
     */
    suspend fun claimDelivery(
        token: NotificationSyncToken,
        id: EntityId,
        nowEpochMillis: Long = System.currentTimeMillis(),
    ): NotificationDeliveryRecord? {
        return commitWrite(token) { current ->
            val record = current.deliveries[id] ?: return@commitWrite null
            if (!record.isClaimable(nowEpochMillis)) return@commitWrite null
            val claimed = record.claim(
                nowEpochMillis = nowEpochMillis,
                claimId = UUID.randomUUID().toString(),
                leaseMillis = DELIVERY_CLAIM_LEASE_MILLIS,
            )
            current.copy(deliveries = current.deliveries + (id to claimed)) to claimed
        }
    }

    suspend fun finishDelivery(
        token: NotificationSyncToken,
        id: EntityId,
        state: NotificationDeliveryState,
        errorCategory: String? = null,
        claimId: String? = null,
    ): Boolean {
        return commitWrite(token) { current ->
            val existing = current.deliveries[id] ?: return@commitWrite null
            if (claimId != null && existing.claimId != claimId) return@commitWrite null
            current.copy(deliveries = current.deliveries + (id to existing.finish(state, errorCategory))) to Unit
        } != null
    }

    fun pendingDeliveries(
        accountId: AccountId,
        nowEpochMillis: Long = System.currentTimeMillis(),
    ): List<NotificationDeliveryRecord> {
        synchronized(this) { if (storageBlockedLocked(accountId)) return emptyList() }
        return observe(accountId).value.deliveries.values.filter {
            it.state == NotificationDeliveryState.Pending || it.state == NotificationDeliveryState.Failed ||
                (it.state == NotificationDeliveryState.Posting && it.claimExpiresAtEpochMillis <= nowEpochMillis)
        }
    }

    @Synchronized
    fun settings(accountId: AccountId): NotificationSettings = observe(accountId).value.settings

    /** A blocked account exposes no registration, so push callbacks route nothing until recovery. */
    @Synchronized
    fun pushRegistration(accountId: AccountId): PushRegistration? =
        if (storageBlockedLocked(accountId)) null else observe(accountId).value.pushRegistration

    suspend fun updateSettings(token: NotificationSyncToken, settings: NotificationSettings): Boolean =
        commitWrite(token) { state ->
            state.copy(settings = settings) to Unit
        } != null

    suspend fun updatePushRegistration(token: NotificationSyncToken, registration: PushRegistration): Boolean {
        // A false return covers both a revoked writer and a rejected store write. The caller
        // must not repeat the remote registration blindly; it re-reads server state after retry.
        return commitWrite(token) { state ->
            if (registration.accountId != token.accountId ||
                registration.generation != token.generation
            ) return@commitWrite null
            state.copy(pushRegistration = registration) to Unit
        } != null
    }

    suspend fun clearPushRegistration(token: NotificationSyncToken): Boolean =
        commitWrite(token) { state ->
            state.copy(pushRegistration = null) to Unit
        } != null

    /**
     * Drops one account's in-memory state and deletes its rows. Row deletion is best effort:
     * revocation and memory removal are authoritative, so a disk failure never blocks local
     * removal or resurrects the account. Callers revoke writers before this call.
     */
    @Synchronized
    fun remove(accountId: AccountId) {
        states.remove(accountId)
        storageHealth.remove(accountId)
        generations.remove(accountId)
        try {
            store.delete(accountId)
        } catch (error: Exception) {
            // Best effort. Memory already revoked the account.
        }
    }

    /**
     * Durable acceptance boundary for every notification-local mutation.
     *
     * The transition computes from committed state, writes to the store on IO, and publishes
     * only after the write succeeds while the writer is still current. A failed write marks
     * the account unavailable, publishes nothing, and returns null, so no failed operation
     * is ever described as durably completed. Transitions for one account serialize on its
     * own lock, so a failure never rolls back a newer accepted change and unrelated
     * accounts never wait on each other. Cancellation propagates without marking health.
     */
    private suspend fun <T> commitWrite(
        token: NotificationSyncToken,
        compute: (NotificationRepositoryState) -> Pair<NotificationRepositoryState, T>?,
    ): T? {
        val accountId = token.accountId
        val lock = lockFor(accountId)
        return lock.withLock {
            val prepared = synchronized(this@NotificationRepository) {
                if (!mutationAllowedLocked(token)) return@withLock null
                compute(stateForLocked(accountId).value)
            } ?: return@withLock null
            val (next, result) = prepared
            if (!synchronized(this@NotificationRepository) { isCurrentLocked(token) }) return@withLock null
            try {
                withContext(ioDispatcher) { store.write(accountId, next) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                synchronized(this@NotificationRepository) {
                    healthForLocked(accountId).value = NotificationStorageHealth.Unavailable
                }
                return@withLock null
            }
            synchronized(this@NotificationRepository) {
                if (!isCurrentLocked(token) || storageBlockedLocked(accountId)) return@synchronized null
                stateForLocked(accountId).value = next
                result
            }
        }
    }

    private fun lockFor(accountId: AccountId): Mutex =
        synchronized(this) { writeLocks.getOrPut(accountId) { Mutex() } }

    private fun stateForLocked(accountId: AccountId): MutableStateFlow<NotificationRepositoryState> =
        states.getOrPut(accountId) { MutableStateFlow(loadStateLocked(accountId)) }

    private fun healthForLocked(accountId: AccountId): MutableStateFlow<NotificationStorageHealth> =
        storageHealth.getOrPut(accountId) { MutableStateFlow(NotificationStorageHealth.Healthy) }

    private fun mutationAllowedLocked(token: NotificationSyncToken): Boolean =
        isCurrentLocked(token) && !storageBlockedLocked(token.accountId)

    private fun storageBlockedLocked(accountId: AccountId): Boolean {
        // Materialize the state entry first so a first-touch mutation classifies the stored value.
        stateForLocked(accountId)
        return healthForLocked(accountId).value != NotificationStorageHealth.Healthy
    }

    /**
     * Reads one account and records its health. Absent and readable storage are both healthy. A
     * corrupt or unavailable read yields an empty in-memory state that mutations must not persist.
     */
    private fun loadStateLocked(accountId: AccountId): NotificationRepositoryState {
        val read = store.read(accountId)
        healthForLocked(accountId).value = read.toStorageHealth()
        return (read as? NotificationStoreRead.Readable)?.state ?: NotificationRepositoryState()
    }

    private fun isCurrentLocked(token: NotificationSyncToken): Boolean =
        (token.generation == 0L && token.accountId !in generations) || generations[token.accountId] == token.generation

    private companion object {
        const val MAX_ITEMS = 500
        const val DELIVERY_CLAIM_LEASE_MILLIS = 2 * 60 * 1000L
    }
}

internal fun AccountId.stableFileName(): String {
    val bytes = "$connection\u0000$localId".toByteArray(Charsets.UTF_8)
    return MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}

private fun Notification.matches(query: NotificationQuery): Boolean {
    if (query.isAll) return true
    return query.categories.any(activity::matchesCategory)
}

internal fun stableNotificationId(id: EntityId): Int = (id.connection + "\u0000" + id.value).hashCode()
