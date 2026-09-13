package me.foxtails.palustris.data.notifications.push

import me.foxtails.palustris.domain.AccountId

interface PushRegistrationManager {
    fun onSessionAvailable(accountId: AccountId)
    suspend fun enable(accountId: AccountId)
    suspend fun retry(accountId: AccountId)
    suspend fun processPendingEndpoint(accountId: AccountId): PushRegistrationWorkResult
    suspend fun disable(accountId: AccountId)
}

enum class PushRegistrationWorkResult { NoWork, Success, Retry, Terminal }

class NoOpPushRegistrationManager : PushRegistrationManager {
    override fun onSessionAvailable(accountId: AccountId) = Unit
    override suspend fun enable(accountId: AccountId) = Unit
    override suspend fun retry(accountId: AccountId) = Unit
    override suspend fun processPendingEndpoint(accountId: AccountId): PushRegistrationWorkResult =
        PushRegistrationWorkResult.NoWork
    override suspend fun disable(accountId: AccountId) = Unit
}
