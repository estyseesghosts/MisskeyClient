package me.foxtails.palustris.ui.directmessages

import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.ConversationId
import me.foxtails.palustris.domain.DirectConversation
import me.foxtails.palustris.domain.Post

data class DirectMessageUiState(
    val conversations: List<DirectConversation> = emptyList(),
    val selectedConversationId: ConversationId? = null,
    val selectedConversation: DirectConversation? = null,
    val recipient: Account? = null,
    val thread: List<Post> = emptyList(),
    val nextCursor: String? = null,
    val loading: Boolean = false,
    val loadingThread: Boolean = false,
    val loadingMore: Boolean = false,
    val sending: Boolean = false,
    val error: String? = null,
    /** Composer text for the active editor target. The feature owner, not the screen, holds it. */
    val editorText: String = "",
    /** Binds accepted send completion to the text it submitted. A newer edit must survive. */
    val editorRevision: Long = 0L,
)
