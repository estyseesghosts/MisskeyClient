package me.foxtails.palustris.data.notifications

import me.foxtails.palustris.domain.NotificationCursor
import me.foxtails.palustris.domain.NotificationPageDirection
import me.foxtails.palustris.domain.NotificationQuery

/**
 * Caller-owned context for one notification ingestion.
 *
 * The repository validates every page against this context before any mutation. A page
 * cannot validate its own claimed query, so the caller query travels beside the page.
 * The expected continuation binds same-query requests to the input boundary they used.
 * A late page whose boundary already moved is rejected without side effects.
 */
data class NotificationIngestRequest(
    val query: NotificationQuery,
    val direction: NotificationPageDirection,
    /** The continuation the caller used for this request. Null for baselines. */
    val expectedContinuation: NotificationCursor? = null,
)
