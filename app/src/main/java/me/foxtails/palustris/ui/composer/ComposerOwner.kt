package me.foxtails.palustris.ui.composer

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.Audience
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.CreatePostRequest
import me.foxtails.palustris.domain.EntityId
import me.foxtails.palustris.domain.OwnedPost
import me.foxtails.palustris.domain.Post
import me.foxtails.palustris.domain.PostDraft
import me.foxtails.palustris.domain.PostDraftQuotePreview
import me.foxtails.palustris.domain.PostingVisibilityPolicy
import me.foxtails.palustris.domain.ServerCapabilities
import me.foxtails.palustris.ui.shell.ComposerContract
import me.foxtails.palustris.ui.shell.DraftsContract
import java.util.UUID

/** A composer transition that the shell applies by placing the composer overlay. */
sealed interface ComposerNavigation {
    data object New : ComposerNavigation
    data class Reply(val target: OwnedPost) : ComposerNavigation
    data class Quote(val target: OwnedPost) : ComposerNavigation
    data class Draft(val draftId: String) : ComposerNavigation
}

/** Inputs the composer owner reads from the connected shell on each recomposition. */
data class ComposerOwnerContext(
    val account: Account? = null,
    val contract: ComposerContract = ComposerContract.Empty,
    val canReply: Boolean = false,
    val canQuote: Boolean = false,
    val composerOpen: Boolean = false,
    val overlayOpen: Boolean = false,
) {
    val anyOverlayOpen: Boolean get() = composerOpen || overlayOpen
}

/**
 * Owns the composer editor for one connected account.
 *
 * The shell requests transitions and renders the editor. It does not hold editor fields, draft
 * identity, or reply and quote restoration. The owner validates a target against the current
 * account before it publishes a navigation request. A session replacement invalidates restored
 * targets.
 */
