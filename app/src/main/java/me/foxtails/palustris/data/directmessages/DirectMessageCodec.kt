package me.foxtails.palustris.data.directmessages

import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.Audience
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.ConversationId
import me.foxtails.palustris.domain.DirectConversation
import me.foxtails.palustris.domain.EntityId
import me.foxtails.palustris.domain.Post
import me.foxtails.palustris.domain.Protocol
import org.json.JSONArray
import org.json.JSONObject

internal object DirectMessageCodec {
    fun encodeConversation(value: DirectConversation): DirectConversationEntity {
        val thread = JSONArray()
        valueThread(value).forEach { thread.put(encodePost(it)) }
        return DirectConversationEntity(
            accountKey = "",
            conversationConnection = value.id.connection,
            conversationId = value.id.value,
            protocol = value.lastPost.author.id.connection.protocol.name,
            rootPostConnection = value.rootPostId?.connection,
            rootPostId = value.rootPostId?.value,
            participantJson = JSONArray(value.participants.map(::encodeAccount)).toString(),
            lastPostJson = encodePost(value.lastPost).toString(),
            threadJson = thread.toString(),
            lastUpdatedEpochMillis = value.lastPost.publishedAtEpochMillis,
            unread = value.unread,
        )
    }

    fun decodeConversation(value: DirectConversationEntity): DirectConversation = DirectConversation(
        id = ConversationId(value.conversationConnection, value.conversationId),
        participants = decodeAccounts(JSONArray(value.participantJson)),
        lastPost = decodePost(JSONObject(value.lastPostJson)),
        unread = value.unread,
        rootPostId = value.rootPostConnection?.let { connection ->
            value.rootPostId?.let { id -> EntityId(connection, id) }
        },
    )

    fun decodeThread(value: DirectConversationEntity): List<Post> = JSONArray(value.threadJson).let { values ->
        (0 until values.length()).mapNotNull { index ->
            runCatching { decodePost(values.getJSONObject(index)) }.getOrNull()
        }
    }

    fun encodePost(value: Post): JSONObject = JSONObject().apply {
        put("id", encodeEntity(value.id))
        put("author", encodeAccount(value.author))
        put("text", value.text)
        put("publishedAt", value.publishedAtEpochMillis)
        put("audience", value.audience.name)
        value.replyTo?.let { put("replyTo", encodeEntity(it)) }
        value.replyToAuthorId?.let { put("replyToAuthor", encodeAccountId(it)) }
        value.url?.let { put("url", it) }
    }

    fun decodePost(value: JSONObject): Post = Post(
        id = decodeEntity(value.getJSONObject("id")),
        author = decodeAccount(value.getJSONObject("author")),
        text = value.optString("text"),
        publishedAtEpochMillis = value.optLong("publishedAt"),
        audience = runCatching { Audience.valueOf(value.optString("audience")) }.getOrDefault(Audience.Direct),
        replyTo = value.optJSONObject("replyTo")?.let(::decodeEntity),
        replyToAuthorId = value.optJSONObject("replyToAuthor")?.let(::decodeAccountId),
        url = value.optString("url").takeIf(String::isNotBlank),
    )

    private fun valueThread(value: DirectConversation): List<Post> = listOf(value.lastPost)

    private fun encodeAccount(value: Account): JSONObject = JSONObject().apply {
        put("id", encodeAccountId(value.id))
        put("displayName", value.displayName)
        put("handle", value.handle)
        value.avatarUrl?.let { put("avatarUrl", it) }
        put("biography", value.biography)
    }

    private fun decodeAccount(value: JSONObject): Account = Account(
        id = decodeAccountId(value.getJSONObject("id")),
        displayName = value.optString("displayName"),
        handle = value.optString("handle"),
        avatarUrl = value.optString("avatarUrl").takeIf(String::isNotBlank),
        biography = value.optString("biography"),
    )

    private fun decodeAccounts(values: JSONArray): List<Account> = (0 until values.length()).mapNotNull { index ->
        runCatching { decodeAccount(values.getJSONObject(index)) }.getOrNull()
    }

    private fun encodeAccountId(value: AccountId): JSONObject = JSONObject()
        .put("origin", value.connection.origin)
        .put("protocol", value.connection.protocol.name)
        .put("localId", value.localId)

    private fun decodeAccountId(value: JSONObject): AccountId = AccountId(
        Connection(
            value.getString("origin"),
            Protocol.valueOf(value.optString("protocol", Protocol.MISSKEY.name)),
        ),
        value.getString("localId"),
    )

    private fun encodeEntity(value: EntityId): JSONObject = JSONObject()
        .put("connection", value.connection)
        .put("value", value.value)

    private fun decodeEntity(value: JSONObject): EntityId = EntityId(
        value.getString("connection"),
        value.getString("value"),
    )
}
