package me.foxtails.palustris.data.notifications

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import me.foxtails.palustris.data.AccountSourceRegistry
import me.foxtails.palustris.data.notifications.work.NotificationWorkScheduler
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.SocialEvent
import me.foxtails.palustris.domain.SourceError

interface NotificationStreamController {
    fun start(accountId: AccountId)
    fun stop(accountId: AccountId)
}

class NoOpNotificationStreamController : NotificationStreamController {
    override fun start(accountId: AccountId) = Unit
    override fun stop(accountId: AccountId) = Unit
}

/** Owns live sockets only while the connected account is in the foreground. */
@Singleton
class ForegroundNotificationStreamController @Inject constructor(
    private val sourceRegistry: AccountSourceRegistry,
    private val synchronizer: NotificationSyncOrchestrator,
    private val scheduler: NotificationWorkScheduler,
) : NotificationStreamController, AutoCloseable {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jobs = mutableMapOf<AccountId, Job>()

    @Synchronized
    override fun start(accountId: AccountId) {
        if (jobs[accountId]?.isActive == true) return
        jobs[accountId] = scope.launch { run(accountId) }
    }

    @Synchronized
    override fun stop(accountId: AccountId) {
        jobs.remove(accountId)?.cancel()
        synchronizer.setStreamStatus(accountId, NotificationStreamStatus.Stopped)
    }

    private suspend fun run(accountId: AccountId) {
        var backoffMillis = INITIAL_BACKOFF_MILLIS
        while (scope.isActive) {
            val source = sourceRegistry.sourceFor(accountId) ?: return
            var ready = false
            try {
                synchronizer.setStreamStatus(accountId, NotificationStreamStatus.Connecting)
                source.streamEvents().takeWhile { event ->
                    if (sourceRegistry.sourceFor(accountId) !== source) return@takeWhile false
                    if (event.payload is SocialEvent.Other && event.payload.kind == "stream.ready") {
                        ready = true
                        synchronizer.setStreamStatus(accountId, NotificationStreamStatus.Ready)
                        return@takeWhile true
                    }
                    if (!synchronizer.accept(event)) return@takeWhile true
                    when (event.payload) {
                        is SocialEvent.NotificationReceived -> scheduler.enqueueDelivery(accountId)
                        is SocialEvent.NotificationReadChanged -> Unit
                        is SocialEvent.Other -> scheduler.enqueueCatchUp(accountId)
                        else -> Unit
                    }
                    true
                }.collect()
                if (sourceRegistry.sourceFor(accountId) !== source) return
                synchronizer.setStreamStatus(
                    accountId,
                    NotificationStreamStatus.Backoff,
                    error = if (ready) "stream_closed" else "stream_not_ready",
                )
            } catch (error: CancellationException) {
                synchronizer.setStreamStatus(accountId, NotificationStreamStatus.Stopped)
                throw error
            } catch (error: SourceError.Unsupported) {
                synchronizer.setStreamStatus(accountId, NotificationStreamStatus.Unsupported, "stream_unsupported")
                return
            } catch (error: SourceError.UnsupportedCredential) {
                synchronizer.setStreamStatus(accountId, NotificationStreamStatus.Unsupported, "stream_unsupported")
                return
            } catch (error: SourceError.ServerUnsupported) {
                synchronizer.setStreamStatus(accountId, NotificationStreamStatus.Unsupported, "stream_unsupported")
                return
            } catch (_: Exception) {
                synchronizer.setStreamStatus(accountId, NotificationStreamStatus.Backoff, "stream_unavailable")
            }
            if (!scope.isActive) return
            if (sourceRegistry.sourceFor(accountId) !== source) return
            try {
                val result: NotificationSyncResult = synchronizer.refresh(accountId)
                if (result.pages > 0) scheduler.enqueueDelivery(accountId)
            } catch (_: Exception) {
                scheduler.enqueueCatchUp(accountId)
            }
            delay(backoffMillis)
            backoffMillis = if (ready) {
                INITIAL_BACKOFF_MILLIS
            } else {
                (backoffMillis * 2).coerceAtMost(MAX_BACKOFF_MILLIS)
            }
        }
    }

    override fun close() {
        synchronized(this) { jobs.values.forEach(Job::cancel); jobs.clear() }
        scope.cancel()
    }

    private companion object {
        const val INITIAL_BACKOFF_MILLIS = 1_000L
        const val MAX_BACKOFF_MILLIS = 60_000L
    }
}
