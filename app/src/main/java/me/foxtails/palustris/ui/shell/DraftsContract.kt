package me.foxtails.palustris.ui.shell

import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.PostDraft

/**
 * Draft listing and persistence for the active account.
 *
 * The owner hides storage selection, legacy migration, and account scoping. The shell keeps only
 * composer fields. [Empty] is an inert preview value.
 */
data class DraftsContract(
    val actions: Actions,
) {
    interface Actions {
        fun load(accountId: AccountId?, onResult: (List<PostDraft>) -> Unit)
        fun save(draft: PostDraft, onResult: (PostDraft) -> Unit, onError: () -> Unit)
        fun delete(accountId: AccountId?, draftId: String, onDone: () -> Unit)
    }

    companion object {
        val Empty = DraftsContract(DraftsEmptyActions)
    }
}

private object DraftsEmptyActions : DraftsContract.Actions {
    override fun load(accountId: AccountId?, onResult: (List<PostDraft>) -> Unit) = onResult(emptyList())
    override fun save(draft: PostDraft, onResult: (PostDraft) -> Unit, onError: () -> Unit) = Unit
    override fun delete(accountId: AccountId?, draftId: String, onDone: () -> Unit) = onDone()
}
