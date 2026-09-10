package me.foxtails.palustris.data.mastodon

import me.foxtails.palustris.data.misskey.MisskeyApi
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.Audience
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.ConversationId
import me.foxtails.palustris.domain.DirectConversation
import me.foxtails.palustris.domain.DirectMessageRequest
import me.foxtails.palustris.domain.EntityId
import me.foxtails.palustris.domain.Page
import me.foxtails.palustris.domain.Post
import me.foxtails.palustris.domain.Protocol
import me.foxtails.palustris.domain.SourceError
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

internal class MastodonDirectMessageService(
    private val origin: String,
    private val token: String,
    private val api: MisskeyApi,
    private val accountId: AccountId,
    private val profileService: MastodonProfileService,
) {
    private val directLastPosts = mutableMapOf<String, Post>()

    suspend fun conversations(cursor: String?): Page<DirectConversation> {
        val response = if (cursor == null) {
            api.getUrl(directConversationsUrl().toString(), token)
        } else {
            api.getUrl(validateDirectConversationsUrl(cursor).toString(), token)
        }
        val values = JSONArray(response.body)
        val items = (0 until values.length()).mapNotNull { index ->
            MastodonMapper.directConversation(values.getJSONObject(index), origin)
                ?.takeIf { it.lastPost.audience == Audience.Direct }
                ?.also { conversation -> directLastPosts[conversation.id.value] = conversation.lastPost }
        }
        return Page(items, response.linkHeaderCursor())
    }

    suspend fun conversationThread(id: ConversationId): List<Post> {
        validateConversationId(id, "direct.thread")
        val lastStatus = directLastPosts[id.value] ?: run {
            val conversation = MastodonMapper.directConversation(
                api.get(origin, "v1/conversations/${id.value.encodePathSegment()}", token).body.toJson(),
                origin,
            ) ?: throw SourceError.ServerError("Mastodon conversation had no last status")
            directLastPosts[id.value] = conversation.lastPost
            if (conversation.lastPost.audience != Audience.Direct) throw SourceError.Unsupported("direct.thread")
            conversation.lastPost
        }
        val context = JSONObject(api.get(
            origin,
            "v1/statuses/${lastStatus.id.value.encodePathSegment()}/context",
            token,
        ).body)
        val ancestors = context.optJSONArray("ancestors").toPostList(origin)
        val descendants = context.optJSONArray("descendants").toPostList(origin)
        return (ancestors + listOf(lastStatus) + descendants)
            .filter { it.audience == Audience.Direct }
            .distinctBy { it.id }
    }

    suspend fun sendDirectMessage(request: DirectMessageRequest): Post {
        validateDirectMessageRequest(request)
        val mentions = request.recipients.map { profileService.profile(it).handle }.distinct().joinToString(" ")
        val status = listOf(mentions, request.text.trim()).filter(String::isNotBlank).joinToString(" ")
        val fields = buildList {
            add("status" to status)
            add("visibility" to "direct")
            request.replyTo?.let { add("in_reply_to_id" to it.value) }
        }
        val post = MastodonMapper.post(api.postForm(origin, "api/v1/statuses", fields, token).body.toJson(), origin)
        directLastPosts[post.id.value] = post
        return post
    }

    suspend fun markConversationRead(id: ConversationId) {
        validateConversationId(id, "direct.read")
        api.postForm(origin, "api/v1/conversations/${id.value.encodePathSegment()}/read", emptyList(), token)
    }

    private fun validateDirectMessageRequest(request: DirectMessageRequest) {
        if (request.recipients.isEmpty() || request.text.isBlank()) throw SourceError.Unsupported("direct.send")
        if (request.recipients.any {
                it.connection != Connection(origin, Protocol.MASTODON) || it.localId.isBlank()
            }
        ) throw SourceError.Unsupported("direct.recipient")
        request.replyTo?.let { if (it.connection != origin || it.value.isBlank()) throw SourceError.Unsupported("direct.reply") }
    }

    private fun validateConversationId(id: ConversationId, feature: String) {
        if (id.connection != origin || id.value.isBlank()) throw SourceError.Unsupported(feature)
    }

    private fun directConversationsUrl(): HttpUrl = origin.toHttpUrl().newBuilder()
        .addPathSegments("api/v1/conversations")
        .addQueryParameter("limit", DIRECT_CONVERSATION_LIMIT.toString())
        .build()

    private fun validateDirectConversationsUrl(cursor: String): HttpUrl {
        val page = cursor.toHttpUrlOrNull() ?: throw SourceError.Unsupported("direct.pagination")
        val authenticatedOrigin = origin.toHttpUrl()
        if (page.scheme != authenticatedOrigin.scheme || page.host != authenticatedOrigin.host ||
            page.port != authenticatedOrigin.port || page.username.isNotEmpty() || page.password.isNotEmpty() ||
            page.fragment != null || page.encodedPath != "/api/v1/conversations"
        ) throw SourceError.Unsupported("direct.pagination")
        return page
    }

    private companion object { const val DIRECT_CONVERSATION_LIMIT = 40 }
}

private fun String.encodePathSegment(): String = URLEncoder.encode(this, Charsets.UTF_8.name()).replace("+", "%20")

private fun String.toJson(): JSONObject = JSONObject(this)

private fun JSONArray?.toPostList(origin: String): List<Post> {
    if (this == null) return emptyList()
    return (0 until length()).mapNotNull { index ->
        runCatching { MastodonMapper.post(getJSONObject(index), origin) }.getOrNull()
    }
}
