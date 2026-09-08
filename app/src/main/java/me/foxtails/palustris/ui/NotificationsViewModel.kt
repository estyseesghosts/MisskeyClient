package me.foxtails.palustris.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import me.foxtails.palustris.data.notifications.NotificationRepository
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.EntityId
import me.foxtails.palustris.domain.Notification
import me.foxtails.palustris.domain.NotificationCheckpoint
import me.foxtails.palustris.domain.NotificationQuery
import me.foxtails.palustris.domain.NotificationSyncToken
import me.foxtails.palustris.domain.NotificationUnreadState
import me.foxtails.palustris.domain.SourceError
import me.foxtails.palustris.domain.SocialSource

data class NotificationsUiState(
    val items: List<Notification> = emptyList(),
    val unreadState: NotificationUnreadState = NotificationUnreadState.Unknown,
    val checkpoint: NotificationCheckpoint? = null,
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val loadingMore: Boolean = false,
    val error: String? = null,
) {
    val isEmpty: Boolean get() = items.isEmpty() && !loading && !refreshing
}

@HiltViewModel(assistedFactory = NotificationsViewModel.Factory::class)
class NotificationsViewModel @AssistedInject constructor(
    @Assisted val accountId: AccountId,
    @Assisted private val source: SocialSource,
    private val repository: NotificationRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(NotificationsUiState())
    val state = _state.asStateFlow()
    private var requestJob: Job? = null

    init {
        viewModelScope.launch {
            repository.observe(accountId).collectLatest { snapshot ->
                _state.value = _state.value.copy(
                    items = snapshot.items,
                    unreadState = snapshot.unreadState,
                    checkpoint = snapshot.checkpoint,
                )
            }
        }
        refresh()
    }

    fun refresh() {
        requestJob?.cancel()
        requestJob = viewModelScope.launch {
            val hasCache = _state.value.items.isNotEmpty()
            _state.value = _state.value.copy(
                loading = !hasCache,
                refreshing = hasCache,
                error = null,
            )
            try {
                val query = NotificationQuery()
                val page = source.notifications(query)
                val token = currentToken()
                repository.ingest(token, page)
                val unread = source.notificationUnreadState()
                repository.updateUnreadState(token, unread)
                _state.value = _state.value.copy(loading = false, refreshing = false, error = null)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _state.value = _state.value.copy(loading = false, refreshing = false, error = sourceErrorMessage(error))
            }
        }
    }

    fun loadOlder() {
        val checkpoint = _state.value.checkpoint ?: return
        if (_state.value.loadingMore || checkpoint.oldest == null) return
        requestJob?.cancel()
        requestJob = viewModelScope.launch {
            _state.value = _state.value.copy(loadingMore = true, error = null)
            try {
                val page = source.fetchOlderNotifications(NotificationQuery(), checkpoint)
                repository.ingest(currentToken(), page)
                _state.value = _state.value.copy(loadingMore = false)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _state.value = _state.value.copy(loadingMore = false, error = sourceErrorMessage(error))
            }
        }
    }

    fun markAllRead() {
        requestJob?.cancel()
        requestJob = viewModelScope.launch {
            try {
                val acknowledgement = source.acknowledgeNotifications()
                repository.acknowledge(currentToken(), acknowledgement)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _state.value = _state.value.copy(error = sourceErrorMessage(error))
            }
        }
    }

    fun markSeen(id: EntityId? = null) {
        viewModelScope.launch { repository.markSeen(currentToken(), id) }
    }

    fun dismiss(notification: Notification) {
        requestJob?.cancel()
        requestJob = viewModelScope.launch {
            try {
                val token = currentToken()
                try {
                    source.dismissNotification(notification.id)
                } catch (_: SourceError.Unsupported) {
                    // Keep dismissal useful for protocols without server-side removal.
                }
                repository.dismiss(token, notification.id)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _state.value = _state.value.copy(error = sourceErrorMessage(error))
            }
        }
    }

    private fun currentToken(): NotificationSyncToken = repository.currentToken(accountId)
        ?: NotificationSyncToken(accountId, 0L)

    @AssistedFactory
    interface Factory {
        fun create(accountId: AccountId, source: SocialSource): NotificationsViewModel
    }
}
