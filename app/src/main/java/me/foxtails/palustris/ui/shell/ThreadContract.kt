package me.foxtails.palustris.ui.shell

import me.foxtails.palustris.domain.EmojiChoice
import me.foxtails.palustris.domain.OwnedPost
import me.foxtails.palustris.ui.thread.PostThreadUiState

/**
 * Selected thread presentation.
 *
 * The thread owner acquires and continues the selected thread and mutates its rows. Feed
 * acquisition and notification queries are deliberately not part of this contract. [Empty] is an
 * inert preview value.
 */
data class ThreadContract(
    val state: PostThreadUiState?,
    val actions: Actions,
) {
    interface Actions {
        fun activate(post: OwnedPost?, enabled: Boolean)
        fun deactivate()
        fun refresh()
        fun continueAcquisition()
        fun favorite(post: OwnedPost)
        fun repost(post: OwnedPost)
        fun bookmark(post: OwnedPost)
        fun react(post: OwnedPost, choice: EmojiChoice)
    }

    companion object {
        val Empty = ThreadContract(null, ThreadEmptyActions)
    }
}

private object ThreadEmptyActions : ThreadContract.Actions {
    override fun activate(post: OwnedPost?, enabled: Boolean) = Unit
    override fun deactivate() = Unit
    override fun refresh() = Unit
    override fun continueAcquisition() = Unit
    override fun favorite(post: OwnedPost) = Unit
    override fun repost(post: OwnedPost) = Unit
    override fun bookmark(post: OwnedPost) = Unit
    override fun react(post: OwnedPost, choice: EmojiChoice) = Unit
}
