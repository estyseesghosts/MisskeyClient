package me.foxtails.palustris.ui

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import me.foxtails.palustris.domain.Post
import me.foxtails.palustris.domain.SocialSource
import me.foxtails.palustris.domain.EntityId
import me.foxtails.palustris.domain.isExactHashtag
import me.foxtails.palustris.domain.mergeExternalActionFields
import me.foxtails.palustris.ui.feed.AccountSearchState

internal class SearchController(
    private val source: SocialSource,
    private val scope: CoroutineScope,
    private val applyFavouritePreference: (Post) -> Post,
    private val onStateChanged: (AccountSearchState) -> Unit,
) {
    private var searchJob: Job? = null
    private var generation = 0L
    private var stopped = false
    private val _state = MutableStateFlow(AccountSearchState())
    val state = _state.asStateFlow()

    fun search(query: String) {
        if (stopped) return
        val normalized = query.trim()
        if (normalized.isBlank()) return
        searchJob?.cancel()
        val requestGeneration = ++generation
        val isHashtag = isExactHashtag(normalized)
        publish(AccountSearchState(
            query = normalized,
            tagQuery = normalized.removePrefix("#").takeIf { isHashtag },
            loading = true,
        ))
        searchJob = scope.launch {
            if (isHashtag) searchHashtag(normalized, requestGeneration)
            else searchAccount(normalized, requestGeneration)
        }
    }

    fun loadMore() {
        if (stopped) return
        val current = _state.value
        val tag = current.tagQuery ?: return
        val cursor = current.nextCursor ?: return
        if (current.loading || current.loadingMore) return
        searchJob?.cancel()
        val requestGeneration = generation
        searchJob = scope.launch {
            publish(current.copy(loadingMore = true, error = null))
            try {
                val page = source.searchHashtag(tag, cursor)
                if (stopped || requestGeneration != generation || _state.value.query != current.query) return@launch
                val latest = _state.value
                publish(latest.copy(
                    posts = (latest.posts + page.items.map(applyFavouritePreference)).distinctBy { it.id },
                    loadingMore = false,
                    nextCursor = page.nextCursor?.takeUnless { it == cursor },
                ))
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                if (stopped || requestGeneration != generation) return@launch
                publish(_state.value.copy(loadingMore = false, error = sourceErrorMessage(e)))
            }
        }
    }

    fun updatePost(id: EntityId, transform: (Post) -> Post) {
        publish(_state.value.copy(
            posts = _state.value.posts.map { post ->
                if (post.id == id || post.actionTargetId == id) transform(post) else post
            },
        ))
    }

    fun updateExternalPost(target: EntityId, incoming: Post) {
        publish(_state.value.copy(
            posts = _state.value.posts.map { post ->
                if (post.id == target || post.actionTargetId == target) mergeExternalActionFields(post, incoming) else post
            },
        ))
    }

    fun updatePosts(transform: (Post) -> Post) {
        publish(_state.value.copy(posts = _state.value.posts.map(transform)))
    }

    fun stop() {
        if (stopped) return
        stopped = true
        searchJob?.cancel()
        generation++
    }

    private suspend fun searchAccount(normalized: String, requestGeneration: Long) {
        try {
            val accounts = source.searchAccounts(normalized).distinctBy { it.id }
            if (stopped || requestGeneration != generation) return
            publish(AccountSearchState(query = normalized, accounts = accounts))
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            if (stopped || requestGeneration != generation) return
            publish(AccountSearchState(query = normalized, error = sourceErrorMessage(e)))
        }
    }

    private suspend fun searchHashtag(normalized: String, requestGeneration: Long) {
        val tag = normalized.removePrefix("#")
        try {
            val page = source.searchHashtag(normalized)
            if (stopped || requestGeneration != generation) return
            publish(AccountSearchState(
                query = normalized,
                posts = page.items.distinctBy { it.id }.map(applyFavouritePreference),
                tagQuery = tag,
                nextCursor = page.nextCursor,
            ))
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            if (stopped || requestGeneration != generation) return
            publish(AccountSearchState(query = normalized, tagQuery = tag, error = sourceErrorMessage(e)))
        }
    }

    private fun mergeExternalActionFields(existing: Post, incoming: Post): Post =
        existing.mergeExternalActionFields(incoming)

    private fun publish(next: AccountSearchState) {
        _state.value = next
        onStateChanged(next)
    }
}
