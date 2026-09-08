package me.foxtails.palustris.data.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import me.foxtails.palustris.ui.notifications.NotificationLaunchRouter

/** Handles only app-owned notification swipes; server/inbox read state is untouched. */
@AndroidEntryPoint
class AndroidNotificationDismissReceiver : BroadcastReceiver() {
    @Inject lateinit var repository: NotificationRepository

    override fun onReceive(context: Context, intent: Intent) {
        val launch = NotificationLaunchRouter().parseDismiss(intent) ?: return
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                repository.markAndroidDismissed(launch.accountId, launch.notificationId)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
