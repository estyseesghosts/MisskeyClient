package me.foxtails.palustris.ui.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.CapabilityStatus
import me.foxtails.palustris.domain.EmojiChoice
import me.foxtails.palustris.domain.OwnedPost
import me.foxtails.palustris.ui.PostActionBubbleTarget
import me.foxtails.palustris.ui.ReactionBubbleMode
import me.foxtails.palustris.ui.emoji.ComposerField
import me.foxtails.palustris.ui.emoji.EmojiPickerTarget
import me.foxtails.palustris.ui.media.ImageViewerContent
import me.foxtails.palustris.ui.media.MediaOpenRequest

/**
 * Transient overlay state holder for the application shell.
 *
 * The holder owns one copy of each transient overlay value: the post-action
 * bubble, the emoji picker target, the media and profile-image requests, and
 * the dialog flags. The shell reads and writes these values but keeps no
 * duplicate. Session bindings refresh on each composition and guard reaction
 * and media opens. Account and session changes clear through
 * [clearForAccountChange] and [clearForSessionChange]. Only the profile
 * dialog flag survives process recreation through [Saver]. All other values
 * stay transient.
 */
internal class ShellOverlayPresenter internal constructor() {
    var postActionBubbleTarget by mutableStateOf<PostActionBubbleTarget?>(null)
    var pendingExpandedReactionTarget by mutableStateOf<OwnedPost?>(null)
    var postReactionHandler by mutableStateOf<((OwnedPost, EmojiChoice) -> Unit)?>(null)
    var pendingEmojiInsertion by mutableStateOf<Pair<EmojiChoice, ComposerField>?>(null)
    var emojiPickerTarget by mutableStateOf<EmojiPickerTarget?>(null)
    var mediaRequest by mutableStateOf<MediaOpenRequest?>(null)
    var profileImageRequest by mutableStateOf<ImageViewerContent?>(null)
    var profileDialog by mutableStateOf(false)
    var signOutDialog by mutableStateOf(false)

    var account: Account? = null
    var sessionRevision: Long = 0L
    var reactionMutation: CapabilityStatus = CapabilityStatus.Unknown

    fun clearPostActionBubble() {
        postActionBubbleTarget = null
        pendingExpandedReactionTarget = null
        postReactionHandler = null
    }

    fun openHashtagBubble(ownedPost: OwnedPost, hashtags: List<String>, bounds: Rect) {
        postReactionHandler = null
        postActionBubbleTarget = PostActionBubbleTarget.HashtagList(
            postId = ownedPost.post.id,
            hashtags = hashtags,
            anchorBounds = bounds,
        )
    }

    // Rejects foreign posts, stale revisions, and unsupported reactions.
    fun openReactionBubble(
        ownedPost: OwnedPost,
        bounds: Rect,
        handler: (OwnedPost, EmojiChoice) -> Unit,
    ) {
        val owner = account ?: return
        if (ownedPost.fetchedBy != owner.id || ownedPost.sessionRevision != sessionRevision ||
            reactionMutation != CapabilityStatus.Supported
        ) {
            return
        }
        postReactionHandler = handler
        postActionBubbleTarget = PostActionBubbleTarget.Reaction(ownedPost, bounds)
    }

    fun expandReactionPicker(target: OwnedPost) {
        pendingExpandedReactionTarget = target
        promotePendingExpansion()
    }

    // Moves a pending expansion into the visible bubble when the bubble
    // already shows the same post. Keeps the deferred promotion in one place.
    fun promotePendingExpansion() {
        val pending = pendingExpandedReactionTarget ?: return
        val current = postActionBubbleTarget as? PostActionBubbleTarget.Reaction ?: return
        if (current.ownedPost.fetchedBy == pending.fetchedBy && current.postId == pending.post.id) {
            postActionBubbleTarget = current.copy(mode = ReactionBubbleMode.Expanded)
            pendingExpandedReactionTarget = null
        }
    }

    // Rejects foreign posts and out-of-range attachments.
    fun openMedia(request: MediaOpenRequest) {
        val ownerId = account?.id
        if (ownerId != null && request.ownedPost.fetchedBy != ownerId) return
        if (request.attachmentIndex !in request.ownedPost.post.attachments.indices) return
        clearPostActionBubble()
        mediaRequest = request
    }

    fun openProfileImage(url: String, owner: AccountId?) {
        if (url.isBlank()) return
        profileImageRequest = ImageViewerContent(
            url = url,
            identity = "profile:${owner?.connection?.origin}:${owner?.localId}:$url",
        )
        clearPostActionBubble()
    }

    // Clears transient overlay values for an account change. Dialog flags and
    // the profile-image request stay. The shell still owns stream teardown.
    fun clearForAccountChange() {
        mediaRequest = null
        emojiPickerTarget = null
        postActionBubbleTarget = null
        postReactionHandler = null
        pendingEmojiInsertion = null
    }

    // Rebinds popup authority after a session replacement. A stale reaction
    // handler must not run a later selection.
    fun clearForSessionChange() {
        postActionBubbleTarget = null
        pendingExpandedReactionTarget = null
        postReactionHandler = null
    }

    companion object {
        val Saver: Saver<ShellOverlayPresenter, *> = listSaver(
            save = { listOf(it.profileDialog) },
            restore = { saved ->
                ShellOverlayPresenter().apply {
                    profileDialog = saved[0] as Boolean
                }
            },
        )
    }
}

/**
 * Creates and binds the transient overlay holder for the connected session.
 *
 * The holder survives recomposition and restores the profile dialog flag
 * after process recreation. Session bindings refresh on each composition.
 */
@Composable
internal fun rememberShellOverlayPresenter(
    account: Account?,
    sessionRevision: Long,
    reactionMutation: CapabilityStatus,
): ShellOverlayPresenter {
    val presenter = rememberSaveable(saver = ShellOverlayPresenter.Saver, init = ::ShellOverlayPresenter)
    presenter.account = account
    presenter.sessionRevision = sessionRevision
    presenter.reactionMutation = reactionMutation
    return presenter
}
