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

    @Synchronized
    fun observeAccount(accountId: AccountId): StateFlow<AccountSyncState> = states.getOrPut(accountId) {
        MutableStateFlow(AccountSyncState())
    }.asStateFlow()

    @Synchronized
    fun unregister(accountId: AccountId) {
        jobs.remove(accountId)?.cancel()
        states[accountId]?.value = states.getValue(accountId).value.copy(isActive = false)
    }

    suspend fun register(accountId: AccountId, source: SocialSource) {
        unregister(accountId)
        val state = synchronized(this) {
            states.getOrPut(accountId) { MutableStateFlow(AccountSyncState()) }
        }
        state.value = state.value.copy(isActive = true)
        val job = scope.launch {
            while (isActive) {
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
        synchronized(this) { jobs[accountId] = job }
    }

    override fun close() {
        scope.cancel()
    }

    private companion object {
        const val POLL_INTERVAL_MILLIS = 60_000L
    }
}
