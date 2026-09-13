package me.foxtails.palustris.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import me.foxtails.palustris.R

@Composable
internal fun AppDialogs(
    profileDialog: Boolean,
    onProfileDialogDismiss: () -> Unit,
    onDiscardProfile: () -> Unit,
    signOutDialog: Boolean,
    onSignOutDialogDismiss: () -> Unit,
    onSignOut: () -> Unit,
) {
    if (profileDialog) AlertDialog(
        onDismissRequest = onProfileDialogDismiss,
        title = { Text(stringResource(R.string.dialog_discard_profile_title)) },
        text = { Text(stringResource(R.string.dialog_discard_profile_text)) },
        confirmButton = { TextButton(onClick = { onProfileDialogDismiss(); onDiscardProfile() }) { Text(stringResource(R.string.dialog_discard)) } },
        dismissButton = { TextButton(onClick = onProfileDialogDismiss) { Text(stringResource(R.string.dialog_keep_editing)) } },
    )
    if (signOutDialog) AlertDialog(
        onDismissRequest = onSignOutDialogDismiss,
        title = { Text(stringResource(R.string.dialog_sign_out_title)) },
        text = { Text(stringResource(R.string.dialog_sign_out_text)) },
        confirmButton = { TextButton(onClick = { onSignOutDialogDismiss(); onSignOut() }) { Text(stringResource(R.string.account_sign_out)) } },
        dismissButton = { TextButton(onClick = onSignOutDialogDismiss) { Text(stringResource(R.string.dialog_cancel)) } },
    )
}
