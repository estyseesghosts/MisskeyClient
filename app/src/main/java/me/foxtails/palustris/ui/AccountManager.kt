package me.foxtails.palustris.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.foxtails.palustris.data.auth.AccountIndex
import me.foxtails.palustris.data.auth.AccountRef
import me.foxtails.palustris.data.auth.AuthCallback
import me.foxtails.palustris.data.auth.AuthGateway
import me.foxtails.palustris.data.auth.PendingLogin
import me.foxtails.palustris.data.auth.SessionStore
import me.foxtails.palustris.data.auth.toAccount
import me.foxtails.palustris.di.IoDispatcher
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.ServerCapabilities
import me.foxtails.palustris.domain.Session

data class SessionUi(
    val starting: Boolean = true,
    val busy: Boolean = false,
    val account: Account? = null,
    val origin: String? = null,
    val pending: Boolean = false,
    val browserUrl: String? = null,
    val error: String? = null,
)

@HiltViewModel
class AccountManager @Inject constructor(
    private val store: SessionStore,
    private val auth: AuthGateway,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {
    private val _session = MutableStateFlow(SessionUi())
    val session = _session.asStateFlow()
    private val _accountIndex = MutableStateFlow(AccountIndex())
    val accountIndex = _accountIndex.asStateFlow()
    private val _activeSession = MutableStateFlow<Session?>(null)
    val activeSession = _activeSession.asStateFlow()
    private var pending: PendingLogin? = null
    private var authJob: Job? = null
    private var deferredCallback: String? = null

    init {
        viewModelScope.launch {
            try {
                val restored = withContext(ioDispatcher) {
                    val index = store.readIndex()
                    val activeAccountId = index.activeAccountId ?: index.accounts.firstOrNull()?.accountId
                    _accountIndex.value = index.copy(activeAccountId = activeAccountId)
                    activeAccountId?.let { accountId ->
                        store.read(accountId)?.let { session ->
                            session to index.accounts.firstOrNull { it.accountId == accountId }?.toAccount()
                        }
                    }
                }
                pending = withContext(ioDispatcher) {
                    store.readPending()?.takeIf { it.isFresh(System.currentTimeMillis()) }
                }
                if (restored != null) {
                    val (session, account) = restored
                    connect(session, account ?: fallbackAccount(session))
                } else {
                    _session.value = SessionUi(starting = false, pending = pending != null, origin = pending?.origin)
                    deferredCallback?.let(::callback)
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _session.value = SessionUi(starting = false, error = "Saved sign-in could not be restored. Please sign in again.")
            }
        }
    }

    fun signIn(input: String) {
        if (_session.value.busy) return
        authJob = viewModelScope.launch {
            _session.value = _session.value.copy(busy = true, error = null)
            try {
                val next = auth.prepare(input)
                withContext(ioDispatcher) { store.writePending(next) }
                pending = next
                _session.value = SessionUi(
                    starting = false,
                    pending = true,
                    origin = next.origin,
                    browserUrl = auth.browserUrl(next),
                )
            } catch (e: Exception) {
                failAuth(e)
            }
        }
    }

    fun browserOpened() { _session.value = _session.value.copy(browserUrl = null) }

    fun browserFailed() {
        _session.value = _session.value.copy(
            browserUrl = null,
            error = "No browser could be opened. Install or enable a browser and try again.",
        )
    }

    fun reopenBrowser() {
        pending?.let { _session.value = _session.value.copy(browserUrl = auth.browserUrl(it), error = null) }
    }

    fun callback(value: String) {
        if (_session.value.starting) {
            deferredCallback = value
            return
        }
        val request = pending ?: return
        if (AuthCallback.matches(value, request, System.currentTimeMillis())) {
            pending = request.copy(authorizationCode = AuthCallback.authorizationCode(value))
            finishSignIn()
        } else {
            _session.value = _session.value.copy(error = "This sign-in callback is invalid or expired. Please try again.")
        }
    }

    fun finishSignIn() {
        val request = pending ?: return
        if (_session.value.busy) return
        authJob = viewModelScope.launch {
            _session.value = _session.value.copy(busy = true, error = null, browserUrl = null)
            try {
                val result = auth.complete(request)
                val account = result.account
                val session = Session(account.id, result.token, ServerCapabilities(canPublish = result.canPublish))
                withContext(ioDispatcher) {
                    store.write(account.id, session)
                    store.writeProfile(account.id, result.user)
                    val index = store.readIndex()
                    val updatedIndex = index.withAccount(account).copy(activeAccountId = account.id)
                    store.writeIndex(updatedIndex)
                    store.clearPending()
                    _accountIndex.value = updatedIndex
                }
                pending = null
                connect(session, account)
            } catch (e: Exception) {
                failAuth(e)
            }
        }
    }

    fun switchAccount(accountId: me.foxtails.palustris.domain.AccountId) {
        if (_session.value.busy) return
        viewModelScope.launch {
            try {
                val switched = withContext(ioDispatcher) {
                    val session = store.read(accountId) ?: return@withContext null
                    val index = store.readIndex().copy(activeAccountId = accountId)
                    store.writeIndex(index)
                    index to session
                }
                if (switched == null) {
                    _session.value = _session.value.copy(error = "That account is no longer available on this device.")
                } else {
                    val (index, session) = switched
                    _accountIndex.value = index
                    connect(session, index.accounts.firstOrNull { it.accountId == accountId }?.toAccount() ?: fallbackAccount(session))
                }
            } catch (e: Exception) {
                failAuth(e)
            }
        }
    }

    fun removeAccount(accountId: me.foxtails.palustris.domain.AccountId) {
        viewModelScope.launch {
            try {
                val replacement = withContext(ioDispatcher) {
                    store.delete(accountId)
                    val index = store.readIndex()
                    val accounts = index.accounts.filterNot { it.accountId == accountId }
                    val nextId = if (index.activeAccountId == accountId) accounts.firstOrNull()?.accountId else index.activeAccountId
                    val updated = index.copy(accounts = accounts, activeAccountId = nextId)
                    store.writeIndex(updated)
                    nextId?.let { store.read(it) }?.let { it to updated } ?: (null to updated)
                }
                _accountIndex.value = replacement.second
                if (loginAccountId() == accountId) {
                    val nextSession = replacement.first
                    if (nextSession == null) {
                        _activeSession.value = null
                        _session.value = SessionUi(starting = false)
                    } else {
                        connect(nextSession, replacement.second.accounts.first { it.accountId == nextSession.accountId }.toAccount())
                    }
                }
            } catch (e: Exception) {
                failAuth(e)
            }
        }
    }

    fun signOut() {
        authJob?.cancel()
        val accountId = loginAccountId()
        if (accountId != null) removeAccount(accountId) else viewModelScope.launch {
            withContext(ioDispatcher) { store.clearPending() }
            pending = null
            _session.value = SessionUi(starting = false)
        }
    }

    private fun connect(value: Session, account: Account) {
        _activeSession.value = value
        _session.value = SessionUi(starting = false, account = account, origin = value.accountId.connection.origin)
    }

    private fun loginAccountId(): me.foxtails.palustris.domain.AccountId? = _activeSession.value?.accountId

    private fun fallbackAccount(session: Session): Account = Account(
        session.accountId,
        session.accountId.localId,
        session.accountId.localId,
    )

    private fun failAuth(e: Exception) {
        if (e is CancellationException) throw e
        _session.value = _session.value.copy(busy = false, error = message(e), browserUrl = null)
    }

    private fun message(e: Exception): String = sourceErrorMessage(e)
}

private fun AccountIndex.withAccount(account: Account): AccountIndex {
    val ref = AccountRef(account.id, account.handle, account.avatarUrl, account.displayName)
    return copy(accounts = accounts.filterNot { it.accountId == account.id } + ref)
}
