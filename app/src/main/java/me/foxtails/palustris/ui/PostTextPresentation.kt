package me.foxtails.palustris.ui

internal data class PostTextPresentation(
    val visibleText: String,
    val trailingHashtags: List<String>,
)

private val hashtagToken = Regex("#[\\p{L}\\p{N}_]+")

/** Splits only the contiguous, whitespace-separated hashtag run at the end of a post. */
internal fun splitTrailingHashtags(text: String): PostTextPresentation {
    var end = text.length
    while (end > 0 && text[end - 1].isWhitespace()) end--

    val hashtags = mutableListOf<String>()
    var cursor = end
    while (cursor > 0) {
        var tokenStart = cursor
        while (tokenStart > 0 && !text[tokenStart - 1].isWhitespace()) tokenStart--
        val token = text.substring(tokenStart, cursor)
        if (!hashtagToken.matches(token)) break

        hashtags.add(0, token)
        cursor = tokenStart
        while (cursor > 0 && text[cursor - 1].isWhitespace()) cursor--
    }

    if (hashtags.isEmpty()) return PostTextPresentation(text, emptyList())
    return PostTextPresentation(text.substring(0, cursor).trimEnd(), hashtags)
}
