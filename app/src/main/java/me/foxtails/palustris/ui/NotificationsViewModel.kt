package me.foxtails.palustris.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import me.foxtails.palustris.data.notifications.NotificationRepository
import me.foxtails.palustris.data.notifications.NotificationSynchronizer
import me.foxtails.palustris.data.notifications.NotificationSyncIntents
import me.foxtails.palustris.data.notifications.SourceBackedNotificationSyncIntents
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.EntityId
import me.foxtails.palustris.domain.Notification
import me.foxtails.palustris.domain.NotificationActionState
import me.foxtails.palustris.domain.NotificationCheckpoint
import me.foxtails.palustris.domain.NotificationQuery
import me.foxtails.palustris.domain.NotificationSyncToken
import me.foxtails.palustris.domain.NotificationUnreadState
import me.foxtails.palustris.domain.SourceError
import me.foxtails.palustris.domain.SocialSource

data class NotificationsUiState(
    val items: List<Notification> = emptyList(),
    val query: NotificationQuery = NotificationQuery(),
    val unreadState: NotificationUnreadState = NotificationUnreadState.Unknown,
    val checkpoint: NotificationCheckpoint? = null,
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val loadingMore: Boolean = false,
    val syncDelayed: Boolean = false,
    val error: String? = null,
    val actionStates: Map<EntityId, NotificationActionState> = emptyMap(),
    val actionErrors: Map<EntityId, String> = emptyMap(),
) {
    val isEmpty: Boolean get() = items.isEmpty() && !loading && !refreshing
}
@HiltViewModel(assistedFactory = NotificationsViewModel.Factory::class)
class NotificationsViewModel @AssistedInject constructor(
    @Assisted val accountId: AccountId,
    @Assisted private val source: SocialSource,
    private val repository: NotificationRepository,
    private val syncIntents: NotificationSyncIntents,
) : ViewModel() {
    constructor(
        accountId: AccountId,
        source: SocialSource,
        repository: NotificationRepository,
    ) : this(
        accountId,
        source,
        repository,
        SourceBackedNotificationSyncIntents(accountId, source, repository, NotificationSynchronizer(repository)),
    )

    private val _state = MutableStateFlow(NotificationsUiState())
    val state = _state.asStateFlow()
    private val actionJobs = ConcurrentHashMap<EntityId, Job>()
    private var refreshJob: Job? = null
    private var olderJob: Job? = null
    private var acknowledgementJob: Job? = null
    private val query = MutableStateFlow(NotificationQuery())

    init {
        viewModelScope.launch {
            query.collectLatest { selectedQuery ->
                repository.observeInbox(accountId, selectedQuery).collectLatest { snapshot ->
                    _state.value = _state.value.copy(
                        items = snapshot.items,
                        query = selectedQuery,
                        unreadState = snapshot.unreadState,
                        checkpoint = snapshot.checkpoint,
                        syncDelayed = snapshot.hasIncompleteSync,
                    )
                }
            }
        }
        refresh()
    }

    fun selectQuery(selectedQuery: NotificationQuery) {
        if (query.value == selectedQuery) return
        query.value = selectedQuery
        _state.value = _state.value.copy(query = selectedQuery, checkpoint = null, error = null)
        refresh()
    }

    fun refresh() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            val hasCache = _state.value.items.isNotEmpty()
            _state.value = _state.value.copy(
                loading = !hasCache,
                refreshing = hasCache,
                error = null,
            )
            try {
                val selectedQuery = query.value
                val result = syncIntents.refresh(accountId, selectedQuery)
                _state.value = _state.value.copy(
                    loading = false,
                    refreshing = false,
                    syncDelayed = result.delayed,
                    error = null,
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _state.value = _state.value.copy(
                    loading = false,
                    refreshing = false,
                    error = sourceErrorMessage(error),
                )
            }
        }
    }

    fun loadOlder() {
        if (_state.value.loadingMore) return
        olderJob?.cancel()
        olderJob = viewModelScope.launch {
            _state.value = _state.value.copy(loadingMore = true, error = null)
            try {
                val result = syncIntents.loadOlder(accountId, query.value)
                _state.value = _state.value.copy(loadingMore = false, syncDelayed = result.delayed)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _state.value = _state.value.copy(loadingMore = false, error = sourceErrorMessage(error))
            }
        }
    }

    fun markAllRead() {
        acknowledgementJob?.cancel()
        acknowledgementJob = viewModelScope.launch {
            try {
                syncIntents.acknowledge(accountId)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _state.value = _state.value.copy(error = sourceErrorMessage(error))
            }
        }
    }

    fun markSeen(id: EntityId? = null) {
        val token = currentToken()
        viewModelScope.launch {
            repository.markLocallySeen(token, id?.let(::setOf) ?: _state.value.items.map { it.id }.toSet())
        }
    }

    fun dismiss(notification: Notification) {
        runRowAction(notification.id) {
            var remoteApplied = false
            try {
                source.dismissNotification(notification.id)
                remoteApplied = true
            } catch (_: SourceError.Unsupported) {
                // A local tombstone is the truthful fallback for servers without dismissal.
            }
            repository.dismissFromInbox(currentToken(), notification.id, remoteApplied)
        }
    }

    fun respondToFollowRequest(notification: Notification, accept: Boolean) {
        val actor = notification.actor ?: return
        runRowAction(notification.id) {
            source.respondToFollowRequest(actor.id, accept)
            repository.dismissFromInbox(currentToken(), notification.id, remoteApplied = true)
        }
    }

    private fun runRowAction(id: EntityId, operation: suspend () -> Unit) {
        if (actionJobs[id]?.isActive == true) return
        _state.value = _state.value.copy(
            actionStates = _state.value.actionStates + (id to NotificationActionState.Running),
            actionErrors = _state.value.actionErrors - id,
        )
        val job = viewModelScope.launch {
            try {
                operation()
                _state.value = _state.value.copy(actionStates = _state.value.actionStates + (id to NotificationActionState.Succeeded))
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _state.value = _state.value.copy(
                    actionStates = _state.value.actionStates + (id to NotificationActionState.Failed),
                    actionErrors = _state.value.actionErrors + (id to sourceErrorMessage(error)),
                )
            } finally {
                actionJobs.remove(id)
            }
        }
        actionJobs[id] = job
    }

    private fun currentToken(): NotificationSyncToken = repository.currentToken(accountId)
        ?: error("Notification account is not active")

    @AssistedFactory
    interface Factory {
        fun create(accountId: AccountId, source: SocialSource): NotificationsViewModel
    }
}
