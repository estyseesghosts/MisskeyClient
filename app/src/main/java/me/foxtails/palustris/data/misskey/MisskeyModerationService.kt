package me.foxtails.palustris.data.misskey

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.ModerationAccount
import me.foxtails.palustris.domain.ModerationCursor
import me.foxtails.palustris.domain.ModerationListKind
import me.foxtails.palustris.domain.ModerationPage
import me.foxtails.palustris.domain.MutedHashtag
import me.foxtails.palustris.domain.SourceError
import org.json.JSONArray
import org.json.JSONObject

class MisskeyModerationService(
    private val origin: String,
    private val token: String,
    private val api: MisskeyApi,
    private val accountId: AccountId,
) {
    suspend fun blocked(cursor: ModerationCursor? = null): ModerationPage<ModerationAccount> = list(ModerationListKind.Blocked, cursor)
    suspend fun muted(cursor: ModerationCursor? = null): ModerationPage<ModerationAccount> = list(ModerationListKind.Muted, cursor)

    suspend fun removeBlocked(entry: ModerationAccount) = remove("blocking/delete", "blockId", entry)
    suspend fun removeMuted(entry: ModerationAccount) = remove("mute/delete", "muteId", entry)

    suspend fun hashtags(cursor: ModerationCursor? = null): ModerationPage<MutedHashtag> {
        // Misskey word mutes include phrases, contexts, and actions; presenting them as
        // hashtag mutes would silently change unrelated server filters.
        throw SourceError.Unsupported("moderation.hashtags")
    }

    private suspend fun list(kind: ModerationListKind, cursor: ModerationCursor?): ModerationPage<ModerationAccount> =
        withContext(Dispatchers.IO) {
            val endpoint = if (kind == ModerationListKind.Blocked) "blocking" else "mute/list"
            val variant = "misskey-${kind.name.lowercase()}-v1"
            val decoded = decodeCursor(cursor, kind, variant)
            val body = JSONObject().put("i", token).put("limit", PAGE_LIMIT)
            decoded?.let { body.put("untilId", it) }
            val values = JSONArray(api.post(origin, endpoint, body).body)
            val items = (0 until values.length()).mapNotNull { index ->
                val entry = values.optJSONObject(index) ?: return@mapNotNull null
                val nested = entry.optJSONObject(if (kind == ModerationListKind.Blocked) "blockee" else "mutee")
                    ?: entry.optJSONObject("user")
                    ?: return@mapNotNull null
                val account = runCatching { MisskeyMapper.account(nested, origin) }.getOrNull() ?: return@mapNotNull null
                ModerationAccount(
                    account = account,
                    relationshipId = entry.optString("id").takeIf(String::isNotBlank),
                    createdAtEpochMillis = entry.optString("createdAt").toEpochMillisOrNull(),
                )
            }
            val relationId = values.optJSONObject(values.length() - 1)?.optString("id")
                ?.takeIf(String::isNotBlank)
            ModerationPage(items, relationId?.let { encodeCursor(kind, variant, it) })
        }

    private suspend fun remove(endpoint: String, parameter: String, entry: ModerationAccount) {
        if (entry.account.id.connection.origin != origin || entry.relationshipId.isNullOrBlank()) {
            throw SourceError.ForeignOrigin("moderation.remove")
        }
        api.post(origin, endpoint, JSONObject().put("i", token).put(parameter, entry.relationshipId))
    }

    private fun decodeCursor(cursor: ModerationCursor?, kind: ModerationListKind, variant: String): String? {
        if (cursor == null) return null
        if (cursor.accountId != accountId || cursor.query.kind != kind || cursor.protocolVariant != variant || cursor.value.isBlank()) {
            throw SourceError.Unsupported("moderation.cursor")
        }
        return cursor.value
    }

    private fun encodeCursor(kind: ModerationListKind, variant: String, value: String): ModerationCursor =
        ModerationCursor(accountId, me.foxtails.palustris.domain.ModerationListQuery(kind), variant, value)

    private companion object { const val PAGE_LIMIT = 40 }
}

private fun String.toEpochMillisOrNull(): Long? = runCatching { java.time.Instant.parse(this).toEpochMilli() }.getOrNull()
