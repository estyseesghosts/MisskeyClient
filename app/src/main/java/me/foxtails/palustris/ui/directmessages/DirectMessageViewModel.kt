package me.foxtails.palustris.ui.directmessages

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import me.foxtails.palustris.data.directmessages.DirectMessageRepository
import me.foxtails.palustris.data.directmessages.DirectMessageStore
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.ConversationId
import me.foxtails.palustris.domain.DirectConversation
import me.foxtails.palustris.domain.DirectMessageRequest
import me.foxtails.palustris.domain.DirectMessageSource
import me.foxtails.palustris.domain.SocialSource
import me.foxtails.palustris.domain.SourceError
import me.foxtails.palustris.ui.sourceErrorMessage

@HiltViewModel(assistedFactory = DirectMessageViewModel.Factory::class)
class DirectMessageViewModel @AssistedInject constructor(
    @Assisted val accountId: AccountId,
    @Assisted source: SocialSource,
    store: DirectMessageStore,
) : ViewModel() {
    private val directSource = source as? DirectMessageSource
    private val repository = directSource?.let { DirectMessageRepository(accountId, it, store) }
    private val _state = MutableStateFlow(DirectMessageUiState())
    val state = _state.asStateFlow()
    private var refreshJob: Job? = null
    private var threadJob: Job? = null

    init {
        val cached = repository?.cachedConversations().orEmpty()
        _state.value = _state.value.copy(conversations = cached)
        refresh()
    }

    fun refresh() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            val repo = repository
            if (repo == null) {
                _state.value = _state.value.copy(error = sourceErrorMessage(SourceError.Unsupported("direct messages")))
                return@launch
            }
            val cached = _state.value.conversations.isNotEmpty()
            _state.value = _state.value.copy(loading = !cached, error = null)
            try {
                val page = repo.conversations()
                val selected = selectedConversation(page.items, _state.value.selectedConversationId)
                _state.value = _state.value.copy(
                    conversations = page.items,
                    selectedConversation = selected,
                    nextCursor = page.nextCursor,
                    loading = false,
                    error = null,
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _state.value = _state.value.copy(loading = false, error = sourceErrorMessage(error))
            }
        }
    }

    fun loadMore() {
        val cursor = _state.value.nextCursor ?: return
        if (_state.value.loadingMore || repository == null) return
        viewModelScope.launch {
            _state.value = _state.value.copy(loadingMore = true, error = null)
            try {
                val page = repository!!.conversations(cursor)
                val combined = (_state.value.conversations + page.items).distinctBy { it.id }
                    .sortedByDescending { it.lastPost.publishedAtEpochMillis }
                _state.value = _state.value.copy(
                    conversations = combined,
                    selectedConversation = selectedConversation(combined, _state.value.selectedConversationId),
                    nextCursor = page.nextCursor,
                    loadingMore = false,
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _state.value = _state.value.copy(loadingMore = false, error = sourceErrorMessage(error))
            }
        }
    }

    fun openConversation(conversation: DirectConversation) {
        val id = conversation.id
        threadJob?.cancel()
        _state.value = _state.value.copy(
            selectedConversationId = id,
            selectedConversation = conversation.copy(unread = false),
            recipient = conversation.participants.firstOrNull { it.id != accountId },
            thread = repository?.cachedThread(id).orEmpty(),
            loadingThread = true,
            error = null,
            conversations = _state.value.conversations.map { item ->
                if (item.id == id) item.copy(unread = false) else item
            },
        )
        val repo = repository ?: return
        threadJob = viewModelScope.launch {
            try {
                val thread = repo.thread(id)
                _state.value = _state.value.copy(thread = thread, loadingThread = false)
                runCatching { repo.markRead(id) }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _state.value = _state.value.copy(loadingThread = false, error = sourceErrorMessage(error))
            }
        }
    }

    fun startConversation(account: Account) {
        threadJob?.cancel()
        _state.value = _state.value.copy(
            selectedConversationId = null,
            selectedConversation = null,
            recipient = account,
            thread = emptyList(),
            loadingThread = false,
            error = null,
        )
    }

    fun closeConversation() {
        threadJob?.cancel()
        _state.value = _state.value.copy(
            selectedConversationId = null,
            selectedConversation = null,
            recipient = null,
            thread = emptyList(),
            loadingThread = false,
            error = null,
        )
    }

    fun send(text: String) {
        val repo = repository ?: return
        val current = _state.value
        val recipients = current.selectedConversation?.participants
            ?.filterNot { it.id == accountId }
            .orEmpty()
            .ifEmpty { listOfNotNull(current.recipient) }
        if (recipients.isEmpty() || text.isBlank() || current.sending) return
        val replyTo = current.thread.lastOrNull()?.id ?: current.selectedConversation?.lastPost?.id
        viewModelScope.launch {
            _state.value = _state.value.copy(sending = true, error = null)
            try {
                val post = repo.send(
                    DirectMessageRequest(recipients.map(Account::id), text.trim(), replyTo),
                    conversationId = current.selectedConversationId,
                    recipientAccounts = recipients,
                )
                val id = current.selectedConversationId ?: ConversationId(accountId.connection.origin, post.id.value)
                val existing = current.selectedConversation
                val conversation = DirectConversation(
                    id = id,
                    participants = (recipients + post.author).distinctBy { it.id },
                    lastPost = post,
                    unread = false,
                    rootPostId = existing?.rootPostId ?: replyTo ?: post.id,
                )
                val conversations = (_state.value.conversations.filterNot { it.id == id } + conversation)
                    .sortedByDescending { it.lastPost.publishedAtEpochMillis }
                _state.value = _state.value.copy(
                    conversations = conversations,
                    selectedConversationId = id,
                    selectedConversation = conversation,
                    recipient = recipients.firstOrNull(),
                    thread = (current.thread + post).distinctBy { it.id },
                    sending = false,
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _state.value = _state.value.copy(sending = false, error = sourceErrorMessage(error))
            }
        }
    }

    private fun selectedConversation(
        conversations: List<DirectConversation>,
        id: ConversationId?,
    ): DirectConversation? = id?.let { selected -> conversations.firstOrNull { it.id == selected } }

    @AssistedFactory
    interface Factory {
        fun create(accountId: AccountId, source: SocialSource): DirectMessageViewModel
    }
}
