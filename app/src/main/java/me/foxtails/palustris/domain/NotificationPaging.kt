package me.foxtails.palustris.domain

/** The direction of a page is part of its meaning; callers must not infer it from a cursor. */
enum class NotificationPageDirection { Initial, Newer, Older }

enum class NotificationSyncCompleteness { Complete, Incomplete, Gap, Unknown }

data class NotificationQuery(
    val categories: Set<NotificationCategory> = setOf(NotificationCategory.All),
    val limit: Int = 30,
    val grouped: Boolean = false,
) {
    init {
        require(limit in 1..100) { "Notification page size must be between 1 and 100." }
    }

    val isAll: Boolean get() = NotificationCategory.All in categories

    /** Stable storage key. Protocol adapters may add their own opaque cursor encoding. */
    val stableKey: String
        get() = buildString {
            append(categories.map { it.name }.sorted().joinToString(","))
            append('|').append(limit).append('|').append(grouped)
        }
}

/** Opaque to shared code; only the adapter that created it may interpret its value. */
@JvmInline
value class NotificationCursor(val value: String)

data class NotificationCheckpoint(
    val accountId: AccountId,
    val query: NotificationQuery,
    val newest: NotificationCursor? = null,
    val oldest: NotificationCursor? = null,
    val capturedAtEpochMillis: Long = 0,
    val newerContinuation: NotificationCursor? = null,
    val olderContinuation: NotificationCursor? = null,
    val completeness: NotificationSyncCompleteness = NotificationSyncCompleteness.Unknown,
    val baselineEstablished: Boolean = false,
)

/** A page carries independent high/low boundaries and an explicit continuation. */
data class NotificationPage(
    val items: List<Notification>,
    val olderCursor: NotificationCursor? = null,
    val newerCursor: NotificationCursor? = null,
    val checkpoint: NotificationCheckpoint? = null,
    val unreadState: NotificationUnreadState = NotificationUnreadState.Unknown,
    val direction: NotificationPageDirection = NotificationPageDirection.Initial,
    val continuation: NotificationCursor? = null,
    val newestBoundary: NotificationCursor? = null,
    val oldestBoundary: NotificationCursor? = null,
    val reachedBoundary: Boolean = false,
) {
    val nextCursor: NotificationCursor?
        get() = continuation ?: olderCursor

    val resolvedNewestBoundary: NotificationCursor?
        get() = newestBoundary ?: checkpoint?.newest ?: newerCursor

    val resolvedOldestBoundary: NotificationCursor?
        get() = oldestBoundary ?: checkpoint?.oldest ?: olderCursor

    val resolvedContinuation: NotificationCursor?
        get() = continuation ?: when (direction) {
            NotificationPageDirection.Older -> olderCursor
            NotificationPageDirection.Newer -> newerCursor
            NotificationPageDirection.Initial -> null
        }
}

