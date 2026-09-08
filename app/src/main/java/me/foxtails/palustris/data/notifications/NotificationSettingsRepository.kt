package me.foxtails.palustris.data.notifications

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.NotificationSettings
import me.foxtails.palustris.domain.NotificationSyncToken

@Singleton
class NotificationSettingsRepository @Inject constructor(
    private val repository: NotificationRepository,
) {
    fun observe(accountId: AccountId): Flow<NotificationSettings> = repository.observe(accountId).map { it.settings }

    suspend fun save(token: NotificationSyncToken, settings: NotificationSettings): Boolean =
        repository.updateSettings(token, settings)
}
