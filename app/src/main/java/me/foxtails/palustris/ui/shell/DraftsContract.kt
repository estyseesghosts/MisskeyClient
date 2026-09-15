package me.foxtails.palustris.ui.shell

import android.content.SharedPreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import me.foxtails.palustris.data.auth.DraftStore
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

/**
 * Draft listing and persistence owner used by the session host.
 *
 * Cancellation rethrows so a cancelled scope never reports success, failure, or
 * completion. Ordinary storage failures keep the established fallbacks.
 */
class DraftActions(
    private val scope: CoroutineScope,
    private val store: DraftStore,
    private val legacyPreferences: () -> SharedPreferences,
) {
    fun load(accountId: AccountId?, onResult: (List<PostDraft>) -> Unit) {
        scope.launch {
            try {
                store.migrateLegacy(accountId, legacyPreferences())
                onResult(store.list(accountId))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                onResult(emptyList())
            }
        }
    }

    fun save(draft: PostDraft, onResult: (PostDraft) -> Unit, onError: () -> Unit) {
        scope.launch {
            try {
                store.save(draft)
                onResult(draft)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                onError()
            }
        }
    }

    fun delete(accountId: AccountId?, draftId: String, onDone: () -> Unit) {
        scope.launch {
            try {
                store.delete(accountId, draftId)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // A failed delete still reports completion; the list refreshes from storage.
            }
            onDone()
        }
    }

    fun asContract(): DraftsContract = DraftsContract(actions = object : DraftsContract.Actions {
        override fun load(accountId: AccountId?, onResult: (List<PostDraft>) -> Unit) =
            this@DraftActions.load(accountId, onResult)
        override fun save(draft: PostDraft, onResult: (PostDraft) -> Unit, onError: () -> Unit) =
            this@DraftActions.save(draft, onResult, onError)
        override fun delete(accountId: AccountId?, draftId: String, onDone: () -> Unit) =
            this@DraftActions.delete(accountId, draftId, onDone)
    })
}
