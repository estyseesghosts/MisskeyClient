package me.foxtails.palustris.data.notifications.push

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import me.foxtails.palustris.data.AccountSourceRegistry
import me.foxtails.palustris.data.SocialSourceFactory
import me.foxtails.palustris.data.auth.SessionStore
import me.foxtails.palustris.data.notifications.NotificationPresenter
import me.foxtails.palustris.data.notifications.NotificationRepository
import me.foxtails.palustris.data.notifications.work.NotificationWorkScheduler
import me.foxtails.palustris.domain.AccessScope
import me.foxtails.palustris.domain.AccessStatus
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.NotificationPushRegistrationState
import me.foxtails.palustris.domain.PushRegistration
import me.foxtails.palustris.domain.PushSubscriptionSpec
import me.foxtails.palustris.domain.Protocol
import me.foxtails.palustris.domain.SourceError
import org.unifiedpush.android.connector.UnifiedPush
import org.unifiedpush.android.connector.data.PushEndpoint
import org.unifiedpush.android.connector.data.PushMessage
import org.unifiedpush.android.connector.keys.DefaultKeyManager

interface PushRegistrationManager {
    fun onSessionAvailable(accountId: AccountId)
    suspend fun enable(accountId: AccountId)
    suspend fun disable(accountId: AccountId)
}

class NoOpPushRegistrationManager : PushRegistrationManager {
    override fun onSessionAvailable(accountId: AccountId) = Unit
    override suspend fun enable(accountId: AccountId) = Unit
    override suspend fun disable(accountId: AccountId) = Unit
}

