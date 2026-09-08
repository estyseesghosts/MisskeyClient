package me.foxtails.palustris

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import me.foxtails.palustris.data.auth.SessionStore
import me.foxtails.palustris.data.notifications.work.NotificationWorkScheduler

@HiltAndroidApp
class PalustrisApplication : Application() {
    @Inject lateinit var sessionStore: SessionStore
    @Inject lateinit var notificationWorkScheduler: NotificationWorkScheduler

    private val startupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        startupScope.launch {
            runCatching {
                sessionStore.readIndex().accounts.forEach { reference ->
                    val session = sessionStore.read(reference.accountId) ?: return@forEach
                    if (session.pushState.endpointCallbackPending) {
                        notificationWorkScheduler.enqueueRegistration(reference.accountId)
                    }
                    if (session.pushState.messageHintPending) {
                        notificationWorkScheduler.enqueueCatchUp(reference.accountId)
                    }
                }
            }
        }
    }
}
