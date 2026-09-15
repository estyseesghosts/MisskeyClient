package me.foxtails.palustris.data.notifications

/**
 * The recoverable health of one account's persisted notification state.
 *
 * Health stays outside the stored version-2 payload. A corrupt or unavailable account keeps its
 * original bytes and blocks mutations until an explicit retry reloads a readable state. Other
 * accounts stay healthy and continue normally.
 */
sealed interface NotificationStorageHealth {
    /** The store supplied a readable state or no state at all. Mutations are allowed. */
    data object Healthy : NotificationStorageHealth

    /** A stored value exists but cannot be decoded. Preserve the original bytes. */
    data object Recoverable : NotificationStorageHealth

    /** The read failed for an environment reason. The stored value is unknown. */
    data object Unavailable : NotificationStorageHealth
}

internal fun NotificationStoreRead.toStorageHealth(): NotificationStorageHealth = when (this) {
    is NotificationStoreRead.Readable, NotificationStoreRead.Absent -> NotificationStorageHealth.Healthy
    NotificationStoreRead.Corrupt -> NotificationStorageHealth.Recoverable
    NotificationStoreRead.Unavailable -> NotificationStorageHealth.Unavailable
}
