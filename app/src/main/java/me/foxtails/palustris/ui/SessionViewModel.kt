package me.foxtails.palustris.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import me.foxtails.palustris.data.auth.*
import me.foxtails.palustris.data.misskey.*
import me.foxtails.palustris.domain.*
import java.io.IOException

// Shared screen state contains domain models, never transport DTOs or credentials.
data class FeedState(val posts: List<Post> = emptyList(), val loading: Boolean = false,
    val loadingMore: Boolean = false, val nextCursor: String? = null, val error: String? = null,
    val needsSignIn: Boolean = false)
data class SessionUi(val starting: Boolean = true, val busy: Boolean = false,
    val account: Account? = null, val origin: String? = null, val pending: Boolean = false,
    val browserUrl: String? = null, val error: String? = null)

class SessionViewModel(
    private val store: SessionStore,
    private val auth: AuthGateway,
    private val sourceFactory: (LoginSession) -> SocialSource,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    private val _session = MutableStateFlow(SessionUi())
    val session = _session.asStateFlow()
    private val _feed = MutableStateFlow(FeedState())
    val feed = _feed.asStateFlow()
    private var login: LoginSession? = null
    private var pending: PendingLogin? = null
    private var source: SocialSource? = null
    private var feedJob: Job? = null
    private var authJob: Job? = null
    private var deferredCallback: String? = null

    init { viewModelScope.launch {
        try {
            val saved = withContext(ioDispatcher) { store.read() }
            login = saved.session
            pending = saved.pending?.takeIf { it.isFresh(System.currentTimeMillis()) }
            if (login != null) connected(login!!) else {
                _session.value = SessionUi(starting = false, pending = pending != null, origin = pending?.origin)
                deferredCallback?.let { callback(it) }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            _session.value = SessionUi(starting = false, error = "Saved sign-in could not be restored. Please sign in again.")
        }
    } }

    fun signIn(input: String) {
        if (_session.value.busy) return
        authJob = viewModelScope.launch {
            _session.value = _session.value.copy(busy = true, error = null)
            try {
                val next = auth.prepare(input)
                withContext(ioDispatcher) { store.write(StoredLogin(pending = next)) }
                pending = next
                _session.value = SessionUi(starting = false, pending = true, origin = next.origin, browserUrl = auth.browserUrl(next))
            } catch (e: Exception) { failAuth(e) }
        }
    }
    fun browserOpened() { _session.value = _session.value.copy(browserUrl = null) }
    fun browserFailed() { _session.value = _session.value.copy(browserUrl = null, error = "No browser could be opened. Install or enable a browser and try again.") }
    fun reopenBrowser() { pending?.let { _session.value = _session.value.copy(browserUrl = auth.browserUrl(it), error = null) } }
    fun callback(value: String) {
        if (_session.value.starting) { deferredCallback = value; return }
        val request = pending ?: return
        if (AuthCallback.matches(value, request, System.currentTimeMillis())) finishSignIn()
        else _session.value = _session.value.copy(error = "This sign-in callback is invalid or expired. Please try again.")
    }
    fun finishSignIn() {
        val request = pending ?: return
        if (_session.value.busy) return
        authJob = viewModelScope.launch {
            _session.value = _session.value.copy(busy = true, error = null, browserUrl = null)
            try {
                val result = auth.complete(request)
                withContext(ioDispatcher) { store.write(StoredLogin(session = result)) }
                pending = null
                connected(result)
            } catch (e: Exception) { failAuth(e) }
        }
    }
    private fun failAuth(e: Exception) {
        if (e is CancellationException) throw e
        _session.value = _session.value.copy(busy = false, error = message(e), browserUrl = null)
    }
    private fun connected(value: LoginSession) {
        login = value; source = sourceFactory(value)
        _session.value = SessionUi(starting = false, account = value.account, origin = value.origin)
        refresh()
    }
    fun refresh() {
        val current = source ?: return
        feedJob?.cancel()
        feedJob = viewModelScope.launch {
            _feed.value = _feed.value.copy(loading = true, loadingMore = false, error = null)
            try {
                val page = current.timeline(Timeline.Home)
                _feed.value = FeedState(posts = page.items.distinctBy { it.id }, nextCursor = page.nextCursor)
            } catch (e: Exception) { feedFailure(e) }
        }
    }
    fun loadMore() {
        val current = source ?: return
        val state = _feed.value
        val cursor = state.nextCursor ?: return
        if (state.loading || state.loadingMore || state.needsSignIn) return
        feedJob = viewModelScope.launch {
            _feed.value = state.copy(loadingMore = true, error = null)
            try {
                val page = current.timeline(Timeline.Home, cursor)
                _feed.value = _feed.value.copy(posts = (state.posts + page.items).distinctBy { it.id }, loadingMore = false,
                    nextCursor = page.nextCursor?.takeUnless { it == cursor })
            } catch (e: Exception) { feedFailure(e) }
        }
    }
    private fun feedFailure(e: Exception) {
        if (e is CancellationException) throw e
        _feed.value = _feed.value.copy(loading = false, loadingMore = false, error = message(e),
            needsSignIn = e is ApiFailure && (e.status == 401 || e.status == 403))
    }
    fun signOut() {
        authJob?.cancel(); feedJob?.cancel()
        viewModelScope.launch {
            try {
                withContext(ioDispatcher) { store.clear() }
                login = null; source = null; pending = null; deferredCallback = null
                _feed.value = FeedState(); _session.value = SessionUi(starting = false)
            } catch (e: Exception) { failAuth(e) }
        }
    }
    private fun message(e: Exception): String = when (e) {
        is ApiFailure -> when (e.status) {
            401, 403 -> "Access was denied. Sign in again and allow access to your account and timeline."
            429 -> "This instance is busy. Wait a moment and try again."
            404 -> "This instance does not support the requested Misskey feature."
            else -> "The instance could not complete the request. Please try again."
        }
        is IllegalArgumentException -> e.message ?: "Please check the instance address and try again."
        is IOException -> "Could not reach the instance. Check your connection and try again."
        else -> "Could not complete the request. Please try again."
    }
}
