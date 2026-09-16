package me.foxtails.palustris.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
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
import me.foxtails.palustris.domain.PostPreferencesRepository
import me.foxtails.palustris.domain.SocialSource
import me.foxtails.palustris.domain.SourceError
import me.foxtails.palustris.domain.normalizeLocalMutedHashtags
import me.foxtails.palustris.ui.UiStrings

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
    private val uiStrings: UiStrings = UiStrings.Default,
) : ViewModel() {
    private val _state = MutableStateFlow(ModerationUiState(accountId = accountId, kind = kind))
    val state = _state.asStateFlow()
    private var loadJob: Job? = null
    private var loadMoreJob: Job? = null
    private val removalJobs = mutableMapOf<String, Job>()

    /** List epoch. Refresh advances it. Removal ownership lives in removal tokens. */
    private var listEpoch = 0L

    /** Removal tokens are keyed by entry. A refresh never invalidates them. */
    private var removalEpoch = 0L
    private val removalTokens = mutableMapOf<String, Long>()
    private var stopped = false

    init { load() }

    fun load() {
        if (stopped) return
        loadJob?.cancel()
        loadMoreJob?.cancel()
        val epoch = ++listEpoch
        // Reserve both loading flags synchronously. A cancelled paging call cannot strand them.
        _state.value = _state.value.copy(loading = true, loadingMore = false, error = null, unsupported = false)
        loadJob = viewModelScope.launch {
            try {
                ensureCurrent()
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
                if (epoch != listEpoch || stopped) return@launch
                if (!isSessionCurrent()) {
                    _state.value = _state.value.copy(loading = false)
                    return@launch
                }
                _state.value = nextState
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (invalid: IllegalStateException) {
                if (epoch == listEpoch && !stopped) _state.value = _state.value.copy(loading = false)
            } catch (error: Exception) {
                if (epoch != listEpoch || stopped) return@launch
                if (!isSessionCurrent()) {
                    _state.value = _state.value.copy(loading = false)
                    return@launch
                }
                updateError(error)
            }
        }
    }

    fun loadMore() {
        if (stopped) return
        val cursor = _state.value.nextCursor ?: return
        if (_state.value.loading || _state.value.loadingMore) return
        // Paging cannot start during refresh. Reserve the page slot synchronously.
        if (loadJob?.isActive == true) return
        val epoch = listEpoch
        val requestedCursor = cursor
        _state.value = _state.value.copy(loadingMore = true, error = null)
        loadMoreJob = viewModelScope.launch {
            try {
                ensureCurrent()
                when (kind) {
                    ModerationListKind.Blocked -> source.blockedAccounts(cursor).let { page ->
                        ensureCurrent()
                        if (stopped || epoch != listEpoch || _state.value.nextCursor != requestedCursor) return@launch
                        _state.value = _state.value.copy(
                            accounts = (_state.value.accounts + page.items).distinctBy { it.account.id },
                            nextCursor = page.nextCursor?.takeUnless { it == requestedCursor },
                            loadingMore = false,
                        )
                    }
                    ModerationListKind.Muted -> source.mutedAccounts(cursor).let { page ->
                        ensureCurrent()
                        if (stopped || epoch != listEpoch || _state.value.nextCursor != requestedCursor) return@launch
                        _state.value = _state.value.copy(
                            accounts = (_state.value.accounts + page.items).distinctBy { it.account.id },
                            nextCursor = page.nextCursor?.takeUnless { it == requestedCursor },
                            loadingMore = false,
                        )
                    }
                    ModerationListKind.Hashtags -> source.mutedHashtags(cursor).let { page ->
                        ensureCurrent()
                        if (stopped || epoch != listEpoch || _state.value.nextCursor != requestedCursor) return@launch
                        _state.value = _state.value.copy(
                            hashtags = (_state.value.hashtags + page.items).distinctBy { it.value.lowercase() },
                            nextCursor = page.nextCursor?.takeUnless { it == requestedCursor },
                            loadingMore = false,
                        )
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (invalid: IllegalStateException) {
                if (epoch == listEpoch && !stopped) _state.value = _state.value.copy(loadingMore = false)
            } catch (error: Exception) {
                if (stopped || epoch != listEpoch) return@launch
                if (!isSessionCurrent()) {
                    _state.value = _state.value.copy(loadingMore = false)
                    return@launch
                }
                updateError(error, loadingMore = false)
            }
        }
    }

    fun remove(entry: ModerationAccount) {
        // Hashtag removal has its own local route. Account removal never runs here.
        if (kind == ModerationListKind.Hashtags || stopped) return
        val key = "${entry.account.id.connection.origin}\u0000${entry.account.id.localId}"
        if (removalJobs[key]?.isActive == true || key in _state.value.removing) return
        val token = ++removalEpoch
        removalTokens[key] = token
        val list = listEpoch
        // Reserve the entry marker synchronously. A retry behind it waits for cleanup.
        _state.value = _state.value.copy(removing = _state.value.removing + key, error = null)
        removalJobs[key] = viewModelScope.launch {
            try {
                ensureCurrent()
                if (kind == ModerationListKind.Blocked) source.removeBlockedAccount(entry)
                else source.removeMutedAccount(entry)
                ensureCurrent()
                if (stopped || removalTokens[key] != token) return@launch
                _state.value = _state.value.copy(removing = _state.value.removing - key)
                if (list == listEpoch) {
                    _state.value = _state.value.copy(
                        accounts = _state.value.accounts.filterNot { it.account.id == entry.account.id },
                    )
                } else {
                    // A refresh moved on while removal ran. Reconcile with a fresh list.
                    load()
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (invalid: IllegalStateException) {
                if (removalTokens[key] == token && !stopped) {
                    _state.value = _state.value.copy(removing = _state.value.removing - key)
                }
            } catch (error: Exception) {
                if (removalTokens[key] != token || stopped) return@launch
                if (!isSessionCurrent()) {
                    _state.value = _state.value.copy(removing = _state.value.removing - key)
                    return@launch
                }
                updateError(error, removing = key)
            } finally {
                val finished = currentCoroutineContext()[Job]
                if (removalJobs[key] === finished) removalJobs.remove(key)
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
        if (stopped) return
        val epoch = listEpoch
        viewModelScope.launch {
            try {
                ensureCurrent()
                postPreferencesRepository.update(accountId) { preferences ->
                    preferences.copy(localMutedHashtags = transform(preferences.localMutedHashtags))
                }
                ensureCurrent()
                if (epoch != listEpoch || stopped) return@launch
                _state.value = _state.value.copy(
                    hashtags = localHashtagEntries(postPreferencesRepository.observe(accountId).first().localMutedHashtags),
                    error = null,
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (invalid: IllegalStateException) {
                // The session is gone. The durable update stays; nothing is published.
            } catch (error: Exception) {
                if (epoch != listEpoch || stopped || !isSessionCurrent()) return@launch
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

    fun stop() {
        if (stopped) return
        stopped = true
        listEpoch += 1
        removalEpoch += 1
        loadJob?.cancel()
        loadMoreJob?.cancel()
        removalJobs.values.forEach(Job::cancel)
        removalJobs.clear()
        _state.value = _state.value.copy(loading = false, loadingMore = false, removing = emptySet())
    }

    override fun onCleared() {
        stop()
        super.onCleared()
    }

    private fun ensureCurrent() {
        check(sourceRegistry.isCurrent(accountId, source)) { uiStrings.accountSessionUnavailable() }
    }

    private fun isSessionCurrent(): Boolean = try {
        ensureCurrent()
        true
    } catch (invalid: IllegalStateException) {
        false
    }

    private fun updateError(error: Exception, loadingMore: Boolean = false, removing: String? = null) {
        _state.value = _state.value.copy(
            loading = false,
            loadingMore = loadingMore,
            removing = removing?.let { _state.value.removing - it } ?: _state.value.removing,
            error = error.message ?: uiStrings.moderationLoadFailed(),
            unsupported = error is me.foxtails.palustris.domain.SourceError.Unsupported,
        )
    }

    @AssistedFactory
    interface Factory {
        fun create(accountId: AccountId, source: SocialSource, kind: ModerationListKind): ModerationViewModel
    }
}
