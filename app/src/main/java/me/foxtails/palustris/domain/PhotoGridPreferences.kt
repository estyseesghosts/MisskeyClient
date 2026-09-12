package me.foxtails.palustris.domain

import kotlinx.coroutines.flow.Flow

data class PhotoGridPreferences(val hashtags: List<String> = emptyList())

interface PhotoGridPreferencesRepository {
    fun observe(accountId: AccountId): Flow<PhotoGridPreferences>
    suspend fun update(accountId: AccountId, transform: (PhotoGridPreferences) -> PhotoGridPreferences)
    suspend fun remove(accountId: AccountId)
}
