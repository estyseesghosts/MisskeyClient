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
import kotlinx.coroutines.flow.collectLatest
import me.foxtails.palustris.data.preferences.InMemoryPostPreferencesRepository
import me.foxtails.palustris.data.preferences.InMemoryPhotoGridPreferencesRepository
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.adjustedBy
import me.foxtails.palustris.domain.CapabilityStatus
import me.foxtails.palustris.domain.CreatePostRequest
import me.foxtails.palustris.domain.DEFAULT_FAVOURITE_EMOJI
import me.foxtails.palustris.domain.EmojiChoice
import me.foxtails.palustris.domain.EntityId
import me.foxtails.palustris.domain.OwnedPost
import me.foxtails.palustris.domain.Post
import me.foxtails.palustris.domain.PostAction
import me.foxtails.palustris.domain.PostPreferencesRepository
import me.foxtails.palustris.domain.PhotoGridPreferencesRepository
import me.foxtails.palustris.domain.PrimaryFavouriteMode
import me.foxtails.palustris.domain.SocialSource
import me.foxtails.palustris.domain.Timeline
import me.foxtails.palustris.domain.effectiveTargetId
import me.foxtails.palustris.ui.posts.PostInteractionMutationOwner

@HiltViewModel(assistedFactory = FeedViewModel.Factory::class)
class FeedViewModel @AssistedInject constructor(
    @Assisted val accountId: AccountId,
    @Assisted private val source: SocialSource,
    private val syncCoordinator: AccountSyncCoordinator,
    private val postPreferencesRepository: PostPreferencesRepository,
    private val photoGridPreferencesRepository: PhotoGridPreferencesRepository,
    @Assisted private val sessionRevision: Long,
) : ViewModel() {
    constructor(
        accountId: AccountId,
        source: SocialSource,
        syncCoordinator: AccountSyncCoordinator,
    ) : this(
        accountId,
        source,
        syncCoordinator,
        InMemoryPostPreferencesRepository(),
        InMemoryPhotoGridPreferencesRepository(),
        0L,
    )

    private val _feed = MutableStateFlow(FeedState())
    val feed = _feed.asStateFlow()
    val sync = syncCoordinator.observeAccount(accountId)
    private var feedJob: Job? = null
    private var setupJob: Job? = null
    private var publishJob: Job? = null
    private var preferencesJob: Job? = null
    private var favouriteEmoji = DEFAULT_FAVOURITE_EMOJI
    private var stopped = false
    private val postProjectionListeners = mutableSetOf<(OwnedPost) -> Unit>()
    private val interactionMutations = PostInteractionMutationOwner(
        accountId = accountId,
        source = source,
        sessionRevision = sessionRevision,
        scope = viewModelScope,
        isActionAvailable = { action -> action in _feed.value.actions },
        favouriteEmoji = { favouriteEmoji },
        updatePost = ::updatePost,
        onFailure = ::feedFailure,
    )
    private val photoGridController = PhotoGridController(
        accountId = accountId,
        source = source,
        sessionRevision = sessionRevision,
        scope = viewModelScope,
        preferencesRepository = photoGridPreferencesRepository,
        applyFavouritePreference = ::applyFavouritePreference,
    )
    val photoGridFeed = photoGridController.state
    private val searchController = SearchController(
        source = source,
        scope = viewModelScope,
        applyFavouritePreference = ::applyFavouritePreference,
        onStateChanged = { search -> _feed.value = _feed.value.copy(accountSearch = search) },
    )

    init {
        setupJob = viewModelScope.launch {
            if (!stopped) {
                refresh()
            }
        }
        preferencesJob = viewModelScope.launch {
            postPreferencesRepository.observe(accountId).collectLatest { preferences ->
                favouriteEmoji = preferences.favouriteEmoji
                if (!stopped) updatePosts { post ->
                    if (source.capabilities.primaryFavourite.mode == PrimaryFavouriteMode.Reaction) {
                        post.copy(favourited = post.myReaction == favouriteEmoji)
                    } else {
                        post
                    }
                }
                _feed.value = _feed.value.copy(favouriteEmoji = favouriteEmoji)
            }
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
                val posts = page.items.distinctBy { it.id }.map(::applyFavouritePreference)
                _feed.value = FeedState(
                    posts = posts,
                    ownedPosts = posts.map { OwnedPost(accountId, it, sessionRevision) },
                    accountSearch = _feed.value.accountSearch,
                    timeline = timeline,
                    timelines = source.capabilities.timelines,
                    canPublish = source.capabilities.canPublish,
                    audiences = source.capabilities.audiences,
                    actions = effectiveActions(),
                    quoteStatus = source.capabilities.quotes,
                    savedPosts = source.capabilities.savedPosts,
                    favouriteEmoji = favouriteEmoji,
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
                val newPosts = page.items.map(::applyFavouritePreference)
                _feed.value = _feed.value.copy(
                    posts = (state.posts + newPosts).distinctBy { it.id },
                    ownedPosts = (state.ownedPosts + newPosts.map { OwnedPost(accountId, it, sessionRevision) })
                        .distinctBy { it.post.id },
                    loadingMore = false,
                    nextCursor = page.nextCursor?.takeUnless { it == cursor },
                )
            } catch (e: Exception) {
                feedFailure(e)
            }
        }
    }

    fun ensurePhotoGridLoaded() {
        photoGridController.ensureLoaded()
    }

    fun selectPhotoGridFeed(feed: PhotoGridFeed) {
        photoGridController.selectFeed(feed)
    }

    fun refreshPhotoGrid() {
        photoGridController.refresh()
    }

    fun loadMorePhotoGrid() {
        photoGridController.loadMore()
    }

    fun addPhotoGridHashtag(value: String, onSuccess: () -> Unit = {}) {
        photoGridController.addHashtag(value, onSuccess)
    }

    fun clearPhotoGridPreferenceError() {
        photoGridController.clearPreferenceError()
    }

    fun create(request: CreatePostRequest, onSuccess: (OwnedPost) -> Unit = {}) {
        if (stopped || _feed.value.publishing) return
        publishJob?.cancel()
        publishJob = viewModelScope.launch {
            _feed.value = _feed.value.copy(publishing = true, error = null)
            try {
                val created = source.create(request)
                _feed.value = _feed.value.copy(publishing = false, error = null)
                if (!stopped) onSuccess(OwnedPost(accountId, created, sessionRevision))
                refresh()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _feed.value = _feed.value.copy(publishing = false)
                feedFailure(e)
            }
        }
    }

    fun searchAccounts(query: String) {
        search(query)
    }

    fun search(query: String) {
        searchController.search(query)
    }

    fun loadMoreSearch() {
        searchController.loadMore()
    }

    fun favorite(ownedPost: OwnedPost) {
        interactionMutations.favorite(ownedPost)
    }

    fun reshare(ownedPost: OwnedPost) {
        interactionMutations.reshare(ownedPost)
    }

    fun react(ownedPost: OwnedPost, choice: EmojiChoice) {
        interactionMutations.react(ownedPost, choice)
    }

    fun bookmark(ownedPost: OwnedPost) {
        interactionMutations.bookmark(ownedPost)
    }

    /** Applies confirmed action fields to existing collections without inserting thread-only posts. */
    fun applyExternalPost(updated: OwnedPost) {
        if (stopped || updated.fetchedBy != accountId || updated.sessionRevision != sessionRevision) return
        val target = updated.effectiveTargetId()
        updateExternalPost(target, updated.post)
    }

    fun addPostProjectionListener(listener: (OwnedPost) -> Unit) {
        if (!stopped) postProjectionListeners += listener
    }

    fun applyPublishedPost(request: CreatePostRequest) {
        if (stopped) return
        request.replyTo?.let { parent ->
            updatePost(parent) { post ->
                post.copy(interactionCounts = post.interactionCounts.copy(
                    replyCount = post.interactionCounts.replyCount.adjustedBy(1),
                ))
            }
        }
        request.quoteOf?.let { target ->
            updatePost(target) { post ->
                post.copy(interactionCounts = post.interactionCounts.copy(
                    quoteRepostCount = post.interactionCounts.quoteRepostCount.adjustedBy(1),
                ))
            }
        }
    }

    fun removePostProjectionListener(listener: (OwnedPost) -> Unit) {
        postProjectionListeners -= listener
    }

    fun stop() {
        if (stopped) return
        stopped = true
        setupJob?.cancel()
        feedJob?.cancel()
        searchController.stop()
        publishJob?.cancel()
        preferencesJob?.cancel()
        interactionMutations.stop()
        photoGridController.stop()
        interactionMutations.stop()
    }

    private fun effectiveActions(): Set<PostAction> = buildSet {
        addAll(source.capabilities.actions)
        if (source.capabilities.primaryFavourite.status == CapabilityStatus.Supported) {
            add(PostAction.Favorite)
        }
        if (source.capabilities.savedPosts?.status == CapabilityStatus.Supported) {
            add(PostAction.Bookmark)
        }
    }.intersect(ClientReadyPostActions)

    private fun applyFavouritePreference(post: Post): Post = if (
        source.capabilities.primaryFavourite.mode == PrimaryFavouriteMode.Reaction
    ) {
        post.copy(favourited = post.myReaction == favouriteEmoji)
    } else {
        post
    }

    private fun updatePost(id: EntityId, transform: (Post) -> Post) {
        val currentOwnedPosts = _feed.value.ownedPosts
        val projected = currentOwnedPosts.map { owned ->
            if (owned.effectiveTargetId() == id && owned.fetchedBy == accountId && owned.sessionRevision == sessionRevision) {
                owned.copy(post = transform(owned.post))
            } else {
                owned
            }
        }
        _feed.value = _feed.value.copy(
            posts = _feed.value.posts.map { if (it.id == id || it.actionTargetId == id) transform(it) else it },
            ownedPosts = projected,
        )
        projected.filterIndexed { index, owned -> owned !== currentOwnedPosts[index] }
            .forEach { updated -> postProjectionListeners.toList().forEach { it(updated) } }
        photoGridController.updatePost(id, transform)
        searchController.updatePost(id, transform)
    }

    private fun updateExternalPost(target: EntityId, incoming: Post) {
        _feed.value = _feed.value.copy(
            posts = _feed.value.posts.map { post ->
                if (post.id == target || post.actionTargetId == target) mergeExternalActionFields(post, incoming) else post
            },
            ownedPosts = _feed.value.ownedPosts.map { owned ->
                if (owned.fetchedBy == accountId && (owned.post.id == target || owned.effectiveTargetId() == target)) {
                    owned.copy(post = mergeExternalActionFields(owned.post, incoming))
                } else owned
            },
        )
        photoGridController.updateExternalPost(target, incoming)
        searchController.updateExternalPost(target, incoming)
    }

    private fun mergeExternalActionFields(existing: Post, incoming: Post): Post = existing.copy(
        favourited = incoming.favourited,
        myReaction = incoming.myReaction,
        selectedReactions = incoming.selectedReactions,
        reactions = incoming.reactions.ifEmpty { existing.reactions },
        reposted = incoming.reposted,
        interactionCounts = existing.interactionCounts.merge(incoming.interactionCounts),
        ownRepostId = incoming.ownRepostId,
        saved = incoming.saved,
    )

    private fun updatePosts(transform: (Post) -> Post) {
        val transformed = _feed.value.posts.associate { it.id to transform(it) }
        _feed.value = _feed.value.copy(
            posts = _feed.value.posts.map { transformed[it.id] ?: it },
            ownedPosts = _feed.value.ownedPosts.map { owned ->
                if (owned.fetchedBy == accountId) owned.copy(post = transformed[owned.post.id] ?: owned.post) else owned
            },
        )
        photoGridController.updatePosts(transform)
        searchController.updatePosts(transform)
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

    @AssistedFactory
    interface Factory {
        fun create(accountId: AccountId, source: SocialSource, sessionRevision: Long): FeedViewModel
    }
}
