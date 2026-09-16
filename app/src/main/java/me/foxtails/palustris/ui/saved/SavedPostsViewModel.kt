package me.foxtails.palustris.ui.saved

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
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.CapabilityStatus
import me.foxtails.palustris.domain.DEFAULT_FAVOURITE_EMOJI
import me.foxtails.palustris.domain.EntityId
import me.foxtails.palustris.domain.OwnedPost
import me.foxtails.palustris.domain.SavedPostsKind
import me.foxtails.palustris.domain.SocialSource
import me.foxtails.palustris.domain.SourceError
import me.foxtails.palustris.domain.adjustedBy
import me.foxtails.palustris.domain.effectiveTargetId
import me.foxtails.palustris.domain.mergeExternalActionFields
import me.foxtails.palustris.ui.UiStrings
import me.foxtails.palustris.ui.posts.PostInteractionExecutionAuthority
import me.foxtails.palustris.ui.posts.PostInteractionMutationOwner
import me.foxtails.palustris.ui.requiresSignIn

@HiltViewModel(assistedFactory = SavedPostsViewModel.Factory::class)
class SavedPostsViewModel @AssistedInject constructor(
    @Assisted val accountId: AccountId,
    @Assisted private val source: SocialSource,
    @Assisted private val collection: SavedPostsCollection = SavedPostsCollection.Bookmarks,
    @Assisted private val sessionRevision: Long = 0L,
    private val executionAuthority: PostInteractionExecutionAuthority = PostInteractionExecutionAuthority(),
    private val uiStrings: UiStrings = UiStrings.Default,
) : ViewModel() {
    private val _state = MutableStateFlow(SavedPostsUiState(collection = collection))
    val state = _state.asStateFlow()
    private var refreshJob: Job? = null
    private var pageJob: Job? = null

    /** Advances on refresh and stop. Binds paging publication authority. */
    private var collectionEpoch = 0L

    /** Accepted page cursors for the active epoch. Protects against cursor cycles. */
    private val acceptedCursors = mutableSetOf<String?>()

    /**
     * Rows with a confirmed membership removal. A refresh never reinserts them from an
     * older page. Entries leave the overlay once a fresh page stops returning them.
     */
    private val confirmedRemovals = mutableSetOf<EntityId>()
    private var stopped = false
    private val interactionMutations = PostInteractionMutationOwner(
        accountId = accountId,
        source = source,
        sessionRevision = sessionRevision,
        scope = viewModelScope,
        isActionAvailable = { true },
        // Collections have no preference owner. The feed path keeps the real emoji.
        favouriteEmoji = { DEFAULT_FAVOURITE_EMOJI },
        updatePost = { _, target, transform -> updatePostByTarget(target, transform) },
        onFailure = ::mutationFailure,
        executionAuthority = executionAuthority,
    )

    init {
        refresh()
    }

    fun refresh() {
        if (stopped) return
        refreshJob?.cancel()
        pageJob?.cancel()
        val epoch = ++collectionEpoch
        acceptedCursors.clear()
        refreshJob = viewModelScope.launch {
            val capability = source.capabilities.savedPosts
            val kind = capability?.kind ?: SavedPostsKind.Bookmarks
            val status = when (collection) {
                SavedPostsCollection.Bookmarks -> capability?.status
                SavedPostsCollection.Likes -> source.capabilities.likedPosts
            }
            if (status == CapabilityStatus.Unsupported) {
                _state.value = SavedPostsUiState(
                    collection,
                    kind,
                    error = uiStrings.savedPostsUnsupported(collection == SavedPostsCollection.Likes),
                )
                return@launch
            }
            if (status == CapabilityStatus.Denied) {
                _state.value = SavedPostsUiState(collection = collection, kind = kind, permissionRequired = true)
                return@launch
            }
            // Reserve the refresh slot synchronously. A queued page cannot start behind it.
            _state.value = SavedPostsUiState(collection = collection, kind = kind, loading = true)
            load(kind, null, replace = true, epoch)
        }
    }

    fun loadMore() {
        if (stopped) return
        val current = _state.value
        val cursor = current.nextCursor ?: return
        if (current.loading || current.loadingMore) return
        // Paging cannot start during refresh. Reserve the page slot synchronously.
        if (refreshJob?.isActive == true) return
        val epoch = collectionEpoch
        _state.value = current.copy(loadingMore = true, error = null)
        pageJob?.cancel()
        pageJob = viewModelScope.launch {
            load(current.kind, cursor, replace = false, epoch)
        }
    }

    fun unsave(ownedPost: OwnedPost) {
        if (collection != SavedPostsCollection.Bookmarks) return
        interactionMutations.bookmark(ownedPost) { result ->
            if (result.selected == false) removeMembership(ownedPost.post.id)
        }
    }

    fun toggleFavourite(ownedPost: OwnedPost) {
        if (collection != SavedPostsCollection.Likes) return
        interactionMutations.favorite(ownedPost) { result ->
            if (result.selected == false) removeMembership(ownedPost.post.id)
        }
    }

    /** Collection reaction mutation shares the session-bound owner with the feed. */
    fun react(ownedPost: OwnedPost, choice: me.foxtails.palustris.domain.EmojiChoice) {
        interactionMutations.react(ownedPost, choice)
    }

    private fun removeMembership(id: EntityId) {
        confirmedRemovals += id
        _state.value = _state.value.copy(posts = _state.value.posts.filterNot { it.post.id == id })
    }

    fun applyExternalPost(updated: OwnedPost) {
        if (stopped || updated.fetchedBy != accountId || updated.sessionRevision != sessionRevision) return
        val target = updated.effectiveTargetId()
        _state.value = _state.value.copy(
            posts = _state.value.posts.map { owned ->
                if (owned.fetchedBy == accountId && owned.sessionRevision == sessionRevision &&
                    (owned.post.id == target || owned.effectiveTargetId() == target)
                ) {
                    owned.copy(post = mergeExternalActionFields(owned.post, updated.post))
                } else owned
            },
        )
    }

    fun applyPublishedPost(request: me.foxtails.palustris.domain.CreatePostRequest) {
        request.replyTo?.let { parent -> incrementKnownReplyCount(parent) }
        request.quoteOf?.let { target -> incrementKnownQuoteCount(target) }
    }

    fun stop() {
        if (stopped) return
        stopped = true
        // Advance the epoch so a late page cannot publish after teardown,
        // mirroring FeedViewModel.stop.
        collectionEpoch += 1
        refreshJob?.cancel()
        pageJob?.cancel()
        interactionMutations.stop()
    }

    private fun mutationFailure(error: Exception) {
        if (stopped) return
        _state.value = _state.value.copy(error = uiStrings.sourceError(error), needsSignIn = requiresSignIn(error))
    }

    private fun updatePostByTarget(id: EntityId, transform: (me.foxtails.palustris.domain.Post) -> me.foxtails.palustris.domain.Post) {
        _state.value = _state.value.copy(posts = _state.value.posts.map { owned ->
            if ((owned.post.id == id || owned.effectiveTargetId() == id) &&
                owned.fetchedBy == accountId && owned.sessionRevision == sessionRevision
            ) {
                owned.copy(post = transform(owned.post))
            } else owned
        })
    }

    private fun updatePost(id: me.foxtails.palustris.domain.EntityId, transform: (me.foxtails.palustris.domain.Post) -> me.foxtails.palustris.domain.Post) {
        updatePostByTarget(id, transform)
    }

    private fun incrementKnownReplyCount(target: me.foxtails.palustris.domain.EntityId) = updatePost(target) { post ->
        post.copy(interactionCounts = post.interactionCounts.copy(
            replyCount = post.interactionCounts.replyCount.adjustedBy(1),
        ))
    }

    private fun incrementKnownQuoteCount(target: me.foxtails.palustris.domain.EntityId) = updatePost(target) { post ->
        post.copy(interactionCounts = post.interactionCounts.copy(
            quoteRepostCount = post.interactionCounts.quoteRepostCount.adjustedBy(1),
        ))
    }

    private fun mergeExternalActionFields(
        existing: me.foxtails.palustris.domain.Post,
        incoming: me.foxtails.palustris.domain.Post,
    ) = existing.mergeExternalActionFields(incoming)

    private suspend fun load(kind: SavedPostsKind, cursor: String?, replace: Boolean, epoch: Long) {
        try {
            val page = when (collection) {
                SavedPostsCollection.Bookmarks -> source.savedPosts(cursor)
                SavedPostsCollection.Likes -> source.likedPosts(cursor)
            }
            if (epoch != collectionEpoch || stopped) return
            // A paged request must still own the current continuation. A newer page
            // that advanced the cursor first makes this page stale in the same epoch.
            if (!replace && _state.value.nextCursor != cursor) return
            val returnedIds = page.items.mapTo(mutableSetOf()) { it.id }
            val rows = page.items.map { post ->
                OwnedPost(
                    accountId,
                    post.copy(
                        saved = collection == SavedPostsCollection.Bookmarks,
                        favourited = post.favourited || collection == SavedPostsCollection.Likes,
                    ),
                    sessionRevision,
                )
            }.filterNot { it.post.id in confirmedRemovals }
            if (replace) {
                // A fresh page that stops returning a removed row agrees with the
                // removal. Entries the server still returns stay hidden behind it.
                confirmedRemovals.removeAll { id -> id !in returnedIds }
            }
            val repeatedCursor = page.nextCursor != null &&
                (page.nextCursor == cursor || !acceptedCursors.add(page.nextCursor))
            val current = _state.value
            val combined = if (replace) rows else (current.posts + rows).distinctBy { it.post.id }
            _state.value = current.copy(
                kind = kind,
                posts = combined.filterNot { it.post.id in confirmedRemovals },
                loading = false,
                loadingMore = false,
                nextCursor = page.nextCursor.takeUnless { repeatedCursor },
                error = null,
                permissionRequired = false,
                needsSignIn = false,
            )
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            if (epoch != collectionEpoch || stopped) return
            val permission = error is SourceError.Unauthorized || error is SourceError.Unsupported &&
                error.feature.contains("permission", ignoreCase = true)
            _state.value = _state.value.copy(
                loading = false,
                loadingMore = false,
                error = uiStrings.sourceError(error),
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
            sessionRevision: Long,
        ): SavedPostsViewModel
    }
}
