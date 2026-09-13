package me.foxtails.palustris.data.notifications

import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.EntityId
import me.foxtails.palustris.domain.Notification
import me.foxtails.palustris.domain.NotificationCheckpoint
import me.foxtails.palustris.domain.NotificationDeliveryRecord
import me.foxtails.palustris.domain.NotificationSettings
import me.foxtails.palustris.domain.NotificationUnreadState
import me.foxtails.palustris.domain.PushRegistration

data class NotificationRepositoryState(
    val items: List<Notification> = emptyList(),
    val unreadState: NotificationUnreadState = NotificationUnreadState.Unknown,
    val checkpoint: NotificationCheckpoint? = null,
    val lastSyncedAtEpochMillis: Long = 0,
    /** Checkpoints are keyed by the stable query fingerprint, never shared between filters. */
    val checkpoints: Map<String, NotificationCheckpoint> = emptyMap(),
    /** Tombstones make local-only dismissal survive refetch, restart, and older-page ingestion. */
    val dismissedIds: Set<EntityId> = emptySet(),
    val deliveries: Map<EntityId, NotificationDeliveryRecord> = emptyMap(),
    val settings: NotificationSettings = NotificationSettings(),
    val pushRegistration: PushRegistration? = null,
)

data class NotificationInboxSnapshot(
    val items: List<Notification>,
    val unreadState: NotificationUnreadState,
    val checkpoint: NotificationCheckpoint?,
    val lastSyncedAtEpochMillis: Long,
    val hasIncompleteSync: Boolean,
)
