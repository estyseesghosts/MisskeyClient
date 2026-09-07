package me.foxtails.palustris.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.CreatePostRequest
import me.foxtails.palustris.domain.EntityId
import me.foxtails.palustris.domain.OwnedPost
import me.foxtails.palustris.domain.PostAction
import me.foxtails.palustris.domain.SocialSource
import me.foxtails.palustris.domain.Timeline

@HiltViewModel(assistedFactory = FeedViewModel.Factory::class)
class FeedViewModel @AssistedInject constructor(
    @Assisted val accountId: AccountId,
    @Assisted private val source: SocialSource,
    private val syncCoordinator: AccountSyncCoordinator,
) : ViewModel() {
    private val _feed = MutableStateFlow(FeedState())
    val feed = _feed.asStateFlow()
    val sync = syncCoordinator.observeAccount(accountId)
    private var feedJob: Job? = null
    private var setupJob: Job? = null
    private var publishJob: Job? = null
    private var actionJob: Job? = null
    private val completedActions = mutableSetOf<ActionKey>()
    private var stopped = false

    init {
        setupJob = viewModelScope.launch {
            syncCoordinator.register(accountId, source)
            if (!stopped) refresh()
        }
    }

    fun refresh(timeline: Timeline = _feed.value.timeline) {
        if (stopped) return
        feedJob?.cancel()
        feedJob = viewModelScope.launch {
            val previousState = _feed.value
            _feed.value = _feed.value.copy(
                posts = emptyList(),
                ownedPosts = emptyList(),
                timeline = timeline,
                loading = true,
                loadingMore = false,
                nextCursor = null,
                error = null,
            )
            try {
                val page = source.timeline(timeline)
                val posts = page.items.distinctBy { it.id }
                _feed.value = FeedState(
                    posts = posts,
                    ownedPosts = posts.map { OwnedPost(accountId, it) },
                    timeline = timeline,
                    timelines = source.capabilities.timelines,
                    canPublish = source.capabilities.canPublish,
                    actions = source.capabilities.actions.intersect(ClientReadyPostActions),
                    publishing = _feed.value.publishing,
                    nextCursor = page.nextCursor,
                )
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _feed.value = previousState.copy(publishing = _feed.value.publishing)
                feedFailure(e)
            }
        }
    }

    fun loadMore(timeline: Timeline = _feed.value.timeline) {
        if (stopped) return
        val state = _feed.value
        if (timeline != state.timeline) return
        val cursor = state.nextCursor ?: return
        if (state.loading || state.loadingMore || state.needsSignIn) return
        feedJob = viewModelScope.launch {
            _feed.value = state.copy(loadingMore = true, error = null)
            try {
                val page = source.timeline(state.timeline, cursor)
                _feed.value = _feed.value.copy(
                    posts = (state.posts + page.items).distinctBy { it.id },
                    ownedPosts = (state.ownedPosts + page.items.map { OwnedPost(accountId, it) })
                        .distinctBy { it.post.id },
                    loadingMore = false,
                    nextCursor = page.nextCursor?.takeUnless { it == cursor },
                )
            } catch (e: Exception) {
                feedFailure(e)
            }
        }
    }

    fun create(request: CreatePostRequest, onSuccess: () -> Unit = {}) {
        if (stopped || _feed.value.publishing) return
        publishJob?.cancel()
        publishJob = viewModelScope.launch {
            _feed.value = _feed.value.copy(publishing = true, error = null)
            try {
                source.create(request)
                _feed.value = _feed.value.copy(publishing = false, error = null)
                onSuccess()
                refresh()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _feed.value = _feed.value.copy(publishing = false)
                feedFailure(e)
            }
        }
    }

    fun favorite(ownedPost: OwnedPost) {
        runAction(ownedPost, PostAction.Favorite) { source.favorite(ownedPost.post.id) }
    }

    fun reshare(ownedPost: OwnedPost) {
        runAction(ownedPost, PostAction.Reshare) { source.renote(ownedPost.post.id) }
    }

    fun react(ownedPost: OwnedPost, emoji: String) {
        runAction(ownedPost, PostAction.React) { source.react(ownedPost.post.id, emoji) }
    }

    fun stop() {
        if (stopped) return
        stopped = true
        setupJob?.cancel()
        feedJob?.cancel()
        publishJob?.cancel()
        actionJob?.cancel()
        syncCoordinator.unregister(accountId)
    }

    private fun runAction(ownedPost: OwnedPost, action: PostAction, operation: suspend () -> Unit) {
        if (stopped || actionJob?.isActive == true || ownedPost.fetchedBy != accountId) return
        if (action !in _feed.value.actions) return
        val key = ActionKey(action, ownedPost.post.id)
        if (!completedActions.add(key)) return
        actionJob = viewModelScope.launch {
            _feed.value = _feed.value.copy(error = null, needsSignIn = false)
            try {
                operation()
            } catch (e: Exception) {
                completedActions.remove(key)
                if (e is CancellationException) throw e
                feedFailure(e)
            }
        }
    }

    private fun feedFailure(e: Exception) {
        if (e is CancellationException) throw e
        _feed.value = _feed.value.copy(
            loading = false,
            loadingMore = false,
            error = sourceErrorMessage(e),
            needsSignIn = requiresSignIn(e),
        )
    }

    override fun onCleared() {
        stop()
        super.onCleared()
    }

    private data class ActionKey(val action: PostAction, val postId: EntityId)

    @AssistedFactory
    interface Factory {
        fun create(accountId: AccountId, source: SocialSource): FeedViewModel
    }
}
