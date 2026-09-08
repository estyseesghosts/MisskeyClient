package me.foxtails.palustris.data

import javax.inject.Inject
import javax.inject.Singleton
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.NotificationSyncToken
import me.foxtails.palustris.domain.SocialSource

/** Application-scoped source ownership. A source is valid only for its registered generation. */
@Singleton
class AccountSourceRegistry @Inject constructor() {
    private data class Entry(val generation: Long, val source: SocialSource)

    private val entries = mutableMapOf<AccountId, Entry>()

    @Synchronized
    fun register(token: NotificationSyncToken, source: SocialSource): SocialSource {
        entries[token.accountId] = Entry(token.generation, source)
        return source
    }

    @Synchronized
    fun sourceFor(token: NotificationSyncToken): SocialSource? = entries[token.accountId]
        ?.takeIf { it.generation == token.generation }
        ?.source

    @Synchronized
    fun sourceFor(accountId: AccountId): SocialSource? = entries[accountId]?.source

    @Synchronized
    fun remove(accountId: AccountId, generation: Long? = null) {
        val current = entries[accountId]
        if (generation == null || current?.generation == generation) entries.remove(accountId)
    }
}

