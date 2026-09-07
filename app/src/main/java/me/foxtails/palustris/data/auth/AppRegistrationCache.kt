package me.foxtails.palustris.data.auth

import java.util.concurrent.ConcurrentHashMap

data class AppRegistration(
    val clientId: String,
    val clientSecret: String,
    val scopes: Set<String> = emptySet(),
    val scopesKnown: Boolean = false,
)

/** Mastodon application credentials are scoped to a connection, not to a user account. */
class AppRegistrationCache {
    private val registrations = ConcurrentHashMap<String, AppRegistration>()

    fun get(origin: String): AppRegistration? = registrations[origin]

    fun get(origin: String, requiredScopes: Set<String>): AppRegistration? =
        registrations[origin]?.takeIf { registration ->
            registration.scopesKnown && requiredScopes.all { it in registration.scopes }
        }

    suspend fun getOrPut(origin: String, create: suspend () -> AppRegistration): AppRegistration {
        get(origin)?.let { return it }
        val created = create()
        return synchronized(this) { get(origin) ?: created.also { put(origin, it) } }
    }

    suspend fun getOrPut(
        origin: String,
        requiredScopes: Set<String>,
        create: suspend () -> AppRegistration,
    ): AppRegistration {
        get(origin, requiredScopes)?.let { return it }
        val created = create()
        return synchronized(this) {
            get(origin, requiredScopes) ?: created.also { put(origin, it) }
        }
    }

    fun put(origin: String, registration: AppRegistration) {
        registrations[origin] = registration
    }
}
