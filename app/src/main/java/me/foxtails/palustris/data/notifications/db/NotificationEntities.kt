package me.foxtails.palustris.data.notifications.db

import androidx.room.Entity

@Entity(tableName = "notification_state")
data class NotificationStateEntity(
    @androidx.room.PrimaryKey val accountKey: String,
    val stateJson: String,
    val updatedAtEpochMillis: Long,
)

@Entity(tableName = "notification_events", primaryKeys = ["accountKey", "eventConnection", "eventId"])
data class NotificationEntity(
    val accountKey: String,
    val eventConnection: String,
    val eventId: String,
    val createdAtEpochMillis: Long,
    val payloadJson: String,
    val locallySeen: Boolean,
    val serverAcknowledged: Boolean,
    val androidPresented: Boolean,
)

@Entity(tableName = "notification_actors", primaryKeys = ["accountKey", "eventConnection", "eventId", "actorId"])
data class NotificationActorEntity(
    val accountKey: String,
    val eventConnection: String,
    val eventId: String,
    val actorId: String,
    val position: Int,
    val payloadJson: String,
)

@Entity(tableName = "notification_groups", primaryKeys = ["accountKey", "groupKey"])
data class NotificationGroupEntity(
    val accountKey: String,
    val groupKey: String,
    val totalCount: Int?,
    val actorContinuation: String?,
    val revision: Long,
)

@Entity(tableName = "notification_query_state", primaryKeys = ["accountKey", "queryKey"])
data class NotificationQueryStateEntity(
    val accountKey: String,
    val queryKey: String,
    val newest: String?,
    val oldest: String?,
    val newerContinuation: String?,
    val olderContinuation: String?,
    val completeness: String,
    val baselineEstablished: Boolean,
    val capturedAtEpochMillis: Long,
)

@Entity(tableName = "notification_dismissals", primaryKeys = ["accountKey", "eventConnection", "eventId", "kind"])
data class NotificationDismissalEntity(
    val accountKey: String,
    val eventConnection: String,
    val eventId: String,
    val kind: String,
    val expiresAtEpochMillis: Long?,
)

@Entity(tableName = "notification_delivery", primaryKeys = ["accountKey", "eventConnection", "eventId"])
data class NotificationDeliveryEntity(
    val accountKey: String,
    val eventConnection: String,
    val eventId: String,
    val state: String,
    val androidTag: String,
    val androidId: Int,
    val attemptCount: Int,
    val lastAttemptAtEpochMillis: Long,
    val lastErrorCategory: String?,
)

@Entity(tableName = "notification_acknowledgement", primaryKeys = ["accountKey", "intentKey"])
data class NotificationAcknowledgementEntity(
    val accountKey: String,
    val intentKey: String,
    val state: String,
    val createdAtEpochMillis: Long,
)

@Entity(tableName = "push_registration")
data class PushRegistrationEntity(
    @androidx.room.PrimaryKey val accountKey: String,
    val generation: Long,
    val instanceName: String,
    val distributorPackage: String?,
    val endpoint: String?,
    val endpointGeneration: Long,
    val state: String,
    val retryCount: Int,
    val lastErrorCategory: String?,
)

@Entity(tableName = "notification_settings")
data class NotificationSettingsEntity(
    @androidx.room.PrimaryKey val accountKey: String,
    val alertsEnabled: Boolean,
    val categories: String,
    val showPreviews: Boolean,
    val quietHoursStartMinutes: Int?,
    val quietHoursEndMinutes: Int?,
    val periodicFallbackEnabled: Boolean,
    val selectedDistributor: String?,
)

