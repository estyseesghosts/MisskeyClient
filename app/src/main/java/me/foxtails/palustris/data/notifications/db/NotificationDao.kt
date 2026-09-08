package me.foxtails.palustris.data.notifications.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface NotificationDao {
    @Query("SELECT * FROM notification_state WHERE accountKey = :accountKey")
    fun state(accountKey: String): NotificationStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun saveState(value: NotificationStateEntity)

    @Query("DELETE FROM notification_state WHERE accountKey = :accountKey")
    fun deleteState(accountKey: String)

    @Query("DELETE FROM notification_events WHERE accountKey = :accountKey")
    fun deleteEvents(accountKey: String)

    @Query("DELETE FROM notification_actors WHERE accountKey = :accountKey")
    fun deleteActors(accountKey: String)

    @Query("DELETE FROM notification_groups WHERE accountKey = :accountKey")
    fun deleteGroups(accountKey: String)

    @Query("DELETE FROM notification_query_state WHERE accountKey = :accountKey")
    fun deleteQueryState(accountKey: String)

    @Query("DELETE FROM notification_dismissals WHERE accountKey = :accountKey")
    fun deleteDismissals(accountKey: String)

    @Query("DELETE FROM notification_delivery WHERE accountKey = :accountKey")
    fun deleteDelivery(accountKey: String)

    @Query("DELETE FROM notification_acknowledgement WHERE accountKey = :accountKey")
    fun deleteAcknowledgements(accountKey: String)

    @Query("DELETE FROM push_registration WHERE accountKey = :accountKey")
    fun deletePushRegistration(accountKey: String)

    @Query("DELETE FROM notification_settings WHERE accountKey = :accountKey")
    fun deleteSettings(accountKey: String)
}

