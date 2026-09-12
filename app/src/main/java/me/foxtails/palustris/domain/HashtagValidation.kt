package me.foxtails.palustris.domain

import java.util.Locale

private val exactHashtagBody = Regex("[\\p{L}\\p{N}_](?:[\\p{L}\\p{N}\\p{M}_])*")

/** Returns the accepted spelling, retaining its optional leading hash. */
fun validateExactHashtag(value: String): String {
    require(value.none(Char::isISOControl)) { "Enter one exact hashtag, such as #photography." }
    val trimmed = value.trim()
    val body = trimmed.removePrefix("#")
    require(body.isNotEmpty() && body == trimmed.removePrefix("#") && body.matches(exactHashtagBody)) {
        "Enter one exact hashtag, such as #photography."
    }
    return trimmed
}

fun isExactHashtag(value: String): Boolean = runCatching { validateExactHashtag(value) }.isSuccess

fun hashtagBody(value: String): String = validateExactHashtag(value).removePrefix("#")

fun hashtagIdentity(value: String): String = hashtagBody(value).lowercase(Locale.ROOT)
