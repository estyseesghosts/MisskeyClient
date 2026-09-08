package me.foxtails.palustris.data.notifications.push

import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import org.unifiedpush.android.connector.FailedReason
import org.unifiedpush.android.connector.PushService
import org.unifiedpush.android.connector.data.PushEndpoint
import org.unifiedpush.android.connector.data.PushMessage

@AndroidEntryPoint
class UnifiedPushService : PushService() {
    @Inject lateinit var registrationManager: UnifiedPushRegistrationManager

    override fun onMessage(message: PushMessage, instance: String) {
        registrationManager.onMessage(message, instance)
    }

    override fun onNewEndpoint(endpoint: PushEndpoint, instance: String) {
        registrationManager.onNewEndpoint(endpoint, instance)
    }

    override fun onRegistrationFailed(reason: FailedReason, instance: String) {
        registrationManager.onRegistrationFailed(instance)
    }

    override fun onTempUnavailable(instance: String) {
        registrationManager.onTempUnavailable(instance)
    }

    override fun onUnregistered(instance: String) {
        registrationManager.onUnregistered(instance)
    }
}
