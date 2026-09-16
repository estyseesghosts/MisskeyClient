package me.foxtails.palustris.ui.composer

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.foxtails.palustris.R
import me.foxtails.palustris.ui.AppIcons
import me.foxtails.palustris.ui.EmptyState
import me.foxtails.palustris.ui.motion.LocalPalustrisMotionScheme
import me.foxtails.palustris.ui.motion.springPress

@Composable
fun DraftsScreen(
    drafts: List<me.foxtails.palustris.domain.PostDraft>,
    onEdit: (me.foxtails.palustris.domain.PostDraft) -> Unit,
    onDelete: (me.foxtails.palustris.domain.PostDraft) -> Unit,
) {
    val scheme = LocalPalustrisMotionScheme.current
    var confirmDelete by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<me.foxtails.palustris.domain.PostDraft?>(null) }
    if (drafts.isEmpty()) EmptyState(AppIcons.Folder, androidx.compose.ui.res.stringResource(R.string.drafts_empty_title), androidx.compose.ui.res.stringResource(R.string.drafts_empty_subtitle))
    else LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(vertical = 16.dp),
    ) {
        items(drafts, key = { it.id }) { draft ->
            val interactionSource = remember(draft.id) { MutableInteractionSource() }
            ElevatedCard(
                onClick = { onEdit(draft) },
                interactionSource = interactionSource,
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp).springPress(interactionSource, pressedScale = scheme.largePressedScale).animateItem(
                    fadeInSpec = scheme.fastFadeIn,
                    fadeOutSpec = scheme.fastFadeOut,
                    placementSpec = scheme.gentleOffset,
                ),
            ) {
                Column(Modifier.padding(20.dp)) {
                    Text(androidx.compose.ui.res.stringResource(R.string.draft_label), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(12.dp))
                    me.foxtails.palustris.ui.emoji.InlineEmojiText(draft.text.ifBlank { draft.contentWarning.orEmpty() }, draft.quotePreview?.postEmoji.orEmpty(), style = MaterialTheme.typography.bodyMedium, maxLines = 5, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(12.dp))
                    Text(androidx.compose.ui.res.stringResource(R.string.draft_continue), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = { pendingDelete = draft; confirmDelete = true }, modifier = Modifier.align(Alignment.End)) { Text(androidx.compose.ui.res.stringResource(R.string.draft_delete_action)) }
                }
            }
        }
    }
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text(androidx.compose.ui.res.stringResource(R.string.draft_delete_title)) },
        text = { Text(androidx.compose.ui.res.stringResource(R.string.draft_delete_text)) },
        confirmButton = { TextButton(onClick = { confirmDelete = false; pendingDelete?.let(onDelete); pendingDelete = null }) { Text(androidx.compose.ui.res.stringResource(R.string.draft_delete)) } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(androidx.compose.ui.res.stringResource(R.string.dialog_cancel)) } },
    )
}
