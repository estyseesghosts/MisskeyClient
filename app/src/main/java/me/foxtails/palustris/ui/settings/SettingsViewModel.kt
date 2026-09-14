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

/**
 * One command owner for application preferences and per-account post preferences.
 *
 * App commands transform the repository's current value, so a delayed write cannot replace a
 * newer field with an old UI snapshot. Post commands capture the target account at call time;
 * a later account switch cannot silently redirect the command.
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

    private val _commandError = MutableStateFlow<String?>(null)
    val commandError: StateFlow<String?> = _commandError.asStateFlow()

    private val _repositoryErrorDismissed = MutableStateFlow(false)
    val repositoryErrorDismissed: StateFlow<Boolean> = _repositoryErrorDismissed.asStateFlow()

    fun setColorScheme(value: AppColorScheme) = updateApp { it.copy(colorScheme = value) }

    fun setColorPalette(value: AppColorPalette) =
        updateApp { it.copy(colorScheme = AppColorScheme.Palette, colorPalette = value) }

    fun setBackground(value: AppBackground) = updateApp { it.copy(background = value) }

    fun setTextSize(value: AppTextSize) = updateApp { it.copy(textSize = value) }

    fun setFont(value: AppFont) = updateApp { it.copy(font = value) }

    fun setRequest60Hz(value: Boolean) = updateApp { it.copy(request60Hz = value) }

    fun setLanguage(value: AppLanguage) = updateApp { it.copy(language = value) }

    fun setTrackingCleanup(value: Boolean) = updateApp { it.copy(cleanTrackingParameters = value) }

    fun setContentWarningRules(value: ContentWarningRules) =
        updateApp { it.copy(contentWarningRules = value.normalized()) }

    fun setHiddenContentPresentation(value: HiddenContentPresentation) =
        updateApp { it.copy(hiddenContentPresentation = value) }

    fun setPostDefaultAudience(accountId: AccountId, value: Audience) =
        updatePost(accountId) { it.copy(defaultAudience = value) }

    fun setPostRepliesUnlisted(accountId: AccountId, value: Boolean) =
        updatePost(accountId) { it.copy(repliesUnlisted = value) }

    fun setPostContentWarningRules(accountId: AccountId, value: ContentWarningRules) =
        updatePost(accountId) { it.copy(contentWarningRules = value.normalized()) }

    fun clearError() {
        _commandError.value = null
        _repositoryErrorDismissed.value = true
    }

    private fun updateApp(transform: (AppPreferences) -> AppPreferences) {
        viewModelScope.launch {
            try {
                repository.update(transform)
                onCommandAccepted()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                onCommandFailed(error)
            }
        }
    }

    private fun updatePost(accountId: AccountId, transform: (PostPreferences) -> PostPreferences) {
        viewModelScope.launch {
            try {
                postPreferencesRepository.update(accountId, transform)
                onCommandAccepted()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                onCommandFailed(error)
            }
        }
    }

    private fun onCommandAccepted() {
        _commandError.value = null
        _repositoryErrorDismissed.value = false
    }

    private fun onCommandFailed(error: Exception) {
        _commandError.value = error.message?.takeIf(String::isNotBlank)
            ?: "Settings could not be saved."
    }
}