@Stable
class ComposerOwner internal constructor(
    private val editorState: MutableState<ComposerEditorState>,
) {
    internal var context: ComposerOwnerContext = ComposerOwnerContext()
    internal var draftsContract: DraftsContract = DraftsContract.Empty

    var drafts by mutableStateOf<List<PostDraft>>(emptyList())
        private set
    var navigation by mutableStateOf<ComposerNavigation?>(null)
        private set
    var closing by mutableStateOf(false)
        private set

    private var quoteOf by mutableStateOf<EntityId?>(null)
    private var replyTo by mutableStateOf<EntityId?>(null)
    private var savedQuoteOf by mutableStateOf<String?>(null)
    private var savedReplyTo by mutableStateOf<String?>(null)
    private var target by mutableStateOf<OwnedPost?>(null)

    val editor: ComposerEditorState get() = editorState.value
    val quoteTarget: OwnedPost? get() = target
    val isReply: Boolean get() = replyTo != null

    val hasChanges: Boolean
        get() = editor.text != editor.savedText ||
            (if (editor.warningEnabled) editor.warning else "") != editor.savedWarning ||
            quoteOf?.value != savedQuoteOf ||
            replyTo?.value != savedReplyTo ||
            editor.audience != editor.savedAudience

    val canPublish: Boolean
        get() = context.contract.canPublish &&
            (editor.draftId == null || drafts.firstOrNull { it.id == editor.draftId }?.accountId == context.account?.id)

    fun setText(text: String) { editorState.value = editor.copy(text = text) }
    fun setWarning(text: String) { editorState.value = editor.copy(warning = text) }
    fun setWarningEnabled(enabled: Boolean) { editorState.value = editor.copy(warningEnabled = enabled) }
    fun setAudience(audience: Audience) { editorState.value = editor.copy(audience = audience) }
    fun removeTargets() {
        target = null
        quoteOf = null
        replyTo = null
    }

    fun consumeNavigation() { navigation = null }

    fun refreshDrafts() {
        draftsContract.actions.load(context.account?.id) { result -> drafts = result }
    }

    fun deleteDraft(item: PostDraft) {
        draftsContract.actions.delete(context.account?.id, item.id) { refreshDrafts() }
    }

    /** Clears restored reply and quote targets after a session replacement. */
    fun resetTargets() {
        target = null
        quoteOf = null
        replyTo = null
        savedQuoteOf = null
        savedReplyTo = null
    }

    fun requestNew() {
        if (context.composerOpen) return
        val first = drafts.firstOrNull()
        if (editor.text.isBlank() && editor.savedText.isBlank() && first != null) {
            requestDraft(first)
            return
        }
        val audience = runCatching {
            PostingVisibilityPolicy.forNewPost(
                context.contract.postPreferences,
                ServerCapabilities(audiences = context.contract.availableAudiences),
            )
        }.getOrDefault(context.contract.postPreferences.defaultAudience)
        editorState.value = editor.copy(audience = audience, savedAudience = audience)
        navigation = ComposerNavigation.New
    }

    fun requestReply(post: OwnedPost) {
        val account = context.account ?: return
        if (post.fetchedBy != account.id || !context.canReply) return
        if (context.anyOverlayOpen || hasChanges) return
        val audience = replyAudience(post)
        resetForTarget()
        editorState.value = editor.copy(audience = audience, savedAudience = audience, error = null)
        target = post
        replyTo = post.post.actionTargetId ?: post.post.id
        navigation = ComposerNavigation.Reply(post)
    }

    fun requestQuote(post: OwnedPost) {
        val account = context.account ?: return
        if (post.fetchedBy != account.id || !context.canQuote) return
        if (context.anyOverlayOpen || hasChanges) return
        val audience = replyAudience(post)
        resetForTarget()
        editorState.value = editor.copy(audience = audience, savedAudience = audience, error = null)
        target = post
        quoteOf = post.post.id
        navigation = ComposerNavigation.Quote(post)
    }

    fun requestDraft(item: PostDraft) {
        resetForTarget()
        editorState.value = ComposerEditorState(
            draftId = item.id,
            text = item.text,
            savedText = item.text,
            warning = item.contentWarning.orEmpty(),
            savedWarning = item.contentWarning.orEmpty(),
            warningEnabled = !item.contentWarning.isNullOrBlank(),
            audience = item.audience,
            savedAudience = item.audience,
        )
        val account = context.account
        quoteOf = item.quoteOf?.takeIf { it.connection == account?.id?.connection?.origin }
        replyTo = item.replyTo?.takeIf { it.connection == account?.id?.connection?.origin }
        target = draftTarget(item)
        savedQuoteOf = quoteOf?.value
        savedReplyTo = replyTo?.value
        navigation = ComposerNavigation.Draft(item.id)
    }

    /** Saves the current editor as a draft. Clears the dirty baseline only after success. */
    fun save(onSaved: () -> Unit = {}) {
        val current = editor
        if (current.text.isBlank() && current.warning.isBlank() && quoteOf == null && replyTo == null) {
            onSaved()
            return
        }
        closing = true
        draftsContract.actions.save(
            draftValue(),
            onResult = { item ->
                editorState.value = editor.copy(
                    draftId = item.id,
                    savedText = item.text,
                    savedWarning = item.contentWarning.orEmpty(),
                    savedAudience = item.audience,
                    error = null,
                )
                savedQuoteOf = item.quoteOf?.value
                savedReplyTo = item.replyTo?.value
                refreshDrafts()
                closing = false
                onSaved()
            },
            onError = {
                editorState.value = editor.copy(error = "Draft could not be saved. Keep editing and try again.")
                closing = false
            },
        )
    }

    /**
     * Saves the draft baseline, then publishes. Successes clear the editor. A saved-draft failure
     * keeps the editor open for retry. A rejected audience surfaces a validation error.
     */
    fun publish(onSent: (replySent: Boolean, quoteSent: Boolean) -> Unit) {
        val account = context.account ?: return
        val submittedText = editor.text
        val submittedWarning = editor.warning.takeIf { editor.warningEnabled && it.isNotBlank() }
        val submittedQuote = quoteOf?.takeIf { it.connection == account.id.connection.origin }
        val submittedReply = replyTo?.takeIf { it.connection == account.id.connection.origin }
        val knownAudiences = context.contract.availableAudiences
        val submittedAudience = if (knownAudiences.isEmpty()) {
            editor.audience
        } else {
            runCatching {
                PostingVisibilityPolicy.validateExplicit(
                    editor.audience,
                    ServerCapabilities(audiences = knownAudiences),
                )
            }.getOrNull()
        }
        if (submittedAudience == null) {
            editorState.value = editor.copy(error = "This audience is not available on this server.")
            return
        }
        val publishingAccountId = account.id
        draftsContract.actions.save(
            draftValue(),
            onResult = { saved ->
                editorState.value = editor.copy(
                    draftId = saved.id,
                    savedText = saved.text,
                    savedWarning = saved.contentWarning.orEmpty(),
                )
                context.contract.actions.publish(
                    CreatePostRequest(
                        submittedText,
                        audience = submittedAudience,
                        contentWarning = submittedWarning,
                        replyTo = submittedReply,
                        quoteOf = submittedQuote,
                    ),
                ) {
                    draftsContract.actions.delete(publishingAccountId, saved.id) { refreshDrafts() }
                    onSent(submittedReply != null, submittedQuote != null)
                    clearAfterPublish()
                }
            },
            onError = {
                editorState.value = editor.copy(error = "Draft could not be saved. Keep the composer open and try again.")
            },
        )
    }

    private fun resetForTarget() {
        editorState.value = ComposerEditorState()
        target = null
        quoteOf = null
        replyTo = null
        savedQuoteOf = null
        savedReplyTo = null
    }

    private fun clearAfterPublish() {
        editorState.value = ComposerEditorState()
        target = null
        quoteOf = null
        replyTo = null
        savedQuoteOf = null
        savedReplyTo = null
    }

    private fun replyAudience(post: OwnedPost): Audience = runCatching {
        PostingVisibilityPolicy.forReply(
            context.contract.postPreferences,
            ServerCapabilities(audiences = context.contract.availableAudiences),
            context = post.post.audience,
        )
    }.getOrDefault(post.post.audience)

    private fun draftValue(): PostDraft {
        val account = context.account
        return PostDraft(
            id = editor.draftId ?: UUID.randomUUID().toString(),
            accountId = account?.id,
            text = editor.text,
            audience = editor.audience,
            contentWarning = editor.warning.takeIf { editor.warningEnabled && it.isNotBlank() },
            quoteOf = quoteOf?.takeIf { it.connection == account?.id?.connection?.origin },
            replyTo = replyTo?.takeIf { it.connection == account?.id?.connection?.origin },
            quotePreview = target?.let { target ->
                PostDraftQuotePreview(
                    authorDisplayName = target.post.author.displayName,
                    authorHandle = target.post.author.handle,
                    text = target.post.text,
                    url = target.post.url,
                )
            },
        )
    }

    private fun draftTarget(item: PostDraft): OwnedPost? {
        val owner = context.account ?: return null
        val targetId = item.quoteOf ?: return null
        val preview = item.quotePreview ?: return null
        if (item.accountId != owner.id || targetId.connection != owner.id.connection.origin) return null
        val author = Account(
            id = AccountId(Connection(targetId.connection, owner.id.connection.protocol), "draft-quote-author"),
            displayName = preview.authorDisplayName.ifBlank { preview.authorHandle.ifBlank { "Quoted post" } },
            handle = preview.authorHandle.ifBlank { "Quoted post" },
        )
        return OwnedPost(
            owner.id,
            Post(
                id = targetId,
                author = author,
                text = preview.text,
                publishedAtEpochMillis = 0L,
                audience = Audience.Public,
                url = preview.url,
            ),
        )
    }
}
