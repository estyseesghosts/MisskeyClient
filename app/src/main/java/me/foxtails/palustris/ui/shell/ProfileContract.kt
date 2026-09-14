package me.foxtails.palustris.ui.shell

import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.EditableProfile
import me.foxtails.palustris.domain.EditableProfilePatch
import me.foxtails.palustris.domain.EmojiChoice
import me.foxtails.palustris.domain.OwnedPost
import me.foxtails.palustris.ui.profile.ProfileCategory
import me.foxtails.palustris.ui.profile.ProfileUiState

/**
 * Profile presentation for one target.
 *
 * The profile owner holds the selected profile, categories, relationships, pages, and editor
 * requests. App navigation actions (opening a profile or an image) remain shell actions and are
 * not part of this contract. [Empty] is an inert preview value.
 */
data class ProfileContract(
    val state: ProfileUiState,
    val actions: Actions,
) {
    interface Actions {
        fun open(account: Account)
        fun selectCategory(category: ProfileCategory)
        fun refresh()
        fun loadMore()
        fun follow()
        fun unfollow()
        fun react(post: OwnedPost, choice: EmojiChoice)
        fun saveEditor(patch: EditableProfilePatch, onSuccess: () -> Unit)
        fun openEditor()
        fun updateEditor(draft: EditableProfile)
        fun closeEditor()
    }

    companion object {
        val Empty = ProfileContract(ProfileUiState(), ProfileEmptyActions)
    }
}

private object ProfileEmptyActions : ProfileContract.Actions {
    override fun open(account: Account) = Unit
    override fun selectCategory(category: ProfileCategory) = Unit
    override fun refresh() = Unit
    override fun loadMore() = Unit
    override fun follow() = Unit
    override fun unfollow() = Unit
    override fun react(post: OwnedPost, choice: EmojiChoice) = Unit
    override fun saveEditor(patch: EditableProfilePatch, onSuccess: () -> Unit) = Unit
    override fun openEditor() = Unit
    override fun updateEditor(draft: EditableProfile) = Unit
    override fun closeEditor() = Unit
}
