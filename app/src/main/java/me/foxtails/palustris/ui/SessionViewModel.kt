package me.foxtails.palustris.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import me.foxtails.palustris.data.auth.*
import me.foxtails.palustris.data.misskey.*
import me.foxtails.palustris.domain.*

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
    private val sourceFactory: (Session) -> SocialSource,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    private val _session = MutableStateFlow(SessionUi())
    val session = _session.asStateFlow()
    private val _feed = MutableStateFlow(FeedState())
    val feed = _feed.asStateFlow()
    private var login: Session? = null
    private var pending: PendingLogin? = null
    private var source: SocialSource? = null
    private var feedJob: Job? = null
    private var authJob: Job? = null
    private var deferredCallback: String? = null

    init { viewModelScope.launch {
        try {
            val restored = withContext(ioDispatcher) {
                val index = store.readIndex()
                val activeAccountId = index.activeAccountId ?: index.accounts.firstOrNull()?.accountId
                activeAccountId?.let { accountId ->
                    store.read(accountId)?.let { session ->
                        session to index.accounts.firstOrNull { it.accountId == accountId }?.toAccount()
                    }
                }
            }
            pending = withContext(ioDispatcher) { store.readPending()?.takeIf { it.isFresh(System.currentTimeMillis()) } }
            if (restored != null) {
                val (session, account) = restored
                login = session
                connected(session, account ?: Account(session.accountId, session.accountId.localId, session.accountId.localId))
            } else {
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
                withContext(ioDispatcher) { store.writePending(next) }
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
        if (AuthCallback.matches(value, request, System.currentTimeMillis())) {
            pending = request.copy(authorizationCode = AuthCallback.authorizationCode(value))
            finishSignIn()
        }
        else _session.value = _session.value.copy(error = "This sign-in callback is invalid or expired. Please try again.")
    }
    fun finishSignIn() {
        val request = pending ?: return
        if (_session.value.busy) return
        authJob = viewModelScope.launch {
            _session.value = _session.value.copy(busy = true, error = null, browserUrl = null)
            try {
                val result = auth.complete(request)
                val account = result.account
                val session = Session(account.id, result.token, ServerCapabilities())
                withContext(ioDispatcher) {
                    store.write(account.id, session)
                    store.writeProfile(account.id, result.user)
                    val index = store.readIndex()
                    store.writeIndex(index.withAccount(account).copy(activeAccountId = account.id))
                    store.clearPending()
                }
                pending = null
                connected(session, account)
            } catch (e: Exception) { failAuth(e) }
        }
    }
    private fun failAuth(e: Exception) {
        if (e is CancellationException) throw e
        _session.value = _session.value.copy(busy = false, error = message(e), browserUrl = null)
    }
    private fun connected(value: Session, account: Account) {
        login = value; source = sourceFactory(value)
        _session.value = SessionUi(starting = false, account = account, origin = value.accountId.connection.origin)
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
            needsSignIn = e is SourceError.Unauthorized)
    }
    fun signOut() {
        authJob?.cancel(); feedJob?.cancel()
        viewModelScope.launch {
            try {
                withContext(ioDispatcher) {
                    login?.accountId?.let { accountId ->
                        store.delete(accountId)
                        val index = store.readIndex()
                        store.writeIndex(index.copy(
                            accounts = index.accounts.filterNot { it.accountId == accountId },
                            activeAccountId = index.accounts.firstOrNull { it.accountId != accountId }?.accountId,
                        ))
                    }
                    store.clearPending()
                }
                login = null; source = null; pending = null; deferredCallback = null
                _feed.value = FeedState(); _session.value = SessionUi(starting = false)
            } catch (e: Exception) { failAuth(e) }
        }
    }
    private fun message(e: Exception): String = when (e) {
        is SourceError.Unauthorized -> "Access was denied. Sign in again and allow access to your account and timeline."
        is SourceError.RateLimited -> "This instance is busy. Wait a moment and try again."
        is SourceError.Unsupported -> "This instance doesn't support ${e.feature}."
        is SourceError.NetworkUnavailable -> "Could not reach the instance. Check your connection and try again."
        is SourceError.ServerError -> e.detail ?: "Could not complete the request. Please try again."
        else -> "Could not complete the request. Please try again."
    }
}

private fun AccountIndex.withAccount(account: Account): AccountIndex {
    val ref = AccountRef(account.id, account.handle, account.avatarUrl, account.displayName)
    return copy(accounts = accounts.filterNot { it.accountId == account.id } + ref)
}
