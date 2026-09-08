package me.foxtails.palustris.data.notifications

import android.content.Context
import android.util.AtomicFile
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
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
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import me.foxtails.palustris.data.notifications.db.NotificationDatabase
import me.foxtails.palustris.data.notifications.db.NotificationStateEntity
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.EntityId
import me.foxtails.palustris.domain.Notification
import me.foxtails.palustris.domain.NotificationAcknowledgement
import me.foxtails.palustris.domain.NotificationActivity
import me.foxtails.palustris.domain.NotificationCheckpoint
import me.foxtails.palustris.domain.NotificationDeliveryRecord
import me.foxtails.palustris.domain.NotificationDeliveryState
import me.foxtails.palustris.domain.NotificationDestination
import me.foxtails.palustris.domain.Event
import me.foxtails.palustris.domain.SocialEvent
import me.foxtails.palustris.domain.NotificationGroup
import me.foxtails.palustris.domain.NotificationGroupId
import me.foxtails.palustris.domain.NotificationPage
import me.foxtails.palustris.domain.NotificationPageDirection
import me.foxtails.palustris.domain.NotificationQuery
import me.foxtails.palustris.domain.NotificationReadState
import me.foxtails.palustris.domain.NotificationReadStatus
import me.foxtails.palustris.domain.NotificationReaction
import me.foxtails.palustris.domain.NotificationSettings
import me.foxtails.palustris.domain.NotificationPushRegistrationState
import me.foxtails.palustris.domain.PushRegistrationFailureReason
import me.foxtails.palustris.domain.PushRegistrationFailureStage
import me.foxtails.palustris.domain.PushRegistration
import me.foxtails.palustris.domain.NotificationSyncCompleteness
import me.foxtails.palustris.domain.NotificationTarget
import me.foxtails.palustris.domain.NotificationSyncToken
import me.foxtails.palustris.domain.NotificationUnreadState
import me.foxtails.palustris.domain.ProfileField
import me.foxtails.palustris.domain.Protocol
import me.foxtails.palustris.domain.ValidatedUrl
import org.json.JSONArray
import org.json.JSONObject

data class NotificationRepositoryState(
    val items: List<Notification> = emptyList(),
    val unreadState: NotificationUnreadState = NotificationUnreadState.Unknown,
    val checkpoint: NotificationCheckpoint? = null,
    val lastSyncedAtEpochMillis: Long = 0,
    /** Checkpoints are keyed by the stable query fingerprint, never shared between filters. */
    val checkpoints: Map<String, NotificationCheckpoint> = emptyMap(),
    /** Tombstones make local-only dismissal survive refetch, restart, and older-page ingestion. */
    val dismissedIds: Set<EntityId> = emptySet(),
    val deliveries: Map<EntityId, NotificationDeliveryRecord> = emptyMap(),
    val settings: NotificationSettings = NotificationSettings(),
    val pushRegistration: PushRegistration? = null,
)

data class NotificationInboxSnapshot(
    val items: List<Notification>,
    val unreadState: NotificationUnreadState,
    val checkpoint: NotificationCheckpoint?,
    val lastSyncedAtEpochMillis: Long,
    val hasIncompleteSync: Boolean,
)

interface NotificationStore {
    fun read(accountId: AccountId): NotificationRepositoryState?
    fun write(accountId: AccountId, state: NotificationRepositoryState)
    fun delete(accountId: AccountId)
}

/** A process-local store used by unit tests and constructor compatibility helpers. */
class InMemoryNotificationStore : NotificationStore {
    private val values = mutableMapOf<AccountId, NotificationRepositoryState>()

    override fun read(accountId: AccountId): NotificationRepositoryState? = values[accountId]

    override fun write(accountId: AccountId, state: NotificationRepositoryState) {
        values[accountId] = state
    }

    override fun delete(accountId: AccountId) {
        values.remove(accountId)
    }
}

/** App-private, no-backup notification summaries. Tokens and session secrets never enter this store. */
class FileNotificationStore @javax.inject.Inject constructor(
    @ApplicationContext context: Context,
) : NotificationStore {
    private val directory = File(context.noBackupFilesDir, "notifications")

    override fun read(accountId: AccountId): NotificationRepositoryState? = runCatching {
        val file = fileFor(accountId)
        if (!file.baseFile.exists()) return null
        decode(JSONObject(String(file.readFully(), Charsets.UTF_8)))
    }.getOrNull()

    override fun write(accountId: AccountId, state: NotificationRepositoryState) {
        directory.mkdirs()
        val file = fileFor(accountId)
        val stream = file.startWrite()
        try {
            stream.write(encode(state).toString().toByteArray(Charsets.UTF_8))
            file.finishWrite(stream)
        } catch (error: Exception) {
            file.failWrite(stream)
            throw error
        }
    }

    override fun delete(accountId: AccountId) {
        fileFor(accountId).delete()
    }

    private fun fileFor(accountId: AccountId): AtomicFile = AtomicFile(
        File(directory, "${accountId.stableFileName()}.json"),
    )
}

