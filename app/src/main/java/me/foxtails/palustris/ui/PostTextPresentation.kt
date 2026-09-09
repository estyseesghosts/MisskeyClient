package me.foxtails.palustris.ui

import me.foxtails.palustris.domain.CustomEmoji
import me.foxtails.palustris.ui.emoji.EmojiTextParser

internal data class PostTextPresentation(
    val visibleText: String,
    val filteredHashtags: List<String>,
    val filteredRanges: List<IntRange>,
)

internal const val PostBodyCharacterLimit = 350

internal fun truncatedPostBody(text: String): String {
    if (text.codePointCount(0, text.length) <= PostBodyCharacterLimit) return text
    val end = text.offsetByCodePoints(0, PostBodyCharacterLimit)
    return text.substring(0, end) + "…"
}

private data class HashtagToken(
    val range: IntRange,
    val value: String,
)

private data class RemovalRange(
    val start: Int,
    val endExclusive: Int,
    val detachedLine: Boolean,
)

private val hashtagToken = Regex("#[\\p{L}\\p{N}_](?:[\\p{L}\\p{N}\\p{M}_])*")

/**
 * Removes terminal hashtag lists and detached decorative hashtag blocks from post text.
 * Metadata-backed custom emoji tokens count as decorative separators; their ranges come
 * from the shared parser so hashtag positions never shift.
 */
internal fun parseHashtagBlocks(
    text: String,
    emoji: Map<String, CustomEmoji> = emptyMap(),
): PostTextPresentation {
    if (text.isEmpty()) return PostTextPresentation("", emptyList(), emptyList())

    val emojiRanges = if (emoji.isEmpty()) emptyList() else EmojiTextParser.parse(text, emoji).emojiRanges
    val tokens = findHashtagTokens(text, emojiRanges)
    if (tokens.isEmpty()) return PostTextPresentation(text, emptyList(), emptyList())

    val removals = mutableListOf<RemovalRange>()
    val semanticEnd = semanticEnd(text)
    val terminalTokens = findTerminalHashtags(text, tokens, semanticEnd, emojiRanges)
    if (terminalTokens.isNotEmpty()) {
        val firstStart = includeTerminalWhitespaceBefore(text, terminalTokens.first().range.first)
        removals += RemovalRange(firstStart, text.length, detachedLine = false)
    }

    findDetachedBlocks(text, tokens, emojiRanges).forEach { block ->
        removals += block
    }

    val mergedRemovals = mergeRemovals(removals)
    if (mergedRemovals.isEmpty()) return PostTextPresentation(text, emptyList(), emptyList())

    val filteredHashtags = tokens
        .filter { token -> mergedRemovals.any { it.contains(token.range) } }
        .map(HashtagToken::value)
    val visibleText = removeRanges(text, mergedRemovals)
    val filteredRanges = mergedRemovals.map { it.start until it.endExclusive }

    return PostTextPresentation(visibleText, filteredHashtags, filteredRanges)
}

private fun findHashtagTokens(text: String, emojiRanges: List<IntRange>): List<HashtagToken> {
    val tokens = mutableListOf<HashtagToken>()
    var searchStart = 0
    while (searchStart < text.length) {
        val hash = text.indexOf('#', searchStart)
        if (hash < 0) break

        val match = hashtagToken.matchAt(text, hash)
        if (match != null) {
            val range = markdownHashtagLinkRange(text, match.range) ?: match.range
            if (hashtagBoundaryIsValid(text, range, emojiRanges)) {
                tokens += HashtagToken(range, match.value)
            }
            searchStart = range.last + 1
        } else {
            searchStart = hash + 1
        }
    }
    return tokens
}

private val markdownLink = Regex("\\[([^]\\r\\n]*)\\]\\(https?://[^)\\s]+\\)")

/** A linked hashtag is one token for block detection, while linked prose remains visible. */
private fun markdownHashtagLinkRange(text: String, hashtagRange: IntRange): IntRange? =
    markdownLink.findAll(text).firstOrNull { link ->
        val labelStart = link.range.first + 1
        val labelEndExclusive = labelStart + link.groupValues[1].length
        hashtagRange.first >= labelStart && hashtagRange.last + 1 <= labelEndExclusive &&
            text.substring(labelStart, labelEndExclusive).trim() == text.substring(hashtagRange)
    }?.range

private fun hashtagBoundaryIsValid(text: String, range: IntRange, emojiRanges: List<IntRange>): Boolean {
    val before = codePointBefore(text, range.first)
    val after = codePointAtOrNull(text, range.last + 1)
    val beforeInEmoji = before != null &&
        emojiRanges.any { range.first - Character.charCount(before) in it }
    val afterInEmoji = emojiRanges.any { range.last + 1 in it }
    return (before == null || isHashtagBoundary(before) || beforeInEmoji) &&
        (after == null || isHashtagBoundary(after) || afterInEmoji)
}

