package me.foxtails.palustris.ui.notifications

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import me.foxtails.palustris.data.notifications.NotificationPermissionController
import me.foxtails.palustris.data.notifications.NotificationChannelKind
import me.foxtails.palustris.data.notifications.NotificationPresentationFactory
import me.foxtails.palustris.data.notifications.NotificationPresenter
import me.foxtails.palustris.data.notifications.NotificationRepository
import me.foxtails.palustris.data.notifications.NotificationSettingsRepository
import me.foxtails.palustris.data.notifications.NotificationStorageHealth
import me.foxtails.palustris.data.notifications.push.PushRegistrationManager
import me.foxtails.palustris.data.notifications.push.UnifiedPushConnector
import me.foxtails.palustris.data.notifications.work.NotificationWorkScheduler
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.EntityId
import me.foxtails.palustris.domain.Notification
import me.foxtails.palustris.domain.NotificationActivity
import me.foxtails.palustris.domain.NotificationCategory
import me.foxtails.palustris.domain.NotificationPushRegistrationState
import me.foxtails.palustris.ProductIdentity
import me.foxtails.palustris.R
import me.foxtails.palustris.domain.NotificationSettings
import me.foxtails.palustris.data.auth.SessionStore
import me.foxtails.palustris.domain.withCategoryEnabled
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class NotificationSettingsUiState(
    val settings: NotificationSettings = NotificationSettings(),
    /** True while stored notification state is corrupt or unreadable. Defaults are not confirmed settings. */
    val storageUnavailable: Boolean = false,
    val permissionGranted: Boolean = false,
    val registrationState: NotificationPushRegistrationState = NotificationPushRegistrationState.Off,
    val failureStage: me.foxtails.palustris.domain.PushRegistrationFailureStage? = null,
    val failureReason: me.foxtails.palustris.domain.PushRegistrationFailureReason? = null,
    val saving: Boolean = false,
    val error: String? = null,
    val localTestMessage: String? = null,
    val availableDistributors: List<UnifiedPushDistributorUi> = emptyList(),
    val selectedDistributor: String? = null,
    val distributorLoading: Boolean = false,
)

data class UnifiedPushDistributorUi(
    val packageName: String,
    val label: String,
)

