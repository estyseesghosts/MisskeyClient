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
import me.foxtails.palustris.data.notifications.NotificationStorageHealth
import me.foxtails.palustris.data.notifications.NotificationSyncIntents
import me.foxtails.palustris.data.notifications.NotificationSynchronizer
import me.foxtails.palustris.data.notifications.SourceBackedNotificationSyncIntents
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.EntityId
import me.foxtails.palustris.domain.Notification
import me.foxtails.palustris.domain.NotificationActionState
import me.foxtails.palustris.domain.NotificationCheckpoint
import me.foxtails.palustris.domain.NotificationQuery
import me.foxtails.palustris.domain.NotificationSyncToken
import me.foxtails.palustris.domain.NotificationUnreadState
import me.foxtails.palustris.domain.OwnedPost
import me.foxtails.palustris.domain.Post
import me.foxtails.palustris.domain.SocialSource
import me.foxtails.palustris.domain.SourceError
import me.foxtails.palustris.domain.adjustedBy
import me.foxtails.palustris.domain.effectiveTargetId
import me.foxtails.palustris.domain.mergeExternalActionFields

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
    /** True while stored notification state is corrupt or unreadable. Writes and alerts stay blocked. */
    val storageUnavailable: Boolean = false,
    val actionStates: Map<EntityId, NotificationActionState> = emptyMap(),
    val actionErrors: Map<EntityId, String> = emptyMap(),
    val postOverlays: Map<EntityId, Post> = emptyMap(),
) {
    val isEmpty: Boolean get() = items.isEmpty() && !loading && !refreshing
}

