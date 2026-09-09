package me.foxtails.palustris.ui.emoji

import me.foxtails.palustris.domain.CustomEmoji

internal sealed interface RichTextSegment {
    val range: IntRange

    data class Text(val text: String, override val range: IntRange) : RichTextSegment

    data class Link(
        val label: List<RichTextSegment>,
        val url: String,
        override val range: IntRange,
    ) : RichTextSegment

    data class Emoji(
        val token: String,
        val emoji: CustomEmoji,
        override val range: IntRange,
    ) : RichTextSegment
}

internal data class RichTextModel(
    val source: String,
    val segments: List<RichTextSegment>,
    /** Source ranges of metadata-backed emoji tokens, for hashtag/decoration filtering. */
    val emojiRanges: List<IntRange>,
)

/**
 * Parses plain text, Markdown link syntax, and metadata-backed custom emoji in one pass.
 * Only tokens present in the entity's emoji map match, preferring the longest match.
 * Link URLs are never parsed. Ranges always index the original source string, and the
 * original shortcode text is preserved for copying and fallback rendering.
 */
internal object EmojiTextParser {
    private val markdownLink = Regex("\\[([^\\]\\r\\n]*)]\\((https?://[^)\\s]+)\\)")

    fun parse(text: String, emoji: Map<String, CustomEmoji>): RichTextModel {
        if (text.isEmpty()) return RichTextModel(text, emptyList(), emptyList())
        val segments = mutableListOf<RichTextSegment>()
        val emojiRanges = mutableListOf<IntRange>()
        parseRange(text, emoji, 0, text.length, segments, emojiRanges)
        return RichTextModel(text, segments, emojiRanges)
    }

    private fun parseRange(
        text: String,
        emoji: Map<String, CustomEmoji>,
        start: Int,
        endExclusive: Int,
        segments: MutableList<RichTextSegment>,
        emojiRanges: MutableList<IntRange>,
    ) {
        var cursor = start
        while (cursor < endExclusive) {
            val link = markdownLink.matchAt(text, cursor)?.takeIf { it.range.last + 1 <= endExclusive }
            if (link != null) {
                val labelStart = cursor + 1
                val labelEnd = labelStart + link.groupValues[1].length
                val labelSegments = mutableListOf<RichTextSegment>()
                val labelEmoji = mutableListOf<IntRange>()
                parseRange(text, emoji, labelStart, labelEnd, labelSegments, labelEmoji)
                segments += RichTextSegment.Link(labelSegments, link.groupValues[2], link.range)
                emojiRanges += labelEmoji
                cursor = link.range.last + 1
                continue
            }
            if (text[cursor] == ':' && emoji.isNotEmpty()) {
                val token = longestKnownToken(text, cursor, endExclusive, emoji)
                if (token != null) {
                    val range = cursor until cursor + token.length
                    segments += RichTextSegment.Emoji(token, emoji.getValue(token), range)
                    emojiRanges += range
                    cursor += token.length
                    continue
                }
            }
            val nextInteresting = nextInterestingPosition(text, cursor + 1, endExclusive)
            segments += RichTextSegment.Text(text.substring(cursor, nextInteresting), cursor until nextInteresting)
            cursor = nextInteresting
        }
    }

    private fun longestKnownToken(
        text: String,
        start: Int,
        endExclusive: Int,
        emoji: Map<String, CustomEmoji>,
    ): String? {
        val colons = mutableListOf<Int>()
        var colon = text.indexOf(':', start + 1)
        while (colon in (start + 1) until endExclusive) {
            colons += colon
            colon = text.indexOf(':', colon + 1)
        }
        for (index in colons.indices.reversed()) {
            val end = colons[index] + 1
            val candidate = text.substring(start, end)
            if (candidate in emoji) return candidate
        }
        return null
    }

    private fun nextInterestingPosition(text: String, from: Int, endExclusive: Int): Int {
        var index = from
        while (index < endExclusive && text[index] != '[' && text[index] != ':') index++
        return index
    }
}
