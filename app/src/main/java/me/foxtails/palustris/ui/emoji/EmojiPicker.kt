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
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import me.foxtails.palustris.domain.EmojiPickerGroupIds
import me.foxtails.palustris.domain.EmojiPickerPreferences
import me.foxtails.palustris.domain.OwnedPost
import me.foxtails.palustris.domain.ReactionSelectionMode

enum class ComposerField { Text, Warning }

sealed interface EmojiPickerTarget {
    data class Reaction(val post: OwnedPost) : EmojiPickerTarget
    data class Composer(val field: ComposerField) : EmojiPickerTarget
}

private val DefaultUnicodeEmojis = listOf(
    "👍", "❤️", "😂", "🎉", "🤔", "😮", "😢", "👀", "💯", "✨", "🔥", "🙏",
    "😀", "😃", "😄", "😁", "😆", "😅", "🤣", "😊", "😇", "🙂", "🙃", "😉", "😌", "😍", "🥰", "😘",
    "😗", "☺️", "😚", "😋", "😛", "😝", "😜", "🤪", "🤨", "🧐", "🤓", "😎", "🤩", "🥳", "😏", "😒",
    "😞", "😔", "😟", "😕", "🙁", "☹️", "😣", "😖", "😫", "😩", "🥺", "😢", "😭", "😤", "😠", "😡",
    "🤬", "🤯", "😳", "🥵", "🥶", "😱", "😨", "😰", "😥", "😓", "🤗", "🤔", "🫣", "🤭", "🫢", "🫡",
    "🤫", "🫠", "🤥", "😶", "🫥", "😐", "🫤", "😑", "😬", "🙄", "😯", "😦", "😧", "😮", "😲", "🥱",
    "😴", "🤤", "😪", "😵", "🫨", "🤐", "🥴", "🤢", "🤮", "🤧", "😷", "🤒", "🤕", "🤑", "🤠", "👻",
    "💀", "☠️", "👽", "👾", "🤖", "🎃", "😈", "👿", "💩", "🤡", "👹", "👺", "🙈", "🙉", "🙊", "😺",
    "😸", "😹", "😻", "😼", "😽", "🙀", "😿", "😾", "👏", "🙌", "👐", "🤲", "🤝", "🙏", "✍️", "💅",
    "🤳", "💪", "🦾", "🦿", "🦵", "🦶", "👂", "👃", "🧠", "🫀", "🫁", "🦷", "🦴", "👀", "👁️", "👅",
    "👄", "💋", "👋", "🤚", "🖐️", "✋", "🖖", "👌", "🤏", "🤌", "🤘", "🤟", "🤞", "✌️", "🤙", "👈",
    "👉", "👆", "👇", "☝️", "✊", "👊", "🤛", "🤜", "🤚", "👎", "✍️", "💖", "💔", "💕", "💞", "💓",
    "💗", "💘", "💝", "💟", "❣️", "💌", "💋", "💤", "💢", "💥", "💦", "💨", "🕳️", "💣", "💬", "👁️‍🗨️",
    "🐶", "🐱", "🐭", "🐹", "🐰", "🦊", "🐻", "🐼", "🐨", "🐯", "🦁", "🐮", "🐷", "🐽", "🐸", "🐵",
    "🙈", "🙉", "🙊", "🐒", "🐔", "🐧", "🐦", "🐤", "🐣", "🐥", "🦆", "🦅", "🦉", "🦇", "🐺", "🐗",
    "🐴", "🦄", "🐝", "🐛", "🦋", "🐌", "🐞", "🐜", "🕷️", "🦂", "🐢", "🐍", "🦎", "🦖", "🦕", "🐙",
    "🦑", "🦀", "🐠", "🐟", "🐡", "🐬", "🐳", "🐋", "🦈", "🐊", "🐅", "🐆", "🦓", "🦍", "🦧", "🐘",
    "🦏", "🦛", "🐪", "🐫", "🦒", "🦘", "🦬", "🐃", "🐂", "🐄", "🐎", "🐖", "🐏", "🐑", "🦙", "🐐",
    "🦌", "🐕", "🐈", "🐓", "🦃", "🕊️", "🐇", "🐁", "🐀", "🐿️", "🦔", "🌸", "🌹", "🌻", "🌞", "🌝",
    "🌚", "🌛", "🌜", "🌟", "⭐", "🌙", "☀️", "⛅", "☁️", "🌧️", "⛈️", "🌩️", "❄️", "☃️", "🌈", "🔥",
    "🌊", "🍏", "🍎", "🍐", "🍊", "🍋", "🍌", "🍉", "🍇", "🍓", "🫐", "🍈", "🍒", "🍑", "🥭", "🍍",
    "🥥", "🥝", "🍅", "🍆", "🥑", "🥦", "🥬", "🥒", "🌶️", "🫑", "🌽", "🥕", "🧄", "🧅", "🥔", "🍞",
    "🥐", "🥨", "🧀", "🥚", "🍳", "🧈", "🥞", "🧇", "🥓", "🥩", "🍗", "🍔", "🍟", "🍕", "🌭", "🌮",
    "🌯", "🥗", "🍿", "🍜", "🍣", "🍱", "🍚", "🍙", "🍘", "🍥", "🍡", "🥟", "🥠", "🍦", "🍩", "🍪",
    "🎂", "🍰", "🧁", "🍫", "🍬", "🍭", "☕", "🍵", "🧃", "🥤", "🍺", "🍻", "🍷", "🥂", "🍸", "🍹",
    "⚽", "🏀", "🏈", "⚾", "🥎", "🎾", "🏐", "🏉", "🥏", "🎱", "🪀", "🏓", "🏸", "🏒", "🏑", "🥍",
    "🏏", "⛳", "🏹", "🎣", "🤿", "🥊", "🥋", "🎽", "🛹", "🛷", "⛸️", "🥌", "🎿", "⛷️", "🏂", "🪂",
    "🏋️", "🤼", "🤸", "⛹️", "🤺", "🤾", "🏌️", "🏇", "🧘", "🏄", "🏊", "🤽", "🚣", "🧗", "🚵", "🚴",
    "🎮", "🕹️", "🎲", "♟️", "🎯", "🎳", "🎭", "🎨", "🎤", "🎧", "🎼", "🎹", "🥁", "🎷", "🎺", "🎸",
    "🚗", "🚕", "🚌", "🚓", "🚑", "🚒", "🚚", "🚜", "🏎️", "🏍️", "🛵", "🚲", "✈️", "🚀", "🛸", "🚢",
    "🏠", "🏢", "🏥", "🏫", "🏰", "🗽", "🗼", "⛺", "🗻", "🌋", "🗺️", "🧭", "⌚", "📱", "💻", "⌨️",
    "🖨️", "📷", "📺", "☎️", "💡", "📚", "✏️", "📝", "📌", "📎", "🔒", "🔑", "🔨", "🧰", "🎁", "🎈",
    "✅", "❌", "⚠️", "❗", "❓", "‼️", "⁉️", "💯", "🔴", "🟠", "🟡", "🟢", "🔵", "🟣", "⚫", "⚪",
    "🟤", "🔺", "🔻", "🔔", "🔕", "🎵", "🎶", "➕", "➖", "✖️", "➗", "♾️", "✔️", "☑️", "©️", "®️",
)

