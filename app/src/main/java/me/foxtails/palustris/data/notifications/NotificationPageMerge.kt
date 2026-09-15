package me.foxtails.palustris.data.notifications

import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.EntityId
import me.foxtails.palustris.domain.Notification
import me.foxtails.palustris.domain.NotificationCheckpoint
import me.foxtails.palustris.domain.NotificationDeliveryRecord
import me.foxtails.palustris.domain.NotificationPage
import me.foxtails.palustris.domain.NotificationPageDirection
import me.foxtails.palustris.domain.NotificationQuery

internal fun mergeNotificationCheckpoint(
    previous: NotificationCheckpoint?,
    page: NotificationPage,
    pageCheckpoint: NotificationCheckpoint?,
    accountId: AccountId,
    query: NotificationQuery,
    direction: NotificationPageDirection,
    baselineEstablished: Boolean,
): NotificationCheckpoint {
    val newest = when (direction) {
        NotificationPageDirection.Older -> previous?.newest ?: page.resolvedNewestBoundary
        NotificationPageDirection.Initial -> page.resolvedNewestBoundary ?: previous?.newest
        NotificationPageDirection.Newer -> if (previous?.newerContinuation != null) previous.newest
        else page.resolvedNewestBoundary ?: previous?.newest
    }
    val oldest = when (direction) {
        NotificationPageDirection.Newer -> previous?.oldest ?: page.resolvedOldestBoundary
        NotificationPageDirection.Initial -> page.resolvedOldestBoundary ?: previous?.oldest
        NotificationPageDirection.Older -> if (page.reachedBoundary || page.resolvedContinuation == null) null
        else page.resolvedOldestBoundary ?: previous?.oldest
    }
    val continuation = page.continuation ?: when (direction) {
        NotificationPageDirection.Older -> page.olderCursor
        NotificationPageDirection.Newer -> page.newerCursor
        NotificationPageDirection.Initial -> null
    }
    val newerContinuation = when (direction) {
        NotificationPageDirection.Newer -> continuation
        else -> previous?.newerContinuation
    }
    val olderContinuation = when (direction) {
        NotificationPageDirection.Older -> continuation
        else -> previous?.olderContinuation
    }
    return NotificationCheckpoint(
        accountId = accountId,
        query = query,
        newest = newest,
        oldest = oldest,
        capturedAtEpochMillis = pageCheckpoint?.capturedAtEpochMillis ?: previous?.capturedAtEpochMillis ?: 0,
        newerContinuation = newerContinuation,
        olderContinuation = olderContinuation,
        completeness = if (page.reachedBoundary || continuation == null) {
            me.foxtails.palustris.domain.NotificationSyncCompleteness.Complete
        } else {
            me.foxtails.palustris.domain.NotificationSyncCompleteness.Incomplete
        },
        baselineEstablished = baselineEstablished || previous?.baselineEstablished == true,
    )
}

internal fun updateNotificationDeliveryOutbox(
    state: NotificationRepositoryState,
    incoming: List<Notification>,
    previousCheckpoint: NotificationCheckpoint?,
    baselineEstablished: Boolean,
    direction: NotificationPageDirection,
): Map<me.foxtails.palustris.domain.EntityId, NotificationDeliveryRecord> {
    if (baselineEstablished || direction != NotificationPageDirection.Newer ||
        previousCheckpoint?.baselineEstablished != true
    ) return state.deliveries
    val knownIds = state.items.asSequence().map(Notification::id).toSet()
    return incoming.fold(state.deliveries) { deliveries, notification ->
        if (notification.id in deliveries || notification.id in state.dismissedIds || notification.id in knownIds) deliveries
        else deliveries + (notification.id to NotificationDeliveryRecord(
            accountId = notification.accountId,
            notificationId = notification.id,
            androidTag = "${notification.accountId.connection.origin}:${notification.accountId.localId}",
            androidId = stableNotificationId(notification.id),
        ))
    }
}

/**
 * Stable delivery identity for one notification. The hash input is the
 * compatibility contract for outbox tags and replacement. Do not merge it
 * with [AndroidNotificationIds] without a separate identity migration.
 */
internal fun stableNotificationId(id: EntityId): Int = (id.connection + "\u0000" + id.value).hashCode()
