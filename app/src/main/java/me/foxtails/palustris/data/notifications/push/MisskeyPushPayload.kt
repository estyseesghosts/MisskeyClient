package me.foxtails.palustris.data.notifications.push

import org.json.JSONObject

internal sealed interface MisskeyPushPayload {
    data class Notification(val body: JSONObject) : MisskeyPushPayload
    data object ReadAllNotifications : MisskeyPushPayload
    data class NewChatMessage(val body: JSONObject) : MisskeyPushPayload
    data object Refresh : MisskeyPushPayload
}
