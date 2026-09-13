package me.foxtails.palustris.data.notifications

import me.foxtails.palustris.domain.NotificationDeliveryRecord
import me.foxtails.palustris.domain.NotificationDeliveryState

internal fun NotificationDeliveryRecord.isClaimable(nowEpochMillis: Long): Boolean = when (state) {
    NotificationDeliveryState.Pending,
    NotificationDeliveryState.Failed,
    -> true
    NotificationDeliveryState.Posting -> claimExpiresAtEpochMillis <= nowEpochMillis
    else -> false
}

internal fun NotificationDeliveryRecord.claim(
    nowEpochMillis: Long,
    claimId: String,
    leaseMillis: Long,
): NotificationDeliveryRecord = copy(
    state = NotificationDeliveryState.Posting,
    attemptCount = attemptCount + 1,
    lastAttemptAtEpochMillis = nowEpochMillis,
    claimId = claimId,
    claimExpiresAtEpochMillis = nowEpochMillis + leaseMillis,
)

internal fun NotificationDeliveryRecord.finish(
    state: NotificationDeliveryState,
    errorCategory: String?,
): NotificationDeliveryRecord = copy(
    state = state,
    lastErrorCategory = errorCategory,
    claimId = null,
    claimExpiresAtEpochMillis = 0,
)
