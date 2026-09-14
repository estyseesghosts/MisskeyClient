package me.foxtails.palustris.ui.directmessages

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.DirectConversation
import me.foxtails.palustris.domain.SocialSource
import me.foxtails.palustris.ui.shell.DirectMessagesContract

/**
 * Owns the direct-message presentation for one connected session.
 *
 * The inbox, selected conversation, and send actions stay beside the direct-message feature. The
 * shell receives only the narrow [DirectMessagesContract].
 */
@Composable
fun DirectMessagesHost(
    accountId: AccountId,
    sessionGeneration: Long,
    source: SocialSource,
): DirectMessagesContract {
    val model = hiltViewModel<DirectMessageViewModel, DirectMessageViewModel.Factory>(
        key = "direct-messages-$accountId-$sessionGeneration",
        creationCallback = { factory -> factory.create(accountId, source) },
    )
    val state by model.state.collectAsStateWithLifecycle()
    val actions = remember(model) {
        object : DirectMessagesContract.Actions {
            override fun refresh() { model.refresh() }
            override fun loadMore() { model.loadMore() }
            override fun openConversation(conversation: DirectConversation) { model.openConversation(conversation) }
            override fun closeConversation() { model.closeConversation() }
            override fun startConversation(account: Account) { model.startConversation(account) }
            override fun send(text: String) { model.send(text) }
        }
    }
    return remember(state, actions) { DirectMessagesContract(state, actions) }
}
