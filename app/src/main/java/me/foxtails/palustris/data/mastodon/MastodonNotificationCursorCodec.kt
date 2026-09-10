package me.foxtails.palustris.data.mastodon

import java.util.Base64
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.NotificationCursor
import me.foxtails.palustris.domain.NotificationQuery
import me.foxtails.palustris.domain.SourceError
import org.json.JSONObject

internal enum class MastodonNotificationCursorDirection { Older, Newer }

internal enum class MastodonNotificationApiVariant(val path: String, val tag: String) {
    V1("api/v1/notifications", "v1"),
    V2("api/v2/notifications", "v2"),
}

internal object MastodonNotificationCursorCodec {
    data class Decoded(val url: String)

    fun encode(
        variant: MastodonNotificationApiVariant,
        accountId: AccountId,
        query: NotificationQuery,
        direction: MastodonNotificationCursorDirection,
        url: String,
    ): NotificationCursor = NotificationCursor(
        Base64.getUrlEncoder().withoutPadding().encodeToString(
            JSONObject()
                .put("variant", variant.tag)
                .put("origin", accountId.connection.origin)
                .put("account", accountId.localId)
                .put("query", query.stableKey)
                .put("direction", direction.name)
                .put("url", url)
                .toString()
                .toByteArray(Charsets.UTF_8),
        ),
    )

    fun decode(
        cursor: NotificationCursor,
        variant: MastodonNotificationApiVariant,
        accountId: AccountId,
        query: NotificationQuery,
        direction: MastodonNotificationCursorDirection,
    ): Decoded {
        val json = runCatching {
            JSONObject(String(Base64.getUrlDecoder().decode(cursor.value), Charsets.UTF_8))
        }.getOrElse { throw SourceError.Unsupported("notifications.cursor") }
        if (json.optString("variant") != variant.tag ||
            json.optString("origin") != accountId.connection.origin ||
            json.optString("account") != accountId.localId ||
            json.optString("query") != query.stableKey ||
            json.optString("direction") != direction.name
        ) throw SourceError.Unsupported("notifications.cursor")
        return Decoded(json.optString("url").takeIf(String::isNotBlank)
            ?: throw SourceError.Unsupported("notifications.cursor"))
    }
}
