package me.foxtails.palustris.data.notifications.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.foxtails.palustris.data.SocialSourceFactory
import me.foxtails.palustris.data.auth.SessionStore
import me.foxtails.palustris.data.notifications.NotificationDeliveryPlanner
import me.foxtails.palustris.data.notifications.NotificationPermissionController
import me.foxtails.palustris.data.notifications.NotificationPresentationFactory
import me.foxtails.palustris.data.notifications.NotificationPresentationAvailability
import me.foxtails.palustris.data.notifications.NotificationPresenter
import me.foxtails.palustris.data.notifications.NotificationRepository
import me.foxtails.palustris.data.notifications.NotificationSynchronizer
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.NotificationDeliveryState
import me.foxtails.palustris.domain.NotificationSyncToken
import me.foxtails.palustris.domain.Protocol
import me.foxtails.palustris.data.notifications.NotificationDeliveryDecision

@EntryPoint
@InstallIn(SingletonComponent::class)
interface NotificationWorkerDependencies {
    fun sessionStore(): SessionStore
    fun sourceFactory(): SocialSourceFactory
    fun repository(): NotificationRepository
    fun synchronizer(): NotificationSynchronizer
    fun scheduler(): NotificationWorkScheduler
    fun planner(): NotificationDeliveryPlanner
    fun permissionController(): NotificationPermissionController
    fun presentationFactory(): NotificationPresentationFactory
    fun presenter(): NotificationPresenter
}

abstract class AccountNotificationWorker(
    appContext: Context,
    protected val workerParameters: WorkerParameters,
) : CoroutineWorker(appContext, workerParameters) {
    protected fun accountId(): AccountId? {
        val origin = inputData.getString(NotificationWorkNames.INPUT_ORIGIN)?.takeIf(String::isNotBlank) ?: return null
        val localId = inputData.getString(NotificationWorkNames.INPUT_LOCAL_ID)?.takeIf(String::isNotBlank) ?: return null
        val protocol = inputData.getString(NotificationWorkNames.INPUT_PROTOCOL)?.let {
            runCatching { Protocol.valueOf(it) }.getOrNull()
        } ?: return null
        return AccountId(Connection(origin, protocol), localId)
    }

    protected fun dependencies(): NotificationWorkerDependencies = EntryPointAccessors.fromApplication(
        applicationContext,
        NotificationWorkerDependencies::class.java,
    )

    protected suspend fun token(accountId: AccountId): NotificationSyncToken? {
        val session = dependencies().sessionStore().read(accountId) ?: return null
        val repository = dependencies().repository()
        repository.currentToken(accountId)?.let { return it }
        return repository.recoverToken(accountId, session.sessionRevision)
    }
}

class NotificationReconcileWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : AccountNotificationWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        val accountId = accountId() ?: return Result.failure()
        val dependencies = dependencies()
        if (withContext(Dispatchers.IO) { dependencies.sessionStore().read(accountId) } == null) return Result.success()
        val token = token(accountId) ?: return Result.success()
        return try {
            val session = withContext(Dispatchers.IO) { dependencies.sessionStore().read(accountId) } ?: return Result.success()
            val result = dependencies.synchronizer().catchUpNewer(
                dependencies.sourceFactory().create(session),
                token,
            )
            if (!result.delayed) {
                dependencies.sessionStore().read(accountId)?.pushInstanceName?.let { instance ->
                    dependencies.sessionStore().clearPushMessageHint(accountId, instance)
                }
            }
            dependencies.scheduler().enqueueDelivery(accountId)
            if (result.delayed) Result.retry() else Result.success()
        } catch (error: IOException) {
            Result.retry()
        } catch (_: Exception) {
            Result.retry()
        }
    }
}

class NotificationCatchUpWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : AccountNotificationWorker(appContext, workerParams) {
    override suspend fun doWork(): Result = NotificationReconcileWorker(applicationContext, workerParameters).doWork()
}

class NotificationDeliveryWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : AccountNotificationWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        val accountId = accountId() ?: return Result.failure()
        val dependencies = dependencies()
        if (withContext(Dispatchers.IO) { dependencies.sessionStore().read(accountId) } == null) return Result.success()
        val token = token(accountId) ?: return Result.success()
        val repository = dependencies.repository()
        val settings = repository.settings(accountId)
        val permissionGranted = dependencies.permissionController().isGranted()
        var retryRequested = false
        repository.pendingDeliveries(accountId).forEach { record ->
            val notification = repository.observe(accountId).value.items.firstOrNull { it.id == record.notificationId } ?: return@forEach
            val claimed = repository.claimDelivery(token, record.notificationId) ?: return@forEach
            val plan = dependencies.planner().plan(notification, settings, permissionGranted, foreground = false)
            when (plan.decision) {
                    NotificationDeliveryDecision.Present -> {
                        val presenter = dependencies.presenter()
                        val presentation = dependencies.presentationFactory().prepare(notification, plan.showPreview, plan.channel)
                        val availability = presenter.availability(plan.channel)
                        if (availability == NotificationPresentationAvailability.Available && presenter.present(presentation)) {
                            repository.markPresented(token, claimed.notificationId)
                            repository.finishDelivery(
                                token,
                                claimed.notificationId,
                                NotificationDeliveryState.Presented,
                                claimId = claimed.claimId,
                            )
                        } else {
                            val errorCategory = when (availability) {
                                NotificationPresentationAvailability.PermissionRequired -> "permission_required"
                                NotificationPresentationAvailability.AppDisabled -> "notifications_disabled"
                                NotificationPresentationAvailability.ChannelDisabled -> "channel_disabled"
                                NotificationPresentationAvailability.Available -> "present_failed"
                            }
                            repository.finishDelivery(
                                token,
                                claimed.notificationId,
                                NotificationDeliveryState.Failed,
                                errorCategory,
                                claimed.claimId,
                            )
                            retryRequested = availability == NotificationPresentationAvailability.Available
                        }
                    }
                    NotificationDeliveryDecision.AlreadyPresented -> repository.finishDelivery(
                        token,
                        claimed.notificationId,
                        NotificationDeliveryState.Presented,
                        claimId = claimed.claimId,
                    )
                    NotificationDeliveryDecision.SuppressedBySettings,
                    NotificationDeliveryDecision.SuppressedByQuietHours,
                    NotificationDeliveryDecision.PermissionRequired,
                    -> repository.finishDelivery(
                        token,
                        claimed.notificationId,
                        NotificationDeliveryState.Failed,
                        if (plan.decision == NotificationDeliveryDecision.PermissionRequired) {
                            "permission_required"
                        } else {
                            plan.decision.name
                        },
                        claimed.claimId,
                    )
            }
        }
        return if (retryRequested) Result.retry() else Result.success()
    }
}
