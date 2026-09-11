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
import me.foxtails.palustris.data.SocialSourceFactory
import me.foxtails.palustris.data.auth.AccountIndex
import me.foxtails.palustris.data.auth.AccountRef
import me.foxtails.palustris.data.auth.AuthCallback
import me.foxtails.palustris.data.auth.AuthGateway
import me.foxtails.palustris.data.auth.PendingLogin
import me.foxtails.palustris.data.auth.SessionStore
import me.foxtails.palustris.data.directmessages.DirectMessageStore
import me.foxtails.palustris.data.directmessages.InMemoryDirectMessageStore
import me.foxtails.palustris.data.emoji.InMemoryEmojiCatalogRepository
import me.foxtails.palustris.data.preferences.InMemoryEmojiPickerPreferencesRepository
import me.foxtails.palustris.data.auth.toAccount
import me.foxtails.palustris.data.misskey.HttpClientPool
import me.foxtails.palustris.data.preferences.InMemoryPostPreferencesRepository
import me.foxtails.palustris.data.notifications.push.NoOpPushRegistrationManager
import me.foxtails.palustris.data.notifications.push.PushRegistrationManager
import me.foxtails.palustris.data.notifications.NoOpNotificationStreamController
import me.foxtails.palustris.data.notifications.NotificationStreamController
import me.foxtails.palustris.di.IoDispatcher
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.EmojiCatalogRepository
import me.foxtails.palustris.domain.EmojiPickerPreferencesRepository
import me.foxtails.palustris.domain.PostPreferencesRepository
import me.foxtails.palustris.domain.PushSessionState
import me.foxtails.palustris.domain.ServerCapabilities
import me.foxtails.palustris.domain.Session
import me.foxtails.palustris.domain.SourceError
import org.json.JSONObject

data class SessionUi(
    val starting: Boolean = true,
    val busy: Boolean = false,
    val account: Account? = null,
    val addingAccount: Boolean = false,
    val origin: String? = null,
    val pending: Boolean = false,
    val browserUrl: String? = null,
    val error: String? = null,
    val sessionGeneration: Long = 0,
)

