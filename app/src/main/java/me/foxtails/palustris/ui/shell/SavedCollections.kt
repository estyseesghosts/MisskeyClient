package me.foxtails.palustris.ui.shell

import me.foxtails.palustris.domain.EmojiChoice
import me.foxtails.palustris.domain.OwnedPost
import me.foxtails.palustris.domain.SavedPostsKind
import me.foxtails.palustris.ui.SavedPostsUiState

/**
 * Bookmark collection presentation.
 *
 * Bookmark membership, paging, permission recovery, and bookmark reactions share one owner.
 * The contract must not absorb like collection membership or like removal semantics.
 */
data class BookmarksContract(
    val state: SavedPostsUiState?,
    val actions: Actions,
) {
    interface Actions {
        fun refresh()
        fun loadMore()
        fun remove(post: OwnedPost)
        fun upgradePermissions()
        fun react(post: OwnedPost, choice: EmojiChoice)
    }

    val kind: SavedPostsKind?
        get() = state?.kind

    companion object {
        val Empty = BookmarksContract(null, BookmarksEmptyActions)
    }
}

/**
 * Like collection presentation.
 *
 * Likes have their own capability and removal behavior. A like toggle must not follow bookmark
 * removal semantics.
 */
data class LikesContract(
    val state: SavedPostsUiState?,
    val actions: Actions,
) {
    interface Actions {
        fun refresh()
        fun loadMore()
        fun toggle(post: OwnedPost)
        fun react(post: OwnedPost, choice: EmojiChoice)
    }

    companion object {
        val Empty = LikesContract(null, LikesEmptyActions)
    }
}

private object BookmarksEmptyActions : BookmarksContract.Actions {
    override fun refresh() = Unit
    override fun loadMore() = Unit
    override fun remove(post: OwnedPost) = Unit
    override fun upgradePermissions() = Unit
    override fun react(post: OwnedPost, choice: EmojiChoice) = Unit
}

private object LikesEmptyActions : LikesContract.Actions {
    override fun refresh() = Unit
    override fun loadMore() = Unit
    override fun toggle(post: OwnedPost) = Unit
    override fun react(post: OwnedPost, choice: EmojiChoice) = Unit
}
