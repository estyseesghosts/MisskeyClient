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
    @Assisted private val collection: SavedPostsCollection = SavedPostsCollection.Bookmarks,
) : ViewModel() {
    private val _state = MutableStateFlow(SavedPostsUiState(collection = collection))
    val state = _state.asStateFlow()
    private var requestJob: Job? = null
    private val unsaveJobs = mutableMapOf<String, Job>()
    private val reactionJobs = mutableMapOf<String, Job>()
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
            val status = when (collection) {
                SavedPostsCollection.Bookmarks -> capability?.status
                SavedPostsCollection.Likes -> source.capabilities.likedPosts
            }
            if (status == CapabilityStatus.Unsupported) {
                val label = if (collection == SavedPostsCollection.Likes) "Likes" else "Saved posts"
                _state.value = SavedPostsUiState(collection, kind, error = "$label are not supported by this server.")
                return@launch
            }
            if (status == CapabilityStatus.Denied) {
                _state.value = SavedPostsUiState(collection = collection, kind = kind, permissionRequired = true)
                return@launch
            }
            _state.value = SavedPostsUiState(collection = collection, kind = kind, loading = true)
            load(kind, null, replace = true)
        }
    }

    fun loadMore() {
        if (stopped) return
        val current = _state.value
        val cursor = current.nextCursor ?: return
        if (current.loading || current.loadingMore) return
        if (current.error != null) {
            requestedCursors.remove(cursor)
            _state.value = current.copy(error = null)
        }
        if (!requestedCursors.add(cursor)) return
        requestJob?.cancel()
        requestJob = viewModelScope.launch {
            _state.value = current.copy(loadingMore = true, error = null)
            load(current.kind, cursor, replace = false)
        }
    }

    fun unsave(ownedPost: OwnedPost) {
        if (collection != SavedPostsCollection.Bookmarks) return
        if (stopped || ownedPost.fetchedBy != accountId) return
        val key = "${ownedPost.post.id.connection}/${ownedPost.post.id.value}"
        unsaveJobs[key]?.cancel()
        val job = viewModelScope.launch {
            try {
                source.unsave(ownedPost.post.actionTargetId ?: ownedPost.post.id)
                _state.value = _state.value.copy(posts = _state.value.posts.filterNot { it.post.id == ownedPost.post.id })
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                _state.value = _state.value.copy(error = sourceErrorMessage(error), needsSignIn = requiresSignIn(error))
            } finally {
                if (unsaveJobs[key] === coroutineContext[Job]) unsaveJobs.remove(key)
            }
        }
        unsaveJobs[key] = job
    }

    fun toggleFavourite(ownedPost: OwnedPost) {
        if (collection != SavedPostsCollection.Likes) return
        if (stopped || ownedPost.fetchedBy != accountId) return
        val key = "${ownedPost.post.id.connection}/${ownedPost.post.id.value}"
        unsaveJobs[key]?.cancel()
        val before = ownedPost.post
        val selected = before.favourited
        updatePost(before.id) { it.copy(favourited = !selected) }
        val job = viewModelScope.launch {
            try {
                val target = before.actionTargetId ?: before.id
                if (selected) source.unfavorite(target) else source.favorite(target)
                if (selected) {
                    _state.value = _state.value.copy(posts = _state.value.posts.filterNot { it.post.id == before.id })
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                updatePost(before.id) { before }
                _state.value = _state.value.copy(error = sourceErrorMessage(error), needsSignIn = requiresSignIn(error))
            } finally {
                if (unsaveJobs[key] === coroutineContext[Job]) unsaveJobs.remove(key)
            }
        }
        unsaveJobs[key] = job
    }

    /** Collection-local reaction mutation using the same reducer and source rules as the feed. */
    fun react(ownedPost: OwnedPost, choice: me.foxtails.palustris.domain.EmojiChoice) {
        if (stopped || ownedPost.fetchedBy != accountId) return
        val selectionMode = source.capabilities.emoji.selectionMode
        val identity = choice.submissionValue
        val selected = ownedPost.post.selectedReactions.any { it.submissionValue == identity } ||
            ownedPost.post.myReaction == identity
        val key = "${ownedPost.post.id.connection}/${ownedPost.post.id.value}"
        if (reactionJobs[key]?.isActive == true) return
        val before = ownedPost.post
        val optimistic = me.foxtails.palustris.domain.PostReactionReducer.apply(
            before, choice, !selected, selectionMode,
        )
        updatePost(before.id) { optimistic }
        val job = viewModelScope.launch {
            try {
                val target = before.actionTargetId ?: before.id
                val previousSelections = before.selectedReactions.ifEmpty {
                    before.myReaction?.let { mine ->
                        listOf(me.foxtails.palustris.domain.EmojiChoice(
                            mine, mine, before.reactions.firstOrNull { it.emoji == mine }?.emojiMetadata,
                        ))
                    }.orEmpty()
                }
                if (selected) {
                    source.removeReaction(target, choice)
                } else {
                    if (selectionMode != me.foxtails.palustris.domain.ReactionSelectionMode.Independent) {
                        previousSelections.filterNot { it.submissionValue == identity }
                            .forEach { previous -> source.removeReaction(target, previous) }
                    }
                    source.react(target, choice)
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                updatePost(before.id) { before }
                _state.value = _state.value.copy(error = sourceErrorMessage(error), needsSignIn = requiresSignIn(error))
            } finally {
                if (reactionJobs[key] === coroutineContext[Job]) reactionJobs.remove(key)
            }
        }
        reactionJobs[key] = job
    }

    fun stop() {
        if (stopped) return
        stopped = true
        requestJob?.cancel()
        unsaveJobs.values.forEach(Job::cancel)
        reactionJobs.values.forEach(Job::cancel)
    }

    private fun updatePost(id: me.foxtails.palustris.domain.EntityId, transform: (me.foxtails.palustris.domain.Post) -> me.foxtails.palustris.domain.Post) {
        _state.value = _state.value.copy(posts = _state.value.posts.map { owned ->
            if (owned.post.id == id && owned.fetchedBy == accountId) owned.copy(post = transform(owned.post)) else owned
        })
    }

    private suspend fun load(kind: SavedPostsKind, cursor: String?, replace: Boolean) {
        try {
            val page = when (collection) {
                SavedPostsCollection.Bookmarks -> source.savedPosts(cursor)
                SavedPostsCollection.Likes -> source.likedPosts(cursor)
            }
            if (stopped) return
            val rows = page.items.map { post ->
                OwnedPost(
                    accountId,
                    post.copy(
                        saved = collection == SavedPostsCollection.Bookmarks,
                        favourited = post.favourited || collection == SavedPostsCollection.Likes,
                    ),
                )
            }
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
            if (cursor != null) requestedCursors.remove(cursor)
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
        fun create(
            accountId: AccountId,
            source: SocialSource,
            collection: SavedPostsCollection,
        ): SavedPostsViewModel
    }
}