@HiltViewModel(assistedFactory = NotificationSettingsViewModel.Factory::class)
class NotificationSettingsViewModel @AssistedInject constructor(
    @Assisted private val accountId: AccountId,
    private val settingsRepository: NotificationSettingsRepository,
    private val repository: NotificationRepository,
    private val workScheduler: NotificationWorkScheduler,
    private val pushRegistrationManager: PushRegistrationManager,
    private val permissionController: NotificationPermissionController,
    private val presentationFactory: NotificationPresentationFactory,
    private val presenter: NotificationPresenter,
    private val connector: UnifiedPushConnector,
    private val sessionStore: SessionStore,
    @param:ApplicationContext private val context: Context,
) : ViewModel() {
    private val _state = MutableStateFlow(NotificationSettingsUiState(
        permissionGranted = permissionController.isGranted(),
    ))
    val state = _state.asStateFlow()
    private val saveMutex = Mutex()
    private var saveRequest = 0L
    private var settingsObservation: Job? = null
    private var repositoryObservation: Job? = null
    private var storageObservation: Job? = null

    init {
        settingsObservation = viewModelScope.launch {
            settingsRepository.observe(accountId).collectLatest { settings ->
                _state.value = _state.value.copy(
                    settings = settings,
                    selectedDistributor = settings.selectedDistributor,
                )
            }
        }
        storageObservation = viewModelScope.launch {
            repository.observeStorageHealth(accountId).collectLatest { health ->
                _state.value = _state.value.copy(
                    storageUnavailable = health != NotificationStorageHealth.Healthy,
                )
            }
        }
        repositoryObservation = viewModelScope.launch {
            repository.observe(accountId).collectLatest { snapshot ->
                val registration = snapshot.pushRegistration
                _state.value = _state.value.copy(
                    registrationState = registration?.state
                        ?: NotificationPushRegistrationState.Off,
                    failureStage = registration?.failureStage,
                    failureReason = registration?.failureReason,
                )
            }
        }
        refreshDistributors()
    }

    /** Releases the long-lived account observers when this owner retires. */
    fun stop() {
        settingsObservation?.cancel()
        repositoryObservation?.cancel()
        storageObservation?.cancel()
        settingsObservation = null
        repositoryObservation = null
        storageObservation = null
    }

    /** Explicit recovery path. The repository reloads storage and clears the failure on success. */
    fun retryStorage() {
        viewModelScope.launch { repository.retry(accountId) }
    }

    /**
     * Explicit destructive recovery for unreadable or newer-format state. The repository
     * advances the account generation and removes only notification-local state.
     */
    fun resetStorage() {
        viewModelScope.launch {
            if (!repository.reset(accountId)) {
                _state.value = _state.value.copy(
                    error = context.getString(R.string.notifications_storage_reset_failed),
                )
            }
        }
    }

    fun setAlertsEnabled(enabled: Boolean) = save(_state.value.settings.copy(alertsEnabled = enabled))

    fun refreshPermission() {
        val granted = permissionController.isGranted()
        _state.value = _state.value.copy(permissionGranted = granted)
        if (granted && _state.value.settings.alertsEnabled &&
            _state.value.registrationState == NotificationPushRegistrationState.PermissionRequired
        ) {
            viewModelScope.launch { pushRegistrationManager.enable(accountId) }
        }
        if (_state.value.settings.alertsEnabled) workScheduler.enqueueDelivery(accountId)
    }

    fun setShowPreviews(enabled: Boolean) = save(_state.value.settings.copy(showPreviews = enabled))

    fun setPeriodicFallbackEnabled(enabled: Boolean) = save(_state.value.settings.copy(periodicFallbackEnabled = enabled))

    fun setQuietHours(enabled: Boolean) = save(
        _state.value.settings.copy(
            quietHoursStartMinutes = if (enabled) 22 * 60 else null,
            quietHoursEndMinutes = if (enabled) 7 * 60 else null,
        ),
    )

    fun setCategoryEnabled(category: NotificationCategory, enabled: Boolean) {
        save(_state.value.settings.withCategoryEnabled(category, enabled))
    }

    fun retryRegistration() {
        viewModelScope.launch {
            try {
                pushRegistrationManager.retry(accountId)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _state.value = _state.value.copy(error = error.message ?: context.getString(R.string.notifications_registration_retry_failed))
            }
        }
    }

    fun refreshDistributors() {
        viewModelScope.launch {
            _state.value = _state.value.copy(distributorLoading = true)
            try {
                val distributors = connector.availableDistributors().map { packageName ->
                    UnifiedPushDistributorUi(packageName, distributorLabel(packageName))
                }
                _state.value = _state.value.copy(
                    availableDistributors = distributors,
                    distributorLoading = false,
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _state.value = _state.value.copy(
                    availableDistributors = emptyList(),
                    distributorLoading = false,
                )
            }
        }
    }

    fun selectDistributor(packageName: String) {
        if (_state.value.availableDistributors.none { it.packageName == packageName }) return
        save(_state.value.settings.copy(selectedDistributor = packageName))
    }

    fun runPushConnectionTest() {
        val current = _state.value
        val session = sessionStore.read(accountId)
        val registration = repository.pushRegistration(accountId)
        val selected = current.settings.selectedDistributor
        val distributor = connector.acknowledgedDistributor()
        val message = when {
            !current.permissionGranted -> context.getString(R.string.notifications_test_permission_required)
            current.availableDistributors.isEmpty() -> context.getString(R.string.notifications_test_no_distributor)
            selected != null && distributor != selected -> context.getString(R.string.notifications_test_distributor_mismatch)
            session == null || session.pushState.publicKey.isNullOrBlank() || session.pushState.authSecret.isNullOrBlank() ->
                context.getString(R.string.notifications_test_no_keys)
            session.pushState.endpoint == null -> context.getString(R.string.notifications_test_no_endpoint)
            registration?.serverEndpoint == null -> context.getString(R.string.notifications_test_no_server_endpoint)
            registration.state != NotificationPushRegistrationState.Connected ->
                context.getString(R.string.notifications_test_not_connected)
            else -> context.getString(R.string.notifications_push_connected)
        }
        _state.value = _state.value.copy(localTestMessage = message)
    }

    fun runLocalPresentationTest() {
        val actor = Account(accountId.copy(localId = "local-test"), ProductIdentity.name, "@local-test")
        val notification = Notification(
            id = EntityId(accountId.connection.origin, "local-presentation-test"),
            accountId = accountId,
            createdAtEpochMillis = System.currentTimeMillis(),
            activity = NotificationActivity.Mention,
            actors = listOf(actor),
            rawType = "local-presentation-test",
        )
        val presented = presenter.present(
            presentationFactory.prepare(
                notification,
                showPreview = false,
                channel = NotificationChannelKind.RepliesAndMentions,
            ),
        )
        _state.value = _state.value.copy(
            localTestMessage = if (presented) {
                context.getString(R.string.notifications_test_posted)
            } else {
                context.getString(R.string.notifications_test_post_failed)
            },
        )
    }

    private fun save(settings: NotificationSettings) {
        val previousSettings = _state.value.settings
        val request = ++saveRequest
        if (_state.value.storageUnavailable) {
            _state.value = _state.value.copy(error = context.getString(R.string.notifications_settings_unavailable))
            return
        }
        val token = repository.currentToken(accountId)
        if (token == null) {
            _state.value = _state.value.copy(error = context.getString(R.string.notifications_settings_account_not_ready))
            return
        }
        viewModelScope.launch {
            saveMutex.withLock {
                if (request != saveRequest) return@withLock
                _state.value = _state.value.copy(saving = true, error = null)
                try {
                    if (!settingsRepository.save(token, settings)) error(context.getString(R.string.notifications_settings_session_changed))
                    if (settings.periodicFallbackEnabled) workScheduler.schedulePeriodicFallback(accountId)
                    else workScheduler.cancelPeriodicFallback(accountId)
                    when {
                        settings.alertsEnabled && settings.selectedDistributor != previousSettings.selectedDistributor -> {
                            pushRegistrationManager.disable(accountId)
                            pushRegistrationManager.enable(accountId)
                        }
                        settings.alertsEnabled && (
                            !previousSettings.alertsEnabled || settings.categories != previousSettings.categories
                            ) -> pushRegistrationManager.enable(accountId)
                        !settings.alertsEnabled && previousSettings.alertsEnabled -> pushRegistrationManager.disable(accountId)
                    }
                    if (settings.alertsEnabled) workScheduler.enqueueDelivery(accountId)
                    _state.value = _state.value.copy(saving = false)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    _state.value = _state.value.copy(saving = false, error = error.message ?: context.getString(R.string.settings_error_save_failed))
                }
            }
        }
    }

    private fun distributorLabel(packageName: String): String = runCatching {
        context.packageManager.getApplicationInfo(packageName, 0).loadLabel(context.packageManager).toString()
    }.getOrDefault(packageName)

    @AssistedFactory
    interface Factory {
        fun create(accountId: AccountId): NotificationSettingsViewModel
    }
}
