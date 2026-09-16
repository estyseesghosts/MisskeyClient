package me.foxtails.palustris.domain

/** Protocol-specific transport boundary for federated private posts. */
interface DirectMessageSource {
    suspend fun conversations(cursor: String? = null): Page<DirectConversation>

    suspend fun conversationThread(request: DirectThreadRequest): List<Post>

    suspend fun sendDirectMessage(request: DirectMessageRequest): Post

    suspend fun markConversationRead(id: ConversationId)
}
