package me.foxtails.palustris.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.AppBackground
import me.foxtails.palustris.domain.AppColorPalette
import me.foxtails.palustris.domain.AppColorScheme
import me.foxtails.palustris.domain.AppFont
import me.foxtails.palustris.domain.AppLanguage
import me.foxtails.palustris.domain.AppPreferences
import me.foxtails.palustris.domain.AppPreferencesRepository
import me.foxtails.palustris.domain.AppPreferencesState
import me.foxtails.palustris.domain.AppTextSize
import me.foxtails.palustris.domain.Audience
import me.foxtails.palustris.domain.ContentWarningRules
import me.foxtails.palustris.domain.HiddenContentPresentation
import me.foxtails.palustris.domain.PostPreferences
import me.foxtails.palustris.domain.PostPreferencesRepository

/** Why a settings command failed. The shell resolves the user-visible message. */
sealed interface SettingsCommandError {
    /** A repository write failed. The message carries the repository detail, if any. */
    data class SaveFailed(val message: String?) : SettingsCommandError

    /** The command targeted an account that is no longer available on this device. */
    data object AccountUnavailable : SettingsCommandError
}

/**
 * One command owner for application preferences and per-account post preferences.
 *
 * App commands transform the repository's current value, so a delayed write cannot replace a
 * newer field with an old UI snapshot. Post commands capture the target account at call time;
 * a later account switch cannot silently redirect the command. Post commands also bind to the
 * validated account set the shell publishes: a command for a removed account reports an
 * explicit error at call time, and a command queued before the removal writes nothing. A
 * failed command is retained for explicit retry. Dismissing the error hides the message but
 * keeps the retry available until the next command.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: AppPreferencesRepository,
    private val postPreferencesRepository: PostPreferencesRepository,
) : ViewModel() {
    val state: StateFlow<AppPreferencesState> = repository.observe().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        AppPreferencesState(),
    )

    private val _commandError = MutableStateFlow<SettingsCommandError?>(null)
    val commandError: StateFlow<SettingsCommandError?> = _commandError.asStateFlow()

    private val _canRetry = MutableStateFlow(false)
    val canRetry: StateFlow<Boolean> = _canRetry.asStateFlow()

    private val _repositoryErrorDismissed = MutableStateFlow(false)
    val repositoryErrorDismissed: StateFlow<Boolean> = _repositoryErrorDismissed.asStateFlow()

    /**
     * Valid account targets for post commands, or null while the restored account index is
     * unknown. An empty set before restore proves nothing, so unknown validity allows the
     * command. The shell publishes the restored index.
     */
    @Volatile
    private var validAccountIds: Set<AccountId>? = null

    private var failedCommand: (() -> Unit)? = null

    fun setValidAccountIds(accountIds: Set<AccountId>?) {
        validAccountIds = accountIds
    }

    fun setColorScheme(value: AppColorScheme): Unit =
        updateApp(retry = { setColorScheme(value) }) { it.copy(colorScheme = value) }

    fun setColorPalette(value: AppColorPalette): Unit =
        updateApp(retry = { setColorPalette(value) }) {
            it.copy(colorScheme = AppColorScheme.Palette, colorPalette = value)
        }

    fun setBackground(value: AppBackground): Unit =
        updateApp(retry = { setBackground(value) }) { it.copy(background = value) }

    fun setTextSize(value: AppTextSize): Unit =
        updateApp(retry = { setTextSize(value) }) { it.copy(textSize = value) }

    fun setFont(value: AppFont): Unit =
        updateApp(retry = { setFont(value) }) { it.copy(font = value) }

    fun setRequest60Hz(value: Boolean): Unit =
        updateApp(retry = { setRequest60Hz(value) }) { it.copy(request60Hz = value) }

    fun setLanguage(value: AppLanguage): Unit =
        updateApp(retry = { setLanguage(value) }) { it.copy(language = value) }

    fun setTrackingCleanup(value: Boolean): Unit =
        updateApp(retry = { setTrackingCleanup(value) }) { it.copy(cleanTrackingParameters = value) }

    fun setContentWarningRules(value: ContentWarningRules): Unit =
        updateApp(retry = { setContentWarningRules(value) }) {
            it.copy(contentWarningRules = value.normalized())
        }

    fun setHiddenContentPresentation(value: HiddenContentPresentation): Unit =
        updateApp(retry = { setHiddenContentPresentation(value) }) {
            it.copy(hiddenContentPresentation = value)
        }

    fun setPostDefaultAudience(accountId: AccountId, value: Audience): Unit =
        updatePost(accountId, retry = { setPostDefaultAudience(accountId, value) }) {
            it.copy(defaultAudience = value)
        }

    fun setPostRepliesUnlisted(accountId: AccountId, value: Boolean): Unit =
        updatePost(accountId, retry = { setPostRepliesUnlisted(accountId, value) }) {
            it.copy(repliesUnlisted = value)
        }

    fun setPostContentWarningRules(accountId: AccountId, value: ContentWarningRules): Unit =
        updatePost(accountId, retry = { setPostContentWarningRules(accountId, value) }) {
            it.copy(contentWarningRules = value.normalized())
        }

    /** Re-runs the last failed command, if any. A removed account target is not retried. */
    fun retryFailedCommand() {
        failedCommand?.invoke()
    }

    fun clearError() {
        _commandError.value = null
        _repositoryErrorDismissed.value = true
    }

    private fun isPostTargetValid(accountId: AccountId): Boolean =
        validAccountIds?.contains(accountId) ?: true

    private fun updateApp(retry: () -> Unit, transform: (AppPreferences) -> AppPreferences) {
        viewModelScope.launch {
            try {
                repository.update(transform)
                onCommandAccepted()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                onCommandFailed(retry, SettingsCommandError.SaveFailed(error.message?.takeIf(String::isNotBlank)))
            }
        }
    }

    private fun updatePost(accountId: AccountId, retry: () -> Unit, transform: (PostPreferences) -> PostPreferences) {
        if (!isPostTargetValid(accountId)) {
            failedCommand = null
            _canRetry.value = false
            _commandError.value = SettingsCommandError.AccountUnavailable
            return
        }
        viewModelScope.launch {
            try {
                // Recheck after the queue delay. A removal that landed first revokes this
                // writer, so a queued command cannot restore deleted account state.
                if (!isPostTargetValid(accountId)) return@launch
                postPreferencesRepository.update(accountId, transform)
                onCommandAccepted()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                onCommandFailed(retry, SettingsCommandError.SaveFailed(error.message?.takeIf(String::isNotBlank)))
            }
        }
    }

    private fun onCommandAccepted() {
        _commandError.value = null
        _repositoryErrorDismissed.value = false
        failedCommand = null
        _canRetry.value = false
    }

    private fun onCommandFailed(retry: () -> Unit, error: SettingsCommandError) {
        failedCommand = retry
        _canRetry.value = true
        _commandError.value = error
    }
}
