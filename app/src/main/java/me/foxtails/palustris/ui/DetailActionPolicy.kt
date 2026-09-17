package me.foxtails.palustris.ui

import me.foxtails.palustris.domain.EmojiChoice
import me.foxtails.palustris.domain.OwnedPost
import me.foxtails.palustris.ui.shell.BookmarksContract
import me.foxtails.palustris.ui.shell.ProfileContract
import me.foxtails.palustris.ui.shell.ThreadContract

/** Resolved post-action handlers for one detail surface. */
internal class DetailActions(
    val favorite: (OwnedPost) -> Unit,
    val reply: (OwnedPost) -> Unit,
    val reshare: (OwnedPost) -> Unit,
    val bookmark: (OwnedPost) -> Unit,
    val react: (OwnedPost, EmojiChoice) -> Unit,
)

/**
 * Selects origin-based handlers for a selected post.
 *
 * Both compact and wide detail surfaces use this policy so one origin resolves to the same owner.
 * Wide detail passes its active thread owner because that owner renders the focal post.
 */
internal fun detailActionsFor(
    origin: LargePostOrigin,
    profile: ProfileContract,
    bookmarks: BookmarksContract,
    fallback: DetailActions,
    thread: ThreadContract? = null,
): DetailActions = DetailActions(
        favorite = thread?.let { threadActions -> threadActions.actions::favorite } ?: fallback.favorite,
        reply = fallback.reply,
        reshare = thread?.let { threadActions -> threadActions.actions::repost } ?: fallback.reshare,
        bookmark = thread?.let { threadActions -> threadActions.actions::bookmark } ?: fallback.bookmark,
        react = thread?.let { threadActions -> threadActions.actions::react } ?: when (origin) {
            LargePostOrigin.Profile -> profile.actions::react
            LargePostOrigin.Saved -> bookmarks.actions::react
            else -> fallback.react
        },
    )