private data class PickerChoice(
    val choice: EmojiChoice,
    val category: String?,
    val section: PickerSection,
    val selected: Boolean,
)

private enum class PickerSection { Recent, Unicode, Server }

internal data class EmojiPickerGroup(
    val id: String,
    val title: String,
    val choices: List<EmojiChoice>,
    val pinnable: Boolean,
    val collapsed: Boolean,
    val pinned: Boolean,
    val pinEnabled: Boolean,
)

internal fun buildEmojiPickerGroups(
    catalogItems: List<CustomEmoji>,
    additionalChoices: List<EmojiChoice>,
    recentIdentities: List<String>,
    selectedIdentities: Set<String>,
    searchQuery: String,
    preferences: EmojiPickerPreferences,
): List<EmojiPickerGroup> {
    val catalogChoices = linkedMapOf<String, EmojiChoice>()
    catalogItems.filter { it.visibleInPicker }.forEach { emoji ->
        catalogChoices.putIfAbsent(
            emoji.submissionValue,
            EmojiChoice(emoji.submissionValue, emoji.token, emoji),
        )
    }
    val unicodeChoices = DefaultUnicodeEmojis.map { EmojiChoice(it, it) }
    val resolvedAdditional = additionalChoices.map { choice ->
        val emoji = choice.emoji ?: catalogChoices[choice.submissionValue]?.emoji
        choice.copy(
            displayText = emoji?.token ?: choice.displayText,
            emoji = emoji,
        )
    }.distinctBy { it.submissionValue }
    val postSpecificChoices = resolvedAdditional.filter {
        it.isCustomIdentity() && it.submissionValue !in catalogChoices
    }
    val standardChoices = (unicodeChoices + resolvedAdditional.filterNot { it.isCustomIdentity() })
        .distinctBy { it.submissionValue }
    val choiceMap = linkedMapOf<String, EmojiChoice>().apply {
        catalogChoices.forEach { (identity, choice) -> putIfAbsent(identity, choice) }
        postSpecificChoices.forEach { putIfAbsent(it.submissionValue, it) }
        standardChoices.forEach { putIfAbsent(it.submissionValue, it) }
    }
    val recentChoices = recentIdentities.mapNotNull { identity ->
        choiceMap[identity] ?: identity.takeUnless(String::isCustomIdentity).let { value ->
            value?.let { EmojiChoice(it, it) }
        }
    }.distinctBy { it.submissionValue }

    val groups = linkedMapOf<String, Pair<String, MutableList<EmojiChoice>>>()
    catalogItems.filter { it.visibleInPicker }.forEach { emoji ->
        val id = EmojiPickerGroupIds.server(emoji.category?.takeIf(String::isNotBlank))
        val title = emoji.category?.takeIf(String::isNotBlank) ?: "Custom emoji"
        groups.getOrPut(id) { title to mutableListOf() }.second +=
            (catalogChoices[emoji.submissionValue] ?: EmojiChoice(emoji.submissionValue, emoji.token, emoji))
    }

    val pinnedIds = preferences.pinnedGroups.filter { it in groups }
    val customIds = groups.keys.toList()
    val orderedCustomIds = pinnedIds + customIds.filterNot { it in pinnedIds }
    val query = searchQuery.trim()
    val pinCount = preferences.pinnedGroups.count(EmojiPickerGroupIds::isServer)

    fun matches(choice: EmojiChoice): Boolean = query.isEmpty() ||
        choice.submissionValue.contains(query, ignoreCase = true) ||
        choice.displayText.contains(query, ignoreCase = true) ||
        choice.emoji?.shortcode?.contains(query, ignoreCase = true) == true ||
        choice.emoji?.aliases?.any { it.contains(query, ignoreCase = true) } == true

    fun createGroup(
        id: String,
        title: String,
        choices: List<EmojiChoice>,
        pinnable: Boolean,
    ): EmojiPickerGroup {
        val collapsed = id in preferences.collapsedGroups
        val pinned = id in preferences.pinnedGroups
        return EmojiPickerGroup(
            id = id,
            title = title,
            choices = if (collapsed) emptyList() else choices.filter(::matches),
            pinnable = pinnable,
            collapsed = collapsed,
            pinned = pinned,
            pinEnabled = pinnable && (pinned || pinCount < 5),
        )
    }

    return buildList {
        add(createGroup(EmojiPickerGroupIds.Favorite, "Favorite Emoji", emptyList(), pinnable = false))
        orderedCustomIds.takeWhile { it in pinnedIds }.forEach { id ->
            val (title, choices) = groups.getValue(id)
            add(createGroup(id, title, choices, pinnable = true))
        }
        if (recentChoices.isNotEmpty()) {
            add(createGroup(EmojiPickerGroupIds.Recent, "Recent", recentChoices, pinnable = false))
        }
        if (postSpecificChoices.isNotEmpty()) {
            add(createGroup(EmojiPickerGroupIds.PostSpecific, "Post-specific custom emoji", postSpecificChoices, pinnable = false))
        }
        orderedCustomIds.drop(pinnedIds.size).forEach { id ->
            val (title, choices) = groups.getValue(id)
            add(createGroup(id, title, choices, pinnable = true))
        }
        add(createGroup(EmojiPickerGroupIds.Unicode, "Standard emoji", standardChoices, pinnable = false))
    }
}

