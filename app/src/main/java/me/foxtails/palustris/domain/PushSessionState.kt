package me.foxtails.palustris.domain

/**
 * Encrypted per-account UnifiedPush state. The distributor endpoint and Web Push key material
 * are callback inputs and must not be copied into UI state, logs, or the account index.
 */
data class PushSessionState(
    val endpoint: ValidatedUrl? = null,
    val publicKey: String? = null,
    val authSecret: String? = null,
    val endpointGeneration: Long = 0,
    val endpointCallbackPending: Boolean = false,
    val messageHintPending: Boolean = false,
    /** Monotonic receipt generation used to compare-and-clear message hints. */
    val messageGeneration: Long = 0,
    val lastCallbackAtEpochMillis: Long = 0,
)
