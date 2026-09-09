@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package me.foxtails.palustris.ui.emoji

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.foxtails.palustris.R
import me.foxtails.palustris.domain.CustomEmoji
import me.foxtails.palustris.domain.EmojiChoice
import me.foxtails.palustris.domain.OwnedPost
import me.foxtails.palustris.domain.ReactionSelectionMode

enum class ComposerField { Text, Warning }

sealed interface EmojiPickerTarget {
    data class Reaction(val post: OwnedPost) : EmojiPickerTarget
    data class Composer(val field: ComposerField) : EmojiPickerTarget
}

private val DefaultUnicodeEmojis = listOf("👍", "❤️", "😂", "🎉", "🤔", "😮", "😢", "👀", "💯", "✨", "🔥", "🙏")

private data class PickerChoice(
    val choice: EmojiChoice,
    val category: String?,
    val section: PickerSection,
    val selected: Boolean,
)

private enum class PickerSection { Recent, Unicode, Server }

/**
 * Reusable picker body and modal-sheet wrapper for reaction selection and composer
 * insertion. The grid container stays transparent; only the Material sheet and
 * individual selected/pressed cells carry a surface. The picker never alters page
 * viewport padding. A null target renders nothing.
 */
@Composable
fun EmojiPickerHost(
    target: EmojiPickerTarget?,
    catalog: EmojiCatalogState,
    selectionMode: ReactionSelectionMode,
    mutationSupported: Boolean,
    onLoadCatalog: () -> Unit,
    onRetryCatalog: () -> Unit,
    onDismiss: () -> Unit,
    onEmojiSelected: (EmojiChoice) -> Unit,
) {
    if (target == null) return
    LaunchedEffect(target) { onLoadCatalog() }
    val readOnlyReactions = target is EmojiPickerTarget.Reaction && !mutationSupported
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.testTag("emoji_picker_sheet"),
    ) {
        Column(Modifier.fillMaxWidth().heightIn(min = 320.dp).padding(horizontal = 16.dp)) {
            Text(
                stringResource(R.string.emoji_picker_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(vertical = 4.dp),
            )
            when {
                readOnlyReactions -> ReadOnlyReactions(target as EmojiPickerTarget.Reaction)
                catalog.loading -> Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(Modifier.size(28.dp))
                    Text(stringResource(R.string.emoji_picker_loading), Modifier.padding(top = 12.dp))
                }
                catalog.unsupported -> PickerMessage(stringResource(R.string.emoji_picker_unsupported))
                catalog.error != null -> Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(catalog.error, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                    TextButton(onClick = onRetryCatalog) { Text(stringResource(R.string.emoji_picker_retry)) }
                }
                catalog.items.isEmpty() && !catalog.empty -> PickerMessage(stringResource(R.string.emoji_picker_empty))
                else -> PickerGrid(
                    target = target,
                    catalogItems = catalog.items,
                    onEmojiSelected = { choice ->
                        onEmojiSelected(choice)
                        onDismiss()
                    },
                )
            }
            Box(Modifier.size(24.dp))
        }
    }
}

