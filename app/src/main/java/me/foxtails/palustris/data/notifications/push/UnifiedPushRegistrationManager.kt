package me.foxtails.palustris.data.notifications.push

import android.util.Log
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
import me.foxtails.palustris.domain.PushRegistrationFailureReason
import me.foxtails.palustris.domain.PushRegistrationFailureStage
import me.foxtails.palustris.domain.PushSubscriptionSpec
import me.foxtails.palustris.domain.Protocol
import me.foxtails.palustris.domain.SourceError
import org.unifiedpush.android.connector.FailedReason
import org.unifiedpush.android.connector.data.PushEndpoint
import org.unifiedpush.android.connector.data.PushMessage

interface PushRegistrationManager {
    fun onSessionAvailable(accountId: AccountId)
    suspend fun enable(accountId: AccountId)
    suspend fun retry(accountId: AccountId)
    suspend fun disable(accountId: AccountId)
}

class NoOpPushRegistrationManager : PushRegistrationManager {
    override fun onSessionAvailable(accountId: AccountId) = Unit
    override suspend fun enable(accountId: AccountId) = Unit
    override suspend fun retry(accountId: AccountId) = Unit
    override suspend fun disable(accountId: AccountId) = Unit
}

/** Bridges the distributor callbacks to an account-scoped, authenticated source. */
@Singleton
class UnifiedPushRegistrationManager @Inject constructor(
    private val registrationRepository: PushRegistrationRepository,
    private val repository: NotificationRepository,
    private val sessionStore: SessionStore,
    private val sourceRegistry: AccountSourceRegistry,
    private val sourceFactory: SocialSourceFactory,
    private val scheduler: NotificationWorkScheduler,
    private val presenter: NotificationPresenter,
    private val connector: UnifiedPushConnector,
) : PushRegistrationManager, AutoCloseable {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Mutex()
    override fun onSessionAvailable(accountId: AccountId) {
        scope.launch {
            if (!repository.settings(accountId).alertsEnabled) return@launch
            val registration = repository.pushRegistration(accountId)
            if (registration?.state == NotificationPushRegistrationState.Connected) return@launch
            if (registration != null && registration.nextRetryAtEpochMillis > System.currentTimeMillis()) return@launch
            enable(accountId)
        }
    }

    override suspend fun enable(accountId: AccountId) = reconcile(accountId, force = false)

    override suspend fun retry(accountId: AccountId) = reconcile(accountId, force = true)

    private suspend fun reconcile(accountId: AccountId, force: Boolean) = lock.withLock {
        val token = repository.currentToken(accountId) ?: return
        val session = sessionStore.read(accountId) ?: return
        val settings = repository.settings(accountId)
        val existing = repository.pushRegistration(accountId)
        if (!force && existing?.state == NotificationPushRegistrationState.Connected) return
        if (!force && existing != null && existing.nextRetryAtEpochMillis > System.currentTimeMillis()) return
        val distributor = try {
            chooseDistributor(settings.selectedDistributor)
        } catch (error: PushConnectorFailure) {
            val registration = registrationRepository.prepare(token, null)
            recordFailure(token, registration, error)
            return
        }
        val registration = registrationRepository.prepare(token, distributor)
        if (session.accountId.connection.protocol == Protocol.MASTODON &&
            session.access.status(AccessScope.Push) == AccessStatus.Denied
        ) {
            update(token, registration.copy(
                state = NotificationPushRegistrationState.AccessDenied,
                lastErrorCategory = "push_scope_denied",
                lastErrorDetail = null,
                failureStage = PushRegistrationFailureStage.ServerSubscription,
                failureReason = PushRegistrationFailureReason.Unauthorized,
            ))
            return
        }
        if (distributor == null) {
            val state = if (connector.availableDistributors().isEmpty()) {
                NotificationPushRegistrationState.NoDistributor
            } else {
                NotificationPushRegistrationState.DistributorSelectionRequired
            }
            update(token, registration.copy(
                state = state,
                lastErrorCategory = "distributor_unavailable",
                lastErrorDetail = null,
                failureStage = PushRegistrationFailureStage.DistributorSelection,
                failureReason = if (state == NotificationPushRegistrationState.NoDistributor) {
                    PushRegistrationFailureReason.NoDistributor
                } else {
                    PushRegistrationFailureReason.Unknown
                },
            ))
            return
        }
        val registering = registration.copy(
            distributorPackage = distributor,
            state = NotificationPushRegistrationState.RegisteringWithDistributor,
            lastErrorCategory = null,
            lastErrorDetail = null,
            failureStage = null,
            failureReason = null,
            nextRetryAtEpochMillis = 0,
        )
        update(token, registering)
        try {
            registerWithDistributor(
                connector = connector,
                distributorPackage = distributor,
                instanceName = registering.instanceName,
                messageForDistributor = "Palustris notifications",
            )
        } catch (error: PushConnectorFailure) {
            recordFailure(token, registering, error)
        } catch (error: Exception) {
            recordFailure(token, registering, error)
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
                runCatching { connector.unregister(it.instanceName) }
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

    fun onRegistrationFailed(instanceName: String, reason: String? = null) {
        updateFromCallback(
            instanceName,
            NotificationPushRegistrationState.TemporarilyUnavailable,
            "distributor_registration_failed",
            PushRegistrationFailureStage.ConnectorRegistration,
            PushRegistrationFailureReason.ConnectorFailure,
            reason,
        )
    }

    fun onTempUnavailable(instanceName: String) {
        updateFromCallback(
            instanceName,
            NotificationPushRegistrationState.TemporarilyUnavailable,
            "distributor_temporarily_unavailable",
            PushRegistrationFailureStage.ConnectorRegistration,
            PushRegistrationFailureReason.Network,
        )
    }

    fun onUnregistered(instanceName: String) {
        updateFromCallback(
            instanceName,
            NotificationPushRegistrationState.TemporarilyUnavailable,
            "distributor_unregistered",
            PushRegistrationFailureStage.ConnectorRegistration,
            PushRegistrationFailureReason.ConnectorFailure,
        )
    }

    private suspend fun handleNewEndpoint(endpoint: PushEndpoint, instanceName: String) = lock.withLock {
        val owner = registrationRepository.find(instanceName) ?: return
        val publicKeySet = endpoint.pubKeySet ?: run {
            update(owner.token, owner.registration.copy(
                state = NotificationPushRegistrationState.TemporarilyUnavailable,
                lastErrorCategory = "endpoint_keys_missing",
                failureStage = PushRegistrationFailureStage.EndpointValidation,
                failureReason = PushRegistrationFailureReason.MissingKeys,
            ))
            return
        }
        val validatedEndpoint = me.foxtails.palustris.domain.ValidatedUrl.https(endpoint.url) ?: run {
            update(owner.token, owner.registration.copy(
                state = NotificationPushRegistrationState.TemporarilyUnavailable,
                lastErrorCategory = "endpoint_invalid",
                failureStage = PushRegistrationFailureStage.EndpointValidation,
                failureReason = PushRegistrationFailureReason.InvalidEndpoint,
            ))
            return
        }
        val previousServerEndpoint = owner.registration.serverEndpoint
        if (owner.registration.endpoint == validatedEndpoint &&
            owner.registration.state == NotificationPushRegistrationState.Connected
        ) return
        val received = owner.registration.copy(
            generation = owner.token.generation,
            endpoint = validatedEndpoint,
            serverEndpoint = previousServerEndpoint,
            endpointGeneration = if (owner.registration.endpoint == validatedEndpoint) {
                owner.registration.endpointGeneration
            } else {
                owner.registration.endpointGeneration + 1
            },
            state = NotificationPushRegistrationState.EndpointReceived,
            lastErrorCategory = null,
            lastErrorDetail = null,
            failureStage = null,
            failureReason = null,
            nextRetryAtEpochMillis = 0,
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
            val subscription = if (previousServerEndpoint == null) {
                sourceFor(owner.session, owner.token)?.createPushSubscription(spec)
                    ?: throw SourceError.Unauthorized
            } else {
                sourceFor(owner.session, owner.token)?.updatePushSubscription(spec)
                    ?: throw SourceError.Unauthorized
            }
            update(owner.token, received.copy(
                endpoint = subscription.endpoint,
                serverEndpoint = subscription.endpoint,
                state = NotificationPushRegistrationState.Connected,
                retryCount = 0,
                lastErrorCategory = null,
                lastErrorDetail = null,
                failureStage = null,
                failureReason = null,
                nextRetryAtEpochMillis = 0,
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
                lastErrorDetail = safePushDiagnostic(error),
                failureStage = PushRegistrationFailureStage.ServerSubscription,
                failureReason = errorReason(error),
                nextRetryAtEpochMillis = nextRetryAt(received.retryCount + 1),
            ))
        } catch (error: Exception) {
            update(owner.token, received.copy(
                state = NotificationPushRegistrationState.TemporarilyUnavailable,
                retryCount = received.retryCount + 1,
                lastErrorCategory = "server_registration_failed",
                lastErrorDetail = safePushDiagnostic(error),
                failureStage = PushRegistrationFailureStage.ServerSubscription,
                failureReason = PushRegistrationFailureReason.Server,
                nextRetryAtEpochMillis = nextRetryAt(received.retryCount + 1),
            ))
        }
    }

    private fun updateFromCallback(
        instanceName: String,
        state: NotificationPushRegistrationState,
        errorCategory: String,
        failureStage: PushRegistrationFailureStage,
        failureReason: PushRegistrationFailureReason,
        detail: String? = null,
    ) {
        scope.launch {
            val owner = registrationRepository.find(instanceName) ?: return@launch
            lock.withLock { update(owner.token, owner.registration.copy(
                generation = owner.token.generation,
                state = state,
                lastErrorCategory = errorCategory,
                lastErrorDetail = detail?.let(::safePushDiagnostic),
                failureStage = failureStage,
                failureReason = failureReason,
                retryCount = owner.registration.retryCount + 1,
                nextRetryAtEpochMillis = nextRetryAt(owner.registration.retryCount + 1),
            )) }
        }
    }

    private suspend fun recordFailure(
        token: me.foxtails.palustris.domain.NotificationSyncToken,
        registration: PushRegistration,
        error: Throwable,
    ) {
        val connectorFailure = error as? PushConnectorFailure
        val category = when (connectorFailure?.operation) {
            PushConnectorOperation.DistributorDiscovery -> "distributor_selection_failed"
            PushConnectorOperation.DistributorSave -> "distributor_save_failed"
            PushConnectorOperation.Registration -> "connector_registration_failed"
            PushConnectorOperation.Unregistration, null -> "distributor_registration_failed"
        }
        val stage = when (connectorFailure?.operation) {
            PushConnectorOperation.DistributorDiscovery -> PushRegistrationFailureStage.DistributorSelection
            PushConnectorOperation.DistributorSave -> PushRegistrationFailureStage.DistributorSave
            PushConnectorOperation.Registration, PushConnectorOperation.Unregistration, null ->
                PushRegistrationFailureStage.ConnectorRegistration
        }
        val nextRetry = registration.retryCount + 1
        Log.w(TAG, "Push operation failed: category=$category detail=${safePushDiagnostic(error)}")
        update(token, registration.copy(
            state = NotificationPushRegistrationState.TemporarilyUnavailable,
            retryCount = nextRetry,
            lastErrorCategory = category,
            lastErrorDetail = safePushDiagnostic(error),
            failureStage = stage,
            failureReason = PushRegistrationFailureReason.ConnectorFailure,
            nextRetryAtEpochMillis = nextRetryAt(nextRetry),
        ))
    }

    private suspend fun update(token: me.foxtails.palustris.domain.NotificationSyncToken, registration: PushRegistration) {
        repository.updatePushRegistration(token, registration)
    }

    private fun sourceFor(session: me.foxtails.palustris.domain.Session, token: me.foxtails.palustris.domain.NotificationSyncToken?) =
        token?.let(sourceRegistry::sourceFor) ?: sourceRegistry.sourceFor(session.accountId) ?: sourceFactory.create(session)

    private fun chooseDistributor(selected: String?): String? {
        val available = connector.availableDistributors()
        val acknowledged = connector.acknowledgedDistributor()
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

    private fun errorReason(error: SourceError): PushRegistrationFailureReason = when (error) {
        SourceError.Unauthorized, SourceError.AccountMismatch -> PushRegistrationFailureReason.Unauthorized
        SourceError.NetworkUnavailable -> PushRegistrationFailureReason.Network
        SourceError.RateLimited -> PushRegistrationFailureReason.RateLimited
        is SourceError.Unsupported -> PushRegistrationFailureReason.Unsupported
        is SourceError.ServerError -> PushRegistrationFailureReason.Server
    }

    private fun nextRetryAt(attempt: Int): Long = System.currentTimeMillis() +
        RETRY_DELAYS_MILLIS[(attempt - 1).coerceIn(0, RETRY_DELAYS_MILLIS.lastIndex)]

    override fun close() {
        scope.cancel()
    }

    private companion object {
        const val TAG = "UnifiedPushRegistration"
        const val SUNUP_PACKAGE = "org.unifiedpush.distributor.sunup"
        val RETRY_DELAYS_MILLIS = longArrayOf(30_000L, 60_000L, 5 * 60_000L, 15 * 60_000L)
    }
}

private fun safePushDiagnostic(error: Throwable): String {
    val type = error::class.simpleName ?: "Exception"
    return safePushDiagnostic(type, error.message)
}

private fun safePushDiagnostic(value: String): String = safePushDiagnostic("Callback", value)

private fun safePushDiagnostic(type: String, rawMessage: String?): String {
    val message = rawMessage.orEmpty()
        .replace(Regex("(?i)https?://\\S+"), "<url>")
        .replace(Regex("(?i)(authorization|token|secret|endpoint|publickey|auth)\\s*[=:]\\s*\\S+"), "<redacted>")
        .replace(Regex("[A-Za-z0-9_-]{32,}"), "<redacted>")
        .replace(Regex("\\s+"), " ")
        .trim()
        .take(120)
    return if (message.isBlank()) type else "$type: $message"
}
