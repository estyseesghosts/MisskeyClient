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
import me.foxtails.palustris.domain.CapabilityStatus
import me.foxtails.palustris.domain.CreatePostRequest
import me.foxtails.palustris.domain.DEFAULT_FAVOURITE_EMOJI
import me.foxtails.palustris.domain.EmojiChoice
import me.foxtails.palustris.domain.EntityId
import me.foxtails.palustris.domain.OwnedPost
import me.foxtails.palustris.domain.Post
import me.foxtails.palustris.domain.PostAction
import me.foxtails.palustris.domain.PostActionResult
import me.foxtails.palustris.domain.PostPreferencesRepository
import me.foxtails.palustris.domain.PhotoGridPreferencesRepository
import me.foxtails.palustris.domain.PhotoGridPreferences
import me.foxtails.palustris.domain.PostReactionReducer
import me.foxtails.palustris.domain.PrimaryFavouriteMode
import me.foxtails.palustris.domain.ReactionSelectionMode
import me.foxtails.palustris.domain.SocialSource
import me.foxtails.palustris.domain.Timeline
import me.foxtails.palustris.domain.effectiveTargetId
import me.foxtails.palustris.domain.isExactHashtag
import me.foxtails.palustris.domain.hashtagIdentity
import me.foxtails.palustris.domain.validateExactHashtag
import me.foxtails.palustris.domain.timelineDisplayOrder
import me.foxtails.palustris.domain.timelineStatus

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
    private var searchJob: Job? = null
    private var publishJob: Job? = null
    private var preferencesJob: Job? = null
    private var photoGridPreferencesJob: Job? = null
    private var photoGridJob: Job? = null
    private var photoGridGeneration = 0L
    private val consumedPhotoGridCursors = mutableSetOf<String>()
    private val _photoGridFeed = MutableStateFlow(PhotoGridFeedState())
    val photoGridFeed = _photoGridFeed.asStateFlow()
    private val actionJobs = mutableMapOf<ActionKey, Job>()
    private var favouriteEmoji = DEFAULT_FAVOURITE_EMOJI
    private var stopped = false

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
        photoGridPreferencesJob = viewModelScope.launch {
            photoGridPreferencesRepository.observe(accountId).collectLatest { preferences ->
                if (!stopped) {
                    _photoGridFeed.value = _photoGridFeed.value.copy(
                        savedHashtags = preferences.hashtags,
                        preferenceLoading = false,
                    )
                }
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
        if (stopped || _photoGridFeed.value.initialLoadComplete || photoGridJob?.isActive == true) return
        val selected = _photoGridFeed.value.selectedFeed
        _photoGridFeed.value = _photoGridFeed.value.copy(availableTimelines = availablePhotoGridTimelines())
        startPhotoGridRequest(selected, ++photoGridGeneration, cursor = null)
    }

    fun selectPhotoGridFeed(feed: PhotoGridFeed) {
        if (stopped || !isValidPhotoGridFeed(feed)) return
        val current = _photoGridFeed.value
        if (current.selectedFeed == feed && (current.loading || current.initialLoadComplete)) return
        photoGridJob?.cancel()
        consumedPhotoGridCursors.clear()
        val generation = ++photoGridGeneration
        _photoGridFeed.value = current.copy(
            selectedFeed = feed,
            posts = emptyList(),
            initialLoadComplete = false,
            loading = true,
            loadingMore = false,
            nextCursor = null,
            error = null,
            needsSignIn = false,
        )
        startPhotoGridRequest(feed, generation, cursor = null)
    }

    fun refreshPhotoGrid() {
        if (stopped) return
        val selected = _photoGridFeed.value.selectedFeed
        photoGridJob?.cancel()
        consumedPhotoGridCursors.clear()
        val generation = ++photoGridGeneration
        _photoGridFeed.value = _photoGridFeed.value.copy(
            posts = emptyList(),
            initialLoadComplete = false,
            loading = true,
            loadingMore = false,
            nextCursor = null,
            error = null,
            needsSignIn = false,
        )
        startPhotoGridRequest(selected, generation, cursor = null)
    }

    fun loadMorePhotoGrid() {
        if (stopped) return
        val state = _photoGridFeed.value
        val cursor = state.nextCursor ?: return
        if (state.loading || state.loadingMore || state.needsSignIn || !consumedPhotoGridCursors.add(cursor)) return
        startPhotoGridRequest(state.selectedFeed, photoGridGeneration, cursor)
    }

    fun addPhotoGridHashtag(value: String, onSuccess: () -> Unit = {}) {
        if (stopped || _photoGridFeed.value.preferenceSaving) return
        val accepted = runCatching { validateExactHashtag(value) }.getOrElse {
            _photoGridFeed.value = _photoGridFeed.value.copy(preferenceError = "invalid")
            return
        }
        val existing = _photoGridFeed.value.savedHashtags.firstOrNull {
            hashtagIdentity(it) == hashtagIdentity(accepted)
        }
        if (existing != null) {
            _photoGridFeed.value = _photoGridFeed.value.copy(preferenceError = null)
            selectPhotoGridFeed(PhotoGridFeed.Hashtag(existing))
            onSuccess()
            return
        }
        viewModelScope.launch {
            _photoGridFeed.value = _photoGridFeed.value.copy(preferenceSaving = true, preferenceError = null)
            try {
                photoGridPreferencesRepository.update(accountId) { preferences ->
                    preferences.copy(hashtags = preferences.hashtags + accepted)
                }
                val saved = (_photoGridFeed.value.savedHashtags + accepted).distinctBy(::hashtagIdentity)
                _photoGridFeed.value = _photoGridFeed.value.copy(
                    savedHashtags = saved,
                    preferenceSaving = false,
                    preferenceError = null,
                )
                selectPhotoGridFeed(PhotoGridFeed.Hashtag(accepted))
                onSuccess()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _photoGridFeed.value = _photoGridFeed.value.copy(
                    preferenceSaving = false,
                    preferenceError = "save",
                )
            }
        }
    }

    fun clearPhotoGridPreferenceError() {
        _photoGridFeed.value = _photoGridFeed.value.copy(preferenceError = null)
    }

    private fun isValidPhotoGridFeed(feed: PhotoGridFeed): Boolean = when (feed) {
        is PhotoGridFeed.TimelineFeed -> feed.timeline in availablePhotoGridTimelines()
        is PhotoGridFeed.Hashtag -> _photoGridFeed.value.savedHashtags.any {
            hashtagIdentity(it) == runCatching { hashtagIdentity(feed.tag) }.getOrNull()
        }
    }

    private fun availablePhotoGridTimelines(): List<Timeline> {
        val available = timelineDisplayOrder.filter {
            it in source.capabilities.timelines && source.capabilities.timelineStatus(it) == CapabilityStatus.Supported
        }
        return available.ifEmpty { listOf(Timeline.Home) }
    }

    private fun startPhotoGridRequest(feed: PhotoGridFeed, generation: Long, cursor: String?) {
        photoGridJob = viewModelScope.launch {
            if (stopped || generation != photoGridGeneration || _photoGridFeed.value.selectedFeed != feed) return@launch
            _photoGridFeed.value = _photoGridFeed.value.copy(
                loading = cursor == null,
                loadingMore = cursor != null,
                error = null,
                needsSignIn = false,
            )
            try {
                val page = when (feed) {
                    is PhotoGridFeed.TimelineFeed -> source.timeline(feed.timeline, cursor)
                    is PhotoGridFeed.Hashtag -> source.searchHashtag(feed.tag, cursor)
                }
                if (stopped || generation != photoGridGeneration || _photoGridFeed.value.selectedFeed != feed) return@launch
                val fetched = page.items.distinctBy { it.id }.map { OwnedPost(accountId, applyFavouritePreference(it), sessionRevision) }
                val current = _photoGridFeed.value
                val merged = if (cursor == null) fetched else (current.posts + fetched).distinctBy { it.post.id }
                val nextCursor = page.nextCursor?.takeUnless {
                    it == cursor || it in consumedPhotoGridCursors
                }
                _photoGridFeed.value = current.copy(
                    posts = merged,
                    availableTimelines = availablePhotoGridTimelines(),
                    initialLoadComplete = true,
                    loading = false,
                    loadingMore = false,
                    nextCursor = nextCursor,
                    error = null,
                    needsSignIn = false,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (stopped || generation != photoGridGeneration || _photoGridFeed.value.selectedFeed != feed) return@launch
                if (cursor != null) consumedPhotoGridCursors.remove(cursor)
                _photoGridFeed.value = _photoGridFeed.value.copy(
                    loading = false,
                    loadingMore = false,
                    error = sourceErrorMessage(e),
                    needsSignIn = requiresSignIn(e),
                )
            }
        }
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
        if (stopped) return
        val normalized = query.trim()
        if (normalized.isBlank()) return
        searchJob?.cancel()
        val isHashtag = isExactHashtag(normalized)
        _feed.value = _feed.value.copy(
            accountSearch = AccountSearchState(
                query = normalized,
                tagQuery = normalized.removePrefix("#").takeIf { isHashtag },
                loading = true,
            ),
        )
        searchJob = viewModelScope.launch {
            if (isHashtag) searchHashtag(normalized)
            else searchAccount(normalized)
        }
    }

    private suspend fun searchAccount(normalized: String) {
        _feed.value = _feed.value.copy(accountSearch = AccountSearchState(query = normalized, loading = true))
        try {
            val accounts = source.searchAccounts(normalized).distinctBy { it.id }
            _feed.value = _feed.value.copy(accountSearch = AccountSearchState(query = normalized, accounts = accounts))
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            _feed.value = _feed.value.copy(accountSearch = AccountSearchState(query = normalized, error = sourceErrorMessage(e)))
        }
    }

    private suspend fun searchHashtag(normalized: String) {
        _feed.value = _feed.value.copy(accountSearch = AccountSearchState(query = normalized, tagQuery = normalized.removePrefix("#"), loading = true))
        try {
            val page = source.searchHashtag(normalized)
            _feed.value = _feed.value.copy(
                accountSearch = AccountSearchState(
                    query = normalized,
                    posts = page.items.distinctBy { it.id }.map(::applyFavouritePreference),
                    tagQuery = normalized.removePrefix("#"),
                    nextCursor = page.nextCursor,
                ),
            )
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            _feed.value = _feed.value.copy(accountSearch = AccountSearchState(query = normalized, tagQuery = normalized.removePrefix("#"), error = sourceErrorMessage(e)))
        }
    }

    fun loadMoreSearch() {
        if (stopped) return
        val state = _feed.value.accountSearch
        val tag = state.tagQuery ?: return
        val cursor = state.nextCursor ?: return
        if (state.loading || state.loadingMore) return
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _feed.value = _feed.value.copy(accountSearch = state.copy(loadingMore = true, error = null))
            try {
                val page = source.searchHashtag(tag, cursor)
                val current = _feed.value.accountSearch
                _feed.value = _feed.value.copy(accountSearch = current.copy(
                    posts = (current.posts + page.items.map(::applyFavouritePreference)).distinctBy { it.id },
                    loadingMore = false,
                    nextCursor = page.nextCursor?.takeUnless { it == cursor },
                ))
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _feed.value = _feed.value.copy(accountSearch = _feed.value.accountSearch.copy(loadingMore = false, error = sourceErrorMessage(e)))
            }
        }
    }

    fun favorite(ownedPost: OwnedPost) {
        val selected = !ownedPost.post.favourited
        val actionTargetId = ownedPost.post.actionTargetId ?: ownedPost.post.id
        runAction(
            ownedPost = ownedPost,
            action = PostAction.Favorite,
            optimistic = { post ->
                post.copy(
                    favourited = selected,
                    myReaction = if (source.capabilities.primaryFavourite.mode == PrimaryFavouriteMode.Reaction) {
                        if (selected) favouriteEmoji else null
                    } else post.myReaction,
                )
            },
            operation = {
                if (selected && source.capabilities.primaryFavourite.mode == PrimaryFavouriteMode.Reaction) {
                    val previousReaction = ownedPost.post.myReaction
                    if (previousReaction != null && previousReaction != favouriteEmoji) {
                        source.removeReaction(actionTargetId, previousReaction)
                    }
                }
                source.setPrimaryFavourite(actionTargetId, favouriteEmoji, selected)
            },
        )
    }

    fun reshare(ownedPost: OwnedPost) {
        val selected = !ownedPost.post.reposted
        val actionTargetId = ownedPost.post.actionTargetId ?: ownedPost.post.id
        runAction(
            ownedPost = ownedPost,
            action = PostAction.Reshare,
            optimistic = { post -> post.copy(reposted = selected, reshareCount = (post.reshareCount + if (selected) 1 else -1).coerceAtLeast(0)) },
            operation = { source.setReshared(actionTargetId, selected, ownedPost.post.ownRepostId) },
        )
    }

    fun react(ownedPost: OwnedPost, choice: EmojiChoice) {
        val selectionMode = source.capabilities.emoji.selectionMode
        val identity = choice.submissionValue
        val selected = ownedPost.post.selectedReactions.any { it.submissionValue == identity } ||
            ownedPost.post.myReaction == identity
        val primaryEmoji = if (source.capabilities.primaryFavourite.mode == PrimaryFavouriteMode.Reaction) {
            favouriteEmoji
        } else {
            null
        }
        val actionTargetId = ownedPost.post.actionTargetId ?: ownedPost.post.id
        runAction(
            ownedPost = ownedPost,
            action = PostAction.React,
            optimistic = { post ->
                PostReactionReducer.apply(post, choice, !selected, selectionMode, primaryEmoji)
            },
            operation = {
                val previousSelections = ownedPost.post.selectedReactions.ifEmpty {
                    ownedPost.post.myReaction?.let { mine ->
                        listOf(EmojiChoice(mine, mine, ownedPost.post.reactions.firstOrNull { it.emoji == mine }?.emojiMetadata))
                    }.orEmpty()
                }
                if (selected) {
                    source.removeReaction(actionTargetId, choice)
                } else {
                    if (selectionMode != ReactionSelectionMode.Independent) {
                        previousSelections.filterNot { it.submissionValue == identity }
                            .forEach { previous -> source.removeReaction(actionTargetId, previous) }
                    }
                    source.react(actionTargetId, choice)
                }
                PostActionResult(selected = !selected)
            },
        )
    }

    fun bookmark(ownedPost: OwnedPost) {
        val selected = !ownedPost.post.saved
        val actionTargetId = ownedPost.post.actionTargetId ?: ownedPost.post.id
        runAction(
            ownedPost = ownedPost,
            action = PostAction.Bookmark,
            optimistic = { post -> post.copy(saved = selected) },
            operation = { source.setSaved(actionTargetId, selected) },
        )
    }

    /** Applies confirmed action fields to existing collections without inserting thread-only posts. */
    fun applyExternalPost(updated: OwnedPost) {
        if (stopped || updated.fetchedBy != accountId || updated.sessionRevision != sessionRevision) return
        val target = updated.effectiveTargetId()
        updateExternalPost(target, updated.post)
    }

    fun stop() {
        if (stopped) return
        stopped = true
        setupJob?.cancel()
        feedJob?.cancel()
        searchJob?.cancel()
        publishJob?.cancel()
        preferencesJob?.cancel()
        photoGridPreferencesJob?.cancel()
        photoGridJob?.cancel()
        photoGridGeneration++
        actionJobs.values.forEach { it.cancel() }
        actionJobs.clear()
    }

    private fun runAction(
        ownedPost: OwnedPost,
        action: PostAction,
        optimistic: (Post) -> Post,
        operation: suspend () -> PostActionResult,
    ) {
        if (stopped || ownedPost.fetchedBy != accountId) return
        if (action !in _feed.value.actions) return
        val key = ActionKey(action, ownedPost.post.id)
        if (actionJobs[key]?.isActive == true) return
        val before = ownedPost.post
        val optimisticPost = optimistic(before)
        updatePost(ownedPost.post.id) { optimisticPost }
        val job = viewModelScope.launch {
            _feed.value = _feed.value.copy(error = null, needsSignIn = false)
            try {
                val result = operation()
                updatePost(ownedPost.post.id) { current -> reconcileAction(action, current, result) }
            } catch (e: Exception) {
                updatePost(ownedPost.post.id) { before }
                if (e is CancellationException) throw e
                feedFailure(e)
            } finally {
                actionJobs.remove(key)
            }
        }
        actionJobs[key] = job
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

    private fun reconcileAction(
        action: PostAction,
        current: Post,
        result: PostActionResult,
    ): Post {
        val serverPost = result.post?.takeIf { it.id == current.id }
        val base = serverPost ?: current
        return when (action) {
            PostAction.Favorite -> base.copy(
                favourited = result.selected ?: base.favourited,
                myReaction = if (source.capabilities.primaryFavourite.mode == PrimaryFavouriteMode.Reaction) {
                    if (result.selected == true) favouriteEmoji else null
                } else {
                    base.myReaction
                },
            )
            PostAction.Reshare -> base.copy(
                reposted = result.selected ?: base.reposted,
                ownRepostId = when {
                    result.selected == false -> null
                    result.createdRepostId != null -> result.createdRepostId
                    else -> base.ownRepostId
                },
            )
            PostAction.Bookmark -> base.copy(saved = result.selected ?: base.saved)
            PostAction.React -> base
            PostAction.Reply -> base
        }
    }

    private fun updatePost(id: EntityId, transform: (Post) -> Post) {
        _feed.value = _feed.value.copy(
            posts = _feed.value.posts.map { if (it.id == id) transform(it) else it },
            ownedPosts = _feed.value.ownedPosts.map { owned ->
                if (owned.post.id == id && owned.fetchedBy == accountId) owned.copy(post = transform(owned.post)) else owned
            },
            accountSearch = _feed.value.accountSearch.copy(
                posts = _feed.value.accountSearch.posts.map { if (it.id == id) transform(it) else it },
            ),
        )
        _photoGridFeed.value = _photoGridFeed.value.copy(
            posts = _photoGridFeed.value.posts.map { owned ->
                if (owned.fetchedBy == accountId && owned.sessionRevision == sessionRevision && owned.post.id == id) {
                    owned.copy(post = transform(owned.post))
                } else owned
            },
        )
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
            accountSearch = _feed.value.accountSearch.copy(
                posts = _feed.value.accountSearch.posts.map { post ->
                    if (post.id == target || post.actionTargetId == target) mergeExternalActionFields(post, incoming) else post
                },
            ),
        )
        _photoGridFeed.value = _photoGridFeed.value.copy(
            posts = _photoGridFeed.value.posts.map { owned ->
                if (owned.fetchedBy == accountId && owned.sessionRevision == sessionRevision &&
                    (owned.post.id == target || owned.effectiveTargetId() == target)
                ) {
                    owned.copy(post = mergeExternalActionFields(owned.post, incoming))
                } else owned
            },
        )
    }

    private fun mergeExternalActionFields(existing: Post, incoming: Post): Post = existing.copy(
        favourited = incoming.favourited,
        myReaction = incoming.myReaction,
        selectedReactions = incoming.selectedReactions,
        reactions = incoming.reactions,
        reposted = incoming.reposted,
        reshareCount = incoming.reshareCount,
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
            accountSearch = _feed.value.accountSearch.copy(
                posts = _feed.value.accountSearch.posts.map(transform),
            ),
        )
        _photoGridFeed.value = _photoGridFeed.value.copy(
            posts = _photoGridFeed.value.posts.map { owned ->
                if (owned.fetchedBy == accountId && owned.sessionRevision == sessionRevision) {
                    owned.copy(post = transform(owned.post))
                } else owned
            },
        )
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
        fun create(accountId: AccountId, source: SocialSource, sessionRevision: Long): FeedViewModel
    }
}
