package me.foxtails.palustris.ui.shell

import me.foxtails.palustris.domain.EmojiChoice
import me.foxtails.palustris.domain.OwnedPost
import me.foxtails.palustris.domain.PostAction

/**
 * Post-interaction capabilities and mutations for one surface.
 *
 * [availableActions] and [quoteEnabled] describe the surface's capability set. Each action routes
 * to exactly one mutation owner. The contract never carries navigation, draft state, or transport
 * objects. [Empty] is an inert preview value.
 */
data class PostInteractions(
    val availableActions: Set<PostAction>,
    val quoteEnabled: Boolean,
    val actions: Actions,
) {
    interface Actions {
        fun favorite(post: OwnedPost)
        fun repost(post: OwnedPost)
        fun bookmark(post: OwnedPost)
        fun react(post: OwnedPost, choice: EmojiChoice)
    }

    companion object {
        val Empty = PostInteractions(emptySet(), false, PostInteractionsEmptyActions)
    }
}

private object PostInteractionsEmptyActions : PostInteractions.Actions {
    override fun favorite(post: OwnedPost) = Unit
    override fun repost(post: OwnedPost) = Unit
    override fun bookmark(post: OwnedPost) = Unit
    override fun react(post: OwnedPost, choice: EmojiChoice) = Unit
}
