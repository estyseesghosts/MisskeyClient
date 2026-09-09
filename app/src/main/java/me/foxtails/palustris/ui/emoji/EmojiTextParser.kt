package me.foxtails.palustris.ui.emoji

import me.foxtails.palustris.domain.CustomEmoji

internal sealed interface RichTextSegment {
    val range: IntRange

    data class Text(val text: String, override val range: IntRange) : RichTextSegment

    data class Link(
        val label: List<RichTextSegment>,
        val url: String,
        override val range: IntRange,
        val plainUrl: Boolean = false,
    ) : RichTextSegment {
        val displayLabel: String get() = richTextDisplayText(label)
        val target: String get() = url
    }

    data class Username(
        val displayLabel: String,
        val target: String,
        override val range: IntRange,
    ) : RichTextSegment

    data class Hashtag(
        val displayLabel: String,
        val target: String,
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

/** Text shown for a segment, excluding Markdown destinations and hidden identity domains. */
internal fun richTextDisplayText(segment: RichTextSegment): String = when (segment) {
    is RichTextSegment.Text -> segment.text
    is RichTextSegment.Link -> segment.displayLabel
    is RichTextSegment.Username -> segment.displayLabel
    is RichTextSegment.Hashtag -> segment.displayLabel
    is RichTextSegment.Emoji -> segment.token
}

internal fun richTextDisplayText(segments: List<RichTextSegment>): String =
    segments.joinToString(separator = "", transform = ::richTextDisplayText)

/** Pixelfed-style instance tag pages are presentation-only wrappers around a hashtag. */
internal fun isInstanceTagSearchUrl(url: String): Boolean =
    Regex("(?i)^https?://[^/\\s]+/(?:discover/)?tags/[^/?#]+(?:[/?#].*)?$").matches(url)

/**
 * Parses text, Markdown links, plain URLs, WebFinger usernames, hashtags, and
 * metadata-backed custom emoji in one pass. Ranges always index the original source.
 */
internal object EmojiTextParser {
    private val markdownLink = Regex("""\[([^\]\r\n]*)\]\(\s*(?:<)?(https?://[^)\s>]+)(?:>)?\s*\)""")
    private val plainUrl = Regex("""https?://[^\s<>()\[\]]+""")
    private val username = Regex("""@[\p{L}\p{N}_.-]+@[\p{L}\p{N}.-]+""")
    private val hashtag = Regex("#[\\p{L}\\p{N}_](?:[\\p{L}\\p{N}\\p{M}_])*")

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
                val labelStart = link.range.first + 1
                val labelEnd = labelStart + link.groupValues[1].length
                val label = link.groupValues[1]
                if (hashtag.matches(label) && isInstanceTagSearchUrl(link.groupValues[2])) {
                    segments += RichTextSegment.Hashtag(label, label, link.range)
                } else {
                    val labelSegments = mutableListOf<RichTextSegment>()
                    val labelEmoji = mutableListOf<IntRange>()
                    parseRange(text, emoji, labelStart, labelEnd, labelSegments, labelEmoji)
                    segments += RichTextSegment.Link(labelSegments, link.groupValues[2], link.range)
                    emojiRanges += labelEmoji
                }
                cursor = link.range.last + 1
                continue
            }

            val url = plainUrl.matchAt(text, cursor)?.takeIf { it.range.last + 1 <= endExclusive }
            if (url != null) {
                val urlEnd = trimUrlEnd(text, url.range.last + 1)
                if (urlEnd > cursor) {
                    val sourceRange = cursor until urlEnd
                    segments += RichTextSegment.Link(
                        label = listOf(RichTextSegment.Text("url.xyz", sourceRange)),
                        url = text.substring(sourceRange),
                        range = sourceRange,
                        plainUrl = true,
                    )
                    cursor = urlEnd
                    continue
                }
            }

            if (text[cursor] == ':' && emoji.isNotEmpty()) {
                val token = longestKnownToken(text, cursor, endExclusive, emoji)
                val value = token?.let { emojiForToken(it, emoji) }
                if (token != null && value != null) {
                    val range = cursor until cursor + token.length
                    segments += RichTextSegment.Emoji(token, value, range)
                    emojiRanges += range
                    cursor += token.length
                    continue
                }
            }

            val usernameMatch = username.matchAt(text, cursor)
                ?.takeIf { it.range.last + 1 <= endExclusive && entityBoundaryBefore(text, cursor) }
            if (usernameMatch != null) {
                val target = usernameMatch.value
                val handle = target.substringBeforeLast('@')
                segments += RichTextSegment.Username(handle, target, usernameMatch.range)
                cursor = usernameMatch.range.last + 1
                continue
            }

            val hashtagMatch = hashtag.matchAt(text, cursor)
                ?.takeIf { it.range.last + 1 <= endExclusive && entityBoundaryBefore(text, cursor) }
            if (hashtagMatch != null) {
                segments += RichTextSegment.Hashtag(hashtagMatch.value, hashtagMatch.value, hashtagMatch.range)
                cursor = hashtagMatch.range.last + 1
                continue
            }

            val nextInteresting = nextInterestingPosition(text, cursor + 1, endExclusive)
            val plainText = text.substring(cursor, nextInteresting)
            appendText(segments, plainText, cursor until nextInteresting)
            cursor = nextInteresting
        }
    }

    private fun appendText(
        segments: MutableList<RichTextSegment>,
        text: String,
        range: IntRange,
    ) {
        val previous = segments.lastOrNull() as? RichTextSegment.Text
        if (previous != null && previous.range.last + 1 == range.first) {
            segments[segments.lastIndex] = RichTextSegment.Text(
                previous.text + text,
                previous.range.first..range.last,
            )
        } else {
            segments += RichTextSegment.Text(text, range)
        }
    }

    private fun trimUrlEnd(text: String, endExclusive: Int): Int {
        var end = endExclusive
        while (end > 0 && text[end - 1] in ".,!?;:") end--
        return end
    }

    private fun entityBoundaryBefore(text: String, start: Int): Boolean {
        if (start == 0) return true
        val previous = text.codePointBefore(start)
        return Character.isWhitespace(previous) || previous == '('.code || previous == '['.code ||
            previous == '{'.code || previous == '"'.code || previous == '\''.code ||
            previous == '.'.code || previous == ','.code || previous == ';'.code ||
            previous == ':'.code || previous == '!'.code || previous == '?'.code
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
            val bare = candidate.removeSurrounding(":")
            if (candidate in emoji || (bare.isNotBlank() && bare in emoji)) return candidate
        }
        return null
    }

    private fun emojiForToken(token: String, emoji: Map<String, CustomEmoji>): CustomEmoji? {
        val bare = token.removeSurrounding(":")
        return emoji[token] ?: bare.takeIf { it.isNotBlank() }?.let(emoji::get)
    }

    private fun nextInterestingPosition(text: String, from: Int, endExclusive: Int): Int {
        var index = from
        while (index < endExclusive && text[index] !in charArrayOf('[', ':', '@', '#', 'h', 'H')) index++
        return index
    }
}
