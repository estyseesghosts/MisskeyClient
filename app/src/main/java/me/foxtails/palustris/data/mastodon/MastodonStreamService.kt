package me.foxtails.palustris.data.mastodon

import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import me.foxtails.palustris.data.misskey.MisskeyApi
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.Event
import me.foxtails.palustris.domain.SocialEvent
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject

internal class MastodonStreamService(
    private val origin: String,
    private val token: String,
    private val api: MisskeyApi,
    private val accountId: AccountId,
) {
    fun events(): Flow<Event> = callbackFlow {
        val socket = api.webSocket(origin, "/api/v1/streaming/user", headers = mapOf("Authorization" to "Bearer $token"), listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: okhttp3.Response) = trySend(Event(accountId, SocialEvent.Other("stream.ready"))).let { }
            override fun onMessage(webSocket: WebSocket, text: String) {
                runCatching {
                    val message = JSONObject(text)
                    when (message.optString("event")) {
                        "notification" -> message.optString("payload").takeIf { it.isNotBlank() }?.let {
                            trySend(Event(accountId, SocialEvent.NotificationReceived(MastodonNotificationMapper.notification(JSONObject(it), origin, accountId))))
                        }
                        "delete", "filters_changed" -> trySend(Event(accountId, SocialEvent.Other("notification.refresh")))
                    }
                }.onFailure { trySend(Event(accountId, SocialEvent.Other("notification.refresh"))) }
            }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: okhttp3.Response?) { close(t) }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) { close() }
        })
        awaitClose { socket.cancel() }
    }
}
