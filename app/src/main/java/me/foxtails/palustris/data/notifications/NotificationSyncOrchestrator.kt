package me.foxtails.palustris.data.notifications

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import me.foxtails.palustris.data.AccountSourceRegistry
import me.foxtails.palustris.data.notifications.work.NoOpNotificationDeliveryScheduler
import me.foxtails.palustris.data.notifications.work.NotificationDeliveryScheduler
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.Event
import me.foxtails.palustris.domain.NotificationQuery
import me.foxtails.palustris.domain.NotificationSyncToken
import me.foxtails.palustris.domain.NotificationAcknowledgement
import me.foxtails.palustris.domain.NotificationUnreadState
import me.foxtails.palustris.domain.SourceError
import me.foxtails.palustris.domain.SocialSource

enum class NotificationStreamStatus {
    Stopped,
    Connecting,
    Ready,
    Backoff,
    Unsupported,
}

data class NotificationSyncState(
    val unreadState: NotificationUnreadState = NotificationUnreadState.Unknown,
    val lastUpdated: Long = 0,
    val isActive: Boolean = false,
    val delayed: Boolean = false,
    val error: String? = null,
    val streamConnected: Boolean = false,
    val streamError: String? = null,
    val streamStatus: NotificationStreamStatus = NotificationStreamStatus.Stopped,
)

interface NotificationSyncController {
    fun observeAccount(accountId: AccountId): StateFlow<NotificationSyncState>
    fun register(accountId: AccountId, source: SocialSource)
    fun unregister(accountId: AccountId)
    fun removeAccount(accountId: AccountId)
}

class NoOpNotificationSyncController : NotificationSyncController {
    private val states = mutableMapOf<AccountId, MutableStateFlow<NotificationSyncState>>()

    @Synchronized
    override fun observeAccount(accountId: AccountId): StateFlow<NotificationSyncState> = states.getOrPut(accountId) {
        MutableStateFlow(NotificationSyncState())
    }.asStateFlow()

    override fun register(accountId: AccountId, source: SocialSource) = Unit

    @Synchronized
    override fun unregister(accountId: AccountId) {
        states[accountId]?.value = states[accountId]?.value?.copy(isActive = false) ?: NotificationSyncState()
    }

    @Synchronized
    override fun removeAccount(accountId: AccountId) {
        unregister(accountId)
        states.remove(accountId)
    }
}

