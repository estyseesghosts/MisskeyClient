package me.foxtails.palustris.data.notifications.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        NotificationStateEntity::class,
        NotificationEntity::class,
        NotificationActorEntity::class,
        NotificationGroupEntity::class,
        NotificationQueryStateEntity::class,
        NotificationDismissalEntity::class,
        NotificationDeliveryEntity::class,
        NotificationAcknowledgementEntity::class,
        PushRegistrationEntity::class,
        NotificationSettingsEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
abstract class NotificationDatabase : RoomDatabase() {
    abstract fun notificationDao(): NotificationDao
}

