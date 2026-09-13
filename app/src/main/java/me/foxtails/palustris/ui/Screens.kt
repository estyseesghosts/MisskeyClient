package me.foxtails.palustris.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.foxtails.palustris.R
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.Audience
import me.foxtails.palustris.domain.OwnedPost
import me.foxtails.palustris.ui.components.AccountAvatar
import me.foxtails.palustris.ui.emoji.ComposerField
import me.foxtails.palustris.ui.motion.AnimatedStatePane
import me.foxtails.palustris.ui.motion.ExpandableContent
import me.foxtails.palustris.ui.motion.LocalPalustrisMotionScheme
import me.foxtails.palustris.ui.motion.springPress

@Composable
fun ComposeScreen(
    text: String, onTextChange: (String) -> Unit,
    warning: String, onWarningChange: (String) -> Unit,
    warningEnabled: Boolean, onWarningEnabled: (Boolean) -> Unit,
    account: Account? = null,
    audience: Audience = Audience.Public,
    availableAudiences: Set<Audience> = emptySet(),
    onAudienceChange: (Audience) -> Unit = {},
    canPublish: Boolean = false,
    publishing: Boolean = false,
    error: String? = null,
    quoteTarget: OwnedPost? = null,
    isReply: Boolean = false,
    onRemoveQuote: () -> Unit = {},
    onPublish: () -> Unit = {},
    onRequestEmoji: ((ComposerField) -> Unit)? = null,
    pendingEmojiInsertion: Pair<me.foxtails.palustris.domain.EmojiChoice, ComposerField>? = null,
    onEmojiInsertionApplied: () -> Unit = {},
    onCleanTrackingParameters: () -> Unit = {},
) {
    val scheme = LocalPalustrisMotionScheme.current
    val emojiPickerDescription = stringResource(R.string.emoji_open_picker)
    val postTextDescription = stringResource(R.string.composer_post_text)
    var textSelection by remember { mutableStateOf(androidx.compose.ui.text.TextRange(text.length)) }
    var warningSelection by remember { mutableStateOf(androidx.compose.ui.text.TextRange(warning.length)) }
    LaunchedEffect(text) {
        if (textSelection.min > text.length) textSelection = androidx.compose.ui.text.TextRange(text.length)
    }
    LaunchedEffect(warning) {
        if (warningSelection.min > warning.length) warningSelection = androidx.compose.ui.text.TextRange(warning.length)
    }
    LaunchedEffect(pendingEmojiInsertion) {
        val insertion = pendingEmojiInsertion ?: return@LaunchedEffect
        when (insertion.second) {
            ComposerField.Text -> {
                val selection = textSelection
                val start = selection.min.coerceIn(0, text.length)
                val end = selection.max.coerceIn(start, text.length)
                val inserted = text.replaceRange(start, end, insertion.first.submissionValue)
                onTextChange(inserted)
                textSelection = androidx.compose.ui.text.TextRange(start + insertion.first.submissionValue.length)
            }
            ComposerField.Warning -> {
                val selection = warningSelection
                val start = selection.min.coerceIn(0, warning.length)
                val end = selection.max.coerceIn(start, warning.length)
                val inserted = warning.replaceRange(start, end, insertion.first.submissionValue)
                onWarningChange(inserted)
                warningSelection = androidx.compose.ui.text.TextRange(start + insertion.first.submissionValue.length)
            }
        }
        onEmojiInsertionApplied()
    }
    val textValue = remember(text, textSelection) { androidx.compose.ui.text.input.TextFieldValue(text, textSelection) }
    val warningValue = remember(warning, warningSelection) { androidx.compose.ui.text.input.TextFieldValue(warning, warningSelection) }
    Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        Row(Modifier.padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            if (account != null) AccountAvatar(account, Modifier.size(48.dp)) else Avatar(Modifier.size(48.dp))
            Spacer(Modifier.width(12.dp))
            Column {
                Text(stringResource(R.string.composer_local_draft), style = MaterialTheme.typography.titleMedium)
                Text(account?.handle ?: stringResource(R.string.composer_no_account), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        quoteTarget?.let { target ->
            OutlinedCard(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
                Column(Modifier.padding(12.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        me.foxtails.palustris.ui.emoji.InlineEmojiText(
                            text = if (isReply) stringResource(R.string.composer_replying_to, target.post.author.displayName) else stringResource(R.string.composer_quoting, target.post.author.displayName),
                            emoji = target.post.author.emoji,
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = onRemoveQuote) { Text(stringResource(R.string.composer_remove_quote)) }
                    }
                    if (target.post.text.isBlank()) Text(stringResource(R.string.composer_no_post_text), maxLines = 4, overflow = TextOverflow.Ellipsis)
                    else me.foxtails.palustris.ui.emoji.PostText(target.post, style = MaterialTheme.typography.bodyMedium, maxLines = 4, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.height(12.dp))
        }
        val audienceOptions = (availableAudiences + audience).toList().sortedBy(Audience::ordinal)
        if (audienceOptions.isNotEmpty()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.composer_visibility), style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = {
                    val current = audienceOptions.indexOf(audience).coerceAtLeast(0)
                    onAudienceChange(audienceOptions[(current + 1) % audienceOptions.size])
                }) { Text(audience.label()) }
            }
        }
        ExpandableContent(visible = warningEnabled, modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = warningValue,
                onValueChange = { changed -> onWarningChange(changed.text); warningSelection = changed.selection },
                label = { Text(stringResource(R.string.composer_content_warning)) },
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = {
                    if (onRequestEmoji != null) TextButton(
                        onClick = { onRequestEmoji(ComposerField.Warning) },
                        modifier = Modifier.semantics { contentDescription = emojiPickerDescription },
                    ) { Text(stringResource(R.string.composer_emoji)) }
                },
            )
        }
        BasicTextField(
            value = textValue,
            onValueChange = { changed -> onTextChange(changed.text); textSelection = changed.selection },
            modifier = Modifier.fillMaxWidth().heightIn(min = 240.dp).padding(vertical = 24.dp).semantics { contentDescription = postTextDescription },
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
            decorationBox = { field -> Box { if (text.isEmpty()) Text(stringResource(R.string.composer_placeholder), color = MaterialTheme.colorScheme.onSurfaceVariant); field() } },
        )
        HorizontalDivider()
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                FilterChip(selected = warningEnabled, onClick = { onWarningEnabled(!warningEnabled) }, label = { Text(stringResource(R.string.composer_content_warning)) })
                if (onRequestEmoji != null) TextButton(
                    onClick = { onRequestEmoji(ComposerField.Text) },
                    modifier = Modifier.semantics { contentDescription = emojiPickerDescription },
                ) { Text(stringResource(R.string.emoji_picker_title)) }
                TextButton(onClick = onCleanTrackingParameters) { Text(stringResource(R.string.composer_clean_links)) }
            }
            Text(pluralStringResource(R.plurals.composer_character_count, text.length, text.length), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(if (canPublish) stringResource(R.string.composer_publish_enabled) else stringResource(R.string.composer_publish_disabled), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        AnimatedStatePane(stateKey = error != null, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) { error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) } }
        Spacer(Modifier.height(24.dp))
        Button(onClick = onPublish, enabled = canPublish && !publishing && text.isNotBlank(), modifier = Modifier.align(Alignment.End)) {
            AnimatedContent(
                targetState = publishing,
                transitionSpec = { if (scheme.reducedMotion) EnterTransition.None togetherWith ExitTransition.None else fadeIn(scheme.fastFadeIn) togetherWith fadeOut(scheme.fastFadeOut) },
                label = "publishButtonContent",
            ) { busy -> if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Text(stringResource(R.string.composer_publish)) }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun Audience.label(): String = stringResource(when (this) {
    Audience.Public -> R.string.audience_everyone
    Audience.Unlisted -> R.string.audience_unlisted
    Audience.Followers -> R.string.audience_followers
    Audience.Direct -> R.string.audience_direct
})

@Composable
fun DraftsScreen(
    drafts: List<me.foxtails.palustris.domain.PostDraft>,
    onEdit: (me.foxtails.palustris.domain.PostDraft) -> Unit,
    onDelete: (me.foxtails.palustris.domain.PostDraft) -> Unit,
) {
    val scheme = LocalPalustrisMotionScheme.current
    var confirmDelete by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<me.foxtails.palustris.domain.PostDraft?>(null) }
    if (drafts.isEmpty()) EmptyState(AppIcons.Folder, stringResource(R.string.drafts_empty_title), stringResource(R.string.drafts_empty_subtitle))
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
                    Text(stringResource(R.string.draft_label), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(12.dp))
                    me.foxtails.palustris.ui.emoji.InlineEmojiText(draft.text.ifBlank { draft.contentWarning.orEmpty() }, draft.quotePreview?.postEmoji.orEmpty(), style = MaterialTheme.typography.bodyMedium, maxLines = 5, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.draft_continue), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = { pendingDelete = draft; confirmDelete = true }, modifier = Modifier.align(Alignment.End)) { Text(stringResource(R.string.draft_delete_action)) }
                }
            }
        }
    }
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text(stringResource(R.string.draft_delete_title)) },
        text = { Text(stringResource(R.string.draft_delete_text)) },
        confirmButton = { TextButton(onClick = { confirmDelete = false; pendingDelete?.let(onDelete); pendingDelete = null }) { Text(stringResource(R.string.draft_delete)) } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.dialog_cancel)) } },
    )
}
