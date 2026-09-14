package me.foxtails.palustris.ui.shell

import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.DirectConversation
import me.foxtails.palustris.ui.directmessages.DirectMessageUiState

/**
 * Direct-message presentation.
 *
 * The inbox, the selected conversation, and send state share one owner. Notification read state
 * and app navigation are deliberately not part of this contract. [Empty] is an inert preview
 * value.
 */
data class DirectMessagesContract(
    val state: DirectMessageUiState,
    val actions: Actions,
) {
    interface Actions {
        fun refresh()
        fun loadMore()
        fun openConversation(conversation: DirectConversation)
        fun closeConversation()
        fun startConversation(account: Account)
        fun send(text: String)
    }

    companion object {
        val Empty = DirectMessagesContract(DirectMessageUiState(), DirectMessagesEmptyActions)
    }
}

private object DirectMessagesEmptyActions : DirectMessagesContract.Actions {
    override fun refresh() = Unit
    override fun loadMore() = Unit
    override fun openConversation(conversation: DirectConversation) = Unit
    override fun closeConversation() = Unit
    override fun startConversation(account: Account) = Unit
    override fun send(text: String) = Unit
}
