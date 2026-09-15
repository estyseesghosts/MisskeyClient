package me.foxtails.palustris.ui.composer

import me.foxtails.palustris.data.auth.DraftActions
import me.foxtails.palustris.domain.PostDraft
import me.foxtails.palustris.ui.shell.DraftsContract

/** Adapts the data-layer draft owner to the presentation contract. */
fun DraftActions.asDraftsContract(): DraftsContract = DraftsContract(
    actions = object : DraftsContract.Actions {
        override fun load(onResult: (List<PostDraft>) -> Unit, onError: (String) -> Unit) =
            this@asDraftsContract.load(onResult, onError)

        override fun save(draft: PostDraft, onResult: (PostDraft) -> Unit, onError: () -> Unit) =
            this@asDraftsContract.save(draft, onResult, onError)

        override fun delete(draftId: String, onDone: () -> Unit, onError: (String) -> Unit) =
            this@asDraftsContract.delete(draftId, onDone, onError)
    },
)
