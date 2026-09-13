package me.foxtails.palustris.data.notifications

import org.json.JSONArray
import org.json.JSONObject

internal fun encode(state: NotificationRepositoryState): JSONObject = JSONObject().apply {
    put("version", 2)
    put("items", JSONArray(state.items.map(::encodeNotification)))
    put("unread", encodeUnread(state.unreadState))
    state.checkpoint?.let { checkpoint -> put("checkpoint", encodeCheckpoint(checkpoint)) }
    put("lastSyncedAt", state.lastSyncedAtEpochMillis)
    put("dismissedIds", JSONArray(state.dismissedIds.map(::encodeEntity)))
    put("checkpoints", JSONObject().apply {
        state.checkpoints.forEach { (key, checkpoint) -> put(key, encodeCheckpoint(checkpoint)) }
    })
    put("deliveries", JSONArray(state.deliveries.values.map(::encodeDelivery)))
    put("settings", encodeSettings(state.settings))
    state.pushRegistration?.let { put("pushRegistration", encodePushRegistration(it)) }
}

internal fun decode(json: JSONObject): NotificationRepositoryState {
    val items = json.optJSONArray("items")?.let { values ->
        (0 until values.length()).mapNotNull { index -> runCatching { decodeNotification(values.getJSONObject(index)) }.getOrNull() }
    }.orEmpty()
    return NotificationRepositoryState(
        items = items,
        unreadState = decodeUnread(json.optJSONObject("unread")),
        checkpoint = json.optJSONObject("checkpoint")?.let(::decodeCheckpoint),
        lastSyncedAtEpochMillis = json.optLong("lastSyncedAt", 0),
        dismissedIds = json.optJSONArray("dismissedIds")?.let { values ->
            (0 until values.length()).mapNotNull { index -> runCatching { decodeEntity(values.getJSONObject(index)) }.getOrNull() }
        }?.toSet().orEmpty(),
        checkpoints = json.optJSONObject("checkpoints")?.let { values ->
            values.keys().asSequence().mapNotNull { key -> runCatching { key to decodeCheckpoint(values.getJSONObject(key)) }.getOrNull() }
                .toMap()
        }.orEmpty(),
        deliveries = json.optJSONArray("deliveries")?.let { values ->
            (0 until values.length()).mapNotNull { index -> runCatching { decodeDelivery(values.getJSONObject(index)) }.getOrNull() }
        }?.associateBy { it.notificationId }.orEmpty(),
        settings = decodeSettings(json.optJSONObject("settings")),
        pushRegistration = json.optJSONObject("pushRegistration")?.let(::decodePushRegistration),
    )
}
