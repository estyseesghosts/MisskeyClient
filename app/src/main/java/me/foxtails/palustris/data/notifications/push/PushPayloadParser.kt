package me.foxtails.palustris.data.notifications.push

import org.json.JSONArray
import org.json.JSONObject

/**
 * Classifies a decrypted push envelope without retaining or exposing its contents.
 * Push data is only a hint: the authenticated source remains the authority for inbox data.
 */
sealed interface PushPayloadHint {
    data object Notification : PushPayloadHint
    data object ReadInvalidation : PushPayloadHint
    data object RegistrationInvalidation : PushPayloadHint
    data object Refresh : PushPayloadHint
    data object Ignored : PushPayloadHint
    data object RejectedSensitive : PushPayloadHint
}

object PushPayloadParser {
    private const val MAX_PAYLOAD_BYTES = 64 * 1024
    private const val MAX_NODES = 256
    private const val MAX_DEPTH = 8

    fun classify(content: ByteArray): PushPayloadHint {
        if (content.isEmpty() || content.size > MAX_PAYLOAD_BYTES) return PushPayloadHint.Refresh
        val json = runCatching { JSONObject(String(content, Charsets.UTF_8)) }.getOrNull()
            ?: return PushPayloadHint.Refresh
        val keys = mutableSetOf<String>()
        if (containsSensitiveField(json, keys, depth = 0, nodes = intArrayOf(0))) {
            return PushPayloadHint.RejectedSensitive
        }
        val normalizedKeys = keys.map(String::lowercase).toSet()
        return when {
            normalizedKeys.any { it in NOTIFICATION_KEYS } -> PushPayloadHint.Notification
            normalizedKeys.any { it in READ_KEYS } -> PushPayloadHint.ReadInvalidation
            normalizedKeys.any { it in REGISTRATION_KEYS } -> PushPayloadHint.RegistrationInvalidation
            normalizedKeys.isEmpty() -> PushPayloadHint.Ignored
            else -> PushPayloadHint.Refresh
        }
    }

    private fun containsSensitiveField(
        value: Any?,
        keys: MutableSet<String>,
        depth: Int,
        nodes: IntArray,
    ): Boolean {
        if (depth > MAX_DEPTH || nodes[0]++ > MAX_NODES) return true
        when (value) {
            is JSONObject -> {
                val iterator = value.keys()
                while (iterator.hasNext()) {
                    val key = iterator.next()
                    val normalized = key.lowercase()
                    if (normalized in SENSITIVE_KEYS || SENSITIVE_KEY_PARTS.any(normalized::contains)) return true
                    keys += normalized
                    if (containsSensitiveField(value.opt(key), keys, depth + 1, nodes)) return true
                }
            }
            is JSONArray -> for (index in 0 until value.length()) {
                if (containsSensitiveField(value.opt(index), keys, depth + 1, nodes)) return true
            }
        }
        return false
    }

    private val SENSITIVE_KEYS = setOf(
        "i", "token", "access_token", "authorization", "bearer", "password", "secret",
        "client_secret", "session", "session_id", "miauth_session",
    )
    private val SENSITIVE_KEY_PARTS = setOf("token", "secret", "password", "authorization", "bearer")
    private val NOTIFICATION_KEYS = setOf("notification", "notification_id", "notification_type", "type")
    private val READ_KEYS = setOf("read", "read_all", "readallnotifications", "last_read_id")
    private val REGISTRATION_KEYS = setOf("subscription", "endpoint", "unregistered", "registration")
}