@HiltViewModel(assistedFactory = NotificationsViewModel.Factory::class)
class NotificationsViewModel @AssistedInject constructor(
    @Assisted val accountId: AccountId,
    @Assisted private val source: SocialSource,
    @Assisted private val sessionRevision: Long,
    private val repository: NotificationRepository,
    private val syncIntents: NotificationSyncIntents,
    private val uiStrings: UiStrings = UiStrings.Default,
) : ViewModel() {
    constructor(
        accountId: AccountId,
        source: SocialSource,
        repository: NotificationRepository,
        sessionRevision: Long = 0L,
    ) : this(
        accountId,
        source,
        sessionRevision,
        repository,
        SourceBackedNotificationSyncIntents(accountId, source, repository, NotificationSynchronizer(repository)),
    )

    private val _state = MutableStateFlow(NotificationsUiState())
    val state = _state.asStateFlow()
    private val actionJobs = ConcurrentHashMap<EntityId, Job>()
    private var observeJob: Job? = null
    private var storageJob: Job? = null
    private var refreshJob: Job? = null
    private var olderJob: Job? = null
    private var acknowledgementJob: Job? = null
    private var stopped = false
    private val query = MutableStateFlow(NotificationQuery())

    /**
     * Request identity for visible refresh and paging state. The epoch advances on every
     * refresh and page request, so two same-query requests with a null or unchanged
     * continuation stay distinguishable. A stale completion changes no current state.
     */
    private var requestEpoch = 0L

    init {
        observeJob = viewModelScope.launch {
            query.collectLatest { selectedQuery ->
                repository.observeInbox(accountId, selectedQuery).collectLatest { snapshot ->
                    _state.value = _state.value.copy(
                        items = snapshot.items.map(::applyPostOverlay),
                        query = selectedQuery,
                        unreadState = snapshot.unreadState,
                        checkpoint = snapshot.checkpoint,
                        syncDelayed = snapshot.hasIncompleteSync,
                    )
                }
            }
        }
        storageJob = viewModelScope.launch {
            repository.observeStorageHealth(accountId).collectLatest { health ->
                _state.value = _state.value.copy(
                    storageUnavailable = health != NotificationStorageHealth.Healthy,
                )
            }
        }
        refresh(showIndicator = false)
    }

    fun selectQuery(selectedQuery: NotificationQuery) {
        if (stopped) return
        if (query.value == selectedQuery) return
        query.value = selectedQuery
        _state.value = _state.value.copy(query = selectedQuery, checkpoint = null, error = null)
        refresh(showIndicator = false)
    }

    fun refresh(showIndicator: Boolean = true) {
        if (stopped) return
        // Capture the query identity before launch and reserve the loading slot synchronously.
        // A queued refresh must not start behind the reserved one.
        val epoch = ++requestEpoch
        val selectedQuery = query.value
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            // A corrupt or unreadable account must recover storage before any network refresh.
            // The same explicit refresh action is the retry path; no side effect is replayed.
            if (!repository.retry(accountId)) {
                if (epoch != requestEpoch || stopped) return@launch
                _state.value = _state.value.copy(
                    loading = false,
                    refreshing = false,
                    loadingMore = false,
                    error = null,
                    storageUnavailable = true,
                )
                return@launch
            }
            _state.value = _state.value.copy(storageUnavailable = false)
            val hasCache = _state.value.items.isNotEmpty()
            _state.value = _state.value.copy(
                loading = !hasCache,
                refreshing = showIndicator,
                loadingMore = false,
                error = null,
            )
            try {
                val result = syncIntents.refresh(accountId, selectedQuery)
                if (epoch != requestEpoch || stopped) return@launch
                _state.value = _state.value.copy(
                    loading = false,
                    refreshing = false,
                    syncDelayed = result.delayed,
                    error = null,
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (epoch != requestEpoch || stopped) return@launch
                _state.value = _state.value.copy(
                    loading = false,
                    refreshing = false,
                    error = uiStrings.sourceError(error),
                )
            }
        }
    }

    fun loadOlder() {
        if (stopped) return
        if (_state.value.loadingMore) return
        // Reserve the page slot synchronously. A queued paging call returns above instead of
        // cancelling the page in flight.
        val epoch = ++requestEpoch
        val selectedQuery = query.value
        _state.value = _state.value.copy(loadingMore = true, error = null)
        olderJob = viewModelScope.launch {
            try {
                val result = syncIntents.loadOlder(accountId, selectedQuery)
                if (epoch != requestEpoch || stopped) return@launch
                _state.value = _state.value.copy(loadingMore = false, syncDelayed = result.delayed)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (epoch != requestEpoch || stopped) return@launch
                _state.value = _state.value.copy(loadingMore = false, error = uiStrings.sourceError(error))
            }
        }
    }

    fun markAllRead() {
        if (stopped) return
        acknowledgementJob?.cancel()
        acknowledgementJob = viewModelScope.launch {
            try {
                syncIntents.acknowledge(accountId)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _state.value = _state.value.copy(error = uiStrings.sourceError(error))
            }
        }
    }

    fun markSeen(id: EntityId? = null) {
        if (stopped) return
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

    /** Keeps optimistic interaction fields local to notification presentation. */
    fun applyExternalPost(updated: OwnedPost) {
        if (updated.fetchedBy != accountId || updated.sessionRevision != sessionRevision) return
        val target = updated.effectiveTargetId()
        val overlays = _state.value.postOverlays +
            (target to updated.post) + (updated.post.id to updated.post)
        _state.value = _state.value.copy(
            postOverlays = overlays,
            items = _state.value.items.map { notification -> applyPostOverlay(notification, overlays) },
        )
    }

    fun applyPublishedPost(request: me.foxtails.palustris.domain.CreatePostRequest) {
        _state.value = _state.value.copy(items = _state.value.items.map { notification ->
            val post = notification.post ?: return@map notification
            val updated = when {
                request.replyTo != null && (post.id == request.replyTo || post.effectiveTargetId() == request.replyTo) ->
                    post.copy(interactionCounts = post.interactionCounts.copy(
                        replyCount = post.interactionCounts.replyCount.adjustedBy(1),
                    ))
                request.quoteOf != null && (post.id == request.quoteOf || post.effectiveTargetId() == request.quoteOf) ->
                    post.copy(interactionCounts = post.interactionCounts.copy(
                        quoteRepostCount = post.interactionCounts.quoteRepostCount.adjustedBy(1),
                    ))
                else -> post
            }
            notification.copy(post = updated)
        })
    }

    private fun applyPostOverlay(notification: Notification): Notification =
        applyPostOverlay(notification, _state.value.postOverlays)

    private fun applyPostOverlay(
        notification: Notification,
        overlays: Map<EntityId, Post>,
    ): Notification {
        val post = notification.post ?: return notification
        val overlay = overlays[post.id] ?: overlays[post.effectiveTargetId()] ?: return notification
        return notification.copy(post = post.mergeExternalActionFields(overlay))
    }

    fun respondToFollowRequest(notification: Notification, accept: Boolean) {
        val actor = notification.actor ?: return
        runRowAction(notification.id) {
            source.respondToFollowRequest(actor.id, accept)
            repository.dismissFromInbox(currentToken(), notification.id, remoteApplied = true)
        }
    }

    private fun runRowAction(id: EntityId, operation: suspend () -> Unit) {
        if (stopped) return
        if (actionJobs[id]?.isActive == true) return
        _state.value = _state.value.copy(
            actionStates = _state.value.actionStates + (id to NotificationActionState.Running),
            actionErrors = _state.value.actionErrors - id,
        )
        val job = viewModelScope.launch {
            try {
                operation()
                if (stopped) return@launch
                _state.value = _state.value.copy(actionStates = _state.value.actionStates + (id to NotificationActionState.Succeeded))
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (stopped) return@launch
                _state.value = _state.value.copy(
                    actionStates = _state.value.actionStates + (id to NotificationActionState.Failed),
                    actionErrors = _state.value.actionErrors + (id to uiStrings.sourceError(error)),
                )
            } finally {
                actionJobs.remove(id)
            }
        }
        actionJobs[id] = job
    }

    private fun currentToken(): NotificationSyncToken = repository.currentToken(accountId)
        ?: error("Notification account is not active")

    /** Releases inbox observation and request jobs. Late completions publish nothing. */
    fun stop() {
        if (stopped) return
        stopped = true
        observeJob?.cancel()
        storageJob?.cancel()
        refreshJob?.cancel()
        olderJob?.cancel()
        acknowledgementJob?.cancel()
        actionJobs.values.forEach(Job::cancel)
        actionJobs.clear()
    }

    override fun onCleared() {
        stop()
        super.onCleared()
    }

    @AssistedFactory
    interface Factory {
        fun create(accountId: AccountId, source: SocialSource, sessionRevision: Long): NotificationsViewModel
    }
}