private fun isHashtagBoundary(codePoint: Int): Boolean =
    isWhitespaceOrFormatting(codePoint) || isApprovedDecorativeSeparator(codePoint)

private fun findTerminalHashtags(
    text: String,
    tokens: List<HashtagToken>,
    semanticEnd: Int,
    emojiRanges: List<IntRange>,
): List<HashtagToken> {
    val terminalCandidates = tokens.filter { it.range.last + 1 <= semanticEnd }
    val last = terminalCandidates.lastOrNull() ?: return emptyList()
    if (!onlyTerminalSeparators(text, last.range.last + 1, semanticEnd, emojiRanges)) return emptyList()

    val result = mutableListOf(last)
    var index = terminalCandidates.lastIndex - 1
    while (index >= 0) {
        val previous = terminalCandidates[index]
        val following = result.first()
        if (!onlyTerminalSeparators(text, previous.range.last + 1, following.range.first, emojiRanges)) break
        result.add(0, previous)
        index--
    }
    return result
}

private fun findDetachedBlocks(
    text: String,
    tokens: List<HashtagToken>,
    emojiRanges: List<IntRange>,
): List<RemovalRange> {
    val lines = linesOf(text)
    val qualifyingLines = lines.map { line ->
        val lineTokens = tokens.filter { token ->
            token.range.first >= line.start && token.range.last < line.contentEndExclusive
        }
        line to lineTokens.takeIf { it.isNotEmpty() && lineContainsOnlySeparators(text, line, it, emojiRanges) }
    }

    val blocks = mutableListOf<RemovalRange>()
    var index = 0
    while (index < qualifyingLines.size) {
        val firstTokens = qualifyingLines[index].second
        if (firstTokens == null) {
            index++
            continue
        }

        var lastIndex = index
        var hashtagCount = firstTokens.size
        while (lastIndex + 1 < qualifyingLines.size) {
            val nextTokens = qualifyingLines[lastIndex + 1].second ?: break
            lastIndex++
            hashtagCount += nextTokens.size
        }
        if (hashtagCount >= 2) {
            val firstLine = qualifyingLines[index].first
            val lastLine = qualifyingLines[lastIndex].first
            val blockAtEnd = lastLine.lineBreakEndExclusive == text.length
            val start = if (blockAtEnd && firstLine.start > 0 && text[firstLine.start - 1] == '\n') {
                if (firstLine.start > 1 && text[firstLine.start - 2] == '\r') firstLine.start - 2 else firstLine.start - 1
            } else {
                firstLine.start
            }
            blocks += RemovalRange(
                start = start,
                endExclusive = lastLine.lineBreakEndExclusive,
                detachedLine = true,
            )
        }
        index = lastIndex + 1
    }
    return blocks
}

private fun lineContainsOnlySeparators(
    text: String,
    line: TextLine,
    tokens: List<HashtagToken>,
    emojiRanges: List<IntRange>,
): Boolean {
    var cursor = line.start
    tokens.forEach { token ->
        if (!onlyBlockSeparators(text, cursor, token.range.first, emojiRanges)) return false
        cursor = token.range.last + 1
    }
    return onlyBlockSeparators(text, cursor, line.contentEndExclusive, emojiRanges)
}

private fun mergeRemovals(removals: List<RemovalRange>): List<RemovalRange> {
    if (removals.isEmpty()) return emptyList()
    val sorted = removals.sortedWith(compareBy<RemovalRange> { it.start }.thenBy { it.endExclusive })
    val merged = mutableListOf<RemovalRange>()
    sorted.forEach { next ->
        val previous = merged.lastOrNull()
        if (previous == null || next.start > previous.endExclusive) {
            merged += next
        } else {
            merged[merged.lastIndex] = RemovalRange(
                start = previous.start,
                endExclusive = maxOf(previous.endExclusive, next.endExclusive),
                detachedLine = previous.detachedLine || next.detachedLine,
            )
        }
    }
    return merged
}

private fun RemovalRange.contains(range: IntRange): Boolean =
    start <= range.first && range.last + 1 <= endExclusive

private fun removeRanges(text: String, ranges: List<RemovalRange>): String {
    val result = StringBuilder(text.length)
    var cursor = 0
    ranges.forEach { range ->
        result.append(text, cursor, range.start)
        if (range.detachedLine) {
            trimExcessBlankLinesAtBoundary(result, text, range.endExclusive)
        }
        cursor = range.endExclusive
    }
    result.append(text, cursor, text.length)
    return result.toString()
}

