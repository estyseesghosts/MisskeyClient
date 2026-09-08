package me.foxtails.palustris.ui

import javax.inject.Inject
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
import me.foxtails.palustris.data.notifications.NotificationRepository
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.NotificationQuery
import me.foxtails.palustris.domain.NotificationSyncToken
import me.foxtails.palustris.domain.NotificationUnreadState
import me.foxtails.palustris.domain.SocialSource
import me.foxtails.palustris.domain.SourceError

data class AccountSyncState(
    val unreadState: NotificationUnreadState = NotificationUnreadState.Unknown,
    val lastUpdated: Long = 0,
    val isActive: Boolean = false,
    val error: String? = null,
) {
    /** Compatibility for callers that can display a number only when the server is precise. */
    @Deprecated("Use unreadState and preserve lower-bound/unknown semantics")
    val unreadCount: Int?
        get() = when (val state = unreadState) {
            is NotificationUnreadState.Exact -> state.count
            is NotificationUnreadState.AtLeast -> state.count
            else -> null
        }
}

/** Account lifetime boundary for notification polling and future push/stream workers. */
interface AccountNotificationSyncController {
    fun observeAccount(accountId: AccountId): StateFlow<AccountSyncState>
    fun register(accountId: AccountId, source: SocialSource)
    fun unregister(accountId: AccountId)
    fun removeAccount(accountId: AccountId)
}

class NoOpAccountNotificationSyncController : AccountNotificationSyncController {
    private val states = mutableMapOf<AccountId, MutableStateFlow<AccountSyncState>>()

    @Synchronized
    override fun observeAccount(accountId: AccountId): StateFlow<AccountSyncState> = states.getOrPut(accountId) {
        MutableStateFlow(AccountSyncState())
    }.asStateFlow()

    override fun register(accountId: AccountId, source: SocialSource) = Unit

    @Synchronized
    override fun unregister(accountId: AccountId) {
        states[accountId]?.value = states[accountId]?.value?.copy(isActive = false) ?: AccountSyncState()
    }

    override fun removeAccount(accountId: AccountId) {
        unregister(accountId)
        states.remove(accountId)
    }
}

class AccountSyncCoordinator @Inject constructor(
    private val repository: NotificationRepository,
) : AccountNotificationSyncController, AutoCloseable {
    constructor() : this(NotificationRepository())

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val states = mutableMapOf<AccountId, MutableStateFlow<AccountSyncState>>()
    private val jobs = mutableMapOf<AccountId, Job>()
    private val generations = mutableMapOf<AccountId, Long>()

    @Synchronized
    override fun observeAccount(accountId: AccountId): StateFlow<AccountSyncState> = states.getOrPut(accountId) {
        MutableStateFlow(AccountSyncState(
            unreadState = repository.observe(accountId).value.unreadState,
        ))
    }.asStateFlow()

    @Synchronized
    override fun unregister(accountId: AccountId) {
        val generation = generations[accountId] ?: 0L
        generations[accountId] = generation + 1L
        jobs.remove(accountId)?.cancel()
        repository.invalidate(accountId, generation)
        states[accountId]?.value = states[accountId]?.value?.copy(isActive = false) ?: AccountSyncState()
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
            val next = (generations[accountId] ?: 0L) + 1L
            generations[accountId] = next
            val syncToken = NotificationSyncToken(accountId, next)
            repository.activate(syncToken)
            states.getOrPut(accountId) { MutableStateFlow(AccountSyncState()) }.also {
                it.value = it.value.copy(
                    unreadState = repository.observe(accountId).value.unreadState,
                    isActive = true,
                    error = null,
                )
            }
            syncToken
        }
        val state = synchronized(this) { states.getValue(accountId) }
        val job = scope.launch {
            while (isActive && isCurrent(token)) {
                try {
                    val page = source.notifications(NotificationQuery())
                    if (!isCurrent(token)) break
                    repository.ingest(token, page)
                    if (!isCurrent(token)) break
                    val unread = runCatching { source.notificationUnreadState() }
                        .getOrElse { error ->
                            if (error is SourceError.Unsupported) NotificationUnreadState.Unknown else throw error
                        }
                    if (!isCurrent(token)) break
                    repository.updateUnreadState(token, unread)
                    if (!isCurrent(token)) break
                    updateState(token.accountId) { current ->
                        current.copy(
                            unreadState = unread,
                            lastUpdated = System.currentTimeMillis(),
                            error = null,
                        )
                    }
                } catch (error: CancellationException) {
                    throw error
                } catch (error: SourceError.Unsupported) {
                    if (!isCurrent(token)) break
                    updateState(token.accountId) { it.copy(lastUpdated = System.currentTimeMillis(), error = null) }
                } catch (error: Exception) {
                    if (!isCurrent(token)) break
                    updateState(token.accountId) { it.copy(error = error.message ?: "Notification sync failed") }
                }
                if (isCurrent(token)) delay(POLL_INTERVAL_MILLIS)
            }
        }
        synchronized(this) {
            if (isCurrent(token)) jobs[accountId] = job else job.cancel()
        }
    }

    @Synchronized
    private fun isCurrent(token: NotificationSyncToken): Boolean = generations[token.accountId] == token.generation

    private fun updateState(accountId: AccountId, transform: (AccountSyncState) -> AccountSyncState) {
        synchronized(this) {
            states[accountId]?.let { state -> state.value = transform(state.value) }
        }
    }

    override fun close() {
        scope.cancel()
    }

    private companion object {
        const val POLL_INTERVAL_MILLIS = 60_000L
    }
}
