package me.foxtails.palustris.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.foxtails.palustris.R
import me.foxtails.palustris.domain.CapabilityStatus
import me.foxtails.palustris.domain.CustomEmoji
import me.foxtails.palustris.domain.EditableProfile
import me.foxtails.palustris.domain.EditableProfileCapabilities
import me.foxtails.palustris.domain.EditableProfileField
import me.foxtails.palustris.ui.emoji.InlineEmojiText
import me.foxtails.palustris.ui.AppIcons

@Composable
fun EditProfileScreen(
    editor: EditableProfile?,
    capabilities: EditableProfileCapabilities,
    emoji: Map<String, CustomEmoji>,
    handle: String,
    loading: Boolean,
    saving: Boolean,
    error: String?,
    onEditorChange: (EditableProfile) -> Unit,
    onSave: () -> Unit,
    onClose: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.profile_editor_title), style = MaterialTheme.typography.headlineSmall)
            TextButton(onClick = onClose) { Text(stringResource(R.string.profile_editor_close)) }
        }
        Text(
            stringResource(R.string.profile_editor_handle),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        InlineEmojiText(
            text = handle,
            emoji = emoji,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))
        if (editor == null) {
            if (!loading) {
                Text(
                    stringResource(R.string.profile_editor_load_error),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        } else {
            EditorFields(
                editor = editor,
                capabilities = capabilities,
                emoji = emoji,
                onEditorChange = onEditorChange,
            )
        }
        error?.let {
            Text(
                it,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onSave,
            enabled = !saving && !loading && editor != null && editor.displayName.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (saving) stringResource(R.string.profile_editor_saving) else stringResource(R.string.profile_editor_save))
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun EditorFields(
    editor: EditableProfile,
    capabilities: EditableProfileCapabilities,
    emoji: Map<String, CustomEmoji>,
    onEditorChange: (EditableProfile) -> Unit,
) {
    OutlinedTextField(
        editor.displayName,
        { onEditorChange(editor.copy(displayName = it)) },
        label = { Text(stringResource(R.string.profile_editor_display_name)) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
    )
    Spacer(Modifier.height(12.dp))
    OutlinedTextField(
        editor.biography,
        { onEditorChange(editor.copy(biography = it)) },
        label = { Text(stringResource(R.string.profile_editor_biography)) },
        modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
        minLines = 4,
    )

    if (capabilities.advancedSettings != CapabilityStatus.Supported) {
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(R.string.profile_editor_unsupported),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
        return
    }

    Spacer(Modifier.height(20.dp))
    Text(stringResource(R.string.profile_editor_fields_title), style = MaterialTheme.typography.titleMedium)
    editor.fields.forEachIndexed { index, field ->
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    field.name,
                    { value -> onEditorChange(editor.copy(fields = editor.fields.replaceAt(index, field.copy(name = value)))) },
                    label = { Text(stringResource(R.string.profile_editor_field_name)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                OutlinedTextField(
                    field.value,
                    { value -> onEditorChange(editor.copy(fields = editor.fields.replaceAt(index, field.copy(value = value)))) },
                    label = { Text(stringResource(R.string.profile_editor_field_value)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            }
            Column {
                IconButton(
                    enabled = index > 0,
                    onClick = {
                        onEditorChange(editor.copy(fields = editor.fields.move(index, index - 1)))
                    },
                ) { Text(stringResource(R.string.profile_move_up)) }
                IconButton(
                    enabled = index < editor.fields.lastIndex,
                    onClick = {
                        onEditorChange(editor.copy(fields = editor.fields.move(index, index + 1)))
                    },
                ) { Text(stringResource(R.string.profile_move_down)) }
                IconButton(
                    onClick = {
                        onEditorChange(editor.copy(fields = editor.fields.filterIndexed { i, _ -> i != index }))
                    },
                ) { Icon(AppIcons.Close, stringResource(R.string.profile_editor_remove_field)) }
            }
        }
    }
    OutlinedButton(
        onClick = { onEditorChange(editor.copy(fields = editor.fields + EditableProfileField("", ""))) },
        modifier = Modifier.padding(top = 8.dp),
    ) {
        Text(stringResource(R.string.profile_editor_add_field))
    }

    Spacer(Modifier.height(20.dp))
    Text(stringResource(R.string.profile_editor_visibility_title), style = MaterialTheme.typography.titleMedium)
    EditorSwitch(editor.locked, stringResource(R.string.profile_editor_locked)) {
        onEditorChange(editor.copy(locked = it))
    }
    EditorSwitch(editor.bot, stringResource(R.string.profile_editor_bot)) {
        onEditorChange(editor.copy(bot = it))
    }
    EditorSwitch(editor.hideCollections, stringResource(R.string.profile_editor_hide_collections)) {
        onEditorChange(editor.copy(hideCollections = it))
    }
    EditorSwitch(editor.discoverable, stringResource(R.string.profile_editor_discoverable)) {
        onEditorChange(editor.copy(discoverable = it))
    }
    EditorSwitch(editor.indexable, stringResource(R.string.profile_editor_indexable)) {
        onEditorChange(editor.copy(indexable = it))
    }
    EditorSwitch(editor.showMedia, stringResource(R.string.profile_editor_media_tab)) {
        onEditorChange(editor.copy(showMedia = it))
    }
    EditorSwitch(editor.showMediaReplies, stringResource(R.string.profile_editor_media_replies)) {
        onEditorChange(editor.copy(showMediaReplies = it))
    }
    EditorSwitch(editor.showFeatured, stringResource(R.string.profile_editor_featured_tab)) {
        onEditorChange(editor.copy(showFeatured = it))
    }

    Spacer(Modifier.height(12.dp))
    AttributionDomains(editor, onEditorChange)

    if (capabilities.imageDescriptions == CapabilityStatus.Supported) {
        Spacer(Modifier.height(20.dp))
        Text(
            stringResource(R.string.profile_editor_image_descriptions_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            editor.avatarDescription.orEmpty(),
            { onEditorChange(editor.copy(avatarDescription = it)) },
            label = { Text(stringResource(R.string.profile_editor_avatar_description)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            editor.headerDescription.orEmpty(),
            { onEditorChange(editor.copy(headerDescription = it)) },
            label = { Text(stringResource(R.string.profile_editor_header_description)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
    }
}

@Composable
private fun AttributionDomains(editor: EditableProfile, onEditorChange: (EditableProfile) -> Unit) {
    Text(
        stringResource(R.string.profile_editor_attribution_domains),
        style = MaterialTheme.typography.titleMedium,
    )
    editor.attributionDomains.forEachIndexed { index, domain ->
        Row(
            Modifier.fillMaxWidth().padding(top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                domain,
                { value ->
                    onEditorChange(editor.copy(attributionDomains = editor.attributionDomains.replaceAt(index, value)))
                },
                modifier = Modifier.weight(1f),
                singleLine = true,
            )
            IconButton(
                onClick = {
                    onEditorChange(editor.copy(
                        attributionDomains = editor.attributionDomains.filterIndexed { i, _ -> i != index },
                    ))
                },
                modifier = Modifier.size(40.dp),
            ) {
                Icon(AppIcons.Close, stringResource(R.string.profile_editor_remove_field))
            }
        }
    }
    OutlinedButton(
        onClick = { onEditorChange(editor.copy(attributionDomains = editor.attributionDomains + "")) },
        modifier = Modifier.padding(top = 8.dp),
    ) {
        Text(stringResource(R.string.profile_editor_add_domain))
    }
}

@Composable
private fun EditorSwitch(value: Boolean, label: String, onCheckedChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        Switch(checked = value, onCheckedChange = onCheckedChange)
    }
}

private fun <T> List<T>.replaceAt(index: Int, value: T): List<T> =
    toMutableList().also { it[index] = value }

private fun <T> List<T>.move(from: Int, to: Int): List<T> = toMutableList().also {
    val item = it.removeAt(from)
    it.add(to, item)
}