private fun trimExcessBlankLinesAtBoundary(result: StringBuilder, source: String, suffixStart: Int) {
    var before = trailingLineBreakCount(result)
    var after = suffixStart
    while (after < source.length && source[after] == '\n') after++
    val afterCount = after - suffixStart
    if (afterCount == 0) return
    var excess = before + afterCount - 2
    if (excess <= 0) return

    while (excess > 0 && before > 0) {
        result.deleteCharAt(result.length - 1)
        if (result.isNotEmpty() && result.last() == '\r') result.deleteCharAt(result.length - 1)
        before--
        excess--
    }
}

private fun trailingLineBreakCount(text: StringBuilder): Int {
    var count = 0
    var index = text.length - 1
    while (index >= 0 && text[index] == '\n') {
        count++
        index--
    }
    return count
}

private fun onlyTerminalSeparators(
    text: String,
    start: Int,
    endExclusive: Int,
    emojiRanges: List<IntRange>,
): Boolean = onlySeparators(text, start, endExclusive, emojiRanges, ::isTerminalSeparator)

private fun onlyBlockSeparators(
    text: String,
    start: Int,
    endExclusive: Int,
    emojiRanges: List<IntRange>,
): Boolean = onlySeparators(text, start, endExclusive, emojiRanges, ::isBlockSeparator)

private fun onlySeparators(
    text: String,
    start: Int,
    endExclusive: Int,
    emojiRanges: List<IntRange>,
    predicate: (Int) -> Boolean,
): Boolean {
    var index = start
    while (index < endExclusive) {
        val covering = emojiRanges.firstOrNull { index in it }
        if (covering != null) {
            index = covering.last + 1
            continue
        }
        val codePoint = text.codePointAt(index)
        if (!predicate(codePoint)) return false
        index += Character.charCount(codePoint)
    }
    return true
}

private fun includeTerminalWhitespaceBefore(text: String, start: Int): Int {
    var cursor = start
    while (cursor > 0) {
        val previous = codePointBefore(text, cursor) ?: break
        if (!isTerminalSeparator(previous)) break
        cursor -= Character.charCount(previous)
    }
    return cursor
}

private fun semanticEnd(text: String): Int {
    var end = text.length
    while (end > 0) {
        val codePoint = codePointBefore(text, end) ?: break
        if (!isWhitespaceOrFormatting(codePoint)) break
        end -= Character.charCount(codePoint)
    }
    return end
}

private data class TextLine(
    val start: Int,
    val contentEndExclusive: Int,
    val lineBreakEndExclusive: Int,
)

private fun linesOf(text: String): List<TextLine> {
    val lines = mutableListOf<TextLine>()
    var start = 0
    while (start <= text.length) {
        val newline = text.indexOf('\n', start)
        if (newline < 0) {
            lines += TextLine(start, text.length, text.length)
            break
        }
        val contentEnd = if (newline > start && text[newline - 1] == '\r') newline - 1 else newline
        lines += TextLine(start, contentEnd, newline + 1)
        start = newline + 1
    }
    return lines
}

private fun codePointBefore(text: String, index: Int): Int? =
    if (index <= 0) null else text.codePointBefore(index)

private fun codePointAtOrNull(text: String, index: Int): Int? =
    if (index >= text.length) null else text.codePointAt(index)

private fun isTerminalSeparator(codePoint: Int): Boolean = isWhitespaceOrFormatting(codePoint)

private fun isBlockSeparator(codePoint: Int): Boolean =
    isWhitespaceOrFormatting(codePoint) || isApprovedDecorativeSeparator(codePoint)

private fun isWhitespaceOrFormatting(codePoint: Int): Boolean =
    Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint) ||
        codePoint == 0xFFFC || codePoint == 0xFEFF || Character.getType(codePoint) == Character.FORMAT.toInt()

private fun isApprovedDecorativeSeparator(codePoint: Int): Boolean {
    if (codePoint == 0xFFFD) return false
    if (codePoint in 0x1F000..0x1FAFF || codePoint in 0x2600..0x27BF) return true
    return codePoint in setOf(
        0x002C, // comma
        0x003B, // semicolon
        0x00B7, // middle dot
        0x2022, // bullet
        0x2023, // triangular bullet
        0x2024, 0x2025, 0x2026, // dot and ellipsis separators
        0x2043, 0x204E, 0x205D,
        0x2219, 0x22C5,
        0x25AA, 0x25AB, 0x25B8, 0x25B9, 0x25CB, 0x25CF, 0x25E6,
        0x25C6, 0x25C7,
        0x2013, 0x2014,
    )
}
