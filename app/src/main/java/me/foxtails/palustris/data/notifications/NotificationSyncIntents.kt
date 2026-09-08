package me.foxtails.palustris.data.notifications

import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.NotificationAcknowledgement
import me.foxtails.palustris.domain.NotificationQuery
import me.foxtails.palustris.domain.NotificationSyncToken
import me.foxtails.palustris.domain.SocialSource

/** User-facing synchronization intents; orchestration and serialization stay application-scoped. */
interface NotificationSyncIntents {
    suspend fun refresh(accountId: AccountId, query: NotificationQuery = NotificationQuery()): NotificationSyncResult
    suspend fun loadOlder(accountId: AccountId, query: NotificationQuery = NotificationQuery()): NotificationSyncResult
    suspend fun acknowledge(accountId: AccountId): NotificationAcknowledgement
}

/** Compatibility adapter for isolated ViewModel tests; production uses NotificationSyncOrchestrator. */
class SourceBackedNotificationSyncIntents(
    private val accountId: AccountId,
    private val source: SocialSource,
    private val repository: NotificationRepository,
    private val synchronizer: NotificationSynchronizer,
) : NotificationSyncIntents {
    override suspend fun refresh(accountId: AccountId, query: NotificationQuery): NotificationSyncResult {
        check(accountId == this.accountId)
        val token = synchronizerToken(accountId)
        return if (repository.checkpoint(accountId, query) == null) {
            synchronizer.establishBaseline(source, token, query)
        } else {
            synchronizer.catchUpNewer(source, token, query)
        }
    }

    override suspend fun loadOlder(accountId: AccountId, query: NotificationQuery): NotificationSyncResult {
        check(accountId == this.accountId)
        return synchronizer.loadOlder(source, synchronizerToken(accountId), query)
    }

    override suspend fun acknowledge(accountId: AccountId): NotificationAcknowledgement {
        check(accountId == this.accountId)
        return synchronizer.applyAcknowledgement(source, synchronizerToken(accountId))
    }

    private fun synchronizerToken(accountId: AccountId): NotificationSyncToken =
        repository.currentToken(accountId)
            ?: error("Notification account is not active")
}
