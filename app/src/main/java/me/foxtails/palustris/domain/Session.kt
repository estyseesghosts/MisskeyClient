package me.foxtails.palustris.domain

/** Runtime state for an authenticated account. Tokens must not be exposed to UI state or logs. */
data class Session(
    val accountId: AccountId,
    val token: String,
    val capabilities: ServerCapabilities,
)
