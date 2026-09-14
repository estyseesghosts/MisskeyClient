package me.foxtails.palustris.ui

import me.foxtails.palustris.domain.EmojiChoice
import me.foxtails.palustris.domain.OwnedPost
import me.foxtails.palustris.ui.shell.BookmarksContract
import me.foxtails.palustris.ui.shell.LikesContract
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
 * An active thread surface owns the mutations. Otherwise the collection that produced the post owns
 * them. This policy carries no protocol behavior.
 */
internal fun detailActionsFor(
    origin: LargePostOrigin,
    threadActive: Boolean,
    thread: ThreadContract,
    profile: ProfileContract,
    bookmarks: BookmarksContract,
    likes: LikesContract,
    fallback: DetailActions,
): DetailActions {
    if (threadActive) {
        return DetailActions(
            favorite = thread.actions::favorite,
            reply = fallback.reply,
            reshare = thread.actions::repost,
            bookmark = thread.actions::bookmark,
            react = thread.actions::react,
        )
    }
    return DetailActions(
        favorite = if (origin == LargePostOrigin.Liked) likes.actions::toggle else fallback.favorite,
        reply = fallback.reply,
        reshare = fallback.reshare,
        bookmark = fallback.bookmark,
        react = when (origin) {
            LargePostOrigin.Profile -> profile.actions::react
            LargePostOrigin.Saved -> bookmarks.actions::react
            LargePostOrigin.Liked -> likes.actions::react
            else -> fallback.react
        },
    )
}
