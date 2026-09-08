package me.foxtails.palustris.data.notifications.push

import java.nio.charset.StandardCharsets
import org.json.JSONArray
import org.json.JSONObject

internal object MisskeyPushPayloadParser {
    private const val MAX_PAYLOAD_BYTES = 64 * 1024
    private const val MAX_NODES = 256
    private const val MAX_DEPTH = 8

    fun parse(content: ByteArray): MisskeyPushPayload {
        if (content.isEmpty() || content.size > MAX_PAYLOAD_BYTES) return MisskeyPushPayload.Refresh
        val root = runCatching { JSONObject(String(content, StandardCharsets.UTF_8)) }.getOrNull()
            ?: return MisskeyPushPayload.Refresh
        if (!isReasonable(root, depth = 0, nodes = intArrayOf(0))) return MisskeyPushPayload.Refresh
        return when (root.optString("type")) {
            "notification" -> root.bodyObject()?.let(MisskeyPushPayload::Notification)
            "readAllNotifications" -> MisskeyPushPayload.ReadAllNotifications
            "newChatMessage" -> root.bodyObject()?.let(MisskeyPushPayload::NewChatMessage)
            else -> null
        } ?: MisskeyPushPayload.Refresh
    }

    private fun JSONObject.bodyObject(): JSONObject? {
        val body = opt("body") ?: return null
        val normalized = when (body) {
            is JSONObject -> body
            is String -> runCatching { JSONObject(body) }.getOrNull()
            else -> null
        } ?: return null
        return normalizeDateTime(normalized).apply {
            if (!has("dateTime")) {
                when (val dateTime = this@bodyObject.opt("dateTime")) {
                    is Number -> put("dateTime", dateTime)
                    is String -> dateTime.toLongOrNull()?.let { put("dateTime", it) }
                }
            }
        }
    }

    private fun normalizeDateTime(body: JSONObject): JSONObject = body.apply {
        if (has("dateTime") && opt("dateTime") is String) {
            optString("dateTime").toLongOrNull()?.let { put("dateTime", it) }
        }
        if (has("createdAt") && opt("createdAt") is String) {
            optString("createdAt").toLongOrNull()?.let { put("createdAt", it) }
        }
    }

    private fun isReasonable(value: Any?, depth: Int, nodes: IntArray): Boolean {
        if (depth > MAX_DEPTH || nodes[0]++ > MAX_NODES) return false
        return when (value) {
            is JSONObject -> value.keys().asSequence().all { isReasonable(value.opt(it), depth + 1, nodes) }
            is JSONArray -> (0 until value.length()).all { isReasonable(value.opt(it), depth + 1, nodes) }
            else -> true
        }
    }
}
