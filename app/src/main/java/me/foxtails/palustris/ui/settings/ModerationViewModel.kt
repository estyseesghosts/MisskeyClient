package me.foxtails.palustris.ui.settings

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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import me.foxtails.palustris.data.AccountSourceRegistry
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.ModerationAccount
import me.foxtails.palustris.domain.ModerationCursor
import me.foxtails.palustris.domain.ModerationListKind
import me.foxtails.palustris.domain.MutedHashtag
import me.foxtails.palustris.domain.SocialSource
import me.foxtails.palustris.domain.SourceError
import me.foxtails.palustris.domain.PostPreferencesRepository
import me.foxtails.palustris.domain.normalizeLocalMutedHashtags

data class ModerationUiState(
    val accountId: AccountId? = null,
    val kind: ModerationListKind = ModerationListKind.Blocked,
    val accounts: List<ModerationAccount> = emptyList(),
    val hashtags: List<MutedHashtag> = emptyList(),
    val nextCursor: ModerationCursor? = null,
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val removing: Set<String> = emptySet(),
    val error: String? = null,
    val unsupported: Boolean = false,
    val localHashtagFallback: Boolean = false,
)

@HiltViewModel(assistedFactory = ModerationViewModel.Factory::class)
class ModerationViewModel @AssistedInject constructor(
    @Assisted private val accountId: AccountId,
    @Assisted private val source: SocialSource,
    @Assisted private val kind: ModerationListKind,
    private val sourceRegistry: AccountSourceRegistry,
    private val postPreferencesRepository: PostPreferencesRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(ModerationUiState(accountId = accountId, kind = kind))
    val state = _state.asStateFlow()
    private var loadJob: Job? = null
    private var loadMoreJob: Job? = null
    private var requestGeneration = 0L

    init { load() }

    fun load() {
        loadJob?.cancel()
        loadMoreJob?.cancel()
        val request = ++requestGeneration
        loadJob = viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null, unsupported = false)
            try {
                ensureCurrent()
                if (request != requestGeneration) return@launch
                val nextState = when (kind) {
                    ModerationListKind.Blocked -> source.blockedAccounts().let { page ->
                        _state.value.copy(accounts = page.items, nextCursor = page.nextCursor, loading = false)
                    }
                    ModerationListKind.Muted -> source.mutedAccounts().let { page ->
                        _state.value.copy(accounts = page.items, nextCursor = page.nextCursor, loading = false)
                    }
                     ModerationListKind.Hashtags -> try {
                         source.mutedHashtags().let { page ->
                             _state.value.copy(hashtags = page.items, nextCursor = page.nextCursor, loading = false, localHashtagFallback = false)
                         }
                     } catch (unsupported: SourceError.Unsupported) {
                         localHashtagPage()
                     }
                }
                if (request == requestGeneration) _state.value = nextState
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (request == requestGeneration) updateError(error)
            }
        }
    }

    fun loadMore() {
        val cursor = _state.value.nextCursor ?: return
        if (_state.value.loading || _state.value.loadingMore) return
        val request = requestGeneration
        val requestedCursor = cursor
        loadMoreJob = viewModelScope.launch {
            _state.value = _state.value.copy(loadingMore = true, error = null)
            try {
                ensureCurrent()
                when (kind) {
                    ModerationListKind.Blocked -> source.blockedAccounts(cursor).let { page ->
                        ensureCurrent()
                        if (request != requestGeneration || _state.value.nextCursor != requestedCursor) return@launch
                        _state.value = _state.value.copy(
                            accounts = (_state.value.accounts + page.items).distinctBy { it.account.id },
                            nextCursor = page.nextCursor?.takeUnless { it == requestedCursor },
                            loadingMore = false,
                        )
                    }
                    ModerationListKind.Muted -> source.mutedAccounts(cursor).let { page ->
                        ensureCurrent()
                        if (request != requestGeneration || _state.value.nextCursor != requestedCursor) return@launch
                        _state.value = _state.value.copy(
                            accounts = (_state.value.accounts + page.items).distinctBy { it.account.id },
                            nextCursor = page.nextCursor?.takeUnless { it == requestedCursor },
                            loadingMore = false,
                        )
                    }
                    ModerationListKind.Hashtags -> source.mutedHashtags(cursor).let { page ->
                        ensureCurrent()
                        if (request != requestGeneration || _state.value.nextCursor != requestedCursor) return@launch
                        _state.value = _state.value.copy(
                            hashtags = (_state.value.hashtags + page.items).distinctBy { it.value.lowercase() },
                            nextCursor = page.nextCursor?.takeUnless { it == requestedCursor },
                            loadingMore = false,
                        )
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (request == requestGeneration) updateError(error, loadingMore = false)
            }
        }
    }

    fun remove(entry: ModerationAccount) {
        val key = "${entry.account.id.connection.origin}\u0000${entry.account.id.localId}"
        val request = requestGeneration
        if (key in _state.value.removing) return
        viewModelScope.launch {
            _state.value = _state.value.copy(removing = _state.value.removing + key, error = null)
            try {
                ensureCurrent()
                if (kind == ModerationListKind.Blocked) source.removeBlockedAccount(entry)
                else source.removeMutedAccount(entry)
                ensureCurrent()
                if (request != requestGeneration) return@launch
                _state.value = _state.value.copy(
                    accounts = _state.value.accounts.filterNot { it.account.id == entry.account.id },
                    removing = _state.value.removing - key,
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                updateError(error, removing = key)
            }
        }
    }

    fun addLocalHashtag(value: String) {
        if (kind != ModerationListKind.Hashtags || !_state.value.localHashtagFallback) return
        val hashtag = normalizeLocalMutedHashtags(listOf(value)).singleOrNull() ?: return
        updateLocalHashtags { current -> current + hashtag }
    }

    fun removeLocalHashtag(value: String) {
        if (kind != ModerationListKind.Hashtags || !_state.value.localHashtagFallback) return
        updateLocalHashtags { current -> current.filterNot { it.equals(value, ignoreCase = true) } }
    }

    private fun updateLocalHashtags(transform: (List<String>) -> List<String>) {
        viewModelScope.launch {
            try {
                ensureCurrent()
                postPreferencesRepository.update(accountId) { preferences ->
                    preferences.copy(localMutedHashtags = transform(preferences.localMutedHashtags))
                }
                _state.value = _state.value.copy(
                    hashtags = localHashtagEntries(postPreferencesRepository.observe(accountId).first().localMutedHashtags),
                    error = null,
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                updateError(error)
            }
        }
    }

    private suspend fun localHashtagPage(): ModerationUiState {
        val preferences = postPreferencesRepository.observe(accountId).first()
        return _state.value.copy(
            hashtags = localHashtagEntries(preferences.localMutedHashtags),
            nextCursor = null,
            loading = false,
            localHashtagFallback = true,
            unsupported = false,
        )
    }

    private fun localHashtagEntries(values: List<String>) = values.map { MutedHashtag(it) }

    private fun ensureCurrent() {
        check(sourceRegistry.isCurrent(accountId, source)) { "This account session is no longer available." }
    }

    private fun updateError(error: Exception, loadingMore: Boolean = false, removing: String? = null) {
        _state.value = _state.value.copy(
            loading = false,
            loadingMore = loadingMore,
            removing = removing?.let { _state.value.removing - it } ?: _state.value.removing,
            error = error.message ?: "The moderation list could not be loaded.",
            unsupported = error is me.foxtails.palustris.domain.SourceError.Unsupported,
        )
    }

    @AssistedFactory
    interface Factory {
        fun create(accountId: AccountId, source: SocialSource, kind: ModerationListKind): ModerationViewModel
    }
}