/** Bridges the distributor callbacks to an account-scoped, authenticated source. */
@Singleton
class UnifiedPushRegistrationManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val registrationRepository: PushRegistrationRepository,
    private val repository: NotificationRepository,
    private val sessionStore: SessionStore,
    private val sourceRegistry: AccountSourceRegistry,
    private val sourceFactory: SocialSourceFactory,
    private val scheduler: NotificationWorkScheduler,
    private val presenter: NotificationPresenter,
) : PushRegistrationManager, AutoCloseable {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Mutex()
    private val keyManager = DefaultKeyManager(context)

    override fun onSessionAvailable(accountId: AccountId) {
        scope.launch {
            if (repository.settings(accountId).alertsEnabled) enable(accountId)
        }
    }

    override suspend fun enable(accountId: AccountId) = lock.withLock {
        val token = repository.currentToken(accountId) ?: return
        val session = sessionStore.read(accountId) ?: return
        val settings = repository.settings(accountId)
        val distributor = chooseDistributor(settings.selectedDistributor)
        val registration = registrationRepository.prepare(token, distributor)
        if (session.accountId.connection.protocol == Protocol.MASTODON &&
            session.access.status(AccessScope.Push) == AccessStatus.Denied
        ) {
            update(token, registration.copy(state = NotificationPushRegistrationState.AccessDenied, lastErrorCategory = "push_scope_denied"))
            return
        }
        if (distributor == null) {
            val state = if (UnifiedPush.getDistributors(context).isEmpty()) {
                NotificationPushRegistrationState.NoDistributor
            } else {
                NotificationPushRegistrationState.DistributorSelectionRequired
            }
            update(token, registration.copy(state = state, lastErrorCategory = "distributor_unavailable"))
            return
        }
        val registering = registration.copy(
            distributorPackage = distributor,
            state = NotificationPushRegistrationState.RegisteringWithDistributor,
            retryCount = 0,
            lastErrorCategory = null,
        )
        update(token, registering)
        try {
            if (!keyManager.exists(registering.instanceName)) keyManager.generate(registering.instanceName)
            UnifiedPush.saveDistributor(context, distributor)
            UnifiedPush.register(
                context,
                registering.instanceName,
                "Palustris notifications",
                null,
                keyManager,
            )
        } catch (_: Exception) {
            update(token, registering.copy(
                state = NotificationPushRegistrationState.TemporarilyUnavailable,
                retryCount = registering.retryCount + 1,
                lastErrorCategory = "distributor_registration_failed",
            ))
        }
    }

    override suspend fun disable(accountId: AccountId) {
        lock.withLock {
            val token = repository.currentToken(accountId)
            val registration = repository.pushRegistration(accountId)
            val session = sessionStore.read(accountId)
            if (token != null && registration != null) update(token, registration.copy(
                generation = token.generation,
                state = NotificationPushRegistrationState.Removing,
                lastErrorCategory = null,
            ))
            try {
                if (session != null) sourceFor(session, token)?.removePushSubscription()
            } catch (_: Exception) {
                // Local opt-out and distributor removal still proceed if the server is unavailable.
            }
            registration?.let {
                runCatching { UnifiedPush.unregister(context, it.instanceName, keyManager) }
                runCatching { keyManager.delete(it.instanceName) }
            }
            scheduler.cancel(accountId)
            repository.observe(accountId).value.items.forEach { presenter.dismiss(accountId, it.id) }
            token?.let { repository.clearPushRegistration(it) }
        }
    }

    fun onNewEndpoint(endpoint: PushEndpoint, instanceName: String) {
        scope.launch { handleNewEndpoint(endpoint, instanceName) }
    }

    fun onMessage(message: PushMessage, instanceName: String) {
        scope.launch {
            val owner = registrationRepository.find(instanceName) ?: return@launch
            if (!message.decrypted) {
                scheduler.enqueueCatchUp(owner.accountId)
                return@launch
            }
            when (PushPayloadParser.classify(message.content)) {
                PushPayloadHint.Ignored -> Unit
                PushPayloadHint.Notification,
                PushPayloadHint.ReadInvalidation,
                PushPayloadHint.RegistrationInvalidation,
                PushPayloadHint.Refresh,
                PushPayloadHint.RejectedSensitive,
                -> scheduler.enqueueCatchUp(owner.accountId)
            }
        }
    }

    fun onRegistrationFailed(instanceName: String) {
        updateFromCallback(instanceName, NotificationPushRegistrationState.TemporarilyUnavailable, "distributor_registration_failed")
    }

    fun onTempUnavailable(instanceName: String) {
        updateFromCallback(instanceName, NotificationPushRegistrationState.TemporarilyUnavailable, "distributor_temporarily_unavailable")
    }

    fun onUnregistered(instanceName: String) {
        updateFromCallback(instanceName, NotificationPushRegistrationState.TemporarilyUnavailable, "distributor_unregistered")
    }

    private suspend fun handleNewEndpoint(endpoint: PushEndpoint, instanceName: String) = lock.withLock {
        val owner = registrationRepository.find(instanceName) ?: return
        val publicKeySet = endpoint.pubKeySet ?: run {
            update(owner.token, owner.registration.copy(
                state = NotificationPushRegistrationState.TemporarilyUnavailable,
                lastErrorCategory = "endpoint_keys_missing",
            ))
            return
        }
        val validatedEndpoint = me.foxtails.palustris.domain.ValidatedUrl.https(endpoint.url) ?: run {
            update(owner.token, owner.registration.copy(
                state = NotificationPushRegistrationState.TemporarilyUnavailable,
                lastErrorCategory = "endpoint_invalid",
            ))
            return
        }
        val previousEndpoint = owner.registration.endpoint
        val received = owner.registration.copy(
            generation = owner.token.generation,
            endpoint = validatedEndpoint,
            endpointGeneration = owner.registration.endpointGeneration + 1,
            state = NotificationPushRegistrationState.EndpointReceived,
            lastErrorCategory = null,
        )
        update(owner.token, received)
        val settings = repository.settings(owner.accountId)
        val spec = PushSubscriptionSpec(
            accountId = owner.accountId,
            endpoint = validatedEndpoint,
            publicKey = publicKeySet.pubKey,
            authSecret = publicKeySet.auth,
            alerts = settings.categories,
        )
        try {
            update(owner.token, received.copy(state = NotificationPushRegistrationState.RegisteringWithServer))
            val subscription = if (previousEndpoint == null) {
                sourceFor(owner.session, owner.token)?.createPushSubscription(spec)
                    ?: throw SourceError.Unauthorized
            } else {
                sourceFor(owner.session, owner.token)?.updatePushSubscription(spec)
                    ?: throw SourceError.Unauthorized
            }
            update(owner.token, received.copy(
                endpoint = subscription.endpoint,
                state = NotificationPushRegistrationState.Connected,
                retryCount = 0,
                lastErrorCategory = null,
            ))
            scheduler.enqueueCatchUp(owner.accountId)
        } catch (error: CancellationException) {
            throw error
        } catch (error: SourceError) {
            update(owner.token, received.copy(
                state = if (error == SourceError.Unauthorized) NotificationPushRegistrationState.AccessDenied
                else NotificationPushRegistrationState.TemporarilyUnavailable,
                retryCount = received.retryCount + 1,
                lastErrorCategory = errorCategory(error),
            ))
        } catch (_: Exception) {
            update(owner.token, received.copy(
                state = NotificationPushRegistrationState.TemporarilyUnavailable,
                retryCount = received.retryCount + 1,
                lastErrorCategory = "server_registration_failed",
            ))
        }
    }

    private fun updateFromCallback(instanceName: String, state: NotificationPushRegistrationState, errorCategory: String) {
        scope.launch {
            val owner = registrationRepository.find(instanceName) ?: return@launch
            lock.withLock { update(owner.token, owner.registration.copy(
                generation = owner.token.generation,
                state = state,
                lastErrorCategory = errorCategory,
            )) }
        }
    }

    private suspend fun update(token: me.foxtails.palustris.domain.NotificationSyncToken, registration: PushRegistration) {
        repository.updatePushRegistration(token, registration)
    }

    private fun sourceFor(session: me.foxtails.palustris.domain.Session, token: me.foxtails.palustris.domain.NotificationSyncToken?) =
        token?.let(sourceRegistry::sourceFor) ?: sourceRegistry.sourceFor(session.accountId) ?: sourceFactory.create(session)

    private fun chooseDistributor(selected: String?): String? {
        val available = UnifiedPush.getDistributors(context)
        val acknowledged = UnifiedPush.getAckDistributor(context)
        return acknowledged?.takeIf { it in available }
            ?: selected?.takeIf { it in available }
            ?: available.firstOrNull { it == SUNUP_PACKAGE }
            ?: available.singleOrNull()
    }

    private fun errorCategory(error: SourceError): String = when (error) {
        SourceError.Unauthorized -> "server_push_unauthorized"
        SourceError.NetworkUnavailable -> "server_push_network"
        SourceError.RateLimited -> "server_push_rate_limited"
        is SourceError.Unsupported -> "server_push_unsupported"
        is SourceError.ServerError -> "server_push_server_error"
        SourceError.AccountMismatch -> "server_push_account_mismatch"
    }

    override fun close() {
        scope.cancel()
    }

    private companion object {
        const val SUNUP_PACKAGE = "org.unifiedpush.distributor.sunup"
    }
}