@HiltViewModel
class AccountManager @Inject constructor(
    private val store: SessionStore,
    private val auth: AuthGateway,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val sourceFactory: SocialSourceFactory,
    private val notificationSync: AccountNotificationSyncController,
    private val pushRegistrationManager: PushRegistrationManager,
    private val notificationStreamController: NotificationStreamController,
    private val postPreferencesRepository: PostPreferencesRepository,
    private val directMessageStore: DirectMessageStore,
    private val emojiCatalogRepository: EmojiCatalogRepository,
    private val emojiPickerPreferencesRepository: EmojiPickerPreferencesRepository,
) : ViewModel() {
    constructor(
        store: SessionStore,
        auth: AuthGateway,
        ioDispatcher: CoroutineDispatcher,
    ) : this(
        store,
        auth,
        ioDispatcher,
        SocialSourceFactory(HttpClientPool()),
        NoOpAccountNotificationSyncController(),
        NoOpPushRegistrationManager(),
        NoOpNotificationStreamController(),
        InMemoryPostPreferencesRepository(),
        InMemoryDirectMessageStore(),
        InMemoryEmojiCatalogRepository(),
        InMemoryEmojiPickerPreferencesRepository(),
    )
    private val _session = MutableStateFlow(SessionUi())
    val session = _session.asStateFlow()
    private val _accountIndex = MutableStateFlow(AccountIndex())
    val accountIndex = _accountIndex.asStateFlow()
    private val _activeSession = MutableStateFlow<Session?>(null)
    val activeSession = _activeSession.asStateFlow()
    private var pending: PendingLogin? = null
    private var authJob: Job? = null
    private var deferredCallback: String? = null
    private var sessionGeneration = 0L

    init {
        viewModelScope.launch {
            try {
                val restored = withContext(ioDispatcher) {
                    val index = store.readIndex()
                    val activeAccountId = index.activeAccountId ?: index.accounts.firstOrNull()?.accountId
                    val sessions = index.accounts.mapNotNull { ref -> store.read(ref.accountId) }
                    val active = activeAccountId?.let { accountId ->
                        sessions.firstOrNull { it.accountId == accountId }?.let { session ->
                            session to index.accounts.firstOrNull { it.accountId == accountId }?.toAccount()
                        }
                    }
                    RestoredAccounts(index.copy(activeAccountId = activeAccountId), sessions, active)
                }
                pending = withContext(ioDispatcher) {
                    store.readPending()?.takeIf { it.isFresh(System.currentTimeMillis()) }
                }
                _accountIndex.value = restored.index
                restored.sessions.forEach(::startNotificationSync)
                if (restored.active != null) {
                    val (session, account) = restored.active
                    connect(session, account ?: fallbackAccount(session))
                    if (pending != null) {
                        _session.value = _session.value.copy(
                            addingAccount = true,
                            pending = true,
                            origin = pending?.origin,
                        )
                    }
                    deferredCallback?.let(::callback)
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

    fun signIn(input: String, replacingAccountId: AccountId? = null) {
        if (_session.value.busy) return
        authJob = viewModelScope.launch {
            _session.value = _session.value.copy(busy = true, error = null, addingAccount = _activeSession.value != null)
            try {
                val next = auth.prepare(input).copy(replacingAccountId = replacingAccountId)
                withContext(ioDispatcher) { store.writePending(next) }
                pending = next
                _session.value = SessionUi(
                    starting = false,
                    pending = true,
                    account = _activeSession.value?.let { active ->
                        _accountIndex.value.accounts.firstOrNull { it.accountId == active.accountId }?.toAccount()
                            ?: fallbackAccount(active)
                    },
                    addingAccount = _activeSession.value != null,
                    origin = next.origin,
                    browserUrl = auth.browserUrl(next),
                )
            } catch (e: Exception) {
                failAuth(e)
            }
        }
    }

    /** Starts a permission upgrade without allowing a different account to replace the target. */
    fun upgradePermissions(accountId: AccountId) {
        if (_accountIndex.value.accounts.none { it.accountId == accountId }) {
            _session.value = _session.value.copy(error = "That account is no longer available on this device.")
            return
        }
        signIn(accountId.connection.origin, replacingAccountId = accountId)
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

    fun beginAddAccount() {
        if (_session.value.busy || _activeSession.value == null) return
        _session.value = _session.value.copy(addingAccount = true, pending = false, error = null)
    }

    fun cancelSignIn() {
        authJob?.cancel()
        pending = null
        deferredCallback = null
        viewModelScope.launch {
            withContext(ioDispatcher) { store.clearPending() }
            val active = _activeSession.value
            if (active == null) {
                _session.value = SessionUi(starting = false)
            } else {
                _session.value = SessionUi(
                    starting = false,
                    account = _accountIndex.value.accounts.firstOrNull { it.accountId == active.accountId }?.toAccount()
                        ?: fallbackAccount(active),
                    origin = active.accountId.connection.origin,
                )
            }
        }
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
                request.replacingAccountId?.let { expected ->
                    if (account.id != expected) throw SourceError.AccountMismatch
                }
                val session = withContext(ioDispatcher) {
                    store.transaction {
                    val previous = store.read(account.id)
                    val session = Session(
                        accountId = account.id,
                        token = result.token,
                        capabilities = result.capabilities.copy(
                            canPublish = result.capabilities.canPublish || result.canPublish,
                        ),
                        access = result.access,
                        pushInstanceName = previous?.pushInstanceName,
                        sessionRevision = (previous?.sessionRevision ?: 0L) + 1L,
                        pushState = previous?.pushState ?: PushSessionState(),
                    )
                    store.write(account.id, session)
                    store.writeProfile(account.id, result.user)
                    val index = store.readIndex()
                    val updatedIndex = index.withAccount(account).copy(activeAccountId = account.id)
                    store.writeIndex(updatedIndex)
                    store.clearPending()
                    _accountIndex.value = updatedIndex
                        session
                    }
                }
                pending = null
                startNotificationSync(session)
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
                    store.transaction {
                    val session = store.read(accountId) ?: return@transaction null
                    val index = store.readIndex().copy(activeAccountId = accountId)
                    store.writeIndex(index)
                        index to session
                    }
                }
                if (switched == null) {
                    _session.value = _session.value.copy(error = "That account is no longer available on this device.")
                } else {
                    val (index, session) = switched
                    _accountIndex.value = index
                    startNotificationSync(session)
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
                notificationStreamController.stop(accountId)
                pushRegistrationManager.disable(accountId)
                notificationSync.removeAccount(accountId)
                val replacement = withContext(ioDispatcher) {
                    postPreferencesRepository.remove(accountId)
                    directMessageStore.delete(accountId)
                    emojiCatalogRepository.remove(accountId)
                    emojiPickerPreferencesRepository.remove(accountId)
                    store.transaction {
                        store.delete(accountId)
                    val index = store.readIndex()
                    val accounts = index.accounts.filterNot { it.accountId == accountId }
                    val nextId = if (index.activeAccountId == accountId) accounts.firstOrNull()?.accountId else index.activeAccountId
                    val updated = index.copy(accounts = accounts, activeAccountId = nextId)
                    store.writeIndex(updated)
                        nextId?.let { store.read(it) }?.let { it to updated } ?: (null to updated)
                    }
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

    fun updateAccount(account: Account) {
        if (_activeSession.value?.accountId != account.id) return
        viewModelScope.launch {
            try {
                withContext(ioDispatcher) {
                    val index = store.readIndex()
                    store.writeIndex(index.withAccount(account))
                    store.writeProfile(account.id, account.toProfileJson())
                    _accountIndex.value = index.withAccount(account)
                }
                _session.value = _session.value.copy(account = account)
            } catch (e: Exception) {
                failAuth(e)
            }
        }
    }

    private fun connect(value: Session, account: Account) {
        sessionGeneration += 1L
        _activeSession.value = value
        _session.value = SessionUi(
            starting = false,
            account = account,
            origin = value.accountId.connection.origin,
            sessionGeneration = sessionGeneration,
        )
    }

    private fun startNotificationSync(session: Session) {
        notificationSync.register(session.accountId, sourceFactory.create(session))
        pushRegistrationManager.onSessionAvailable(session.accountId)
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

private data class RestoredAccounts(
    val index: AccountIndex,
    val sessions: List<Session>,
    val active: Pair<Session, Account?>?,
)

private fun AccountIndex.withAccount(account: Account): AccountIndex {
    val ref = AccountRef(account.id, account.handle, account.avatarUrl, account.displayName,
        biography = account.biography, profileFields = account.profileFields,
        bannerUrl = account.bannerUrl, followersCount = account.followersCount,
        followingCount = account.followingCount, postsCount = account.postsCount,
        locked = account.locked, bot = account.bot)
    return copy(accounts = accounts.filterNot { it.accountId == account.id } + ref)
}

private fun Account.toProfileJson(): JSONObject = JSONObject().apply {
    put("id", id.localId)
    put("username", handle.removePrefix("@").substringBefore('@'))
    put("host", handle.removePrefix("@").substringAfter('@', id.connection.origin.removePrefix("https://")))
    put("name", displayName)
    put("display_name", displayName)
    put("description", biography)
    put("note", biography)
    put("bannerUrl", bannerUrl)
    put("followersCount", followersCount)
    put("followingCount", followingCount)
    put("notesCount", postsCount)
    put("statuses_count", postsCount)
    put("isLocked", locked)
    put("isBot", bot)
    put("fields", org.json.JSONArray(profileFields.map { field ->
        JSONObject().put("name", field.name).put("value", field.value)
    }))
    avatarUrl?.let { put("avatarUrl", it); put("avatar", it) }
}