@Composable
private fun PickerMessage(message: String) {
    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

@Composable
private fun ReadOnlyReactions(target: EmojiPickerTarget.Reaction) {
    val post = target.post.post
    LazyVerticalGrid(
        columns = GridCells.Adaptive(48.dp),
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        contentPadding = PaddingValues(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(post.reactions, key = { it.emoji }) { reaction ->
            Box(
                Modifier
                    .heightIn(min = 48.dp)
                    .semantics {
                        contentDescription = "${reaction.emoji}, ${reaction.count}"
                        this.selected = reaction.selected
                    },
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CustomEmojiImage(
                        emoji = reaction.emojiMetadata,
                        fallbackText = reaction.emoji,
                        modifier = Modifier.size(28.dp),
                    )
                    Text(reaction.count.toString(), style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
private fun PickerGrid(
    target: EmojiPickerTarget,
    catalogItems: List<CustomEmoji>,
    onEmojiSelected: (EmojiChoice) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var recents by rememberSaveable { mutableStateOf(listOf<String>()) }
    val post = (target as? EmojiPickerTarget.Reaction)?.post?.post
    val selectedIdentities = remember(post) {
        (post?.selectedReactions?.map { it.submissionValue }.orEmpty()).toSet()
    }
    val unicodeChoices = remember {
        DefaultUnicodeEmojis.map { EmojiChoice(it, it) }
    }
    val recentChoices = remember(recents) {
        recents.map { EmojiChoice(it, it) }
    }
    val serverChoices = remember(catalogItems) {
        catalogItems.map { item ->
            EmojiChoice(
                submissionValue = item.submissionValue,
                displayText = item.token,
                emoji = item,
            )
        }
    }
    val allChoices = remember(recentChoices, unicodeChoices, serverChoices) {
        buildList {
            recentChoices.forEach { add(PickerChoice(it, null, PickerSection.Recent, false)) }
            unicodeChoices.forEach { add(PickerChoice(it, null, PickerSection.Unicode, false)) }
            serverChoices.forEach { add(PickerChoice(it, it.emoji?.category, PickerSection.Server, false)) }
        }.map { it.copy(selected = it.choice.submissionValue in selectedIdentities) }
    }
    val filtered = remember(allChoices, query) {
        val needle = query.trim()
        if (needle.isEmpty()) {
            allChoices
        } else {
            allChoices.filter { picker ->
                picker.choice.submissionValue.contains(needle, ignoreCase = true) ||
                    picker.choice.displayText.contains(needle, ignoreCase = true) ||
                    picker.choice.emoji?.shortcode?.contains(needle, ignoreCase = true) == true ||
                    picker.choice.emoji?.aliases?.any { it.contains(needle, ignoreCase = true) } == true
            }
        }
    }
    Column(Modifier.fillMaxWidth()) {
        TextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .testTag("emoji_picker_search"),
            placeholder = { Text(stringResource(R.string.emoji_picker_search_hint)) },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            colors = TextFieldDefaults.colors(
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
        )
        LazyVerticalGrid(
            columns = GridCells.Adaptive(48.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("emoji_picker_grid"),
            contentPadding = PaddingValues(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val sections = filtered.groupBy { it.section }
            val sectionHeaders = listOf(
                PickerSection.Recent to stringResource(R.string.emoji_recent_section),
                PickerSection.Unicode to stringResource(R.string.emoji_unicode_section),
            )
            sectionHeaders.forEach { (section, header) ->
                val sectionChoices = sections[section].orEmpty()
                if (sectionChoices.isNotEmpty()) {
                    item(key = "header-$section", span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                        Text(
                            header,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                        )
                    }
                    sectionChoices.forEach { picker ->
                        item(key = "${section}-${picker.choice.submissionValue}") {
                            PickerCell(
                                choice = picker.choice,
                                selected = picker.selected,
                                onClick = {
                                    recents = (listOf(picker.choice.submissionValue) +
                                        recents.filterNot { it == picker.choice.submissionValue }).take(RECENT_LIMIT)
                                    onEmojiSelected(picker.choice)
                                },
                            )
                        }
                    }
                }
            }
            val serverChoices = sections[PickerSection.Server].orEmpty()
            serverChoices.groupBy { it.category }.forEach { (category, categoryChoices) ->
                if (category != null) {
                    item(key = "category-$category", span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                        Text(
                            category,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                        )
                    }
                }
                categoryChoices.forEach { picker ->
                    item(key = "server-${picker.choice.submissionValue}") {
                        PickerCell(
                            choice = picker.choice,
                            selected = picker.selected,
                            onClick = {
                                recents = (listOf(picker.choice.submissionValue) +
                                    recents.filterNot { it == picker.choice.submissionValue }).take(RECENT_LIMIT)
                                onEmojiSelected(picker.choice)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PickerCell(
    choice: EmojiChoice,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val background = when {
        selected -> MaterialTheme.colorScheme.secondaryContainer
        pressed -> MaterialTheme.colorScheme.surfaceContainer
        else -> Color.Transparent
    }
    val custom = choice.emoji
    val description = custom?.let { stringResource(R.string.custom_emoji_image_description, it.shortcode) }
        ?: choice.displayText
    Box(
        Modifier
            .heightIn(min = 48.dp)
            .background(background, RoundedCornerShape(12.dp))
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .semantics {
                contentDescription = description
                role = Role.Button
                this.selected = selected
            }
            .testTag("emoji_picker_cell_${choice.submissionValue}"),
        contentAlignment = Alignment.Center,
    ) {
        if (custom != null) {
            CustomEmojiImage(
                emoji = custom,
                fallbackText = custom.token,
                modifier = Modifier.size(32.dp),
            )
        } else {
            Text(
                choice.displayText,
                fontSize = 24.sp,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip,
                textAlign = TextAlign.Center,
            )
        }
    }
}

private const val RECENT_LIMIT = 16
