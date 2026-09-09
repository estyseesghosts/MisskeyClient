package me.foxtails.palustris.data.mastodon

import me.foxtails.palustris.domain.CustomEmoji
import me.foxtails.palustris.domain.MediaRequestPolicy
import org.json.JSONArray
import org.json.JSONObject

/**
 * Central parsing of standard Mastodon `emojis` arrays reused for accounts, statuses,
 * and `/api/v1/custom_emojis`. Standard emoji metadata is display-only and never
 * enables reactions. Invalid entries are skipped without discarding the entity that
 * carried them.
 */
object MastodonEmojiMapper {
    fun parseEmojis(array: JSONArray?, origin: String): Map<String, CustomEmoji> {
        if (array == null) return emptyMap()
        val result = linkedMapOf<String, CustomEmoji>()
        (0 until array.length()).mapNotNull { index ->
            array.optJSONObject(index)?.let { parseEmoji(it, origin) }
        }.forEach { entry ->
            result.putIfAbsent(entry.first, entry.second)
            entry.second.aliases.forEach { alias -> result.putIfAbsent(alias, entry.second) }
        }
        return result
    }

    fun parseCatalog(body: String, origin: String): List<CustomEmoji> =
        parseEmojis(runCatching { JSONArray(body) }.getOrNull(), origin).values.toList()

    private fun parseEmoji(json: JSONObject, origin: String): Pair<String, CustomEmoji>? {
        val shortcode = json.optString("shortcode").ifBlank { json.optString("short_code").ifBlank { "" } }
        if (shortcode.isBlank()) return null
        val url = MediaRequestPolicy.validatedWebUrl(json.optString("url"), origin)
        val staticUrl = MediaRequestPolicy.validatedWebUrl(json.optString("static_url"), origin)
        if (url == null && staticUrl == null) return null
        val entry = CustomEmoji(
            shortcode = shortcode,
            animatedUrl = url,
            staticUrl = staticUrl,
            category = json.optString("category").takeIf { it.isNotBlank() },
            aliases = (json.optJSONArray("tags")?.let(::stringValues) ?: emptyList()) +
                (json.optJSONArray("aliases")?.let(::stringValues) ?: emptyList()),
            visibleInPicker = json.optBoolean("visible_in_picker", true),
            submissionValue = ":$shortcode:",
        )
        return shortcode to entry
    }

    private fun stringValues(array: JSONArray): List<String> =
        (0 until array.length()).mapNotNull { array.optString(it).takeIf(String::isNotBlank) }
}
