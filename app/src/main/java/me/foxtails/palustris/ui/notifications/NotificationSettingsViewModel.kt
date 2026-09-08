package me.foxtails.palustris.ui.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import me.foxtails.palustris.data.notifications.NotificationPermissionController
import me.foxtails.palustris.data.notifications.NotificationRepository
import me.foxtails.palustris.data.notifications.NotificationSettingsRepository
import me.foxtails.palustris.data.notifications.push.PushRegistrationManager
import me.foxtails.palustris.data.notifications.work.NotificationWorkScheduler
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.NotificationCategory
import me.foxtails.palustris.domain.NotificationPushRegistrationState
import me.foxtails.palustris.domain.NotificationSettings

data class NotificationSettingsUiState(
    val settings: NotificationSettings = NotificationSettings(),
    val permissionGranted: Boolean = false,
    val registrationState: NotificationPushRegistrationState = NotificationPushRegistrationState.Off,
    val saving: Boolean = false,
    val error: String? = null,
    val localTestMessage: String? = null,
)

@HiltViewModel(assistedFactory = NotificationSettingsViewModel.Factory::class)
class NotificationSettingsViewModel @AssistedInject constructor(
    @Assisted private val accountId: AccountId,
    private val settingsRepository: NotificationSettingsRepository,
    private val repository: NotificationRepository,
    private val workScheduler: NotificationWorkScheduler,
    private val pushRegistrationManager: PushRegistrationManager,
    private val permissionController: NotificationPermissionController,
) : ViewModel() {
    private val _state = MutableStateFlow(NotificationSettingsUiState(
        permissionGranted = permissionController.isGranted(),
    ))
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            settingsRepository.observe(accountId).collectLatest { settings ->
                _state.value = _state.value.copy(settings = settings)
            }
        }
        viewModelScope.launch {
            repository.observe(accountId).collectLatest { snapshot ->
                _state.value = _state.value.copy(
                    registrationState = snapshot.pushRegistration?.state
                        ?: NotificationPushRegistrationState.Off,
                )
            }
        }
    }

    fun setAlertsEnabled(enabled: Boolean) = save(_state.value.settings.copy(alertsEnabled = enabled))

    fun refreshPermission() {
        _state.value = _state.value.copy(permissionGranted = permissionController.isGranted())
    }

    fun setShowPreviews(enabled: Boolean) = save(_state.value.settings.copy(showPreviews = enabled))

    fun setPeriodicFallbackEnabled(enabled: Boolean) = save(_state.value.settings.copy(periodicFallbackEnabled = enabled))

    fun setQuietHours(enabled: Boolean) = save(
        _state.value.settings.copy(
            quietHoursStartMinutes = if (enabled) 22 * 60 else null,
            quietHoursEndMinutes = if (enabled) 7 * 60 else null,
        ),
    )

    fun toggleCategory(category: NotificationCategory) {
        val current = _state.value.settings.categories
        val next = if (category in current) current - category else (current - NotificationCategory.All) + category
        save(_state.value.settings.copy(categories = next.ifEmpty { setOf(NotificationCategory.All) }))
    }

    fun runLocalPresentationTest() {
        _state.value = _state.value.copy(
            localTestMessage = "Local presentation test only; this does not prove server delivery.",
        )
    }

    private fun save(settings: NotificationSettings) {
        val previousSettings = _state.value.settings
        val token = repository.currentToken(accountId)
        if (token == null) {
            _state.value = _state.value.copy(error = "This account is not ready for notification settings.")
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(saving = true, error = null)
            try {
                if (!settingsRepository.save(token, settings)) error("Account session changed; try again.")
                if (settings.periodicFallbackEnabled) workScheduler.schedulePeriodicFallback(accountId)
                else workScheduler.cancelPeriodicFallback(accountId)
                when {
                    settings.alertsEnabled && (
                        !previousSettings.alertsEnabled || settings.categories != previousSettings.categories ||
                            settings.selectedDistributor != previousSettings.selectedDistributor
                        ) -> pushRegistrationManager.enable(accountId)
                    !settings.alertsEnabled && previousSettings.alertsEnabled -> pushRegistrationManager.disable(accountId)
                }
                if (settings.alertsEnabled) workScheduler.enqueueDelivery(accountId)
                _state.value = _state.value.copy(saving = false)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _state.value = _state.value.copy(saving = false, error = error.message ?: "Settings could not be saved.")
            }
        }
    }

    @AssistedFactory
    interface Factory {
        fun create(accountId: AccountId): NotificationSettingsViewModel
    }
}
