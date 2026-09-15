package me.foxtails.palustris.data.notifications

import javax.inject.Inject
import javax.inject.Singleton
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
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
) {
    constructor() : this(InMemoryNotificationStore())

    private val states = mutableMapOf<AccountId, MutableStateFlow<NotificationRepositoryState>>()
    private val storageHealth = mutableMapOf<AccountId, MutableStateFlow<NotificationStorageHealth>>()
    private val generations = mutableMapOf<AccountId, Long>()

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
        val next = synchronized(this) {
            if (!mutationAllowedLocked(token)) return false
            if (page.direction != direction) return false
            if (page.checkpoint?.accountId?.let { it != token.accountId } == true ||
                page.items.any {
                    it.accountId != token.accountId ||
                        it.id.connection != token.accountId.connection.origin
                }
            ) return false
            // The caller query owns validation. A claimed checkpoint query must equal it,
            // even for empty pages. Checkpoint-free pages are accepted only under the
            // explicit requested query, never a borrowed one.
            if (page.checkpoint?.query?.let { it != request.query } == true) return false
            val state = stateForLocked(token.accountId).value
            val query = request.query
            val previousCheckpoint = validatedCheckpoint(state, token.accountId, query)
            if (!checkBoundaryLocked(request, previousCheckpoint)) return false
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
            ).also { stateForLocked(token.accountId).value = it }
        }
        persistIfCurrent(token, next)
        return true
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

    suspend fun updateUnreadState(token: NotificationSyncToken, unreadState: NotificationUnreadState): Boolean {
        val next = synchronized(this) {
            if (!mutationAllowedLocked(token)) return false
            stateForLocked(token.accountId).value.copy(unreadState = unreadState).also {
                stateForLocked(token.accountId).value = it
            }
        }
        persistIfCurrent(token, next)
        return true
    }

    suspend fun applyStreamEvent(token: NotificationSyncToken, event: Event): Boolean {
        if (event.accountId != token.accountId) return false
        val next = synchronized(this) {
            if (!mutationAllowedLocked(token)) return false
            val current = stateForLocked(token.accountId).value
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
            stateForLocked(token.accountId).value = updated
            updated
        }
        persistIfCurrent(token, next)
        return true
    }

    suspend fun markLocallySeen(token: NotificationSyncToken, ids: Set<EntityId>): Boolean {
        val next = synchronized(this) {
            if (!mutationAllowedLocked(token)) return false
            val current = stateForLocked(token.accountId).value
            current.copy(items = current.items.map { item ->
                if (item.id in ids) item.copy(readState = item.readState.copy(locallySeen = true)) else item
            }).also { stateForLocked(token.accountId).value = it }
        }
        persistIfCurrent(token, next)
        return true
    }

    suspend fun markSeen(token: NotificationSyncToken, id: EntityId? = null): Boolean {
        val next = synchronized(this) {
            if (!mutationAllowedLocked(token)) return false
            val current = stateForLocked(token.accountId).value
            val items = current.items.map { item ->
                if (id == null || item.id == id) item.copy(readState = item.readState.copy(locallySeen = true)) else item
            }
            current.copy(items = items).also { stateForLocked(token.accountId).value = it }
        }
        persistIfCurrent(token, next)
        return true
    }

    suspend fun markPresented(token: NotificationSyncToken, id: EntityId): Boolean {
        val next = synchronized(this) {
            if (!mutationAllowedLocked(token)) return false
            val current = stateForLocked(token.accountId).value
            val items = current.items.map { item ->
                if (item.id == id) item.copy(readState = item.readState.copy(androidPresented = true)) else item
            }
            current.copy(items = items).also { stateForLocked(token.accountId).value = it }
        }
        persistIfCurrent(token, next)
        return true
    }

    /** Records only the Android surface dismissal; it never acknowledges the source notification. */
    suspend fun markAndroidDismissed(accountId: AccountId, id: EntityId): Boolean {
        val next = synchronized(this) {
            val state = states[accountId]?.value ?: return false
            if (storageBlockedLocked(accountId)) return false
            if (state.items.none { it.id == id }) return false
            state.copy(items = state.items.map { item ->
                if (item.id == id) item.copy(readState = item.readState.copy(androidDismissed = true)) else item
            }).also { states.getValue(accountId).value = it }
        }
        withContext(Dispatchers.IO) {
            synchronized(this@NotificationRepository) {
                if (states[accountId]?.value == next) store.write(accountId, next)
            }
        }
        return true
    }

    suspend fun acknowledge(
        token: NotificationSyncToken,
        acknowledgement: NotificationAcknowledgement,
    ): Boolean {
        val next = synchronized(this) {
            if (!mutationAllowedLocked(token) || acknowledgement.accountId != token.accountId) return false
            val current = stateForLocked(token.accountId).value
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
            current.copy(items = items, unreadState = acknowledgement.readState)
                .also { stateForLocked(token.accountId).value = it }
        }
        persistIfCurrent(token, next)
        return true
    }

    suspend fun applyAcknowledgement(
        token: NotificationSyncToken,
        acknowledgement: NotificationAcknowledgement,
    ): Boolean = acknowledge(token, acknowledgement)

    suspend fun dismiss(token: NotificationSyncToken, id: EntityId): Boolean {
        return dismissFromInbox(token, id, remoteApplied = false)
    }

    suspend fun dismissFromInbox(token: NotificationSyncToken, id: EntityId, remoteApplied: Boolean): Boolean {
        val next = synchronized(this) {
            if (!mutationAllowedLocked(token) || id.connection != token.accountId.connection.origin) return false
            val current = stateForLocked(token.accountId).value
            current.copy(
                items = current.items.filterNot { it.id == id },
                dismissedIds = current.dismissedIds + id,
                deliveries = current.deliveries - id,
            ).also {
                stateForLocked(token.accountId).value = it
            }
        }
        persistIfCurrent(token, next)
        return true
    }

    suspend fun claimDelivery(
        token: NotificationSyncToken,
        id: EntityId,
        nowEpochMillis: Long = System.currentTimeMillis(),
    ): NotificationDeliveryRecord? {
        val result = synchronized(this) {
            if (!mutationAllowedLocked(token)) return null
            val current = stateForLocked(token.accountId).value
            val record = current.deliveries[id] ?: return null
            if (!record.isClaimable(nowEpochMillis)) return null
            val claimed = record.claim(
                nowEpochMillis = nowEpochMillis,
                claimId = UUID.randomUUID().toString(),
                leaseMillis = DELIVERY_CLAIM_LEASE_MILLIS,
            )
            stateForLocked(token.accountId).value = current.copy(deliveries = current.deliveries + (id to claimed))
            claimed
        }
        result?.let { persistIfCurrent(token, observe(token.accountId).value) }
        return result
    }

    suspend fun finishDelivery(
        token: NotificationSyncToken,
        id: EntityId,
        state: NotificationDeliveryState,
        errorCategory: String? = null,
        claimId: String? = null,
    ): Boolean {
        val next = synchronized(this) {
            if (!mutationAllowedLocked(token)) return false
            val current = stateForLocked(token.accountId).value
            val existing = current.deliveries[id] ?: return false
            if (claimId != null && existing.claimId != claimId) return false
            current.copy(deliveries = current.deliveries + (id to existing.finish(state, errorCategory)))
                .also { stateForLocked(token.accountId).value = it }
        }
        persistIfCurrent(token, next)
        return true
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

    suspend fun updateSettings(token: NotificationSyncToken, settings: NotificationSettings): Boolean {
        val next = synchronized(this) {
            if (!mutationAllowedLocked(token)) return false
            stateForLocked(token.accountId).value.copy(settings = settings).also {
                stateForLocked(token.accountId).value = it
            }
        }
        persistIfCurrent(token, next)
        return true
    }

    suspend fun updatePushRegistration(token: NotificationSyncToken, registration: PushRegistration): Boolean {
        val next = synchronized(this) {
            if (!mutationAllowedLocked(token) || registration.accountId != token.accountId ||
                registration.generation != token.generation
            ) return false
            stateForLocked(token.accountId).value.copy(pushRegistration = registration).also {
                stateForLocked(token.accountId).value = it
            }
        }
        persistIfCurrent(token, next)
        return true
    }

    suspend fun clearPushRegistration(token: NotificationSyncToken): Boolean {
        val next = synchronized(this) {
            if (!mutationAllowedLocked(token)) return false
            stateForLocked(token.accountId).value.copy(pushRegistration = null).also {
                stateForLocked(token.accountId).value = it
            }
        }
        persistIfCurrent(token, next)
        return true
    }

    @Synchronized
    fun remove(accountId: AccountId) {
        states.remove(accountId)
        storageHealth.remove(accountId)
        generations.remove(accountId)
        store.delete(accountId)
    }

    private suspend fun persistIfCurrent(token: NotificationSyncToken, @Suppress("UNUSED_PARAMETER") state: NotificationRepositoryState) {
        withContext(Dispatchers.IO) {
            synchronized(this@NotificationRepository) {
                if (isCurrentLocked(token) && !storageBlockedLocked(token.accountId)) {
                    store.write(token.accountId, stateForLocked(token.accountId).value)
                }
            }
        }
    }

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
