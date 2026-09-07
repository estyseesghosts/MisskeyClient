package me.foxtails.palustris.ui

import javax.inject.Inject
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
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.SocialSource
import me.foxtails.palustris.domain.SourceError

data class AccountSyncState(
    val unreadCount: Int = 0,
    val lastUpdated: Long = 0,
    val isActive: Boolean = false,
)

class AccountSyncCoordinator @Inject constructor() : AutoCloseable {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val states = mutableMapOf<AccountId, MutableStateFlow<AccountSyncState>>()
    private val jobs = mutableMapOf<AccountId, Job>()
    private val generations = mutableMapOf<AccountId, Long>()

    @Synchronized
    fun observeAccount(accountId: AccountId): StateFlow<AccountSyncState> = states.getOrPut(accountId) {
        MutableStateFlow(AccountSyncState())
    }.asStateFlow()

    @Synchronized
    fun unregister(accountId: AccountId) {
        generations[accountId] = (generations[accountId] ?: 0L) + 1L
        jobs.remove(accountId)?.cancel()
        states[accountId]?.value = states[accountId]?.value?.copy(isActive = false) ?: AccountSyncState()
    }

    suspend fun register(accountId: AccountId, source: SocialSource) {
        val generation = synchronized(this) {
            jobs.remove(accountId)?.cancel()
            val next = (generations[accountId] ?: 0L) + 1L
            generations[accountId] = next
            states.getOrPut(accountId) { MutableStateFlow(AccountSyncState()) }
                .also { it.value = it.value.copy(isActive = true) }
            next
        }
        val state = synchronized(this) { states.getValue(accountId) }
        val job = scope.launch {
            while (isActive && isCurrent(accountId, generation)) {
                try {
                    val page = source.notifications()
                    state.value = state.value.copy(
                        unreadCount = page.items.count { !it.isRead },
                        lastUpdated = System.currentTimeMillis(),
                    )
                } catch (_: SourceError.Unsupported) {
                    state.value = state.value.copy(lastUpdated = System.currentTimeMillis())
                } catch (_: Exception) {
                    // Sync failures must not cancel other accounts' jobs.
                }
                delay(POLL_INTERVAL_MILLIS)
            }
        }
        synchronized(this) {
            if (isCurrent(accountId, generation)) jobs[accountId] = job else job.cancel()
        }
    }

    @Synchronized
    private fun isCurrent(accountId: AccountId, generation: Long): Boolean = generations[accountId] == generation

    override fun close() {
        scope.cancel()
    }

    private companion object {
        const val POLL_INTERVAL_MILLIS = 60_000L
    }
}
