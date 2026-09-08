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
import kotlinx.coroutines.launch
import me.foxtails.palustris.domain.CapabilityStatus
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.OwnedPost
import me.foxtails.palustris.domain.PostAction
import me.foxtails.palustris.domain.SavedPostsKind
import me.foxtails.palustris.domain.SocialSource
import me.foxtails.palustris.domain.SourceError

@HiltViewModel(assistedFactory = SavedPostsViewModel.Factory::class)
class SavedPostsViewModel @AssistedInject constructor(
    @Assisted val accountId: AccountId,
    @Assisted private val source: SocialSource,
) : ViewModel() {
    private val _state = MutableStateFlow(SavedPostsUiState())
    val state = _state.asStateFlow()
    private var requestJob: Job? = null
    private var unsaveJob: Job? = null
    private val requestedCursors = mutableSetOf<String?>()
    private var stopped = false

    init {
        refresh()
    }

    fun refresh() {
        if (stopped) return
        requestJob?.cancel()
        requestedCursors.clear()
        requestJob = viewModelScope.launch {
            val capability = source.capabilities.savedPosts
            val kind = capability?.kind ?: SavedPostsKind.Bookmarks
            if (capability?.status == CapabilityStatus.Unsupported) {
                _state.value = SavedPostsUiState(kind = kind, error = "Saved posts are not supported by this server.")
                return@launch
            }
            if (capability?.status == CapabilityStatus.Denied) {
                _state.value = SavedPostsUiState(kind = kind, permissionRequired = true)
                return@launch
            }
            _state.value = SavedPostsUiState(kind = kind, loading = true)
            load(kind, null, replace = true)
        }
    }

    fun loadMore() {
        if (stopped) return
        val current = _state.value
        val cursor = current.nextCursor ?: return
        if (current.loading || current.loadingMore || current.error != null || !requestedCursors.add(cursor)) return
        requestJob?.cancel()
        requestJob = viewModelScope.launch {
            _state.value = current.copy(loadingMore = true, error = null)
            load(current.kind, cursor, replace = false)
        }
    }

    fun unsave(ownedPost: OwnedPost) {
        if (stopped || ownedPost.fetchedBy != accountId) return
        unsaveJob?.cancel()
        unsaveJob = viewModelScope.launch {
            try {
                source.unsave(ownedPost.post.id)
                _state.value = _state.value.copy(posts = _state.value.posts.filterNot { it.post.id == ownedPost.post.id })
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                _state.value = _state.value.copy(error = sourceErrorMessage(error), needsSignIn = requiresSignIn(error))
            }
        }
    }

    fun stop() {
        if (stopped) return
        stopped = true
        requestJob?.cancel()
        unsaveJob?.cancel()
    }

    private suspend fun load(kind: SavedPostsKind, cursor: String?, replace: Boolean) {
        try {
            val page = source.savedPosts(cursor)
            if (stopped) return
            val rows = page.items.map { OwnedPost(accountId, it.copy(saved = true)) }
            val current = _state.value
            val combined = if (replace) rows else (current.posts + rows).distinctBy { it.post.id }
            _state.value = current.copy(
                kind = kind,
                posts = combined,
                loading = false,
                loadingMore = false,
                nextCursor = page.nextCursor?.takeUnless { it == cursor },
                error = null,
                permissionRequired = false,
                needsSignIn = false,
            )
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            val permission = error is SourceError.Unauthorized || error is SourceError.Unsupported &&
                error.feature.contains("permission", ignoreCase = true)
            _state.value = _state.value.copy(
                loading = false,
                loadingMore = false,
                error = sourceErrorMessage(error),
                needsSignIn = requiresSignIn(error),
                permissionRequired = permission,
            )
        }
    }

    override fun onCleared() {
        stop()
        super.onCleared()
    }

    @AssistedFactory
    interface Factory {
        fun create(accountId: AccountId, source: SocialSource): SavedPostsViewModel
    }
}
