package me.foxtails.palustris.data.misskey

import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.Event
import me.foxtails.palustris.domain.NotificationReadState
import me.foxtails.palustris.domain.NotificationReadStatus
import me.foxtails.palustris.domain.SocialEvent
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject

internal class MisskeyStreamService(private val origin: String, private val token: String, private val api: MisskeyApi, private val accountId: AccountId?) {
    fun events(): Flow<Event> = callbackFlow {
        val account = accountId ?: throw me.foxtails.palustris.domain.SourceError.Unsupported("notifications.account")
        val socket = api.webSocket(origin, "/streaming", headers = mapOf("Authorization" to "Bearer $token"), listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: okhttp3.Response) { webSocket.send(JSONObject().put("type", "connect").put("body", JSONObject().put("channel", "main").put("id", "notifications").put("params", JSONObject().put("i", token))).toString()) }
            override fun onMessage(webSocket: WebSocket, text: String) {
                runCatching {
                    val message = JSONObject(text); when (message.optString("type")) {
                        "connected" -> if (message.optJSONObject("body")?.optString("id") == "notifications") trySend(Event(account, SocialEvent.Other("stream.ready")))
                        "channel" -> { val body = message.optJSONObject("body") ?: return@runCatching; when (body.optString("type")) {
                            "notification" -> body.optJSONObject("body")?.let { trySend(Event(account, SocialEvent.NotificationReceived(MisskeyNotificationMapper.notification(it, origin, account)))) }
                            "readAllNotifications" -> trySend(Event(account, SocialEvent.NotificationReadChanged(account, NotificationReadState(NotificationReadStatus.Read, serverAcknowledged = true))))
                            else -> trySend(Event(account, SocialEvent.Other("notification.refresh")))
                        } }
                    }
                }.onFailure { trySend(Event(account, SocialEvent.Other("notification.refresh"))) }
            }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: okhttp3.Response?) { close(t) }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) { close() }
        })
        awaitClose { socket.cancel() }
    }
}
