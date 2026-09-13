package me.foxtails.palustris.ui.emoji

import me.foxtails.palustris.domain.CustomEmoji
import me.foxtails.palustris.domain.EmojiChoice
import me.foxtails.palustris.domain.EmojiPickerGroupIds
import me.foxtails.palustris.domain.EmojiPickerPreferences

internal sealed interface EmojiPickerGroupLabel {
    data object Favorite : EmojiPickerGroupLabel
    data object Recent : EmojiPickerGroupLabel
    data object PostSpecific : EmojiPickerGroupLabel
    data object Standard : EmojiPickerGroupLabel
    data object Custom : EmojiPickerGroupLabel
    data class ServerCategory(val value: String) : EmojiPickerGroupLabel
}

internal data class EmojiPickerGroup(
    val id: String,
    val label: EmojiPickerGroupLabel,
    val choices: List<EmojiChoice>,
    val pinnable: Boolean,
    val collapsed: Boolean,
    val pinned: Boolean,
    val pinEnabled: Boolean,
    val hasMatchingChoices: Boolean = true,
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
        catalogChoices.putIfAbsent(emoji.submissionValue, EmojiChoice(emoji.submissionValue, emoji.token, emoji))
    }
    val unicodeChoices = DefaultUnicodeEmojis.map { EmojiChoice(it, it) }
    val resolvedAdditional = additionalChoices.map { choice ->
        val emoji = choice.emoji ?: catalogChoices[choice.submissionValue]?.emoji
        choice.copy(displayText = emoji?.token ?: choice.displayText, emoji = emoji)
    }.distinctBy { it.submissionValue }
    val postSpecificChoices = resolvedAdditional.filter { it.isCustomIdentity() && it.submissionValue !in catalogChoices }
    val standardChoices = (unicodeChoices + resolvedAdditional.filterNot { it.isCustomIdentity() }).distinctBy { it.submissionValue }
    val choiceMap = linkedMapOf<String, EmojiChoice>().apply {
        catalogChoices.forEach { (identity, choice) -> putIfAbsent(identity, choice) }
        postSpecificChoices.forEach { putIfAbsent(it.submissionValue, it) }
        standardChoices.forEach { putIfAbsent(it.submissionValue, it) }
    }
    val pinnedChoices = preferences.pinnedEmoji.mapNotNull(choiceMap::get).distinctBy { it.submissionValue }
    val pinnedEmojiIds = pinnedChoices.mapTo(mutableSetOf()) { it.submissionValue }
    val recentChoices = recentIdentities.mapNotNull { identity ->
        choiceMap[identity] ?: identity.takeUnless(String::isCustomIdentity)?.let { EmojiChoice(it, it) }
    }.distinctBy { it.submissionValue }.filterNot { it.submissionValue in pinnedEmojiIds }
    val filteredPostSpecificChoices = postSpecificChoices.filterNot { it.submissionValue in pinnedEmojiIds }
    val filteredStandardChoices = standardChoices.filterNot { it.submissionValue in pinnedEmojiIds }
    val groups = linkedMapOf<String, Pair<EmojiPickerGroupLabel, MutableList<EmojiChoice>>>()
    catalogItems.filter { it.visibleInPicker }.forEach { emoji ->
        val category = emoji.category?.takeIf(String::isNotBlank)
        val id = EmojiPickerGroupIds.server(category)
        val label = category?.let(EmojiPickerGroupLabel::ServerCategory) ?: EmojiPickerGroupLabel.Custom
        groups.getOrPut(id) { label to mutableListOf() }.second +=
            (catalogChoices[emoji.submissionValue] ?: EmojiChoice(emoji.submissionValue, emoji.token, emoji))
    }
    groups.values.forEach { (_, choices) -> choices.removeAll { it.submissionValue in pinnedEmojiIds } }
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

    fun createGroup(id: String, label: EmojiPickerGroupLabel, choices: List<EmojiChoice>, pinnable: Boolean): EmojiPickerGroup {
        val collapsed = id in preferences.collapsedGroups
        val pinned = id in preferences.pinnedGroups
        val matchingChoices = choices.filter(::matches)
        return EmojiPickerGroup(
            id = id,
            label = label,
            choices = if (collapsed) emptyList() else matchingChoices,
            pinnable = pinnable,
            collapsed = collapsed,
            pinned = pinned,
            pinEnabled = pinnable && (pinned || pinCount < 5),
            hasMatchingChoices = matchingChoices.isNotEmpty(),
        )
    }
    return buildList {
        fun addIfVisible(group: EmojiPickerGroup) {
            if (group.id == EmojiPickerGroupIds.Favorite) {
                if (group.hasMatchingChoices) add(group)
            } else if (query.isEmpty() || group.hasMatchingChoices) {
                add(group)
            }
        }
        addIfVisible(createGroup(EmojiPickerGroupIds.Favorite, EmojiPickerGroupLabel.Favorite, pinnedChoices, false))
        orderedCustomIds.takeWhile { it in pinnedIds }.forEach { id ->
            val (label, choices) = groups.getValue(id)
            addIfVisible(createGroup(id, label, choices, true))
        }
        if (recentChoices.isNotEmpty()) addIfVisible(createGroup(EmojiPickerGroupIds.Recent, EmojiPickerGroupLabel.Recent, recentChoices, false))
        if (filteredPostSpecificChoices.isNotEmpty()) addIfVisible(createGroup(EmojiPickerGroupIds.PostSpecific, EmojiPickerGroupLabel.PostSpecific, filteredPostSpecificChoices, false))
        orderedCustomIds.drop(pinnedIds.size).forEach { id ->
            val (label, choices) = groups.getValue(id)
            addIfVisible(createGroup(id, label, choices, true))
        }
        addIfVisible(createGroup(EmojiPickerGroupIds.Unicode, EmojiPickerGroupLabel.Standard, filteredStandardChoices, false))
    }
}

private fun EmojiChoice.isCustomIdentity(): Boolean = emoji != null || submissionValue.startsWith(":")

private fun String.isCustomIdentity(): Boolean = startsWith(":")
