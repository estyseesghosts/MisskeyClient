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
import me.foxtails.palustris.domain.DirectThreadRequest
import me.foxtails.palustris.domain.EntityId
import me.foxtails.palustris.domain.Page
import me.foxtails.palustris.domain.Post
import me.foxtails.palustris.domain.SourceError

/** Thrown when a revoked writer reaches the storage boundary. Surfaces as cancellation. */
private class StaleDirectMessageWriter : CancellationException("Direct-message writer is stale")

/** Merges adapter results with account-scoped conversation state. */
class DirectMessageRepository(
    private val accountId: AccountId,
    private val source: DirectMessageSource,
    private val store: DirectMessageStore,
    private val writeGeneration: Long,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    // Required, not defaulted. The account lifecycle owner invalidates one
    // authority per account. A private instance would accept revoked writes.
    private val authority: DirectMessageWriteAuthority,
) {
    suspend fun conversations(cursor: String? = null): Page<DirectConversation> = withContext(ioDispatcher) {
        // Capture the local previews before the request. A write accepted while the request is in
        // flight is newer than the response and must survive the merge.
        val beforeLastPost = store.conversations(accountId).associate { it.id to it.lastPost.id }
        val remote = source.conversations(cursor)
        authority.commitIfCurrent(accountId, writeGeneration) {
            val items = mutableListOf<DirectConversation>()
            val remoteIds = mutableSetOf<ConversationId>()
            remote.items.forEach { incoming ->
                remoteIds += incoming.id
                val stored = store.conversation(accountId, incoming.id)
                val merged = when {
                    // A local write accepted during the request outranks a stale response.
                    stored != null && beforeLastPost[stored.id] != stored.lastPost.id -> stored
                    // Preserve local read state for the same last post on every page.
                    stored?.lastPost?.id == incoming.lastPost.id -> incoming.copy(unread = stored.unread)
                    else -> incoming
                }
                store.save(accountId, merged)
                items += merged
            }
            if (cursor == null) {
                // Keep cached conversations that the first page omitted. Do not sort.
                store.conversations(accountId).forEach { cached ->
                    if (cached.id !in remoteIds) items += cached
                }
            }
            // Keep adapter order. Do not sort opaque identifiers or infer chronology.
            Page(items = items, nextCursor = remote.nextCursor)
        } ?: throw StaleDirectMessageWriter()
    }

    suspend fun thread(id: ConversationId): List<Post> = withContext(ioDispatcher) {
        // Capture the local preview before the request. A send accepted during the request is
        // newer than the response and must win the preview merge.
        val beforeLastPostId = store.conversation(accountId, id)?.lastPost?.id
        // The anchor is a known post in the conversation. Without persisted conversation state
        // there is no identity to load, so the result is a normalized unsupported error instead
        // of a guessed conversation lookup.
        val anchor = beforeLastPostId ?: throw SourceError.Unsupported("direct.thread")
        val remote = source.conversationThread(DirectThreadRequest(id, anchor))
        authority.commitIfCurrent(accountId, writeGeneration) {
            val current = store.conversation(accountId, id) ?: return@commitIfCurrent remote
            val storedThread = store.thread(accountId, id)
            // Merge with the stored thread so a late response cannot drop a cached post. Adapter
            // order stays first. Absence from the response is not proof of chronology.
            val merged = (remote + storedThread).distinctBy { it.id }
            val localSendDuringRequest = current.lastPost.id != beforeLastPostId
            val lastPost = when {
                localSendDuringRequest -> current.lastPost
                remote.isNotEmpty() -> remote.last()
                else -> current.lastPost
            }
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
        // Route the local write through the same serialized boundary as every other write.
        authority.commitIfCurrent(accountId, writeGeneration) {
            store.markRead(accountId, id)
        }
    }

    fun cachedConversations(): List<DirectConversation> = store.conversations(accountId)
    fun cachedThread(id: ConversationId): List<Post> = store.thread(accountId, id)
}
