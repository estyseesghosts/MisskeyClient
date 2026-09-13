package me.foxtails.palustris.data.mastodon

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONArray
import me.foxtails.palustris.data.misskey.MisskeyApi
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.ModerationAccount
import me.foxtails.palustris.domain.ModerationCursor
import me.foxtails.palustris.domain.ModerationListKind
import me.foxtails.palustris.domain.ModerationPage
import me.foxtails.palustris.domain.MutedHashtag
import me.foxtails.palustris.domain.ProfileRelationship
import me.foxtails.palustris.domain.ReportRequest
import me.foxtails.palustris.domain.SourceError
import org.json.JSONObject

class MastodonModerationService(
    private val origin: String,
    private val token: String,
    private val api: MisskeyApi,
    private val accountId: AccountId,
) {
    suspend fun blocked(cursor: ModerationCursor? = null): ModerationPage<ModerationAccount> = list(ModerationListKind.Blocked, cursor)
    suspend fun muted(cursor: ModerationCursor? = null): ModerationPage<ModerationAccount> = list(ModerationListKind.Muted, cursor)
    suspend fun hashtags(cursor: ModerationCursor? = null): ModerationPage<MutedHashtag> =
        throw SourceError.Unsupported("moderation.hashtags")

    suspend fun removeBlocked(entry: ModerationAccount) = remove("block", entry.account.id)
    suspend fun removeMuted(entry: ModerationAccount) = remove("mute", entry.account.id)

    suspend fun setBlocked(target: AccountId, blocked: Boolean): ProfileRelationship {
        validateTarget(target, "profile.block")
        return relationship(target, if (blocked) "block" else "unblock")
    }

    suspend fun setMuted(target: AccountId, muted: Boolean): ProfileRelationship {
        validateTarget(target, "profile.mute")
        return relationship(target, if (muted) "mute" else "unmute")
    }

    suspend fun report(request: ReportRequest) {
        validateOrigin(request.targetAccountId.connection.origin, "moderation.report")
        request.postId?.let { validateOrigin(it.connection, "moderation.report") }
        val fields = buildList {
            add("account_id" to request.targetAccountId.localId)
            request.postId?.let { add("status_ids[]" to it.value) }
            if (request.comment.isNotBlank()) add("comment" to request.comment)
        }
        api.postForm(origin, "api/v1/reports", fields, token)
    }

    private suspend fun list(kind: ModerationListKind, cursor: ModerationCursor?): ModerationPage<ModerationAccount> {
        val variant = "mastodon-${kind.name.lowercase()}-v1"
        val url = cursor?.let { decodeCursor(it, kind, variant) }
        val endpoint = "v1/accounts/${if (kind == ModerationListKind.Blocked) "blocked" else "muted"}?limit=40"
        val response = if (url == null) api.get(origin, endpoint, token) else api.getUrl(url.toString(), token)
        val values = JSONArray(response.body)
        val items = (0 until values.length()).mapNotNull { index ->
            val account = runCatching { MastodonMapper.account(values.getJSONObject(index), origin) }.getOrNull() ?: return@mapNotNull null
            ModerationAccount(account = account, relationshipId = account.id.localId)
        }
        val next = response.linkHeaderCursor()?.let { encodeCursor(kind, variant, it) }
        return ModerationPage(items, next)
    }

    private suspend fun remove(action: String, target: AccountId) {
        validateOrigin(target.connection.origin, "moderation.remove")
        api.delete(origin, "api/v1/accounts/${target.localId}/$action", token)
    }

    private suspend fun relationship(target: AccountId, action: String): ProfileRelationship {
        validateTarget(target, "profile.$action")
        val response = if (action == "block" || action == "mute") {
            api.postForm(origin, "api/v1/accounts/${target.localId}/$action", emptyList(), token)
        } else {
            api.delete(origin, "api/v1/accounts/${target.localId}/$action", token)
        }
        return runCatching { parseRelationship(response.body, target) }
            .getOrElse { profileRelationship(target) }
    }

    private suspend fun profileRelationship(target: AccountId): ProfileRelationship {
        val response = api.get(
            origin,
            "api/v1/accounts/relationships?id[]=${target.localId}",
            token,
        )
        return parseRelationship(response.body, target)
    }

    private fun parseRelationship(body: String, profileId: AccountId): ProfileRelationship {
        val root = body.trimStart()
        val json = if (root.startsWith("[")) JSONArray(body).optJSONObject(0) else JSONObject(body)
        if (json == null || (!json.has("following") && !json.has("requested") && !json.has("followed_by") &&
                !json.has("muting") && !json.has("blocking"))) {
            throw SourceError.Unsupported("profile.relationship")
        }
        return MastodonMapper.relationship(json, profileId)
    }

    private fun validateTarget(target: AccountId, feature: String) {
        if (target.connection.origin != origin) throw SourceError.ForeignOrigin(feature)
        if (target.localId.isBlank()) throw SourceError.Unsupported(feature)
    }

    private fun validateOrigin(targetOrigin: String, feature: String) {
        if (targetOrigin != origin) throw SourceError.ForeignOrigin(feature)
    }

    private fun decodeCursor(cursor: ModerationCursor, kind: ModerationListKind, variant: String): HttpUrl {
        if (cursor.accountId != accountId || cursor.query.kind != kind || cursor.protocolVariant != variant) {
            throw SourceError.Unsupported("moderation.cursor")
        }
        val page = cursor.value.toHttpUrlOrNull() ?: throw SourceError.Unsupported("moderation.cursor")
        val authenticated = origin.toHttpUrl()
        if (page.scheme != authenticated.scheme || page.host != authenticated.host || page.port != authenticated.port ||
            page.username.isNotEmpty() || page.password.isNotEmpty() || page.fragment != null
        ) throw SourceError.ForeignOrigin("moderation.cursor")
        return page
    }

    private fun encodeCursor(kind: ModerationListKind, variant: String, value: String) =
        ModerationCursor(accountId, me.foxtails.palustris.domain.ModerationListQuery(kind), variant, value)
}
