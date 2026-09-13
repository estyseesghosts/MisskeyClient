package me.foxtails.palustris.domain

import java.net.URI

/** Removes only parameters known to be used for click attribution. */
object TrackingParameterCleaner {
    private val webUrl = Regex("https?://[^\\s<>\\\"]+")
    private val trackingNames = setOf(
        "utm_source", "utm_medium", "utm_campaign", "utm_term", "utm_content",
        "gclid", "dclid", "fbclid", "mc_cid", "mc_eid", "igshid", "_ga",
    )
    private val signedNames = setOf("signature", "sig", "x-amz-signature", "x-amz-credential", "x-amz-security-token")

    fun clean(url: String, enabled: Boolean): String {
        if (!enabled) return url
        val parsed = runCatching { URI(url) }.getOrNull() ?: return url
        if (parsed.scheme !in setOf("http", "https") || parsed.host.isNullOrBlank()) return url
        val query = parsed.rawQuery ?: return url
        val segments = query.split('&')
        val names = segments.map { it.substringBefore('=').decodeName() }
        if (names.any { it in signedNames }) return url
        val kept = segments.filterNot { it.substringBefore('=').decodeName() in trackingNames }
        if (kept.size == segments.size) return url
        return runCatching {
            buildString {
                append(parsed.scheme).append("://").append(parsed.rawAuthority)
                append(parsed.rawPath.orEmpty())
                kept.joinToString("&").takeIf(String::isNotEmpty)?.let { append('?').append(it) }
                parsed.rawFragment?.let { append('#').append(it) }
            }
        }.getOrDefault(url)
    }

    fun cleanText(text: String): String = webUrl.replace(text) { match -> clean(match.value.trimEnd('.', ',', ')', ']', ';'), true) + match.value.takeLastWhile { it in ".,)] ;" } }

    private fun String.decodeName(): String = runCatching {
        java.net.URLDecoder.decode(this, Charsets.UTF_8.name()).lowercase()
    }.getOrDefault(lowercase())
}
