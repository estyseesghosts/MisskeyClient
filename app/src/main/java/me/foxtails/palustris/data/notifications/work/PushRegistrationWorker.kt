package me.foxtails.palustris.data.notifications.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import me.foxtails.palustris.data.notifications.push.PushRegistrationWorkResult
import me.foxtails.palustris.data.notifications.push.PushRegistrationManager

@EntryPoint
@InstallIn(SingletonComponent::class)
interface PushRegistrationWorkerDependencies {
    fun pushRegistrationManager(): PushRegistrationManager
}

/** Performs the authenticated endpoint reconciliation after the distributor callback returns. */
class PushRegistrationWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : AccountNotificationWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        val accountId = accountId() ?: return Result.failure()
        val manager = EntryPointAccessors.fromApplication(
            applicationContext,
            PushRegistrationWorkerDependencies::class.java,
        ).pushRegistrationManager()
        return when (manager.processPendingEndpoint(accountId)) {
            PushRegistrationWorkResult.NoWork,
            PushRegistrationWorkResult.Success,
            PushRegistrationWorkResult.Terminal,
            -> Result.success()
            PushRegistrationWorkResult.Retry -> Result.retry()
        }
    }
}
