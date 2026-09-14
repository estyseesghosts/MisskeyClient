package me.foxtails.palustris.ui.shell

import me.foxtails.palustris.domain.Audience
import me.foxtails.palustris.domain.CreatePostRequest
import me.foxtails.palustris.domain.OwnedPost
import me.foxtails.palustris.domain.PostPreferences

/**
 * Composer capabilities and publication for one connected account.
 *
 * The composer owner holds audience policy, publication state, and the publish operation. Draft
 * persistence is not part of this contract. [Empty] is an inert preview value.
 */
data class ComposerContract(
    val postPreferences: PostPreferences,
    val availableAudiences: Set<Audience>,
    val canPublish: Boolean,
    val publishing: Boolean,
    val error: String?,
    val actions: Actions,
) {
    interface Actions {
        fun publish(request: CreatePostRequest, onAccepted: (OwnedPost) -> Unit)
    }

    companion object {
        val Empty = ComposerContract(PostPreferences(), emptySet(), false, false, null, ComposerEmptyActions)
    }
}

private object ComposerEmptyActions : ComposerContract.Actions {
    override fun publish(request: CreatePostRequest, onAccepted: (OwnedPost) -> Unit) = Unit
}
