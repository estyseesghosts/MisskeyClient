package me.foxtails.palustris.data.misskey

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.ModerationAccount
import me.foxtails.palustris.domain.ModerationCursor
import me.foxtails.palustris.domain.ModerationListKind
import me.foxtails.palustris.domain.ModerationPage
import me.foxtails.palustris.domain.MutedHashtag
import me.foxtails.palustris.domain.ProfileRelationship
import me.foxtails.palustris.domain.Protocol
import me.foxtails.palustris.domain.ReportRequest
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

    suspend fun setBlocked(target: AccountId, blocked: Boolean): ProfileRelationship {
        validateTarget(target, "profile.block")
        if (blocked) {
            api.post(origin, "blocking/create", JSONObject().put("i", token).put("userId", target.localId))
        } else {
            deleteByAccount("blocking", target)
        }
        return relationship(target)
    }

    suspend fun setMuted(target: AccountId, muted: Boolean): ProfileRelationship {
        validateTarget(target, "profile.mute")
        if (muted) {
            api.post(origin, "mute/create", JSONObject().put("i", token).put("userId", target.localId))
        } else {
            deleteByAccount("mute", target)
        }
        return relationship(target)
    }

    suspend fun report(request: ReportRequest) {
        if (request.targetAccountId.connection.origin != origin) throw SourceError.ForeignOrigin("moderation.report")
        if (request.postId != null) throw SourceError.Unsupported("moderation.report.post")
        withContext(Dispatchers.IO) {
            api.post(
                origin,
                "users/report",
                JSONObject()
                    .put("i", token)
                    .put("userId", request.targetAccountId.localId)
                    .put("comment", request.comment),
            )
        }
    }

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

    private suspend fun deleteByAccount(kind: String, target: AccountId) {
        val response = api.post(
            origin,
            if (kind == "blocking") "blocking" else "mute/list",
            JSONObject().put("i", token).put("userId", target.localId).put("limit", PAGE_LIMIT),
        )
        val values = JSONArray(response.body)
        val relationId = (0 until values.length()).asSequence()
            .mapNotNull { values.optJSONObject(it) }
            .firstOrNull { entry ->
                val nested = entry.optJSONObject(if (kind == "blocking") "blockee" else "mutee")
                    ?: entry.optJSONObject("user")
                nested?.optString("id") == target.localId
            }
            ?.optString("id")
            ?.takeIf(String::isNotBlank)
            ?: throw SourceError.Unsupported("profile.${if (kind == "blocking") "unblock" else "unmute"}")
        api.post(
            origin,
            if (kind == "blocking") "blocking/delete" else "mute/delete",
            JSONObject().put("i", token).put(if (kind == "blocking") "blockId" else "muteId", relationId),
        )
    }

    private suspend fun relationship(target: AccountId): ProfileRelationship {
        val response = api.post(
            origin,
            "users/relation",
            JSONObject().put("i", token).put("userId", target.localId),
        )
        return MisskeyMapper.relationship(JSONObject(response.body), target)
    }

    private fun validateTarget(target: AccountId, feature: String) {
        if (target.connection.origin != origin || target.localId.isBlank() || target.connection.protocol != Protocol.MISSKEY) {
            throw SourceError.ForeignOrigin(feature)
        }
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
