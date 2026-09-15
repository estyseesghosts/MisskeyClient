package me.foxtails.palustris

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.testing.WorkManagerTestInitHelper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import me.foxtails.palustris.data.AccountSourceRegistry
import me.foxtails.palustris.data.SocialSourceFactory
import me.foxtails.palustris.data.auth.AccountIndex
import me.foxtails.palustris.data.auth.SessionStore
import me.foxtails.palustris.data.misskey.HttpClientPool
import me.foxtails.palustris.data.notifications.AndroidNotificationPresenter
import me.foxtails.palustris.data.notifications.InMemoryNotificationStore
import me.foxtails.palustris.data.notifications.NotificationPermissionController
import me.foxtails.palustris.data.notifications.NotificationPresentation
import me.foxtails.palustris.data.notifications.NotificationPresentationFactory
import me.foxtails.palustris.data.notifications.NotificationPresenter
import me.foxtails.palustris.data.notifications.NotificationRepository
import me.foxtails.palustris.data.notifications.NotificationSettingsRepository
import me.foxtails.palustris.data.notifications.push.PushConnectorFailure
import me.foxtails.palustris.data.notifications.push.PushConnectorOperation
import me.foxtails.palustris.data.notifications.push.PushMessageDecoder
import me.foxtails.palustris.data.notifications.push.PushRegistrationManager
import me.foxtails.palustris.data.notifications.push.PushRegistrationRepository
import me.foxtails.palustris.data.notifications.push.PushRegistrationWorkResult
import me.foxtails.palustris.data.notifications.push.UnifiedPushConnector
import me.foxtails.palustris.data.notifications.push.UnifiedPushMessageHandler
import me.foxtails.palustris.data.notifications.push.UnifiedPushRegistrationManager
import me.foxtails.palustris.data.notifications.push.registerWithDistributor
import me.foxtails.palustris.data.notifications.work.NotificationWorkScheduler
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.CapabilityStatus
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.EntityId
import me.foxtails.palustris.domain.NotificationCheckpoint
import me.foxtails.palustris.domain.NotificationPage
import me.foxtails.palustris.domain.NotificationPushRegistrationState
import me.foxtails.palustris.domain.NotificationQuery
import me.foxtails.palustris.domain.NotificationSyncToken
import me.foxtails.palustris.domain.Page
import me.foxtails.palustris.domain.Post
import me.foxtails.palustris.domain.Protocol
import me.foxtails.palustris.domain.PushProviderInfo
import me.foxtails.palustris.domain.PushRegistration
import me.foxtails.palustris.domain.PushSubscription
import me.foxtails.palustris.domain.ServerCapabilities
import me.foxtails.palustris.domain.Session
import me.foxtails.palustris.domain.SocialSource
import me.foxtails.palustris.domain.Timeline
import me.foxtails.palustris.domain.ValidatedUrl
import me.foxtails.palustris.ui.notifications.NotificationSettingsViewModel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Cancellation stays cancellation across push registration, removal, and settings paths. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PushCancellationTest {
    private val account = AccountId(Connection("https://push-cancel.example", Protocol.MISSKEY), "receiver")
    private val session = Session(
        account,
        "session-token",
        ServerCapabilities(),
        pushInstanceName = "push-instance",
        sessionRevision = 4,
    )

    @Before
    fun initializeWorkManager() {
        // Synchronous test driver. A real WorkManager database tracker crashes
        // under Robolectric and poisons later tests with uncaught exceptions.
        runCatching {
            WorkManagerTestInitHelper.initializeTestWorkManager(
                ApplicationProvider.getApplicationContext(),
            )
        }
    }

    @After
    fun resetMain() {
        Dispatchers.resetMain()
    }

    @Test
    fun reconcileRethrowsCancellationInsteadOfRecordingFailure() = runBlocking {
        val harness = Harness(pushInfo = { throw CancellationException("provider cancelled") })
        harness.repository.activate(NotificationSyncToken(account, 1))
        harness.registry.register(NotificationSyncToken(account, 1), harness.source)

        try {
            harness.manager.enable(account)
            fail("enable must rethrow cancellation")
        } catch (cancelled: CancellationException) {
            assertEquals("provider cancelled", cancelled.message)
        }

        val registration = harness.repository.pushRegistration(account)!!
        assertEquals(NotificationPushRegistrationState.RegisteringWithDistributor, registration.state)
        assertNull(registration.lastErrorCategory)
        assertNull(registration.failureStage)
    }

    @Test
    fun disableCompletesLocalCleanupAndRethrowsCancellation() = runBlocking {
        val harness = Harness(query = { throw CancellationException("removal cancelled") })
        val token = NotificationSyncToken(account, 1)
        harness.repository.activate(token)
        harness.repository.updatePushRegistration(token, PushRegistration(
            accountId = account,
            generation = token.generation,
            instanceName = "push-instance",
            sessionRevision = session.sessionRevision,
            state = NotificationPushRegistrationState.Connected,
        ))
        harness.registry.register(token, harness.source)

        try {
            harness.manager.disable(account)
            fail("disable must rethrow cancellation after local cleanup")
        } catch (cancelled: CancellationException) {
            assertEquals("removal cancelled", cancelled.message)
        }

        assertNull(harness.repository.pushRegistration(account))
    }

    @Test
    fun connectorPropagatesCancellationWithoutWrapping() {
        val connector = object : UnifiedPushConnector {
            override fun availableDistributors(): List<String> = listOf("com.example.distributor")
            override fun acknowledgedDistributor(): String? = null
            override fun saveDistributor(packageName: String) = throw CancellationException("save cancelled")
            override fun register(instanceName: String, messageForDistributor: String?, vapidPublicKey: String?) = Unit
            override fun unregister(instanceName: String) = Unit
        }
        try {
            registerWithDistributor(connector, "com.example.distributor", "instance", "message", null)
            fail("connector cancellation must propagate")
        } catch (cancelled: CancellationException) {
            assertEquals("save cancelled", cancelled.message)
        } catch (failure: PushConnectorFailure) {
            fail("cancellation must not become a connector failure: $failure")
        }
    }

    @Test
    fun retryCancellationPublishesNoError() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repository = NotificationRepository(InMemoryNotificationStore())
        val failing = object : PushRegistrationManager {
            override fun onSessionAvailable(accountId: AccountId) = Unit
            override suspend fun enable(accountId: AccountId) = Unit
            override suspend fun retry(accountId: AccountId): Unit = throw CancellationException("retry cancelled")
            override suspend fun processPendingEndpoint(accountId: AccountId) = PushRegistrationWorkResult.NoWork
            override suspend fun disable(accountId: AccountId) = Unit
        }
        val viewModel = NotificationSettingsViewModel(
            account,
            NotificationSettingsRepository(repository),
            repository,
            NotificationWorkScheduler(context),
            failing,
            GrantedPermission,
            NotificationPresentationFactory(context),
            NoPresenter(),
            Distributors(emptyList()),
            harnessStore(),
            context,
        )
        advanceUntilIdle()

        viewModel.retryRegistration()
        advanceUntilIdle()

        assertNull(viewModel.state.value.error)
    }

    @Test
    fun distributorsCancellationSkipsOrdinaryFailureMapping() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repository = NotificationRepository(InMemoryNotificationStore())
        val viewModel = NotificationSettingsViewModel(
            account,
            NotificationSettingsRepository(repository),
            repository,
            NotificationWorkScheduler(context),
            NoManager(),
            GrantedPermission,
            NotificationPresentationFactory(context),
            NoPresenter(),
            Distributors(fail = CancellationException("distributors cancelled")),
            harnessStore(),
            context,
        )
        advanceUntilIdle()

        assertTrue(viewModel.state.value.availableDistributors.isEmpty())
        assertTrue(viewModel.state.value.distributorLoading)
    }

    private inner class Harness(
        pushInfo: () -> PushProviderInfo = { PushProviderInfo(CapabilityStatus.Unsupported) },
        query: () -> PushSubscription? = { null },
    ) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repository = NotificationRepository(InMemoryNotificationStore())
        val store = harnessStore()
        val registry = AccountSourceRegistry()
        val source = CancellingSource(account, pushInfo, query)
        val connector = Distributors(listOf("com.example.distributor"))
        val presenter = NoPresenter()
        val scheduler = NotificationWorkScheduler(context)
        val registrationRepository = PushRegistrationRepository(repository, store)
        val manager = UnifiedPushRegistrationManager(
            registrationRepository,
            repository,
            store,
            registry,
            SocialSourceFactory(HttpClientPool()),
            scheduler,
            presenter,
            connector,
            GrantedPermission,
            context,
            UnifiedPushMessageHandler(
                registrationRepository,
                repository,
                store,
                registry,
                SocialSourceFactory(HttpClientPool()),
                scheduler,
                presenter,
                PushMessageDecoder(context),
            ),
        )
    }

    private fun harnessStore(): SessionStore = object : SessionStore {
        override fun read(accountId: AccountId): Session? = session.takeIf { it.accountId == accountId }
        override fun write(accountId: AccountId, session: Session) = Unit
        override fun delete(accountId: AccountId) = Unit
        override fun readIndex(): AccountIndex = AccountIndex()
        override fun writeIndex(index: AccountIndex) = Unit
        override fun clear() = Unit
    }

    private class CancellingSource(
        private val account: AccountId,
        private val pushInfo: () -> PushProviderInfo,
        private val query: () -> PushSubscription?,
    ) : SocialSource {
        override val capabilities = ServerCapabilities(timelines = setOf(Timeline.Home))
        override suspend fun timeline(timeline: Timeline, cursor: String?): Page<Post> = Page(emptyList())
        override suspend fun notifications(
            query: NotificationQuery,
            cursor: me.foxtails.palustris.domain.NotificationCursor?,
        ): NotificationPage = NotificationPage(
            items = emptyList(),
            checkpoint = NotificationCheckpoint(account, query),
        )
        override suspend fun dismissNotification(id: EntityId) = Unit
        override suspend fun pushProviderInfo(): PushProviderInfo = pushInfo()
        override suspend fun queryOwnedPushSubscription(knownEndpoint: ValidatedUrl?): PushSubscription? = query()
    }

    private class Distributors(
        private val available: List<String> = emptyList(),
        private val fail: Throwable? = null,
    ) : UnifiedPushConnector {
        override fun availableDistributors(): List<String> {
            fail?.let { throw it }
            return available
        }
        override fun acknowledgedDistributor(): String? = null
        override fun saveDistributor(packageName: String) {
            fail?.let { throw it }
        }
        override fun register(instanceName: String, messageForDistributor: String?, vapidPublicKey: String?) = Unit
        override fun unregister(instanceName: String) = Unit
    }

    private class NoPresenter : NotificationPresenter {
        override fun present(presentation: NotificationPresentation): Boolean = false
        override fun dismiss(accountId: AccountId, notificationId: EntityId) = Unit
    }

    private object GrantedPermission : NotificationPermissionController {
        override fun isGranted(): Boolean = true
    }

    private class NoManager : PushRegistrationManager {
        override fun onSessionAvailable(accountId: AccountId) = Unit
        override suspend fun enable(accountId: AccountId) = Unit
        override suspend fun retry(accountId: AccountId) = Unit
        override suspend fun processPendingEndpoint(accountId: AccountId) = PushRegistrationWorkResult.NoWork
        override suspend fun disable(accountId: AccountId) = Unit
    }
}
