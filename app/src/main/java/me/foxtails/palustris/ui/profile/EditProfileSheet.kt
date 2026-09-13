package me.foxtails.palustris.ui.profile

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.EditableProfile
import me.foxtails.palustris.domain.EditableProfileCapabilities

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun EditProfileSheet(
    account: Account,
    editor: EditableProfile?,
    editorBase: EditableProfile?,
    capabilities: EditableProfileCapabilities,
    emoji: Map<String, me.foxtails.palustris.domain.CustomEmoji>,
    loading: Boolean,
    saving: Boolean,
    error: String?,
    onEditorChange: (EditableProfile) -> Unit,
    onSave: (me.foxtails.palustris.domain.EditableProfilePatch) -> Unit,
    onClose: () -> Unit,
) {
    if (editor == null) return
    ModalBottomSheet(onDismissRequest = onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        EditProfileScreen(
            editor = editor,
            capabilities = capabilities,
            emoji = emoji,
            handle = account.handle,
            loading = loading,
            saving = saving,
            error = error,
            onEditorChange = onEditorChange,
            onSave = { editorBase?.let { onSave(editableProfilePatch(it, editor)) } },
            onClose = onClose,
        )
    }
}
