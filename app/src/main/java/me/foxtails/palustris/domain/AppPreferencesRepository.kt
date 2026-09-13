package me.foxtails.palustris.domain

import kotlinx.coroutines.flow.Flow

interface AppPreferencesRepository {
    fun observe(): Flow<AppPreferencesState>
    suspend fun update(transform: (AppPreferences) -> AppPreferences)
}
