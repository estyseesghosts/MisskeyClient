package me.foxtails.palustris.data.notifications.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import me.foxtails.palustris.domain.AccountId

@Singleton
class NotificationWorkScheduler @Inject constructor(
    @param:ApplicationContext context: Context,
) {
    private val workManager by lazy { WorkManager.getInstance(context) }

    fun enqueueReconcile(accountId: AccountId) {
        workManager.enqueueUniqueWork(
            NotificationWorkNames.reconcile(accountId),
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<NotificationReconcileWorker>().setInputData(accountData(accountId)).build(),
        )
    }

    fun enqueueCatchUp(accountId: AccountId) {
        workManager.enqueueUniqueWork(
            NotificationWorkNames.catchUp(accountId),
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<NotificationCatchUpWorker>().setInputData(accountData(accountId)).build(),
        )
    }

    fun enqueueDelivery(accountId: AccountId) {
        workManager.enqueueUniqueWork(
            NotificationWorkNames.delivery(accountId),
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<NotificationDeliveryWorker>().setInputData(accountData(accountId)).build(),
        )
    }

    fun schedulePeriodicFallback(accountId: AccountId) {
        workManager.enqueueUniquePeriodicWork(
            NotificationWorkNames.reconcile(accountId),
            ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<NotificationReconcileWorker>(30, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setInputData(accountData(accountId))
                .build(),
        )
    }

    fun cancel(accountId: AccountId) {
        workManager.cancelUniqueWork(NotificationWorkNames.reconcile(accountId))
        workManager.cancelUniqueWork(NotificationWorkNames.catchUp(accountId))
        workManager.cancelUniqueWork(NotificationWorkNames.delivery(accountId))
        cancelPeriodicFallback(accountId)
    }

    fun cancelPeriodicFallback(accountId: AccountId) {
        workManager.cancelUniqueWork(NotificationWorkNames.periodic(accountId))
    }

    private fun accountData(accountId: AccountId): Data = Data.Builder()
        .putString(NotificationWorkNames.INPUT_ORIGIN, accountId.connection.origin)
        .putString(NotificationWorkNames.INPUT_LOCAL_ID, accountId.localId)
        .putString(NotificationWorkNames.INPUT_PROTOCOL, accountId.connection.protocol.name)
        .build()
}
