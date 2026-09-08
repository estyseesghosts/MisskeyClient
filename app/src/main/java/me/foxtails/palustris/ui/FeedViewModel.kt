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
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.CapabilityStatus
import me.foxtails.palustris.domain.CreatePostRequest
import me.foxtails.palustris.domain.DEFAULT_FAVOURITE_EMOJI
import me.foxtails.palustris.domain.EntityId
import me.foxtails.palustris.domain.OwnedPost
import me.foxtails.palustris.domain.Post
import me.foxtails.palustris.domain.PostAction
import me.foxtails.palustris.domain.PostActionResult
import me.foxtails.palustris.domain.PostPreferencesRepository
import me.foxtails.palustris.domain.PrimaryFavouriteMode
import me.foxtails.palustris.domain.SocialSource
import me.foxtails.palustris.domain.Timeline

@HiltViewModel(assistedFactory = FeedViewModel.Factory::class)
class FeedViewModel @AssistedInject constructor(
    @Assisted val accountId: AccountId,
    @Assisted private val source: SocialSource,
    private val syncCoordinator: AccountSyncCoordinator,
    private val postPreferencesRepository: PostPreferencesRepository,
) : ViewModel() {
    constructor(
        accountId: AccountId,
        source: SocialSource,
        syncCoordinator: AccountSyncCoordinator,
    ) : this(accountId, source, syncCoordinator, InMemoryPostPreferencesRepository())

    private val _feed = MutableStateFlow(FeedState())
    val feed = _feed.asStateFlow()
    val sync = syncCoordinator.observeAccount(accountId)
    private var feedJob: Job? = null
    private var setupJob: Job? = null
    private var searchJob: Job? = null
    private var publishJob: Job? = null
    private var preferencesJob: Job? = null
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
                    ownedPosts = posts.map { OwnedPost(accountId, it) },
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
                    ownedPosts = (state.ownedPosts + newPosts.map { OwnedPost(accountId, it) })
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

    fun searchAccounts(query: String) {
        search(query)
    }

    fun search(query: String) {
        if (stopped) return
        val normalized = query.trim()
        if (normalized.isBlank()) return
        searchJob?.cancel()
        val isHashtag = normalized.matches(EXACT_HASHTAG)
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

    private companion object {
        val EXACT_HASHTAG = Regex("#[\\p{L}\\p{N}_](?:[\\p{L}\\p{N}\\p{M}_])*")
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
                    posts = page.items.distinctBy { it.id },
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
                    posts = (current.posts + page.items).distinctBy { it.id },
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
                        source.removeReaction(ownedPost.post.id, previousReaction)
                    }
                }
                source.setPrimaryFavourite(ownedPost.post.id, favouriteEmoji, selected)
            },
        )
    }

    fun reshare(ownedPost: OwnedPost) {
        val selected = !ownedPost.post.reposted
        runAction(
            ownedPost = ownedPost,
            action = PostAction.Reshare,
            optimistic = { post -> post.copy(reposted = selected, reshareCount = (post.reshareCount + if (selected) 1 else -1).coerceAtLeast(0)) },
            operation = { source.setReshared(ownedPost.post.id, selected, ownedPost.post.ownRepostId) },
        )
    }

    fun react(ownedPost: OwnedPost, emoji: String) {
        val selected = ownedPost.post.myReaction == emoji
        runAction(
            ownedPost = ownedPost,
            action = PostAction.React,
            optimistic = { post -> updateReaction(post, emoji, !selected) },
            operation = {
                val previousReaction = ownedPost.post.myReaction
                if (selected) {
                    source.removeReaction(ownedPost.post.id, emoji)
                } else {
                    if (previousReaction != null && previousReaction != emoji) {
                        source.removeReaction(ownedPost.post.id, previousReaction)
                    }
                    source.react(ownedPost.post.id, emoji)
                }
                PostActionResult(selected = !selected)
            },
        )
    }

    fun bookmark(ownedPost: OwnedPost) {
        val selected = !ownedPost.post.saved
        runAction(
            ownedPost = ownedPost,
            action = PostAction.Bookmark,
            optimistic = { post -> post.copy(saved = selected) },
            operation = { source.setSaved(ownedPost.post.id, selected) },
        )
    }

    fun stop() {
        if (stopped) return
        stopped = true
        setupJob?.cancel()
        feedJob?.cancel()
        searchJob?.cancel()
        publishJob?.cancel()
        preferencesJob?.cancel()
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

    private fun updateReaction(post: Post, emoji: String, selected: Boolean): Post {
        val previousEmoji = post.myReaction
        val nextReactions = post.reactions.toMutableList()
        if (previousEmoji != null && previousEmoji != emoji) {
            val index = nextReactions.indexOfFirst { it.emoji == previousEmoji }
            if (index >= 0) {
                val previous = nextReactions[index]
                if (previous.count <= 1) nextReactions.removeAt(index)
                else nextReactions[index] = previous.copy(count = previous.count - 1, selected = false)
            }
        }
        val index = nextReactions.indexOfFirst { it.emoji == emoji }
        if (selected) {
            if (index >= 0) {
                val reaction = nextReactions[index]
                nextReactions[index] = reaction.copy(count = reaction.count + 1, selected = true)
            } else {
                nextReactions += me.foxtails.palustris.domain.Reaction(emoji, 1, true)
            }
        } else if (index >= 0) {
            val reaction = nextReactions[index]
            if (reaction.count <= 1) nextReactions.removeAt(index)
            else nextReactions[index] = reaction.copy(count = reaction.count - 1, selected = false)
        }
        val nextReaction = emoji.takeIf { selected }
        return post.copy(
            myReaction = nextReaction,
            favourited = if (source.capabilities.primaryFavourite.mode == PrimaryFavouriteMode.Reaction) {
                nextReaction == favouriteEmoji
            } else {
                post.favourited
            },
            reactions = nextReactions,
        )
    }

    private fun updatePost(id: EntityId, transform: (Post) -> Post) {
        _feed.value = _feed.value.copy(
            posts = _feed.value.posts.map { if (it.id == id) transform(it) else it },
            ownedPosts = _feed.value.ownedPosts.map { owned ->
                if (owned.post.id == id && owned.fetchedBy == accountId) owned.copy(post = transform(owned.post)) else owned
            },
        )
    }

    private fun updatePosts(transform: (Post) -> Post) {
        val transformed = _feed.value.posts.associate { it.id to transform(it) }
        _feed.value = _feed.value.copy(
            posts = _feed.value.posts.map { transformed[it.id] ?: it },
            ownedPosts = _feed.value.ownedPosts.map { owned ->
                if (owned.fetchedBy == accountId) owned.copy(post = transformed[owned.post.id] ?: owned.post) else owned
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
        fun create(accountId: AccountId, source: SocialSource): FeedViewModel
    }
}
