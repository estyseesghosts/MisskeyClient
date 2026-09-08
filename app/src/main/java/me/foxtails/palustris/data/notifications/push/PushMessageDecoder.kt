package me.foxtails.palustris.data.notifications.push

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import org.unifiedpush.android.connector.data.PushMessage
import org.unifiedpush.android.connector.keys.DefaultKeyManager

@Singleton
class PushMessageDecoder @Inject constructor(
    @param:ApplicationContext context: Context,
) {
    private val keyManager = DefaultKeyManager(context)

    fun decode(instanceName: String, message: PushMessage): ByteArray? = runCatching {
        if (message.decrypted) message.content else keyManager.decrypt(instanceName, message.content)
    }.getOrNull()
}