private fun EmojiChoice.isCustomIdentity(): Boolean = emoji != null || submissionValue.startsWith(":")

private fun String.isCustomIdentity(): Boolean = startsWith(":")

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
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    stringResource(
                        if (target is EmojiPickerTarget.Reaction) R.string.emoji_add_reaction
                        else R.string.emoji_picker_title,
                    ),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
                IconButton(
                    onClick = onRetryCatalog,
                    enabled = !catalog.initialLoading && !catalog.refreshing,
                    modifier = Modifier.testTag("emoji_picker_refresh"),
                ) {
                    Text(stringResource(R.string.common_refresh), style = MaterialTheme.typography.labelLarge)
                }
            }
            when {
                readOnlyReactions -> ReadOnlyReactions(target as EmojiPickerTarget.Reaction)
                else -> Column(Modifier.fillMaxWidth()) {
                    if (catalog.initialLoading || catalog.refreshing) {
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            Text(
                                stringResource(
                                    if (catalog.initialLoading) R.string.emoji_picker_loading
                                    else R.string.emoji_picker_refreshing,
                                ),
                                Modifier.padding(start = 8.dp),
                            )
                        }
                    }
                    if (catalog.unsupported) {
                        PickerMessage(stringResource(R.string.emoji_picker_unsupported))
                    }
                    catalog.error?.let { error ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(error, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                            TextButton(onClick = onRetryCatalog) { Text(stringResource(R.string.emoji_picker_retry)) }
                        }
                    }
                    EmojiChoiceGrid(
                    catalogItems = catalog.items,
                    preferences = catalog.preferences,
                    additionalChoices = (target as? EmojiPickerTarget.Reaction)?.post?.post?.reactions?.map { reaction ->
                        EmojiChoice(reaction.emoji, reaction.emoji, reaction.emojiMetadata)
                    }.orEmpty(),
                    selectedIdentities = (target as? EmojiPickerTarget.Reaction)?.post?.post?.let { post ->
                        buildSet {
                            post.selectedReactions.forEach { add(it.submissionValue) }
                            post.reactions.filter { it.selected }.forEach { add(it.emoji) }
                            post.myReaction?.let(::add)
                        }
                    }.orEmpty(),
                    onEmojiSelected = { choice ->
                        onEmojiSelected(choice)
                        onDismiss()
                    },
                    )
                }
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
fun EmojiChoiceGrid(
    catalogItems: List<CustomEmoji>,
    additionalChoices: List<EmojiChoice> = emptyList(),
    selectedIdentities: Set<String> = emptySet(),
    preferences: EmojiPickerPreferences = EmojiPickerPreferences(),
    compact: Boolean = false,
    modifier: Modifier = Modifier,
    testTag: String = "emoji_picker_grid",
    onEmojiSelected: (EmojiChoice) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var recents by rememberSaveable { mutableStateOf(listOf<String>()) }
    val unicodeChoices = remember {
        DefaultUnicodeEmojis.map { EmojiChoice(it, it) }
    }
    val recentChoices = remember(recents) {
        recents.map { EmojiChoice(it, it) }
    }
    val serverChoices = remember(catalogItems, additionalChoices) {
        (catalogItems.filter { it.visibleInPicker }.map { item ->
            EmojiChoice(
                submissionValue = item.submissionValue,
                displayText = item.token,
                emoji = item,
            )
        } + additionalChoices).distinctBy { it.submissionValue }
    }
    val allChoices = remember(recentChoices, unicodeChoices, serverChoices, selectedIdentities) {
        buildList {
            recentChoices.forEach { add(PickerChoice(it, null, PickerSection.Recent, false)) }
            unicodeChoices.forEach { add(PickerChoice(it, null, PickerSection.Unicode, false)) }
            serverChoices.forEach { add(PickerChoice(it, it.emoji?.category, PickerSection.Server, false)) }
        }.distinctBy { it.choice.submissionValue }
            .map { it.copy(selected = it.choice.submissionValue in selectedIdentities) }
    }
    val compactChoices = remember(allChoices) {
        buildList {
            addAll(allChoices.filter { it.section == PickerSection.Recent })
            addAll(allChoices.filter { it.section == PickerSection.Unicode }.take(COMPACT_UNICODE_LIMIT))
            addAll(allChoices.filter { it.section == PickerSection.Server }.take(COMPACT_SERVER_LIMIT))
            addAll(allChoices.filter { it.selected })
        }.distinctBy { it.choice.submissionValue }
    }
    if (compact) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(48.dp),
            modifier = modifier.fillMaxWidth().heightIn(max = 160.dp).testTag(testTag),
            contentPadding = PaddingValues(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(compactChoices, key = { "compact-${it.choice.submissionValue}" }) { picker ->
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
        return
    }
    val groups = remember(catalogItems, additionalChoices, recents, selectedIdentities, query, preferences) {
        buildEmojiPickerGroups(
            catalogItems = catalogItems,
            additionalChoices = additionalChoices,
            recentIdentities = recents,
            selectedIdentities = selectedIdentities,
            searchQuery = query,
            preferences = preferences,
        )
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
            modifier = modifier
                .fillMaxWidth()
                .testTag(testTag),
            contentPadding = PaddingValues(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            groups.forEach { group ->
                item(key = "header-${group.id}", span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                    Text(
                        group.title,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                    )
                }
                group.choices.forEach { choice ->
                    item(key = "${group.id}-${choice.submissionValue}") {
                        PickerCell(
                            choice = choice,
                            selected = choice.submissionValue in selectedIdentities,
                            onClick = {
                                recents = (listOf(choice.submissionValue) +
                                    recents.filterNot { it == choice.submissionValue }).take(RECENT_LIMIT)
                                onEmojiSelected(choice)
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
private const val COMPACT_UNICODE_LIMIT = 8
private const val COMPACT_SERVER_LIMIT = 4
