package me.foxtails.palustris.ui.composer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import me.foxtails.palustris.ui.shell.DraftsContract

/**
 * Creates and binds the composer owner for the connected session.
 *
 * The owner survives recomposition and process recreation. It reloads drafts for the active
 * account. A session replacement retires restored reply and quote targets.
 */
@Composable
fun rememberComposerOwner(
    context: ComposerOwnerContext,
    draftsContract: DraftsContract,
    sessionGeneration: Long,
    sessionRevision: Long,
): ComposerOwner {
    val editorState: MutableState<ComposerEditorState> = rememberSaveable(
        stateSaver = ComposerEditorState.Saver,
        init = { mutableStateOf(ComposerEditorState()) },
    )
    val owner = remember { ComposerOwner(editorState) }
    owner.context = context
    owner.draftsContract = draftsContract
    owner.sessionRevision = sessionRevision
    LaunchedEffect(context.account?.id, draftsContract) { owner.refreshDrafts() }
    LaunchedEffect(context.account?.id) { owner.resetTargets() }
    LaunchedEffect(sessionGeneration, sessionRevision) { owner.resetTargets() }
    return owner
}
