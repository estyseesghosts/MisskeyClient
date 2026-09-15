package me.foxtails.palustris

import androidx.lifecycle.ViewModelStore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import me.foxtails.palustris.data.AccountSourceRegistry
import me.foxtails.palustris.data.SocialSourceFactory
import me.foxtails.palustris.data.auth.AccountIndex
import me.foxtails.palustris.data.auth.AccountRef
import me.foxtails.palustris.data.auth.AuthGateway
import me.foxtails.palustris.data.auth.DraftWriteAuthority
import me.foxtails.palustris.data.auth.InMemoryDraftStore
import me.foxtails.palustris.data.auth.LoginSession
import me.foxtails.palustris.data.auth.PendingLogin
import me.foxtails.palustris.data.auth.SessionStore
import me.foxtails.palustris.data.directmessages.DirectMessageWriteAuthority
import me.foxtails.palustris.data.directmessages.InMemoryDirectMessageStore
import me.foxtails.palustris.data.emoji.InMemoryEmojiCatalogRepository
import me.foxtails.palustris.data.misskey.HttpClientPool
import me.foxtails.palustris.data.notifications.NoOpNotificationStreamController
import me.foxtails.palustris.data.notifications.NotificationSyncController
import me.foxtails.palustris.data.notifications.NotificationSyncState
import me.foxtails.palustris.data.notifications.push.NoOpPushRegistrationManager
import me.foxtails.palustris.data.preferences.InMemoryEmojiPickerPreferencesRepository
import me.foxtails.palustris.data.preferences.InMemoryPhotoGridPreferencesRepository
import me.foxtails.palustris.data.preferences.InMemoryPostPreferencesRepository
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.NotificationSyncToken
import me.foxtails.palustris.domain.Protocol
import me.foxtails.palustris.domain.ServerCapabilities
import me.foxtails.palustris.domain.Session
import me.foxtails.palustris.domain.SocialSource
import me.foxtails.palustris.ui.AccountManager
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Regression coverage for the coherent connected identity.
 *
 * The shell must receive one accepted context that joins the account, the durable revision, the
 * runtime presentation generation, and the registered source. It must never join a separate
 * account emission with a separate session emission, and it must never create an unregistered
 * source fallback.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ConnectedSessionContextTest {
    private val login = LoginSession(
        "https://example.org",
        "test-token",
        JSONObject("""{"id":"a","username":"alice"}"""),
    )

    private class MemoryStore(
        initialSession: Session? = null,
        initialAccount: Account? = null,
    ) : SessionStore {
        val sessions = mutableMapOf<AccountId, Session>().apply {
            initialSession?.let { put(it.accountId, it) }
        }
        var pending: PendingLogin? = null
        var index = initialAccount?.let { account ->
            AccountIndex(
                accounts = listOf(AccountRef(account.id, account.handle, account.avatarUrl, account.displayName)),
                activeAccountId = account.id,
            )
        } ?: AccountIndex()

        override fun read(accountId: AccountId): Session? = sessions[accountId]
        override fun write(accountId: AccountId, session: Session) { sessions[accountId] = session }
        override fun delete(accountId: AccountId) { sessions.remove(accountId) }
        override fun readIndex() = index
        override fun writeIndex(index: AccountIndex) { this.index = index }
        override fun readPending() = pending
        override fun writePending(pending: PendingLogin) { this.pending = pending }
        override fun clearPending() { pending = null }
        override fun clear() { sessions.clear(); pending = null; index = AccountIndex() }
    }

    private class RegistryController(
        private val registry: AccountSourceRegistry,
    ) : NotificationSyncController {
        private val states = mutableMapOf<AccountId, MutableStateFlow<NotificationSyncState>>()
        private val generations = mutableMapOf<AccountId, Long>()

        @Synchronized
        override fun observeAccount(accountId: AccountId): StateFlow<NotificationSyncState> =
            states.getOrPut(accountId) { MutableStateFlow(NotificationSyncState()) }.asStateFlow()

        override fun register(accountId: AccountId, source: SocialSource): NotificationSyncToken {
            val token = synchronized(this) {
                val generation = (generations[accountId] ?: 0L) + 1L
                generations[accountId] = generation
                NotificationSyncToken(accountId, generation)
            }
            registry.register(token, source)
            return token
        }

        override fun unregister(accountId: AccountId) { registry.remove(accountId) }

        override fun removeAccount(accountId: AccountId) {
            registry.remove(accountId)
            synchronized(this) { states.remove(accountId) }
        }
    }

    private fun accountManager(
        store: SessionStore,
        auth: AuthGateway,
        controller: NotificationSyncController,
        dispatcher: CoroutineDispatcher,
    ) = AccountManager(
        store = store,
        auth = auth,
        ioDispatcher = dispatcher,
        sourceFactory = SocialSourceFactory(HttpClientPool()),
        notificationSync = controller,
        pushRegistrationManager = NoOpPushRegistrationManager(),
        notificationStreamController = NoOpNotificationStreamController(),
        postPreferencesRepository = InMemoryPostPreferencesRepository(),
        photoGridPreferencesRepository = InMemoryPhotoGridPreferencesRepository(),
        directMessageStore = InMemoryDirectMessageStore(),
        directMessageWriteAuthority = DirectMessageWriteAuthority(),
        emojiCatalogRepository = InMemoryEmojiCatalogRepository(),
        emojiPickerPreferencesRepository = InMemoryEmojiPickerPreferencesRepository(),
        draftStore = InMemoryDraftStore(),
        draftWriteAuthority = DraftWriteAuthority(),
    )

    private fun auth(result: LoginSession) = object : AuthGateway {
        override suspend fun prepare(input: String) =
            PendingLogin(input, "session-id", System.currentTimeMillis())
        override fun browserUrl(pending: PendingLogin) = "${pending.origin}/miauth/${pending.id}"
        override suspend fun complete(pending: PendingLogin) = result
    }

    private fun ref(account: Account) = AccountRef(
        account.id,
        account.handle,
        account.avatarUrl,
        account.displayName,
    )

    @Test
    fun publishesOneContextOnlyAfterTheRegisteredSourceIsReady() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val owner = ViewModelStore()
        try {
            val store = MemoryStore(
                Session(login.account.id, login.token, ServerCapabilities()),
                login.account,
            )
            val registry = AccountSourceRegistry()
            val manager = accountManager(store, auth(login), RegistryController(registry), StandardTestDispatcher(testScheduler))
            owner.put("accounts", manager)

            // During startup the shell has one bounded loading state and no partial identity.
            assertNull(manager.connectedContext.value)

            advanceUntilIdle()

            val context = manager.connectedContext.value!!
            assertEquals(login.account.id, context.accountId)
            assertEquals(1L, context.sessionRevision)
            assertEquals(1L, context.presentationGeneration)
            assertEquals(context.accountId, context.registryToken.accountId)
            assertSame(context.source, registry.sourceFor(context.registryToken))
            // The visible account and the context are published together.
            assertEquals(context.accountId, manager.session.value.account?.id)
        } finally {
            owner.clear()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun switchingAccountReplacesTheWholeContext() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val owner = ViewModelStore()
        try {
            val second = LoginSession(
                "https://other.example",
                "second-token",
                JSONObject("""{"id":"b","username":"bob"}"""),
            )
            val store = MemoryStore(
                Session(login.account.id, login.token, ServerCapabilities()),
                login.account,
            )
            store.sessions[second.account.id] = Session(second.account.id, "second-token", ServerCapabilities())
            store.index = AccountIndex(
                accounts = listOf(ref(login.account), ref(second.account)),
                activeAccountId = login.account.id,
            )
            val registry = AccountSourceRegistry()
            val manager = accountManager(store, auth(login), RegistryController(registry), StandardTestDispatcher(testScheduler))
            owner.put("accounts", manager)
            advanceUntilIdle()
            val first = manager.connectedContext.value!!

            manager.switchAccount(second.account.id)
            advanceUntilIdle()

            val next = manager.connectedContext.value!!
            assertEquals(second.account.id, next.accountId)
            assertEquals(next.accountId, manager.session.value.account?.id)
            assertTrue(next.presentationGeneration > first.presentationGeneration)
            assertNotSame(first.source, next.source)
        } finally {
            owner.clear()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun sameAccountReplacementBumpsRevisionAndRetiresTheOldRegistration() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val owner = ViewModelStore()
        try {
            val replacement = LoginSession(
                "https://example.org",
                "replacement-token",
                JSONObject("""{"id":"a","username":"alice"}"""),
            )
            val store = MemoryStore(
                Session(login.account.id, login.token, ServerCapabilities()),
                login.account,
            )
            val registry = AccountSourceRegistry()
            val manager = accountManager(store, auth(replacement), RegistryController(registry), StandardTestDispatcher(testScheduler))
            owner.put("accounts", manager)
            advanceUntilIdle()
            val first = manager.connectedContext.value!!

            manager.signIn("https://example.org")
            advanceUntilIdle()
            manager.callback("palustris://auth/misskey?session=session-id")
            advanceUntilIdle()

            val replaced = manager.connectedContext.value!!
            assertEquals(login.account.id, replaced.accountId)
            assertEquals(2L, replaced.sessionRevision)
            assertTrue(replaced.presentationGeneration > first.presentationGeneration)
            assertNotSame(first.source, replaced.source)
            // The retired registration is not resolvable for the old token.
            assertNull(registry.sourceFor(first.registryToken))
            assertSame(replaced.source, registry.sourceFor(replaced.registryToken))
        } finally {
            owner.clear()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun profileUpdateKeepsTheAcceptedSourceRevisionAndGeneration() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val owner = ViewModelStore()
        try {
            val store = MemoryStore(
                Session(login.account.id, login.token, ServerCapabilities()),
                login.account,
            )
            val registry = AccountSourceRegistry()
            val manager = accountManager(store, auth(login), RegistryController(registry), StandardTestDispatcher(testScheduler))
            owner.put("accounts", manager)
            advanceUntilIdle()
            val before = manager.connectedContext.value!!

            manager.updateAccount(login.account.copy(displayName = "Alice Updated"))
            advanceUntilIdle()

            val after = manager.connectedContext.value!!
            assertEquals("Alice Updated", after.account.displayName)
            assertSame(before.source, after.source)
            assertEquals(before.sessionRevision, after.sessionRevision)
            assertEquals(before.presentationGeneration, after.presentationGeneration)
            assertEquals(before.registryToken, after.registryToken)
        } finally {
            owner.clear()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun aRetiredRegistrationCannotResolveToTheAcceptedSource() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val owner = ViewModelStore()
        try {
            val store = MemoryStore(
                Session(login.account.id, login.token, ServerCapabilities()),
                login.account,
            )
            val registry = AccountSourceRegistry()
            val manager = accountManager(store, auth(login), RegistryController(registry), StandardTestDispatcher(testScheduler))
            owner.put("accounts", manager)
            advanceUntilIdle()
            val context = manager.connectedContext.value!!
            assertSame(context.source, registry.sourceFor(context.registryToken))

            // A retired registration cannot be resolved back to a live source.
            registry.remove(context.accountId, context.registryToken.generation)
            assertNull(registry.sourceFor(context.registryToken))
        } finally {
            owner.clear()
            Dispatchers.resetMain()
        }
    }
}
