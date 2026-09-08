package me.foxtails.palustris.domain

/** Runtime state for an authenticated account. Tokens must not be exposed to UI state or logs. */
data class Session(
    val accountId: AccountId,
    val token: String,
    val capabilities: ServerCapabilities,
    val access: AccessGrant = AccessGrant(),
    /** Opaque per-account UnifiedPush instance, kept with the encrypted session. */
    val pushInstanceName: String? = null,
    /** Durable owner revision used to reject callbacks from an older authentication session. */
    val sessionRevision: Long = 1L,
    val pushState: PushSessionState = PushSessionState(),
)
