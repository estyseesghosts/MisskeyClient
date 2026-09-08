package me.foxtails.palustris.data.notifications.push

import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import me.foxtails.palustris.data.auth.SessionStore
import me.foxtails.palustris.data.notifications.NotificationRepository
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.NotificationSyncToken
import me.foxtails.palustris.domain.PushRegistration
import me.foxtails.palustris.domain.Session

data class PushRegistrationOwner(
    val accountId: AccountId,
    val session: Session,
    val token: NotificationSyncToken,
    val registration: PushRegistration,
)

/** Keeps the opaque distributor instance associated with exactly one committed account. */
@Singleton
class PushRegistrationRepository @Inject constructor(
    private val repository: NotificationRepository,
    private val sessionStore: SessionStore,
) {
    suspend fun prepare(token: NotificationSyncToken, distributorPackage: String?): PushRegistration {
        val current = repository.pushRegistration(token.accountId)
        val session = sessionStore.read(token.accountId)
            ?: error("Notification account session is not available.")
        val instanceName = current?.instanceName ?: session.pushInstanceName ?: UUID.randomUUID().toString()
        if (session.pushInstanceName != instanceName) sessionStore.writePushInstance(token.accountId, instanceName)
        val registration = (current ?: PushRegistration(
            accountId = token.accountId,
            generation = token.generation,
            instanceName = instanceName,
        )).copy(
            accountId = token.accountId,
            generation = token.generation,
            distributorPackage = distributorPackage,
        )
        check(repository.updatePushRegistration(token, registration)) { "Notification account session changed." }
        return registration
    }

    fun find(instanceName: String): PushRegistrationOwner? {
        if (instanceName.isBlank()) return null
        return sessionStore.readIndex().accounts.asSequence().mapNotNull { reference ->
            val session = sessionStore.read(reference.accountId) ?: return@mapNotNull null
            val registration = repository.pushRegistration(reference.accountId) ?: return@mapNotNull null
            if (registration.instanceName != instanceName) return@mapNotNull null
            // A distributor callback can start the app in a fresh process, before the
            // account synchronizer has created its in-memory generation. Generation zero is
            // the repository's restart-safe callback token and is superseded by the next
            // account sync generation.
            val token = repository.currentToken(reference.accountId)
                ?: NotificationSyncToken(reference.accountId, 0L).also(repository::activate)
            PushRegistrationOwner(reference.accountId, session, token, registration)
        }.firstOrNull()
    }
}
