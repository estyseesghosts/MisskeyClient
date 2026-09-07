package me.foxtails.palustris.data.auth

import java.util.concurrent.ConcurrentHashMap

data class AppRegistration(val clientId: String, val clientSecret: String)

/** Mastodon application credentials are scoped to a connection, not to a user account. */
class AppRegistrationCache {
    private val registrations = ConcurrentHashMap<String, AppRegistration>()

    fun get(origin: String): AppRegistration? = registrations[origin]

    suspend fun getOrPut(origin: String, create: suspend () -> AppRegistration): AppRegistration {
        get(origin)?.let { return it }
        val created = create()
        return synchronized(this) { get(origin) ?: created.also { put(origin, it) } }
    }

    fun put(origin: String, registration: AppRegistration) {
        registrations[origin] = registration
    }
}
