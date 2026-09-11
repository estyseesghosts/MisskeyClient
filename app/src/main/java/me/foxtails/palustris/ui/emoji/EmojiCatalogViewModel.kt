package me.foxtails.palustris.ui.emoji

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.EmojiCatalogRepository
import me.foxtails.palustris.domain.EmojiCatalogSnapshot
import me.foxtails.palustris.domain.SocialSource
import me.foxtails.palustris.domain.SourceError
import me.foxtails.palustris.ui.sourceErrorMessage

/**
 * Account-scoped emoji catalog backed only by [SocialSource]. The catalog loads lazily
 * when the picker opens and is never cached in AccountManager.
 */
@HiltViewModel(assistedFactory = EmojiCatalogViewModel.Factory::class)
class EmojiCatalogViewModel @AssistedInject constructor(
    @Assisted val accountId: AccountId,
    @Assisted private val source: SocialSource,
    private val repository: EmojiCatalogRepository,
    private val clock: Clock,
) : ViewModel() {
    private val _state = MutableStateFlow(EmojiCatalogState())
    val state = _state.asStateFlow()
    private var loadJob: Job? = null
    private var stopped = false

    fun loadIfNeeded() {
        if (stopped || loadJob?.isActive == true || _state.value.unsupported) return
        loadJob = viewModelScope.launch {
            val cached = runCatching { repository.read(accountId) }.getOrNull()
            if (cached != null) publishSnapshot(cached)
            if (cached != null && isFresh(cached)) return@launch
            refreshInternal(cached != null)
        }
    }

    fun load() {
        if (stopped) return
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            refreshInternal(_state.value.hasSnapshot)
        }
    }

    fun retry() = load()

    fun stop() {
        stopped = true
        loadJob?.cancel()
    }

    private suspend fun refreshInternal(hasCachedSnapshot: Boolean) {
        val previous = _state.value
        _state.value = previous.copy(
            initialLoading = !hasCachedSnapshot,
            refreshing = hasCachedSnapshot,
            error = null,
            unsupported = false,
        )
        try {
            publishSnapshot(repository.refresh(accountId, source))
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            _state.value = _state.value.copy(
                initialLoading = false,
                refreshing = false,
                error = if (e is SourceError.Unsupported) null else sourceErrorMessage(e),
                unsupported = e is SourceError.Unsupported,
                items = if (e is SourceError.Unsupported) emptyList() else _state.value.items,
                hasSnapshot = if (e is SourceError.Unsupported) false else _state.value.hasSnapshot,
            )
        }
    }

    private fun publishSnapshot(snapshot: EmojiCatalogSnapshot) {
        _state.value = _state.value.copy(
            items = snapshot.items.filter { it.visibleInPicker },
            initialLoading = false,
            refreshing = false,
            error = null,
            empty = snapshot.items.isEmpty(),
            unsupported = false,
            hasSnapshot = true,
        )
    }

    private fun isFresh(snapshot: EmojiCatalogSnapshot): Boolean =
        clock.millis() - snapshot.refreshedAtEpochMillis < FRESHNESS_MILLIS

    override fun onCleared() {
        stop()
        super.onCleared()
    }

    @AssistedFactory
    interface Factory {
        fun create(accountId: AccountId, source: SocialSource): EmojiCatalogViewModel
    }

    private companion object {
        const val FRESHNESS_MILLIS = 24L * 60L * 60L * 1000L
    }
}