/** Application/account lifetime owner for REST reconciliation and future push/stream hints. */
@Singleton
class NotificationSyncOrchestrator @Inject constructor(
    private val repository: NotificationRepository,
    private val synchronizer: NotificationSynchronizer,
    private val sourceRegistry: AccountSourceRegistry,
    private val deliveryScheduler: NotificationDeliveryScheduler,
) : NotificationSyncController, NotificationSyncIntents, AutoCloseable {
    private constructor(dependencies: Dependencies) : this(
        dependencies.repository,
        dependencies.synchronizer,
        dependencies.sourceRegistry,
        dependencies.deliveryScheduler,
    )

    constructor() : this(Dependencies())

    private class Dependencies {
        val repository = NotificationRepository()
        val synchronizer = NotificationSynchronizer(repository)
        val sourceRegistry = AccountSourceRegistry()
        val deliveryScheduler = NoOpNotificationDeliveryScheduler()
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val states = mutableMapOf<AccountId, MutableStateFlow<NotificationSyncState>>()
    private val jobs = mutableMapOf<AccountId, Job>()
    private val accountLocks = mutableMapOf<AccountId, Mutex>()
    private val generations = mutableMapOf<AccountId, Long>()

    @Synchronized
    override fun observeAccount(accountId: AccountId): StateFlow<NotificationSyncState> = states.getOrPut(accountId) {
        MutableStateFlow(NotificationSyncState(
            unreadState = repository.observe(accountId).value.unreadState,
        ))
    }.asStateFlow()

    @Synchronized
    override fun unregister(accountId: AccountId) {
        val nextGeneration = (generations[accountId] ?: 0L) + 1L
        generations[accountId] = nextGeneration
        jobs.remove(accountId)?.cancel()
        accountLocks.remove(accountId)
        sourceRegistry.remove(accountId)
        repository.invalidate(accountId, nextGeneration)
        states[accountId]?.value = states[accountId]?.value?.copy(isActive = false) ?: NotificationSyncState()
    }

    @Synchronized
    override fun removeAccount(accountId: AccountId) {
        unregister(accountId)
        repository.remove(accountId)
        states.remove(accountId)
    }

    override fun register(accountId: AccountId, source: SocialSource) {
        val token = synchronized(this) {
            jobs.remove(accountId)?.cancel()
            val generation = (generations[accountId] ?: 0L) + 1L
            generations[accountId] = generation
            val created = NotificationSyncToken(accountId, generation)
            repository.activate(created)
            sourceRegistry.register(created, source)
            states.getOrPut(accountId) { MutableStateFlow(NotificationSyncState()) }.also {
                it.value = it.value.copy(
                    unreadState = repository.observe(accountId).value.unreadState,
                    isActive = true,
                    error = null,
                )
            }
            created
        }
        val state = synchronized(this) { states.getValue(accountId) }
        val job = scope.launch {
            while (isActive && isCurrent(token)) {
                try {
                    // A socket is an optimization, not the freshness authority. Keep REST
                    // reconciliation active because readiness can be overstated and events can
                    // be missed while a stream reconnects.
                    val result = synchronize(token, source, NotificationQuery())
                    if (!isCurrent(token)) break
                    state.value = state.value.copy(
                        unreadState = result.unreadState.takeUnless { it is NotificationUnreadState.Unknown }
                            ?: repository.observe(accountId).value.unreadState,
                        lastUpdated = System.currentTimeMillis(),
                        delayed = result.delayed,
                        error = null,
                    )
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    if (!isCurrent(token)) break
                    state.value = state.value.copy(error = error.message ?: "Notification sync failed")
                }
                if (isCurrent(token)) delay(POLL_INTERVAL_MILLIS)
            }
        }
        synchronized(this) {
            if (isCurrent(token)) jobs[accountId] = job else job.cancel()
        }
    }

    override suspend fun refresh(accountId: AccountId, query: NotificationQuery): NotificationSyncResult {
        val token = repository.currentToken(accountId) ?: throw SourceError.Unauthorized
        val source = sourceRegistry.sourceFor(token) ?: throw SourceError.Unsupported("notifications.source")
        return synchronize(token, source, query)
    }

    override suspend fun loadOlder(accountId: AccountId, query: NotificationQuery): NotificationSyncResult {
        val token = repository.currentToken(accountId) ?: throw SourceError.Unauthorized
        val source = sourceRegistry.sourceFor(token) ?: throw SourceError.Unsupported("notifications.source")
        return lockFor(accountId).withLock { synchronizer.loadOlder(source, token, query) }
    }

    override suspend fun acknowledge(accountId: AccountId): NotificationAcknowledgement {
        val token = repository.currentToken(accountId) ?: throw SourceError.Unauthorized
        val source = sourceRegistry.sourceFor(token) ?: throw SourceError.Unsupported("notifications.source")
        return lockFor(accountId).withLock { synchronizer.applyAcknowledgement(source, token) }
    }

    suspend fun accept(event: Event): Boolean {
        val token = repository.currentToken(event.accountId) ?: return false
        return lockFor(event.accountId).withLock { repository.applyStreamEvent(token, event) }
    }

    fun setStreamConnected(accountId: AccountId, connected: Boolean, error: String? = null) {
        setStreamStatus(
            accountId,
            if (connected) NotificationStreamStatus.Ready else NotificationStreamStatus.Stopped,
            error,
        )
    }

    fun setStreamStatus(
        accountId: AccountId,
        status: NotificationStreamStatus,
        error: String? = null,
    ) {
        synchronized(this) {
            states[accountId]?.value = states[accountId]?.value?.copy(
                streamConnected = status == NotificationStreamStatus.Ready,
                streamError = error,
                streamStatus = status,
            ) ?: return
        }
    }

    private suspend fun synchronize(
        token: NotificationSyncToken,
        source: SocialSource,
        query: NotificationQuery,
    ): NotificationSyncResult {
        val result = lockFor(token.accountId).withLock {
        if (repository.checkpoint(token.accountId, query) == null) {
            synchronizer.establishBaseline(source, token, query)
        } else {
            synchronizer.catchUpNewer(source, token, query)
        }
        }
        deliveryScheduler.enqueueDelivery(token.accountId)
        return result
    }

    private fun lockFor(accountId: AccountId): Mutex = synchronized(this) {
        accountLocks.getOrPut(accountId, ::Mutex)
    }

    @Synchronized
    private fun isCurrent(token: NotificationSyncToken): Boolean = generations[token.accountId] == token.generation

    override fun close() {
        scope.cancel()
    }

    private companion object {
        const val POLL_INTERVAL_MILLIS = 60_000L
    }
}
