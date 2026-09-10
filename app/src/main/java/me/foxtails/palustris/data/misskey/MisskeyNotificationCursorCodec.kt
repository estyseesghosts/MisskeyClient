package me.foxtails.palustris.data.misskey

import java.util.Base64
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.NotificationCursor
import me.foxtails.palustris.domain.NotificationQuery
import me.foxtails.palustris.domain.SourceError
import org.json.JSONObject

internal enum class MisskeyNotificationCursorDirection { Older, Newer }

internal object MisskeyNotificationCursorCodec {
    data class Decoded(val rawId: String)

    fun encode(
        accountId: AccountId,
        query: NotificationQuery,
        direction: MisskeyNotificationCursorDirection,
        rawId: String,
    ): NotificationCursor = NotificationCursor(
        Base64.getUrlEncoder().withoutPadding().encodeToString(
            JSONObject()
                .put("origin", accountId.connection.origin)
                .put("account", accountId.localId)
                .put("query", query.stableKey)
                .put("direction", direction.name)
                .put("id", rawId)
                .toString()
                .toByteArray(Charsets.UTF_8),
        ),
    )

    fun decode(
        cursor: NotificationCursor,
        accountId: AccountId,
        query: NotificationQuery,
        direction: MisskeyNotificationCursorDirection,
    ): Decoded {
        val json = runCatching {
            JSONObject(String(Base64.getUrlDecoder().decode(cursor.value), Charsets.UTF_8))
        }.getOrElse { throw SourceError.Unsupported("notifications.cursor") }
        if (json.optString("origin") != accountId.connection.origin ||
            json.optString("account") != accountId.localId ||
            json.optString("query") != query.stableKey ||
            json.optString("direction") != direction.name
        ) throw SourceError.Unsupported("notifications.cursor")
        return Decoded(json.optString("id").takeIf(String::isNotBlank)
            ?: throw SourceError.Unsupported("notifications.cursor"))
    }
}
