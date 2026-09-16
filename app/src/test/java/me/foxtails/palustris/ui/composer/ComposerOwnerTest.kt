package me.foxtails.palustris.ui.composer

import androidx.compose.runtime.mutableStateOf
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.Audience
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.CreatePostRequest
import me.foxtails.palustris.domain.EntityId
import me.foxtails.palustris.domain.OwnedPost
import me.foxtails.palustris.domain.Post
import me.foxtails.palustris.domain.PostDraft
import me.foxtails.palustris.domain.PostPreferences
import me.foxtails.palustris.domain.Protocol
import me.foxtails.palustris.ui.composer.ComposerEditorState
import me.foxtails.palustris.ui.composer.ComposerNavigation
import me.foxtails.palustris.ui.composer.ComposerOwner
import me.foxtails.palustris.ui.composer.ComposerOwnerContext
import me.foxtails.palustris.ui.shell.ComposerContract
import me.foxtails.palustris.ui.shell.DraftsContract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ComposerOwnerTest {
    private val connection = Connection("https://example.org", Protocol.MASTODON)
    private val accountId = AccountId(connection, "owner")
    private val account = Account(accountId, "Owner", "@owner@example.org")
    private val otherAccount = Account(AccountId(connection, "other"), "Other", "@other@example.org")

    private fun post(
        id: String,
        author: Account = account,
        audience: Audience = Audience.Public,
        actionTargetId: EntityId? = null,
    ) = Post(
        id = EntityId(connection.origin, id),
        author = author,
        text = id,
        publishedAtEpochMillis = 0L,
        audience = audience,
        actionTargetId = actionTargetId,
    )

    private class RecordingDrafts : DraftsContract.Actions {
        val saved = mutableListOf<PostDraft>()
        val deleted = mutableListOf<String>()
        var failSave = false
        var loadResult: List<PostDraft> = emptyList()

        override fun load(onResult: (List<PostDraft>) -> Unit, onError: (String) -> Unit) = onResult(loadResult)
        override fun save(draft: PostDraft, onResult: (PostDraft) -> Unit, onError: () -> Unit) {
            if (failSave) onError() else { saved += draft; onResult(draft) }
        }
        override fun delete(draftId: String, onDone: () -> Unit, onError: (String) -> Unit) {
            deleted += draftId
            onDone()
        }
    }

    private class DeferredDrafts : DraftsContract.Actions {
        val saved = mutableListOf<PostDraft>()
        val deleted = mutableListOf<String>()
        var pendingSave: (() -> Unit)? = null

        override fun load(onResult: (List<PostDraft>) -> Unit, onError: (String) -> Unit) = onResult(emptyList())
        override fun save(draft: PostDraft, onResult: (PostDraft) -> Unit, onError: () -> Unit) {
            saved += draft
            pendingSave = { onResult(draft) }
        }
        override fun delete(draftId: String, onDone: () -> Unit, onError: (String) -> Unit) {
            deleted += draftId
            onDone()
        }
    }

    private class RecordingComposer : ComposerContract.Actions {
        val published = mutableListOf<CreatePostRequest>()
        var acceptedPost: OwnedPost? = null
        override fun publish(request: CreatePostRequest, onAccepted: (OwnedPost) -> Unit) {
            published += request
            acceptedPost?.let(onAccepted)
        }
    }

    private fun contract(
        actions: RecordingComposer,
        audiences: Set<Audience> = setOf(Audience.Public, Audience.Unlisted, Audience.Followers),
        preferences: PostPreferences = PostPreferences(),
    ) = ComposerContract(
        postPreferences = preferences,
        availableAudiences = audiences,
        canPublish = true,
        publishing = false,
        error = null,
        actions = actions,
    )

    private fun owner(
        composer: RecordingComposer = RecordingComposer(),
        drafts: DraftsContract.Actions = RecordingDrafts(),
        canReply: Boolean = true,
        canQuote: Boolean = true,
        composerOpen: Boolean = false,
        overlayOpen: Boolean = false,
    ): ComposerOwner {
        val owner = ComposerOwner(mutableStateOf(ComposerEditorState()))
        owner.context = ComposerOwnerContext(
            account = account,
            contract = contract(composer),
            canReply = canReply,
            canQuote = canQuote,
            composerOpen = composerOpen,
            overlayOpen = overlayOpen,
        )
        owner.draftsContract = DraftsContract(drafts)
        return owner
    }

    @Test
    fun replyOpensForTheEffectiveActionTarget() {
        val owner = owner()
        owner.requestReply(OwnedPost(accountId, post("a", actionTargetId = EntityId(connection.origin, "orig"))))

        assertTrue(owner.navigation is ComposerNavigation.Reply)
        assertTrue(owner.isReply)
        assertEquals("orig", (owner.quoteTarget?.post?.actionTargetId ?: owner.quoteTarget?.post?.id)?.value)
    }

    @Test
    fun replyRejectsAForeignAccountPost() {
        val owner = owner()
        owner.requestReply(OwnedPost(otherAccount.id, post("foreign", author = otherAccount)))

        assertNull(owner.navigation)
        assertFalse(owner.isReply)
    }

    @Test
    fun replyRejectsWhileTheEditorIsDirty() {
        val owner = owner()
        owner.setText("unsaved text")
        owner.requestReply(OwnedPost(accountId, post("a")))

        assertNull(owner.navigation)
        assertFalse(owner.isReply)
        assertEquals("unsaved text", owner.editor.text)
    }

    @Test
    fun quoteSetsTheQuoteTarget() {
        val owner = owner()
        owner.requestQuote(OwnedPost(accountId, post("q")))

        assertTrue(owner.navigation is ComposerNavigation.Quote)
        assertFalse(owner.isReply)
        assertEquals("q", owner.quoteTarget?.post?.id?.value)
    }

    @Test
    fun draftRestoresTextWarningAndAudienceWithoutDirtyChanges() {
        val owner = owner()
        val draft = PostDraft(
            id = "draft-1",
            accountId = accountId,
            text = "saved body",
            audience = Audience.Unlisted,
            contentWarning = "spoiler",
        )
        owner.requestDraft(draft)

        assertEquals("draft-1", owner.editor.draftId)
        assertEquals("saved body", owner.editor.text)
        assertTrue(owner.editor.warningEnabled)
        assertEquals("spoiler", owner.editor.warning)
        assertEquals(Audience.Unlisted, owner.editor.audience)
        assertFalse(owner.hasChanges)
        assertTrue(owner.navigation is ComposerNavigation.Draft)
    }

    @Test
    fun saveClearsTheDirtyBaselineOnlyAfterSuccess() {
        val drafts = RecordingDrafts()
        val owner = owner(drafts = drafts)
        owner.setText("body to save")
        owner.save()

        assertEquals(1, drafts.saved.size)
        assertEquals("body to save", owner.editor.savedText)
        assertFalse(owner.hasChanges)
        assertNull(owner.editor.error)
        assertFalse(owner.closing)
    }

    @Test
    fun failedSaveKeepsTheTextForRecovery() {
        val drafts = RecordingDrafts().apply { failSave = true }
        val owner = owner(drafts = drafts)
        owner.setText("body to recover")
        owner.save()

        assertEquals("body to recover", owner.editor.text)
        assertTrue(owner.hasChanges)
        assertTrue(owner.editor.error != null)
        assertFalse(owner.closing)
    }

    @Test
    fun publishRejectsAnUnavailableAudienceWithoutPublishing() {
        val composer = RecordingComposer()
        val owner = owner(composer = composer)
        owner.setText("body")
        owner.setAudience(Audience.Direct)
        owner.publish { _, _ -> }

        assertTrue(composer.published.isEmpty())
        assertEquals("This audience is not available on this server.", owner.editor.error)
    }

    @Test
    fun publishSavesThenPublishesAndClearsOnAcceptance() {
        val composer = RecordingComposer().apply { acceptedPost = OwnedPost(accountId, post("sent")) }
        val drafts = RecordingDrafts()
        val owner = owner(composer = composer, drafts = drafts)
        owner.setText("hello world")
        var sentReply = true
        var sentQuote = true
        owner.publish { reply, quote ->
            sentReply = reply
            sentQuote = quote
        }

        assertEquals(1, composer.published.size)
        assertEquals("hello world", composer.published.single().text)
        assertFalse(sentReply)
        assertFalse(sentQuote)
        assertEquals("", owner.editor.text)
        assertFalse(owner.hasChanges)
        assertEquals(1, drafts.deleted.size)
    }

    @Test
    fun sessionReplacementClearsRestoredTargets() {
        val owner = owner()
        owner.requestReply(OwnedPost(accountId, post("a")))
        owner.consumeNavigation()
        owner.resetTargets()

        assertNull(owner.quoteTarget)
        assertFalse(owner.isReply)
        assertNull(owner.navigation)
    }

    @Test
    fun duplicatePublishIsRejectedWhileASaveIsPending() {
        val drafts = DeferredDrafts()
        val composer = RecordingComposer()
        val owner = owner(composer = composer, drafts = drafts)
        owner.setText("body")
        owner.publish { _, _ -> }
        owner.publish { _, _ -> }

        assertEquals(1, drafts.saved.size)
        assertTrue(owner.submitting)
        assertTrue(composer.published.isEmpty())
    }

    @Test
    fun obsoleteSaveCallbackCannotPublishAfterSessionReplacement() {
        val drafts = DeferredDrafts()
        val composer = RecordingComposer().apply { acceptedPost = OwnedPost(accountId, post("sent")) }
        val owner = owner(composer = composer, drafts = drafts)
        owner.setText("body")
        owner.publish { _, _ -> }
        owner.sessionRevision = 7L
        drafts.pendingSave?.invoke()

        assertTrue(composer.published.isEmpty())
        assertFalse(owner.submitting)
    }

    @Test
    fun newerEditsDuringPublishSurviveAcceptance() {
        val drafts = DeferredDrafts()
        val composer = RecordingComposer().apply { acceptedPost = OwnedPost(accountId, post("sent")) }
        val owner = owner(composer = composer, drafts = drafts)
        owner.setText("first")
        owner.publish { _, _ -> }
        owner.setText("first and second")
        drafts.pendingSave?.invoke()

        assertEquals("first", composer.published.single().text)
        assertEquals("first and second", owner.editor.text)
        assertFalse(owner.submitting)
    }
}
