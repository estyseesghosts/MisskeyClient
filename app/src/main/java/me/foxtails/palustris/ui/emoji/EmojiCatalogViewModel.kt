package me.foxtails.palustris.ui.emoji

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
) : ViewModel() {
    private val _state = MutableStateFlow(EmojiCatalogState())
    val state = _state.asStateFlow()
    private var loadJob: Job? = null
    private var stopped = false

    fun loadIfNeeded() {
        if (_state.value.items.isEmpty() && !_state.value.loading &&
            !_state.value.unsupported && _state.value.error == null
        ) {
            load()
        }
    }

    fun load() {
        if (stopped) return
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _state.value = EmojiCatalogState(loading = true)
            try {
                val items = source.customEmojis()
                _state.value = EmojiCatalogState(
                    items = items.filter { it.visibleInPicker },
                    empty = items.isEmpty(),
                )
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _state.value = if (e is SourceError.Unsupported) {
                    EmojiCatalogState(unsupported = true)
                } else {
                    EmojiCatalogState(error = sourceErrorMessage(e))
                }
            }
        }
    }

    fun retry() = load()

    fun stop() {
        stopped = true
        loadJob?.cancel()
    }

    override fun onCleared() {
        stop()
        super.onCleared()
    }

    @AssistedFactory
    interface Factory {
        fun create(accountId: AccountId, source: SocialSource): EmojiCatalogViewModel
    }
}
