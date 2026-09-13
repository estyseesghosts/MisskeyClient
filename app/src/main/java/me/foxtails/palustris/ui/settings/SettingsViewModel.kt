package me.foxtails.palustris.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.foxtails.palustris.domain.AppBackground
import me.foxtails.palustris.domain.AppColorScheme
import me.foxtails.palustris.domain.AppFont
import me.foxtails.palustris.domain.AppLanguage
import me.foxtails.palustris.domain.AppPreferencesRepository
import me.foxtails.palustris.domain.AppPreferencesState
import me.foxtails.palustris.domain.AppTextSize

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: AppPreferencesRepository,
) : ViewModel() {
    val state: StateFlow<AppPreferencesState> = repository.observe().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        AppPreferencesState(),
    )

    fun setColorScheme(value: AppColorScheme) = update { it.copy(colorScheme = value) }
    fun setBackground(value: AppBackground) = update { it.copy(background = value) }
    fun setTextSize(value: AppTextSize) = update { it.copy(textSize = value) }
    fun setFont(value: AppFont) = update { it.copy(font = value) }
    fun setRequest60Hz(value: Boolean) = update { it.copy(request60Hz = value) }
    fun setLanguage(value: AppLanguage) = update { it.copy(language = value) }
    fun setTrackingCleanup(value: Boolean) = update { it.copy(cleanTrackingParameters = value) }
    fun setContentWarningRules(value: me.foxtails.palustris.domain.ContentWarningRules) =
        update { it.copy(contentWarningRules = value.normalized()) }

    private fun update(transform: (me.foxtails.palustris.domain.AppPreferences) -> me.foxtails.palustris.domain.AppPreferences) {
        viewModelScope.launch { runCatching { repository.update(transform) } }
    }
}
