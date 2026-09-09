package me.foxtails.palustris.data.directmessages

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.ConversationId
import me.foxtails.palustris.domain.DirectConversation
import me.foxtails.palustris.domain.DirectMessageRequest
import me.foxtails.palustris.domain.DirectMessageSource
import me.foxtails.palustris.domain.EntityId
import me.foxtails.palustris.domain.Page
import me.foxtails.palustris.domain.Post

/** Merges adapter results with account-scoped conversation state. */
class DirectMessageRepository(
    private val accountId: AccountId,
    private val source: DirectMessageSource,
    private val store: DirectMessageStore,
) {
    suspend fun conversations(cursor: String? = null): Page<DirectConversation> = withContext(Dispatchers.IO) {
        val remote = source.conversations(cursor)
        val cached = store.conversations(accountId)
        val byId = linkedMapOf<ConversationId, DirectConversation>()
        if (cursor == null) cached.forEach { byId[it.id] = it }
        remote.items.forEach { incoming ->
            val previous = byId[incoming.id]
            val merged = if (previous?.lastPost?.id == incoming.lastPost.id) {
                incoming.copy(unread = previous.unread)
            } else {
                incoming
            }
            byId[incoming.id] = merged
            store.save(accountId, merged)
        }
        Page(
            items = byId.values.sortedByDescending { it.lastPost.publishedAtEpochMillis },
            nextCursor = remote.nextCursor,
        )
    }

    suspend fun thread(id: ConversationId): List<Post> = withContext(Dispatchers.IO) {
        val remote = source.conversationThread(id)
        if (remote.isNotEmpty()) {
            store.conversation(accountId, id)?.let { current ->
                store.save(accountId, current.copy(lastPost = remote.last()), remote)
            }
            remote
        } else {
            store.thread(accountId, id)
        }
    }

    suspend fun send(
        request: DirectMessageRequest,
        conversationId: ConversationId? = null,
        recipientAccounts: List<Account> = emptyList(),
    ): Post = withContext(Dispatchers.IO) {
        val post = source.sendDirectMessage(request)
        val id = conversationId ?: ConversationId(accountId.connection.origin, post.id.value)
        val previous = store.conversation(accountId, id)
        val root = previous?.rootPostId ?: request.replyTo ?: post.id
        val participants = (previous?.participants.orEmpty() + recipientAccounts + post.author)
            .distinctBy(Account::id)
        val conversation = DirectConversation(
            id = id,
            participants = participants,
            lastPost = post,
            unread = false,
            rootPostId = root,
        )
        val thread = (store.thread(accountId, id) + post).distinctBy { it.id }
        store.save(accountId, conversation, thread)
        post
    }

    suspend fun markRead(id: ConversationId) = withContext(Dispatchers.IO) {
        source.markConversationRead(id)
        store.markRead(accountId, id)
    }

    fun cachedConversations(): List<DirectConversation> = store.conversations(accountId)
    fun cachedThread(id: ConversationId): List<Post> = store.thread(accountId, id)
    fun deleteAccount() = store.delete(accountId)
}
