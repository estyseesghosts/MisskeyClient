package me.foxtails.palustris.data.misskey

import java.util.Base64
import me.foxtails.palustris.domain.Account
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
import org.json.JSONArray
import org.json.JSONObject

internal class MisskeyDirectMessageService(
    private val origin: String,
    private val token: String,
    private val api: MisskeyApi,
    private val accountId: AccountId?,
    private val postLoader: suspend (EntityId) -> Post,
) {
    suspend fun conversations(cursor: String?): Page<DirectConversation> {
        val account = requireAccountId()
        val untilId = cursor?.let { decodeCursor(it, account) }
        val body = JSONObject().put("i", token).put("limit", DIRECT_PAGE_LIMIT).put("markAsRead", false)
        untilId?.let { body.put("untilId", it) }
        val mentionedNotes = fetchMentionedNotes(body)
        val sentNotes = JSONArray(api.post(origin, "users/notes", JSONObject(body.toString()).put("userId", account.localId).put("includeReplies", true)).body)
        val posts = (mentionedNotes + directPosts(sentNotes)).distinctBy { it.id }
        val byId = posts.associateBy { it.id }
        val items = posts.groupBy { rootFor(it, byId) }.map { (root, thread) ->
            val latest = thread.maxByOrNull(Post::publishedAtEpochMillis) ?: thread.first()
            DirectConversation(ConversationId(origin, root.value), (thread.map(Post::author) + localAccount(account)).distinctBy { it.id }, latest, latest.author.id != account, root)
        }.sortedByDescending { it.lastPost.publishedAtEpochMillis }
        return Page(items, posts.minByOrNull(Post::publishedAtEpochMillis)?.id?.value?.let { encodeCursor(account, it) })
    }

    suspend fun conversationThread(id: ConversationId): List<Post> {
        validateConversationId(id, "direct.thread")
        return postLoader(EntityId(origin, id.value)).let { root ->
            val ancestors = mutableListOf<Post>(); val visited = mutableSetOf(root.id); var current = root
            while (current.replyTo != null && visited.add(current.replyTo!!)) {
                val parent = postLoader(current.replyTo!!); ancestors += parent; current = parent
            }
            ancestors.reverse()
            val children = JSONArray(api.post(origin, "notes/children", JSONObject().put("i", token).put("noteId", root.id.value).put("limit", 30)).body)
            val descendants = (0 until children.length()).map { MisskeyMapper.post(children.getJSONObject(it), origin) }
            (ancestors + root + descendants).distinctBy { it.id }.filter { it.audience == Audience.Direct }
        }
    }

    suspend fun send(message: DirectMessageRequest): Post {
        validateDirectMessageRequest(message)
        val body = JSONObject().put("i", token).put("text", message.text.trim()).put("visibility", "specified")
            .put("visibleUserIds", JSONArray(message.recipients.map(AccountId::localId)))
        message.replyTo?.let { body.put("replyId", it.value) }
        return MisskeyMapper.post(JSONObject(api.post(origin, "notes/create", body).body).getJSONObject("createdNote"), origin)
    }

    suspend fun markConversationRead(id: ConversationId) {
        validateConversationId(id, "direct.read")
    }

    private suspend fun fetchMentionedNotes(body: JSONObject): List<Post> {
        val response = try { api.post(origin, "notes/mentions", body) } catch (error: ApiFailure) {
            if (error.status != 404) throw error
            api.post(origin, "i/notifications", JSONObject(body.toString()).put("includeTypes", JSONArray(listOf("mention", "reply"))))
        }
        val values = JSONArray(response.body)
        val notes = if (response.body.trimStart().startsWith("[")) (0 until values.length()).mapNotNull {
            val value = values.optJSONObject(it) ?: return@mapNotNull null; value.optJSONObject("note") ?: value
        } else emptyList()
        return notes.mapNotNull { runCatching { MisskeyMapper.post(it, origin) }.getOrNull()?.takeIf { post -> post.audience == Audience.Direct } }
    }
    private fun directPosts(values: JSONArray) = (0 until values.length()).mapNotNull {
        val note = values.optJSONObject(it) ?: return@mapNotNull null
        runCatching { MisskeyMapper.post(note, origin) }.getOrNull()?.takeIf { post -> post.audience == Audience.Direct }
    }
    private fun rootFor(post: Post, posts: Map<EntityId, Post>): EntityId {
        var current = post; val visited = mutableSetOf<EntityId>()
        while (current.replyTo != null && visited.add(current.id)) current = posts[current.replyTo] ?: break
        return current.id
    }
    private fun localAccount(account: AccountId): Account {
        val host = java.net.URI(origin).host.orEmpty(); return Account(account, account.localId, "@${account.localId}@$host")
    }
    private fun validateDirectMessageRequest(message: DirectMessageRequest) {
        if (message.recipients.isEmpty() || message.text.isBlank()) throw SourceError.Unsupported("direct.send")
        if (message.recipients.any { it.connection != Connection(origin, Protocol.MISSKEY) || it.localId.isBlank() }) throw SourceError.Unsupported("direct.recipient")
        message.replyTo?.let { if (it.connection != origin || it.value.isBlank()) throw SourceError.Unsupported("direct.reply") }
    }
    private fun validateConversationId(id: ConversationId, feature: String) { if (id.connection != origin || id.value.isBlank()) throw SourceError.Unsupported(feature) }
    private fun requireAccountId() = accountId ?: throw SourceError.Unsupported("notifications.account")
    private fun encodeCursor(account: AccountId, untilId: String) = Base64.getUrlEncoder().withoutPadding().encodeToString(JSONObject().put("origin", account.connection.origin).put("account", account.localId).put("untilId", untilId).toString().toByteArray(Charsets.UTF_8))
    private fun decodeCursor(cursor: String, account: AccountId): String {
        val json = runCatching { JSONObject(String(Base64.getUrlDecoder().decode(cursor), Charsets.UTF_8)) }.getOrElse { throw SourceError.Unsupported("direct.pagination") }
        if (json.optString("origin") != account.connection.origin || json.optString("account") != account.localId) throw SourceError.Unsupported("direct.pagination")
        return json.optString("untilId").takeIf(String::isNotBlank) ?: throw SourceError.Unsupported("direct.pagination")
    }
    private companion object { const val DIRECT_PAGE_LIMIT = 30 }
}
