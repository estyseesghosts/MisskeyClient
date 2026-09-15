package me.foxtails.palustris.data.notifications

/**
 * The distinct outcome of reading one account's persisted notification state.
 *
 * The store boundary must not collapse these cases into a single nullable value. A missing
 * row follows the legacy-import path. A readable row supplies state. A corrupt row exists
 * but cannot be decoded and must never be overwritten with defaults. An unavailable read
 * failed for an environment reason, such as a database or disk error, and carries no claim
 * about corruption.
 *
 * A corrupt read never carries stored bytes or decoded content, so a diagnostic cannot log
 * notification data by accident.
 */
sealed interface NotificationStoreRead {
    /** No persisted state exists for the account. */
    data object Absent : NotificationStoreRead

    /** Persisted state was decoded successfully. */
    data class Readable(val state: NotificationRepositoryState) : NotificationStoreRead

    /** Persisted state exists but cannot be decoded. Preserve the original bytes. */
    data object Corrupt : NotificationStoreRead

    /**
     * Persisted state uses a newer format than this build understands.
     *
     * The bytes are well-formed JSON, so this is not corruption. They must stay untouched so a
     * newer build can still read them. An older writer must never overwrite this value.
     */
    data object Unsupported : NotificationStoreRead

    /** The read failed for an environment reason. The stored value is unknown. */
    data object Unavailable : NotificationStoreRead
}