/** Room-backed production store. The repository owns domain merging; Room owns durability. */
class RoomNotificationStore @javax.inject.Inject constructor(
    private val database: NotificationDatabase,
    private val importer: LegacyNotificationFileImporter,
) : NotificationStore {
    private val dao = database.notificationDao()

    override fun read(accountId: AccountId): NotificationRepositoryState? = runBlocking(Dispatchers.IO) {
        val key = accountId.stableFileName()
        dao.state(key)?.let { return@runBlocking decode(JSONObject(it.stateJson)) }
        importer.importIfPresent(accountId)?.also { imported ->
            dao.saveState(NotificationStateEntity(key, encode(imported).toString(), System.currentTimeMillis()))
        }
    }

    override fun write(accountId: AccountId, state: NotificationRepositoryState) {
        runBlocking(Dispatchers.IO) {
            dao.saveState(NotificationStateEntity(
                accountId.stableFileName(),
                encode(state).toString(),
                System.currentTimeMillis(),
            ))
        }
    }

    override fun delete(accountId: AccountId) {
        runBlocking(Dispatchers.IO) {
            val key = accountId.stableFileName()
            database.runInTransaction {
                dao.deleteState(key)
                dao.deleteEvents(key)
                dao.deleteActors(key)
                dao.deleteGroups(key)
                dao.deleteQueryState(key)
                dao.deleteDismissals(key)
                dao.deleteDelivery(key)
                dao.deleteAcknowledgements(key)
                dao.deletePushRegistration(key)
                dao.deleteSettings(key)
            }
        }
    }
}

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
    private val generations = mutableMapOf<AccountId, Long>()

    @Synchronized
    fun observe(accountId: AccountId): StateFlow<NotificationRepositoryState> = states.getOrPut(accountId) {
        MutableStateFlow(store.read(accountId) ?: NotificationRepositoryState())
    }.asStateFlow()

    fun observeInbox(accountId: AccountId, query: NotificationQuery): Flow<NotificationInboxSnapshot> =
        observe(accountId).map { state ->
            val checkpoint = state.checkpoints[query.stableKey]
                ?: state.checkpoint?.takeIf { it.query == query }
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
        return state.checkpoints[query.stableKey] ?: state.checkpoint?.takeIf { it.query == query }
    }

    @Synchronized
    fun activate(token: NotificationSyncToken) {
        val current = generations[token.accountId]
        if (current == null || token.generation >= current) generations[token.accountId] = token.generation
        states.getOrPut(token.accountId) { MutableStateFlow(store.read(token.accountId) ?: NotificationRepositoryState()) }
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

    suspend fun establishBaseline(token: NotificationSyncToken, page: NotificationPage): Boolean =
        applyPage(token, page, NotificationPageDirection.Initial, baselineEstablished = true)

    suspend fun ingestNewerPage(token: NotificationSyncToken, page: NotificationPage): Boolean =
        applyPage(token, page, NotificationPageDirection.Newer)

    suspend fun ingestOlderPage(token: NotificationSyncToken, page: NotificationPage): Boolean =
        applyPage(token, page, NotificationPageDirection.Older)

    /** Compatibility entry point; new synchronization code must choose a direction explicitly. */
    @Deprecated("Use establishBaseline, ingestNewerPage, or ingestOlderPage")
    suspend fun ingest(token: NotificationSyncToken, page: NotificationPage): Boolean =
        when (page.direction) {
            NotificationPageDirection.Initial -> establishBaseline(token, page)
            NotificationPageDirection.Newer -> ingestNewerPage(token, page)
            NotificationPageDirection.Older -> ingestOlderPage(token, page)
        }

    private suspend fun applyPage(
        token: NotificationSyncToken,
        page: NotificationPage,
        direction: NotificationPageDirection,
        baselineEstablished: Boolean = false,
    ): Boolean {
        val next = synchronized(this) {
            if (!isCurrentLocked(token)) return false
            val state = stateForLocked(token.accountId).value
            val query = page.checkpoint?.query ?: page.items.firstOrNull()?.let { state.checkpoint?.query }
            if (query == null) {
                applyLegacyPageLocked(token, page, direction, baselineEstablished)
            } else {
            val previousCheckpoint = state.checkpoints[query.stableKey]
                ?: state.checkpoint?.takeIf { it.query == query }
            val previous = state.items.associateBy(Notification::id)
            val incoming = page.items.filter { item ->
                item.accountId == token.accountId && item.id.connection == token.accountId.connection.origin
            }
            val merged = (incoming + state.items)
                .distinctBy(Notification::id)
                .map { item -> item.mergeReadState(previous[item.id]?.readState) }
                .filterNot { it.id in state.dismissedIds }
                .sortedWith(compareByDescending<Notification> { it.createdAtEpochMillis }.thenByDescending { it.id.value })
                .take(MAX_ITEMS)
            val pageCheckpoint = page.checkpoint?.takeIf { it.accountId == token.accountId && it.query == query }
            val nextCheckpoint = mergeCheckpoint(
                previous = previousCheckpoint,
                page = page,
                pageCheckpoint = pageCheckpoint,
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
                deliveries = updateDeliveryOutbox(
                    state,
                    incoming,
                    previousCheckpoint,
                    baselineEstablished,
                    direction,
                ),
            ).also { stateForLocked(token.accountId).value = it }
            }
        }
        persistIfCurrent(token, next)
        return true
    }

    private fun applyLegacyPageLocked(
        token: NotificationSyncToken,
        page: NotificationPage,
        direction: NotificationPageDirection,
        baselineEstablished: Boolean,
    ): NotificationRepositoryState {
        val state = stateForLocked(token.accountId).value
        val previous = state.items.associateBy(Notification::id)
        val incoming = page.items.filter { it.accountId == token.accountId }
        val merged = (incoming + state.items).distinctBy(Notification::id)
            .map { it.mergeReadState(previous[it.id]?.readState) }
            .filterNot { it.id in state.dismissedIds }
            .sortedWith(compareByDescending<Notification> { it.createdAtEpochMillis }.thenByDescending { it.id.value })
            .take(MAX_ITEMS)
        return state.copy(
            items = merged,
            unreadState = page.unreadState.takeIf { it !is NotificationUnreadState.Unknown } ?: state.unreadState,
            checkpoint = page.checkpoint ?: state.checkpoint,
            lastSyncedAtEpochMillis = page.checkpoint?.capturedAtEpochMillis ?: state.lastSyncedAtEpochMillis,
            deliveries = updateDeliveryOutbox(
                state,
                incoming,
                state.checkpoint,
                baselineEstablished,
                direction,
            ),
        ).also { stateForLocked(token.accountId).value = it }
    }

    private fun mergeCheckpoint(
        previous: NotificationCheckpoint?,
        page: NotificationPage,
        pageCheckpoint: NotificationCheckpoint?,
        direction: NotificationPageDirection,
        baselineEstablished: Boolean,
    ): NotificationCheckpoint {
        val query = pageCheckpoint?.query ?: previous?.query ?: error("Notification page has no query")
        val newest = when (direction) {
            NotificationPageDirection.Older -> previous?.newest ?: page.resolvedNewestBoundary
            NotificationPageDirection.Initial -> page.resolvedNewestBoundary ?: previous?.newest
            NotificationPageDirection.Newer -> if (previous?.newerContinuation != null) {
                previous.newest
            } else {
                page.resolvedNewestBoundary ?: previous?.newest
            }
        }
        val oldest = when (direction) {
            NotificationPageDirection.Newer -> previous?.oldest ?: page.resolvedOldestBoundary
            NotificationPageDirection.Initial -> page.resolvedOldestBoundary ?: previous?.oldest
            NotificationPageDirection.Older -> if (page.reachedBoundary || page.resolvedContinuation == null) {
                null
            } else {
                page.resolvedOldestBoundary ?: previous?.oldest
            }
        }
        val continuation = page.continuation ?: when (direction) {
            NotificationPageDirection.Older -> page.olderCursor
            NotificationPageDirection.Newer -> page.newerCursor
            NotificationPageDirection.Initial -> null
        }
        val newerContinuation = when (direction) {
            NotificationPageDirection.Newer -> continuation
            else -> previous?.newerContinuation
        }
        val olderContinuation = when (direction) {
            NotificationPageDirection.Older -> continuation
            else -> previous?.olderContinuation
        }
        val complete = page.reachedBoundary || continuation == null
        return NotificationCheckpoint(
            accountId = queryAccountId(page, previous),
            query = query,
            newest = newest,
            oldest = oldest,
            capturedAtEpochMillis = pageCheckpoint?.capturedAtEpochMillis ?: previous?.capturedAtEpochMillis ?: 0,
            newerContinuation = newerContinuation,
            olderContinuation = olderContinuation,
            completeness = if (complete) NotificationSyncCompleteness.Complete else NotificationSyncCompleteness.Incomplete,
            baselineEstablished = baselineEstablished || previous?.baselineEstablished == true,
        )
    }

    private fun queryAccountId(page: NotificationPage, previous: NotificationCheckpoint?): AccountId =
        page.checkpoint?.accountId ?: previous?.accountId ?: page.items.firstOrNull()?.accountId
        ?: error("Notification page has no account")

    private fun updateDeliveryOutbox(
        state: NotificationRepositoryState,
        incoming: List<Notification>,
        previousCheckpoint: NotificationCheckpoint?,
        baselineEstablished: Boolean,
        direction: NotificationPageDirection,
    ): Map<EntityId, NotificationDeliveryRecord> {
        if (baselineEstablished || direction != NotificationPageDirection.Newer ||
            previousCheckpoint?.baselineEstablished != true
        ) return state.deliveries
        val knownIds = state.items.asSequence().map(Notification::id).toSet()
        return incoming.fold(state.deliveries) { deliveries, notification ->
            if (notification.id in deliveries || notification.id in state.dismissedIds || notification.id in knownIds) deliveries
            else deliveries + (notification.id to NotificationDeliveryRecord(
                accountId = notification.accountId,
                notificationId = notification.id,
                androidTag = "${notification.accountId.connection.origin}:${notification.accountId.localId}",
                androidId = stableNotificationId(notification.id),
            ))
        }
    }

    suspend fun updateUnreadState(token: NotificationSyncToken, unreadState: NotificationUnreadState): Boolean {
        val next = synchronized(this) {
            if (!isCurrentLocked(token)) return false
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
            if (!isCurrentLocked(token)) return false
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
                        val deliveries = if (current.checkpoints.values.any { it.baselineEstablished } && incoming.id !in current.deliveries) {
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
            if (!isCurrentLocked(token)) return false
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
            if (!isCurrentLocked(token)) return false
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
            if (!isCurrentLocked(token)) return false
            val current = stateForLocked(token.accountId).value
            val items = current.items.map { item ->
                if (item.id == id) item.copy(readState = item.readState.copy(androidPresented = true)) else item
            }
            current.copy(items = items).also { stateForLocked(token.accountId).value = it }
        }
        persistIfCurrent(token, next)
        return true
    }

    suspend fun acknowledge(
        token: NotificationSyncToken,
        acknowledgement: NotificationAcknowledgement,
    ): Boolean {
        val next = synchronized(this) {
            if (!isCurrentLocked(token) || acknowledgement.accountId != token.accountId) return false
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
            if (!isCurrentLocked(token) || id.connection != token.accountId.connection.origin) return false
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
            if (!isCurrentLocked(token)) return null
            val current = stateForLocked(token.accountId).value
            val record = current.deliveries[id] ?: return null
            val claimable = when (record.state) {
                NotificationDeliveryState.Pending,
                NotificationDeliveryState.Failed,
                -> true
                NotificationDeliveryState.Posting -> record.claimExpiresAtEpochMillis <= nowEpochMillis
                else -> false
            }
            if (!claimable) return null
            val claimed = record.copy(
                state = NotificationDeliveryState.Posting,
                attemptCount = record.attemptCount + 1,
                lastAttemptAtEpochMillis = nowEpochMillis,
                claimId = UUID.randomUUID().toString(),
                claimExpiresAtEpochMillis = nowEpochMillis + DELIVERY_CLAIM_LEASE_MILLIS,
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
            if (!isCurrentLocked(token)) return false
            val current = stateForLocked(token.accountId).value
            val existing = current.deliveries[id] ?: return false
            if (claimId != null && existing.claimId != claimId) return false
            current.copy(deliveries = current.deliveries + (id to existing.copy(
                state = state,
                lastErrorCategory = errorCategory,
                claimId = null,
                claimExpiresAtEpochMillis = 0,
            ))).also { stateForLocked(token.accountId).value = it }
        }
        persistIfCurrent(token, next)
        return true
    }

    fun pendingDeliveries(
        accountId: AccountId,
        nowEpochMillis: Long = System.currentTimeMillis(),
    ): List<NotificationDeliveryRecord> =
        observe(accountId).value.deliveries.values.filter {
            it.state == NotificationDeliveryState.Pending || it.state == NotificationDeliveryState.Failed ||
                (it.state == NotificationDeliveryState.Posting && it.claimExpiresAtEpochMillis <= nowEpochMillis)
        }

    @Synchronized
    fun settings(accountId: AccountId): NotificationSettings = observe(accountId).value.settings

    @Synchronized
    fun pushRegistration(accountId: AccountId): PushRegistration? = observe(accountId).value.pushRegistration

    suspend fun updateSettings(token: NotificationSyncToken, settings: NotificationSettings): Boolean {
        val next = synchronized(this) {
            if (!isCurrentLocked(token)) return false
            stateForLocked(token.accountId).value.copy(settings = settings).also {
                stateForLocked(token.accountId).value = it
            }
        }
        persistIfCurrent(token, next)
        return true
    }

    suspend fun updatePushRegistration(token: NotificationSyncToken, registration: PushRegistration): Boolean {
        val next = synchronized(this) {
            if (!isCurrentLocked(token) || registration.accountId != token.accountId ||
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
            if (!isCurrentLocked(token)) return false
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
        generations.remove(accountId)
        store.delete(accountId)
    }

    private suspend fun persistIfCurrent(token: NotificationSyncToken, @Suppress("UNUSED_PARAMETER") state: NotificationRepositoryState) {
        withContext(Dispatchers.IO) {
            synchronized(this@NotificationRepository) {
                if (isCurrentLocked(token)) store.write(token.accountId, stateForLocked(token.accountId).value)
            }
        }
    }

    private fun Notification.mergeReadState(previous: NotificationReadState?): Notification {
        val known = readState.status.takeUnless { it == NotificationReadStatus.Unknown }
            ?: previous?.status ?: NotificationReadStatus.Unknown
        return copy(readState = readState.copy(
            status = known,
            locallySeen = readState.locallySeen || previous?.locallySeen == true,
            serverAcknowledged = readState.serverAcknowledged || previous?.serverAcknowledged == true,
            androidPresented = readState.androidPresented || previous?.androidPresented == true,
        ))
    }

    private fun stateForLocked(accountId: AccountId): MutableStateFlow<NotificationRepositoryState> =
        states.getOrPut(accountId) { MutableStateFlow(store.read(accountId) ?: NotificationRepositoryState()) }

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

private fun encode(state: NotificationRepositoryState): JSONObject = JSONObject().apply {
    put("version", 2)
    put("items", JSONArray(state.items.map(::encodeNotification)))
    put("unread", encodeUnread(state.unreadState))
    state.checkpoint?.let { checkpoint -> put("checkpoint", encodeCheckpoint(checkpoint)) }
    put("lastSyncedAt", state.lastSyncedAtEpochMillis)
    put("dismissedIds", JSONArray(state.dismissedIds.map(::encodeEntity)))
    put("checkpoints", JSONObject().apply {
        state.checkpoints.forEach { (key, checkpoint) -> put(key, encodeCheckpoint(checkpoint)) }
    })
    put("deliveries", JSONArray(state.deliveries.values.map(::encodeDelivery)))
    put("settings", encodeSettings(state.settings))
    state.pushRegistration?.let { put("pushRegistration", encodePushRegistration(it)) }
}

private fun decode(json: JSONObject): NotificationRepositoryState {
    val items = json.optJSONArray("items")?.let { values ->
        (0 until values.length()).mapNotNull { index -> runCatching { decodeNotification(values.getJSONObject(index)) }.getOrNull() }
    }.orEmpty()
    return NotificationRepositoryState(
        items = items,
        unreadState = decodeUnread(json.optJSONObject("unread")),
        checkpoint = json.optJSONObject("checkpoint")?.let(::decodeCheckpoint),
        lastSyncedAtEpochMillis = json.optLong("lastSyncedAt", 0),
        dismissedIds = json.optJSONArray("dismissedIds")?.let { values ->
            (0 until values.length()).mapNotNull { index -> runCatching { decodeEntity(values.getJSONObject(index)) }.getOrNull() }
        }?.toSet().orEmpty(),
        checkpoints = json.optJSONObject("checkpoints")?.let { values ->
            values.keys().asSequence().mapNotNull { key -> runCatching { key to decodeCheckpoint(values.getJSONObject(key)) }.getOrNull() }
                .toMap()
        }.orEmpty(),
        deliveries = json.optJSONArray("deliveries")?.let { values ->
            (0 until values.length()).mapNotNull { index -> runCatching { decodeDelivery(values.getJSONObject(index)) }.getOrNull() }
        }?.associateBy { it.notificationId }.orEmpty(),
        settings = decodeSettings(json.optJSONObject("settings")),
        pushRegistration = json.optJSONObject("pushRegistration")?.let(::decodePushRegistration),
    )
}

private fun encodePushRegistration(registration: PushRegistration): JSONObject = JSONObject().apply {
    put("accountId", encodeAccountId(registration.accountId))
    put("generation", registration.generation)
    put("sessionRevision", registration.sessionRevision)
    put("instanceName", registration.instanceName)
    registration.distributorPackage?.let { put("distributorPackage", it) }
    registration.endpoint?.let { put("endpoint", it.value) }
    registration.serverEndpoint?.let { put("serverEndpoint", it.value) }
    registration.serverRemoteId?.let { put("serverRemoteId", it) }
    put("confirmedEndpointGeneration", registration.confirmedEndpointGeneration)
    put("state", registration.state.name)
    put("endpointGeneration", registration.endpointGeneration)
    put("retryCount", registration.retryCount)
    registration.lastErrorCategory?.let { put("lastErrorCategory", it) }
    registration.lastErrorDetail?.let { put("lastErrorDetail", it) }
    registration.failureStage?.let { put("failureStage", it.name) }
    registration.failureReason?.let { put("failureReason", it.name) }
    put("nextRetryAt", registration.nextRetryAtEpochMillis)
}

private fun decodePushRegistration(json: JSONObject): PushRegistration {
    val endpoint = json.optString("endpoint").takeIf { it.isNotBlank() }?.let { ValidatedUrl.https(it) }
    val state = runCatching { NotificationPushRegistrationState.valueOf(json.optString("state")) }
        .getOrDefault(NotificationPushRegistrationState.Off)
    val serverEndpoint = json.optString("serverEndpoint").takeIf { it.isNotBlank() }
        ?.let { ValidatedUrl.https(it) }
        // Older stores only had one endpoint field. Connected records were written after a
        // successful server response, so they can safely seed the confirmed field on upgrade.
        ?: endpoint.takeIf { state == NotificationPushRegistrationState.Connected }
    return PushRegistration(
        accountId = decodeAccountId(json.getJSONObject("accountId")),
        generation = json.optLong("generation"),
        sessionRevision = json.optLong("sessionRevision", 1L),
        instanceName = json.getString("instanceName"),
        distributorPackage = json.optString("distributorPackage").takeIf { it.isNotBlank() },
        endpoint = endpoint,
        serverEndpoint = serverEndpoint,
        serverRemoteId = json.optString("serverRemoteId").takeIf { it.isNotBlank() },
        confirmedEndpointGeneration = json.optLong("confirmedEndpointGeneration", if (state == NotificationPushRegistrationState.Connected) {
            json.optLong("endpointGeneration")
        } else {
            0L
        }),
        state = state,
        endpointGeneration = json.optLong("endpointGeneration"),
        retryCount = json.optInt("retryCount"),
        lastErrorCategory = json.optString("lastErrorCategory").takeIf { it.isNotBlank() },
        lastErrorDetail = json.optString("lastErrorDetail").takeIf { it.isNotBlank() },
        failureStage = json.optString("failureStage").takeIf { it.isNotBlank() }?.let {
            runCatching { PushRegistrationFailureStage.valueOf(it) }.getOrNull()
        },
        failureReason = json.optString("failureReason").takeIf { it.isNotBlank() }?.let {
            runCatching { PushRegistrationFailureReason.valueOf(it) }.getOrNull()
        },
        nextRetryAtEpochMillis = json.optLong("nextRetryAt"),
    )
}

private fun encodeSettings(settings: NotificationSettings): JSONObject = JSONObject().apply {
    put("alertsEnabled", settings.alertsEnabled)
    put("categories", JSONArray(settings.categories.map { it.name }))
    put("showPreviews", settings.showPreviews)
    settings.quietHoursStartMinutes?.let { put("quietStart", it) }
    settings.quietHoursEndMinutes?.let { put("quietEnd", it) }
    put("periodicFallbackEnabled", settings.periodicFallbackEnabled)
    settings.selectedDistributor?.let { put("selectedDistributor", it) }
}

private fun decodeSettings(json: JSONObject?): NotificationSettings {
    if (json == null) return NotificationSettings()
    val categories = json.optJSONArray("categories")?.let { values ->
        (0 until values.length()).mapNotNull { index ->
            runCatching { me.foxtails.palustris.domain.NotificationCategory.valueOf(values.getString(index)) }.getOrNull()
        }
    }?.toSet()
        ?: setOf(me.foxtails.palustris.domain.NotificationCategory.All)
    return NotificationSettings(
        alertsEnabled = json.optBoolean("alertsEnabled"),
        categories = categories,
        showPreviews = json.optBoolean("showPreviews"),
        quietHoursStartMinutes = json.optInt("quietStart").takeIf { json.has("quietStart") },
        quietHoursEndMinutes = json.optInt("quietEnd").takeIf { json.has("quietEnd") },
        periodicFallbackEnabled = json.optBoolean("periodicFallbackEnabled"),
        selectedDistributor = json.optString("selectedDistributor").takeIf { it.isNotBlank() },
    )
}

private fun encodeDelivery(record: NotificationDeliveryRecord): JSONObject = JSONObject().apply {
    put("accountId", encodeAccountId(record.accountId))
    put("notificationId", encodeEntity(record.notificationId))
    put("state", record.state.name)
    put("androidTag", record.androidTag)
    put("androidId", record.androidId)
    put("attemptCount", record.attemptCount)
    put("lastAttemptAt", record.lastAttemptAtEpochMillis)
    record.lastErrorCategory?.let { put("lastErrorCategory", it) }
    record.claimId?.let { put("claimId", it) }
    put("claimExpiresAt", record.claimExpiresAtEpochMillis)
}

private fun decodeDelivery(json: JSONObject): NotificationDeliveryRecord = NotificationDeliveryRecord(
    accountId = decodeAccountId(json.getJSONObject("accountId")),
    notificationId = decodeEntity(json.getJSONObject("notificationId")),
    state = runCatching { NotificationDeliveryState.valueOf(json.optString("state")) }
        .getOrDefault(NotificationDeliveryState.Pending),
    androidTag = json.optString("androidTag"),
    androidId = json.optInt("androidId"),
    attemptCount = json.optInt("attemptCount"),
    lastAttemptAtEpochMillis = json.optLong("lastAttemptAt"),
    lastErrorCategory = json.optString("lastErrorCategory").takeIf { it.isNotBlank() },
    claimId = json.optString("claimId").takeIf { it.isNotBlank() },
    claimExpiresAtEpochMillis = json.optLong("claimExpiresAt"),
)

private fun encodeNotification(notification: Notification): JSONObject = JSONObject().apply {
    put("id", encodeEntity(notification.id))
    put("accountId", encodeAccountId(notification.accountId))
    put("createdAt", notification.createdAtEpochMillis)
    put("activity", encodeActivity(notification.activity))
    put("actors", JSONArray(notification.actors.map(::encodeAccount)))
    notification.target?.let { put("target", encodeTarget(it)) }
    put("readStatus", notification.readState.status.name)
    put("locallySeen", notification.readState.locallySeen)
    put("serverAcknowledged", notification.readState.serverAcknowledged)
    put("androidPresented", notification.readState.androidPresented)
    put("rawType", notification.rawType)
    notification.group?.let { put("group", encodeGroup(it)) }
}

private fun decodeNotification(json: JSONObject): Notification = Notification(
    id = decodeEntity(json.getJSONObject("id")),
    accountId = decodeAccountId(json.getJSONObject("accountId")),
    createdAtEpochMillis = json.optLong("createdAt"),
    activity = decodeActivity(json.getJSONObject("activity")),
    actors = json.optJSONArray("actors")?.let { values ->
        (0 until values.length()).mapNotNull { index -> runCatching { decodeAccount(values.getJSONObject(index)) }.getOrNull() }
    }.orEmpty(),
    target = json.optJSONObject("target")?.let(::decodeTarget),
    destination = json.optJSONObject("target")?.let { NotificationDestination.InApp(decodeTarget(it)) },
    readState = NotificationReadState(
        status = runCatching { NotificationReadStatus.valueOf(json.optString("readStatus")) }
            .getOrDefault(NotificationReadStatus.Unknown),
        locallySeen = json.optBoolean("locallySeen"),
        serverAcknowledged = json.optBoolean("serverAcknowledged"),
        androidPresented = json.optBoolean("androidPresented"),
    ),
    rawType = json.optString("rawType", "unknown"),
    group = json.optJSONObject("group")?.let(::decodeGroup),
)

private fun encodeEntity(id: EntityId): JSONObject = JSONObject().put("connection", id.connection).put("value", id.value)

private fun decodeEntity(json: JSONObject): EntityId = EntityId(json.getString("connection"), json.getString("value"))

private fun encodeAccountId(id: AccountId): JSONObject = JSONObject()
    .put("origin", id.connection.origin).put("protocol", id.connection.protocol.name).put("localId", id.localId)

private fun decodeAccountId(json: JSONObject): AccountId = AccountId(
    Connection(json.getString("origin"), Protocol.valueOf(json.getString("protocol"))),
    json.getString("localId"),
)

private fun encodeAccount(account: Account): JSONObject = JSONObject()
    .put("id", encodeAccountId(account.id)).put("displayName", account.displayName)
    .put("handle", account.handle).put("avatarUrl", account.avatarUrl)

private fun decodeAccount(json: JSONObject): Account = Account(
    id = decodeAccountId(json.getJSONObject("id")),
    displayName = json.optString("displayName"),
    handle = json.optString("handle"),
    avatarUrl = json.optString("avatarUrl").takeIf { it.isNotBlank() },
    biography = json.optString("biography"),
    profileFields = emptyList<ProfileField>(),
)

private fun encodeTarget(target: NotificationTarget): JSONObject = JSONObject().apply {
    when (target) {
        is NotificationTarget.Post -> put("kind", "post").put("id", encodeEntity(target.id))
        is NotificationTarget.Profile -> put("kind", "profile").put("id", encodeAccountId(target.id))
        is NotificationTarget.Poll -> put("kind", "poll").put("id", encodeEntity(target.id))
        is NotificationTarget.Conversation -> put("kind", "conversation").put("id", encodeEntity(target.id))
    }
}

private fun decodeTarget(json: JSONObject): NotificationTarget = when (json.getString("kind")) {
    "post" -> NotificationTarget.Post(decodeEntity(json.getJSONObject("id")))
    "profile" -> NotificationTarget.Profile(decodeAccountId(json.getJSONObject("id")))
    "poll" -> NotificationTarget.Poll(decodeEntity(json.getJSONObject("id")))
    "conversation" -> NotificationTarget.Conversation(decodeEntity(json.getJSONObject("id")))
    else -> error("Unknown notification target")
}

private fun encodeActivity(activity: NotificationActivity): JSONObject = JSONObject().apply {
    when (activity) {
        NotificationActivity.Mention -> put("kind", "mention")
        NotificationActivity.Reply -> put("kind", "reply")
        NotificationActivity.Reshare -> put("kind", "reshare")
        NotificationActivity.Quote -> put("kind", "quote")
        NotificationActivity.Favourite -> put("kind", "favourite")
        is NotificationActivity.EmojiReaction -> put("kind", "reaction").put("identity", activity.reaction.identity)
            .put("fallback", activity.reaction.fallbackText).put("imageUrl", activity.reaction.imageUrl)
        NotificationActivity.Follow -> put("kind", "follow")
        NotificationActivity.FollowRequest -> put("kind", "follow_request")
        NotificationActivity.AcceptedRequest -> put("kind", "accepted_request")
        NotificationActivity.SubscribedPost -> put("kind", "subscribed")
        is NotificationActivity.PollResult -> put("kind", "poll_result").put("option", activity.option)
        NotificationActivity.PostUpdate -> put("kind", "post_update")
        NotificationActivity.QuotedPostUpdate -> put("kind", "quoted_post_update")
        is NotificationActivity.System.Moderation -> put("kind", "system").put("systemKind", "Moderation")
            .put("title", activity.title).put("detail", activity.detail)
        is NotificationActivity.System.RelationshipChange -> put("kind", "system").put("systemKind", "RelationshipChange")
            .put("title", activity.title).put("detail", activity.detail)
        is NotificationActivity.System.RoleOrAchievement -> put("kind", "system").put("systemKind", "RoleOrAchievement")
            .put("title", activity.title).put("detail", activity.detail)
        is NotificationActivity.System.AppEvent -> put("kind", "system").put("systemKind", "AppEvent")
            .put("title", activity.title).put("detail", activity.detail)
        is NotificationActivity.Unknown -> put("kind", "unknown").put("fallback", activity.fallbackText)
            .put("destination", (activity.validatedDestination as? NotificationDestination.Server)?.url?.value)
    }
}

private fun decodeActivity(json: JSONObject): NotificationActivity = when (json.optString("kind")) {
    "mention" -> NotificationActivity.Mention
    "reply" -> NotificationActivity.Reply
    "reshare" -> NotificationActivity.Reshare
    "quote" -> NotificationActivity.Quote
    "favourite" -> NotificationActivity.Favourite
    "reaction" -> NotificationActivity.EmojiReaction(NotificationReaction(
        json.optString("identity", "reaction"), json.optString("fallback", "Reaction"),
        json.optString("imageUrl").takeIf { it.isNotBlank() },
    ))
    "follow" -> NotificationActivity.Follow
    "follow_request" -> NotificationActivity.FollowRequest
    "accepted_request" -> NotificationActivity.AcceptedRequest
    "subscribed" -> NotificationActivity.SubscribedPost
    "poll_result" -> NotificationActivity.PollResult(json.optString("option").takeIf { it.isNotBlank() })
    "post_update" -> NotificationActivity.PostUpdate
    "quoted_post_update" -> NotificationActivity.QuotedPostUpdate
    "system" -> when (json.optString("systemKind")) {
        "Moderation" -> NotificationActivity.System.Moderation(json.optString("title"), json.optString("detail").takeIf { it.isNotBlank() })
        "RelationshipChange" -> NotificationActivity.System.RelationshipChange(json.optString("title"), json.optString("detail").takeIf { it.isNotBlank() })
        "RoleOrAchievement" -> NotificationActivity.System.RoleOrAchievement(json.optString("title"), json.optString("detail").takeIf { it.isNotBlank() })
        else -> NotificationActivity.System.AppEvent(json.optString("title"), json.optString("detail").takeIf { it.isNotBlank() })
    }
    else -> NotificationActivity.Unknown(json.optString("fallback", "New activity"))
}

private fun encodeGroup(group: NotificationGroup): JSONObject = JSONObject().apply {
    put("accountId", encodeAccountId(group.id.accountId)).put("value", group.id.value)
    put("actors", JSONArray(group.actorPreviews.map(::encodeAccount))).put("totalCount", group.totalCount)
}

private fun decodeGroup(json: JSONObject): NotificationGroup = NotificationGroup(
    id = NotificationGroupId(decodeAccountId(json.getJSONObject("accountId")), json.getString("value")),
    actorPreviews = json.optJSONArray("actors")?.let { values ->
        (0 until values.length()).mapNotNull { index -> runCatching { decodeAccount(values.getJSONObject(index)) }.getOrNull() }
    }.orEmpty(),
    totalCount = json.optInt("totalCount").takeIf { it > 0 },
)

private fun encodeUnread(state: NotificationUnreadState): JSONObject = JSONObject().apply {
    when (state) {
        is NotificationUnreadState.Exact -> put("kind", "exact").put("count", state.count)
        is NotificationUnreadState.AtLeast -> put("kind", "at_least").put("count", state.count)
        NotificationUnreadState.Present -> put("kind", "present")
        NotificationUnreadState.None -> put("kind", "none")
        NotificationUnreadState.Unknown -> put("kind", "unknown")
    }
}

private fun decodeUnread(json: JSONObject?): NotificationUnreadState = when (json?.optString("kind")) {
    "exact" -> NotificationUnreadState.Exact(json.optInt("count").coerceAtLeast(0))
    "at_least" -> NotificationUnreadState.AtLeast(json.optInt("count").coerceAtLeast(0))
    "present" -> NotificationUnreadState.Present
    "none" -> NotificationUnreadState.None
    else -> NotificationUnreadState.Unknown
}

private fun encodeCheckpoint(checkpoint: NotificationCheckpoint): JSONObject = JSONObject().apply {
    put("accountId", encodeAccountId(checkpoint.accountId)).put("capturedAt", checkpoint.capturedAtEpochMillis)
    put("query", JSONObject().put("categories", JSONArray(checkpoint.query.categories.map { it.name }))
        .put("limit", checkpoint.query.limit).put("grouped", checkpoint.query.grouped))
    checkpoint.newest?.let { put("newest", it.value) }
    checkpoint.oldest?.let { put("oldest", it.value) }
    checkpoint.newerContinuation?.let { put("newerContinuation", it.value) }
    checkpoint.olderContinuation?.let { put("olderContinuation", it.value) }
    put("completeness", checkpoint.completeness.name)
    put("baselineEstablished", checkpoint.baselineEstablished)
}

private fun decodeCheckpoint(json: JSONObject): NotificationCheckpoint {
    val queryJson = json.getJSONObject("query")
    val categories = queryJson.optJSONArray("categories")?.let { values ->
        (0 until values.length()).mapNotNull { index -> runCatching {
            me.foxtails.palustris.domain.NotificationCategory.valueOf(values.getString(index))
        }.getOrNull() }
    }.orEmpty().toSet()
    return NotificationCheckpoint(
        accountId = decodeAccountId(json.getJSONObject("accountId")),
        query = me.foxtails.palustris.domain.NotificationQuery(categories, queryJson.optInt("limit", 30), queryJson.optBoolean("grouped")),
        newest = json.optString("newest").takeIf { it.isNotBlank() }?.let(::meFoxtailsNotificationCursor),
        oldest = json.optString("oldest").takeIf { it.isNotBlank() }?.let(::meFoxtailsNotificationCursor),
        capturedAtEpochMillis = json.optLong("capturedAt"),
        newerContinuation = json.optString("newerContinuation").takeIf { it.isNotBlank() }?.let(::meFoxtailsNotificationCursor),
        olderContinuation = json.optString("olderContinuation").takeIf { it.isNotBlank() }?.let(::meFoxtailsNotificationCursor),
        completeness = runCatching { NotificationSyncCompleteness.valueOf(json.optString("completeness")) }
            .getOrDefault(NotificationSyncCompleteness.Unknown),
        baselineEstablished = json.optBoolean("baselineEstablished"),
    )
}

private fun meFoxtailsNotificationCursor(value: String) = me.foxtails.palustris.domain.NotificationCursor(value)

private fun Notification.matches(query: NotificationQuery): Boolean {
    if (query.isAll) return true
    return query.categories.any { category ->
        when (category) {
            me.foxtails.palustris.domain.NotificationCategory.All -> true
            me.foxtails.palustris.domain.NotificationCategory.Mentions -> activity == NotificationActivity.Mention
            me.foxtails.palustris.domain.NotificationCategory.Replies -> activity == NotificationActivity.Reply
            me.foxtails.palustris.domain.NotificationCategory.Quotes -> activity == NotificationActivity.Quote ||
                activity == NotificationActivity.QuotedPostUpdate
            me.foxtails.palustris.domain.NotificationCategory.Social -> activity in setOf(
                NotificationActivity.Reshare,
                NotificationActivity.Favourite,
                NotificationActivity.Follow,
                NotificationActivity.FollowRequest,
                NotificationActivity.AcceptedRequest,
                NotificationActivity.SubscribedPost,
            ) || activity is NotificationActivity.EmojiReaction
            me.foxtails.palustris.domain.NotificationCategory.Polls -> activity is NotificationActivity.PollResult
            me.foxtails.palustris.domain.NotificationCategory.System -> activity is NotificationActivity.System ||
                activity is NotificationActivity.Unknown
        }
    }
}

private fun stableNotificationId(id: EntityId): Int = (id.connection + "\u0000" + id.value).hashCode()
