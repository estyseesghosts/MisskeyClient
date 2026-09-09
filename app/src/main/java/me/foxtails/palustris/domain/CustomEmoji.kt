package me.foxtails.palustris.domain

/**
 * Protocol-neutral custom emoji metadata for one server entity.
 * `shortcode` is the plain name without colons; `submissionValue` is the opaque
 * identity the server accepts for reactions and must never be rewritten by UI code.
 */
data class CustomEmoji(
    val shortcode: String,
    val animatedUrl: ValidatedUrl?,
    val staticUrl: ValidatedUrl?,
    val category: String? = null,
    val aliases: List<String> = emptyList(),
    val visibleInPicker: Boolean = true,
    val submissionValue: String,
) {
    /** The original `:shortcode:` token doubles as fallback and accessibility text. */
    val token: String get() = ":$shortcode:"
}

/**
 * One picker/submission choice. Unicode emoji carry no metadata; custom emoji keep
 * both image metadata and their opaque submission identity so optimistic reactions
 * retain rendering information.
 */
data class EmojiChoice(
    val submissionValue: String,
    val displayText: String,
    val emoji: CustomEmoji? = null,
)
