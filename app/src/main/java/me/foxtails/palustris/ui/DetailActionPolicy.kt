package me.foxtails.palustris.ui

import me.foxtails.palustris.domain.EmojiChoice
import me.foxtails.palustris.domain.OwnedPost
import me.foxtails.palustris.ui.shell.BookmarksContract
import me.foxtails.palustris.ui.shell.ProfileContract

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
 * Thread acquisition does not change the mutation owner. This keeps optimistic projections shared
 * with the feed and avoids protocol-specific action behavior in detail presentation.
 */
internal fun detailActionsFor(
    origin: LargePostOrigin,
    profile: ProfileContract,
    bookmarks: BookmarksContract,
    fallback: DetailActions,
): DetailActions = DetailActions(
        favorite = fallback.favorite,
        reply = fallback.reply,
        reshare = fallback.reshare,
        bookmark = fallback.bookmark,
        react = when (origin) {
            LargePostOrigin.Profile -> profile.actions::react
            LargePostOrigin.Saved -> bookmarks.actions::react
            else -> fallback.react
        },
    )
