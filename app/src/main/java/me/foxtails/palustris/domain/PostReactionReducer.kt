package me.foxtails.palustris.domain

/**
 * Pure reducer for reaction mutations shared by every post collection.
 * Reaction identities are opaque: the reducer matches `Reaction.emoji` against
 * `EmojiChoice.submissionValue` and never rewrites either value.
 */
object PostReactionReducer {
    fun apply(
        post: Post,
        choice: EmojiChoice,
        selected: Boolean,
        selectionMode: ReactionSelectionMode,
        primaryFavouriteEmoji: String? = null,
    ): Post {
        val identity = choice.submissionValue
        val previousSelections = post.selectedReactions.ifEmpty {
            post.myReaction?.takeIf { it.isNotBlank() }?.let { mine ->
                listOf(EmojiChoice(mine, mine, post.reactions.firstOrNull { it.emoji == mine }?.emojiMetadata))
            }.orEmpty()
        }
        val selectionsToRemove = when {
            !selected -> previousSelections.filter { it.submissionValue == identity }
            selectionMode == ReactionSelectionMode.Independent -> emptyList()
            else -> previousSelections.filter { it.submissionValue != identity }
        }
        val keptSelections = previousSelections.filter { current ->
            current.submissionValue != identity && selectionsToRemove.none { it.submissionValue == current.submissionValue }
        }.toMutableList()
        var reactions = post.reactions.toMutableList()
        selectionsToRemove.forEach { removed -> reactions = decrement(reactions, removed) }
        if (selected && keptSelections.none { it.submissionValue == identity }) {
            reactions = increment(reactions, identity, choice.emoji)
            keptSelections += choice
        }
        val selectedIdentities = keptSelections.map { it.submissionValue }.toSet()
        reactions = reactions.map { reaction ->
            reaction.copy(selected = reaction.emoji in selectedIdentities)
        }.toMutableList()
        val primary = when {
            primaryFavouriteEmoji != null -> keptSelections.firstOrNull { it.submissionValue == primaryFavouriteEmoji }
                ?: keptSelections.firstOrNull()
            keptSelections.size == 1 -> keptSelections.single()
            else -> null
        }
        return post.copy(
            reactions = reactions,
            selectedReactions = keptSelections,
            myReaction = primary?.submissionValue,
            favourited = if (primaryFavouriteEmoji != null) {
                keptSelections.any { it.submissionValue == primaryFavouriteEmoji }
            } else {
                post.favourited
            },
        )
    }

    private fun decrement(reactions: MutableList<Reaction>, removed: EmojiChoice): MutableList<Reaction> {
        val identity = removed.submissionValue
        val index = reactions.indexOfFirst { it.emoji == identity }
        if (index < 0) return reactions
        val reaction = reactions[index]
        return if (reaction.count <= 1) {
            reactions.apply { removeAt(index) }
        } else {
            reactions.apply { this[index] = reaction.copy(count = reaction.count - 1, selected = false) }
        }
    }

    private fun increment(
        reactions: MutableList<Reaction>,
        identity: String,
        metadata: CustomEmoji?,
    ): MutableList<Reaction> {
        val index = reactions.indexOfFirst { it.emoji == identity }
        if (index >= 0) {
            val reaction = reactions[index]
            reactions[index] = reaction.copy(
                count = reaction.count + 1,
                selected = true,
                emojiMetadata = metadata ?: reaction.emojiMetadata,
            )
        } else {
            reactions += Reaction(identity, 1, selected = true, emojiMetadata = metadata)
        }
        return reactions
    }
}
