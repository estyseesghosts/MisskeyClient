package me.foxtails.palustris.ui.shell

import me.foxtails.palustris.domain.EmojiChoice
import me.foxtails.palustris.domain.OwnedPost
import me.foxtails.palustris.domain.PostAction
import me.foxtails.palustris.domain.PostDraft
import me.foxtails.palustris.ui.LargePostOrigin
import me.foxtails.palustris.ui.navigation.AppRoute

/**
 * Post interaction data and callbacks shared by every destination branch.
 *
 * The branches render the same post actions under the same gating data,
 * so the callbacks travel as one bundle instead of eight parameters.
 */
internal data class DestinationPostCallbacks(
    val availableActions: Set<PostAction>,
    val quoteEnabled: Boolean,
    val onReact: (OwnedPost) -> Unit,
    val onReply: (OwnedPost) -> Unit,
    val onReshare: (OwnedPost) -> Unit,
    val onBookmark: (OwnedPost) -> Unit,
    val onReaction: (OwnedPost, EmojiChoice) -> Unit,
    val onQuote: (OwnedPost) -> Unit,
)

/** Draft list with its load and delete callbacks for the local page branch. */
internal data class DestinationDraftCallbacks(
    val drafts: List<PostDraft>,
    val onLoadDraft: (PostDraft) -> Unit,
    val onDeleteDraft: (PostDraft) -> Unit,
)

/**
 * Navigation callbacks for the destination branches.
 *
 * Branches call these instead of holding the navigator, so navigation
 * policy stays with the shell.
 */
internal data class DestinationNavigationCallbacks(
    val onOpenPost: (OwnedPost, LargePostOrigin) -> Unit,
    val onOpenNotificationTarget: (AppRoute) -> Unit,
    val onEditProfile: () -> Unit,
)
