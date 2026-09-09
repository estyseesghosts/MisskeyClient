package me.foxtails.palustris.data.directmessages

import androidx.room.withTransaction
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.ConversationId
import me.foxtails.palustris.domain.DirectConversation
import me.foxtails.palustris.domain.Post
import java.security.MessageDigest

interface DirectMessageStore {
    fun conversations(accountId: AccountId): List<DirectConversation>
    fun conversation(accountId: AccountId, id: ConversationId): DirectConversation?
    fun thread(accountId: AccountId, id: ConversationId): List<Post>
    fun save(accountId: AccountId, value: DirectConversation, thread: List<Post> = emptyList())
    fun markRead(accountId: AccountId, id: ConversationId)
    fun delete(accountId: AccountId)
}

class InMemoryDirectMessageStore : DirectMessageStore {
    private val values = mutableMapOf<AccountId, MutableMap<ConversationId, Pair<DirectConversation, List<Post>>>>()

    override fun conversations(accountId: AccountId): List<DirectConversation> = values[accountId].orEmpty()
        .values.sortedByDescending { it.first.lastPost.publishedAtEpochMillis }.map { it.first }

    override fun conversation(accountId: AccountId, id: ConversationId): DirectConversation? = values[accountId]?.get(id)?.first

    override fun thread(accountId: AccountId, id: ConversationId): List<Post> = values[accountId]?.get(id)?.second.orEmpty()

    override fun save(accountId: AccountId, value: DirectConversation, thread: List<Post>) {
        val accountValues = values.getOrPut(accountId) { mutableMapOf() }
        val existing = accountValues[value.id]
        val storedThread = if (thread.isEmpty()) existing?.second.orEmpty() else thread
        val storedUnread = if (existing?.first?.lastPost?.id == value.lastPost.id) existing.first.unread else value.unread
        accountValues[value.id] = value.copy(unread = storedUnread) to storedThread
    }

    override fun markRead(accountId: AccountId, id: ConversationId) {
        values[accountId]?.get(id)?.let { (conversation, thread) ->
            values.getValue(accountId)[id] = conversation.copy(unread = false) to thread
        }
    }

    override fun delete(accountId: AccountId) {
        values.remove(accountId)
    }
}

@Singleton
class RoomDirectMessageStore @Inject constructor(
    private val database: DirectMessageDatabase,
) : DirectMessageStore {
    private val dao = database.directMessageDao()

    override fun conversations(accountId: AccountId): List<DirectConversation> = runBlocking(Dispatchers.IO) {
        dao.conversations(accountId.key()).mapNotNull { entity ->
            runCatching { DirectMessageCodec.decodeConversation(entity) }.getOrNull()
        }
    }

    override fun conversation(accountId: AccountId, id: ConversationId): DirectConversation? = runBlocking(Dispatchers.IO) {
        dao.conversation(accountId.key(), id.connection, id.value)?.let {
            runCatching { DirectMessageCodec.decodeConversation(it) }.getOrNull()
        }
    }

    override fun thread(accountId: AccountId, id: ConversationId): List<Post> = runBlocking(Dispatchers.IO) {
        dao.conversation(accountId.key(), id.connection, id.value)?.let {
            runCatching { DirectMessageCodec.decodeThread(it) }.getOrDefault(emptyList())
        }.orEmpty()
    }

    override fun save(accountId: AccountId, value: DirectConversation, thread: List<Post>) {
        runBlocking(Dispatchers.IO) {
            val key = accountId.key()
            val previous = dao.conversation(key, value.id.connection, value.id.value)
            val existingThread = previous?.let { runCatching { DirectMessageCodec.decodeThread(it) }.getOrDefault(emptyList()) }
                .orEmpty()
            val storedThread = if (thread.isEmpty()) existingThread else thread
            val storedUnread = if (previous != null && previous.lastPostJson == DirectMessageCodec.encodePost(value.lastPost).toString()) {
                previous.unread
            } else {
                value.unread
            }
            val encoded = DirectMessageCodec.encodeConversation(value.copy(unread = storedUnread))
                .copy(accountKey = key, threadJson = org.json.JSONArray(storedThread.map(DirectMessageCodec::encodePost)).toString())
            dao.save(encoded)
        }
    }

    override fun markRead(accountId: AccountId, id: ConversationId) {
        runBlocking(Dispatchers.IO) { dao.markRead(accountId.key(), id.connection, id.value) }
    }

    override fun delete(accountId: AccountId) {
        runBlocking(Dispatchers.IO) { dao.deleteAccount(accountId.key()) }
    }
}

private fun AccountId.key(): String {
    val bytes = "$connection\u0000$localId".toByteArray(Charsets.UTF_8)
    return MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
