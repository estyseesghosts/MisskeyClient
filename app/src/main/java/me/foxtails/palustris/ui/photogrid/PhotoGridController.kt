package me.foxtails.palustris.ui.photogrid

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.CapabilityStatus
import me.foxtails.palustris.domain.EntityId
import me.foxtails.palustris.domain.OwnedPost
import me.foxtails.palustris.domain.PhotoGridPreferencesRepository
import me.foxtails.palustris.domain.Post
import me.foxtails.palustris.domain.SocialSource
import me.foxtails.palustris.domain.Timeline
import me.foxtails.palustris.domain.effectiveTargetId
import me.foxtails.palustris.domain.hashtagIdentity
import me.foxtails.palustris.domain.mergeExternalActionFields
import me.foxtails.palustris.domain.timelineDisplayOrder
import me.foxtails.palustris.domain.timelineStatus
import me.foxtails.palustris.domain.validateExactHashtag
import me.foxtails.palustris.ui.UiStrings
import me.foxtails.palustris.ui.requiresSignIn

internal class PhotoGridController(
    private val accountId: AccountId,
    private val source: SocialSource,
    private val sessionRevision: Long,
    private val scope: CoroutineScope,
    private val preferencesRepository: PhotoGridPreferencesRepository,
    private val applyFavouritePreference: (Post) -> Post,
    private val uiStrings: UiStrings = UiStrings.Default,
) {
    private var preferencesJob: Job? = null
    private var loadingJob: Job? = null
    private var saveJob: Job? = null
    private var generation = 0L
    private val consumedCursors = mutableSetOf<String>()
    private var stopped = false
    private val _state = MutableStateFlow(PhotoGridFeedState())
    val state = _state.asStateFlow()

    init {
        preferencesJob = scope.launch {
            preferencesRepository.observe(accountId).collectLatest { preferences ->
                if (!stopped) {
                    _state.value = _state.value.copy(
                        savedHashtags = preferences.hashtags,
                        preferenceLoading = false,
                    )
                }
            }
        }
    }

    fun ensureLoaded() {
        if (stopped || _state.value.initialLoadComplete || loadingJob?.isActive == true) return
        val selected = _state.value.selectedFeed
        _state.value = _state.value.copy(availableTimelines = availableTimelines())
        startRequest(selected, ++generation, cursor = null)
    }

    fun selectFeed(feed: PhotoGridFeed) {
        if (stopped || !isValidFeed(feed)) return
        val current = _state.value
        if (current.selectedFeed == feed && (current.loading || current.initialLoadComplete)) return
        loadingJob?.cancel()
        consumedCursors.clear()
        val nextGeneration = ++generation
        _state.value = current.copy(
            selectedFeed = feed,
            posts = emptyList(),
            initialLoadComplete = false,
            loading = true,
            loadingMore = false,
            nextCursor = null,
            error = null,
            needsSignIn = false,
        )
        startRequest(feed, nextGeneration, cursor = null)
    }

    fun refresh() {
        if (stopped) return
        val selected = _state.value.selectedFeed
        loadingJob?.cancel()
        consumedCursors.clear()
        val nextGeneration = ++generation
        _state.value = _state.value.copy(
            posts = emptyList(),
            initialLoadComplete = false,
            loading = true,
            loadingMore = false,
            nextCursor = null,
            error = null,
            needsSignIn = false,
        )
        startRequest(selected, nextGeneration, cursor = null)
    }

    fun loadMore() {
        if (stopped) return
        val current = _state.value
        val cursor = current.nextCursor ?: return
        if (current.loading || current.loadingMore || current.needsSignIn || !consumedCursors.add(cursor)) return
        startRequest(current.selectedFeed, generation, cursor)
    }

    fun addHashtag(value: String, onSuccess: () -> Unit = {}) {
        if (stopped || _state.value.preferenceSaving) return
        val accepted = runCatching { validateExactHashtag(value) }.getOrElse {
            _state.value = _state.value.copy(preferenceError = "invalid")
            return
        }
        val existing = _state.value.savedHashtags.firstOrNull {
            hashtagIdentity(it) == hashtagIdentity(accepted)
        }
        if (existing != null) {
            _state.value = _state.value.copy(preferenceError = null)
            selectFeed(PhotoGridFeed.Hashtag(existing))
            onSuccess()
            return
        }
        saveJob?.cancel()
        saveJob = scope.launch {
            _state.value = _state.value.copy(preferenceSaving = true, preferenceError = null)
            try {
                preferencesRepository.update(accountId) { preferences ->
                    preferences.copy(hashtags = preferences.hashtags + accepted)
                }
                val saved = (_state.value.savedHashtags + accepted).distinctBy(::hashtagIdentity)
                _state.value = _state.value.copy(
                    savedHashtags = saved,
                    preferenceSaving = false,
                    preferenceError = null,
                )
                selectFeed(PhotoGridFeed.Hashtag(accepted))
                onSuccess()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _state.value = _state.value.copy(
                    preferenceSaving = false,
                    preferenceError = "save",
                )
            }
        }
    }

    fun clearPreferenceError() {
        _state.value = _state.value.copy(preferenceError = null)
    }

    fun updatePost(id: EntityId, transform: (Post) -> Post) {
        _state.value = _state.value.copy(
            posts = _state.value.posts.map { owned ->
                if (owned.post.id == id) owned.copy(post = transform(owned.post)) else owned
            },
        )
    }

    fun updateExternalPost(target: EntityId, incoming: Post) {
        _state.value = _state.value.copy(
            posts = _state.value.posts.map { owned ->
                if (owned.fetchedBy == accountId && owned.sessionRevision == sessionRevision &&
                    (owned.post.id == target || owned.effectiveTargetId() == target)
                ) {
                    owned.copy(post = mergeExternalActionFields(owned.post, incoming))
                } else owned
            },
        )
    }

    fun updatePosts(transform: (Post) -> Post) {
        _state.value = _state.value.copy(
            posts = _state.value.posts.map { owned ->
                if (owned.fetchedBy == accountId && owned.sessionRevision == sessionRevision) {
                    owned.copy(post = transform(owned.post))
                } else owned
            },
        )
    }

    fun stop() {
        if (stopped) return
        stopped = true
        preferencesJob?.cancel()
        loadingJob?.cancel()
        saveJob?.cancel()
        generation++
    }

    private fun isValidFeed(feed: PhotoGridFeed): Boolean = when (feed) {
        is PhotoGridFeed.TimelineFeed -> feed.timeline in availableTimelines()
        is PhotoGridFeed.Hashtag -> _state.value.savedHashtags.any {
            hashtagIdentity(it) == runCatching { hashtagIdentity(feed.tag) }.getOrNull()
        }
    }

    private fun availableTimelines(): List<Timeline> {
        val available = timelineDisplayOrder.filter {
            it in source.capabilities.timelines && source.capabilities.timelineStatus(it) == CapabilityStatus.Supported
        }
        return available.ifEmpty { listOf(Timeline.Home) }
    }

    private fun startRequest(feed: PhotoGridFeed, requestGeneration: Long, cursor: String?) {
        loadingJob = scope.launch {
            if (stopped || requestGeneration != generation || _state.value.selectedFeed != feed) return@launch
            _state.value = _state.value.copy(
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
                if (stopped || requestGeneration != generation || _state.value.selectedFeed != feed) return@launch
                val fetched = page.items.distinctBy { it.id }.map {
                    OwnedPost(accountId, applyFavouritePreference(it), sessionRevision)
                }
                val current = _state.value
                val merged = if (cursor == null) fetched else (current.posts + fetched).distinctBy { it.post.id }
                val nextCursor = page.nextCursor?.takeUnless {
                    it == cursor || it in consumedCursors
                }
                _state.value = current.copy(
                    posts = merged,
                    availableTimelines = availableTimelines(),
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
                if (stopped || requestGeneration != generation || _state.value.selectedFeed != feed) return@launch
                if (cursor != null) consumedCursors.remove(cursor)
                _state.value = _state.value.copy(
                    loading = false,
                    loadingMore = false,
                    error = uiStrings.sourceError(e),
                    needsSignIn = requiresSignIn(e),
                )
            }
        }
    }

    private fun mergeExternalActionFields(existing: Post, incoming: Post): Post =
        existing.mergeExternalActionFields(incoming)
}
