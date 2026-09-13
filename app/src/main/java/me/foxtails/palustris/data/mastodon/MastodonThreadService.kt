package me.foxtails.palustris.data.mastodon

import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.EntityId
import me.foxtails.palustris.domain.Post
import me.foxtails.palustris.domain.ThreadAcquisitionState
import me.foxtails.palustris.domain.ThreadContext
import me.foxtails.palustris.domain.ThreadContinuation
import me.foxtails.palustris.domain.ThreadRefreshHint
import me.foxtails.palustris.domain.ThreadSessionKey
import me.foxtails.palustris.data.misskey.MisskeyApi
import org.json.JSONArray
import org.json.JSONObject

internal class MastodonThreadService(
    private val origin: String,
    private val token: String,
    private val api: MisskeyApi,
    private val accountId: AccountId,
    private val sessionRevision: Long,
) {
    suspend fun context(focalId: EntityId, continuation: ThreadContinuation?): ThreadContext {
        val key = ThreadSessionKey(accountId, sessionRevision, focalId)
        if (continuation != null && continuation.sessionKey != key) {
            throw me.foxtails.palustris.domain.SourceError.Unsupported("thread.continuation")
        }
        val path = "v1/statuses/${focalId.value.encodePathSegment()}"
        val focal = MastodonMapper.post(
            api.get(origin, path, token, MAX_RESPONSE_BYTES).body.toJson(),
            origin,
        )
        val contextResponse = api.get(origin, "$path/context", token, MAX_RESPONSE_BYTES)
        val context = JSONObject(contextResponse.body)
        return ThreadContext(
            focal = focal,
            ancestors = context.optJSONArray("ancestors").toPosts(origin),
            descendants = context.optJSONArray("descendants").toPosts(origin),
            acquisitionState = ThreadAcquisitionState.Finished,
            refreshHint = parseRefreshHint(contextResponse.headers["Mastodon-Async-Refresh"]),
        )
    }

    private companion object {
        const val MAX_RESPONSE_BYTES = 4L * 1024L * 1024L
    }
}

private fun JSONArray?.toPosts(origin: String): List<Post> {
    if (this == null) return emptyList()
    return (0 until length()).mapNotNull { index ->
        runCatching { MastodonMapper.post(getJSONObject(index), origin) }.getOrNull()
    }
}

private fun parseRefreshHint(header: String?): ThreadRefreshHint? {
    val value = header?.trim() ?: return null
    val match = Regex("^id=\"[^\"]+\"\\s*,\\s*retry=(\\d+)\\s*,\\s*result_count=(\\d+)\\s*$")
        .matchEntire(value) ?: return null
    val retrySeconds = match.groupValues[1].toLongOrNull() ?: return null
    return ThreadRefreshHint(retrySeconds * 1_000L)
}

private fun String.encodePathSegment(): String =
    java.net.URLEncoder.encode(this, Charsets.UTF_8.name()).replace("+", "%20")

private fun String.toJson(): JSONObject = JSONObject(this)
