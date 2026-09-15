package me.foxtails.palustris.data.auth

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.PostDraft

/**
 * Draft listing and persistence for one account.
 *
 * This owner hides storage selection, legacy migration, and account scoping. The presentation
 * contract carries no storage type. Cancellation rethrows so a cancelled scope never reports
 * success, failure, or completion. Load and delete failures report an explicit message. Save
 * and delete run through the draft write authority, so a writer revoked by account removal
 * writes nothing and reports no success.
 */
class DraftActions(
    private val scope: CoroutineScope,
    private val store: DraftStore,
    private val accountId: AccountId?,
    private val legacyPreferences: () -> SharedPreferences,
    private val writeGeneration: Long = 0L,
    private val writeAuthority: DraftWriteAuthority = DraftWriteAuthority(),
) {
    fun load(onResult: (List<PostDraft>) -> Unit, onError: (String) -> Unit = {}) {
        scope.launch {
            try {
                store.migrateLegacy(accountId, legacyPreferences())
                onResult(store.list(accountId))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                onError(LOAD_ERROR)
            }
        }
    }

    fun save(draft: PostDraft, onResult: (PostDraft) -> Unit, onError: () -> Unit) {
        scope.launch {
            try {
                val accepted = writeIfCurrent { store.save(draft) }
                // A revoked writer writes nothing and reports no success.
                if (accepted) onResult(draft) else return@launch
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                onError()
            }
        }
    }

    fun delete(draftId: String, onDone: () -> Unit, onError: (String) -> Unit = {}) {
        scope.launch {
            try {
                val accepted = writeIfCurrent { store.delete(accountId, draftId) }
                // A revoked writer deletes nothing and reports no completion.
                if (accepted) onDone() else return@launch
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // The list refreshes from storage, so a failed delete still reports completion.
                onError(DELETE_ERROR)
                onDone()
            }
        }
    }

    private suspend fun writeIfCurrent(block: suspend () -> Unit): Boolean {
        val bound = accountId ?: run {
            block()
            return true
        }
        return writeAuthority.commitIfCurrent(bound, writeGeneration) { block() } != null
    }

    companion object {
        const val LOAD_ERROR = "Drafts could not be loaded."
        const val DELETE_ERROR = "Draft could not be deleted."

        /** Production construction. The legacy preferences lookup stays in the data layer. */
        fun create(
            scope: CoroutineScope,
            store: DraftStore,
            accountId: AccountId?,
            context: Context,
            writeGeneration: Long = 0L,
            writeAuthority: DraftWriteAuthority = DraftWriteAuthority(),
        ): DraftActions = DraftActions(
            scope = scope,
            store = store,
            accountId = accountId,
            legacyPreferences = {
                context.getSharedPreferences("local_draft", Context.MODE_PRIVATE)
            },
            writeGeneration = writeGeneration,
            writeAuthority = writeAuthority,
        )
    }
}
