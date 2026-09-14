package me.foxtails.palustris.data.directmessages

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.foxtails.palustris.di.IoDispatcher
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.ConversationId
import me.foxtails.palustris.domain.DirectConversation
import me.foxtails.palustris.domain.DirectMessageRequest
import me.foxtails.palustris.domain.DirectMessageSource
import me.foxtails.palustris.domain.EntityId
import me.foxtails.palustris.domain.Page
import me.foxtails.palustris.domain.Post

/** Thrown when a revoked writer reaches the storage boundary. Surfaces as cancellation. */
private class StaleDirectMessageWriter : CancellationException("Direct-message writer is stale")

/** Merges adapter results with account-scoped conversation state. */
class DirectMessageRepository(
    private val accountId: AccountId,
    private val source: DirectMessageSource,
    private val store: DirectMessageStore,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val authority: DirectMessageWriteAuthority = DirectMessageWriteAuthority(),
) {
    /** Writer generation captured at activation. Removal and replacement revoke it. */
    private val writeGeneration: Long = authority.issue(accountId)

    suspend fun conversations(cursor: String? = null): Page<DirectConversation> = withContext(ioDispatcher) {
        val remote = source.conversations(cursor)
        authority.commitIfCurrent(accountId, writeGeneration) {
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
        } ?: throw StaleDirectMessageWriter()
    }

    suspend fun thread(id: ConversationId): List<Post> = withContext(ioDispatcher) {
        val remote = source.conversationThread(id)
        authority.commitIfCurrent(accountId, writeGeneration) {
            if (remote.isEmpty()) return@commitIfCurrent store.thread(accountId, id)
            val current = store.conversation(accountId, id) ?: return@commitIfCurrent remote
            // Merge with the stored thread so a late response cannot drop a newer sent
            // message. Opaque IDs carry no order: when the store already holds posts the
            // response never saw, the stored preview is newer and must win.
            val storedThread = store.thread(accountId, id)
            val storedExtras = storedThread.filter { stored -> remote.none { it.id == stored.id } }
            val merged = (remote + storedExtras).distinctBy { it.id }
            val lastPost = if (storedExtras.isNotEmpty()) current.lastPost else remote.last()
            store.save(accountId, current.copy(lastPost = lastPost), merged)
            merged
        } ?: throw StaleDirectMessageWriter()
    }

    suspend fun send(
        request: DirectMessageRequest,
        conversationId: ConversationId? = null,
        recipientAccounts: List<Account> = emptyList(),
    ): Post = withContext(ioDispatcher) {
        val post = source.sendDirectMessage(request)
        authority.commitIfCurrent(accountId, writeGeneration) {
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
        } ?: throw StaleDirectMessageWriter()
    }

    suspend fun markRead(id: ConversationId) {
        withContext(ioDispatcher) { source.markConversationRead(id) }
        // A single null-safe store call needs no lock. Stale writers skip the write.
        if (authority.isCurrent(accountId, writeGeneration)) {
            store.markRead(accountId, id)
        }
    }

    fun cachedConversations(): List<DirectConversation> = store.conversations(accountId)
    fun cachedThread(id: ConversationId): List<Post> = store.thread(accountId, id)
}
