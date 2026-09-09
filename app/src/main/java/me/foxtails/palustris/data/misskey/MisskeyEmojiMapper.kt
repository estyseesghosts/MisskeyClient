package me.foxtails.palustris.data.misskey

import me.foxtails.palustris.domain.CustomEmoji
import me.foxtails.palustris.domain.MediaRequestPolicy
import org.json.JSONArray
import org.json.JSONObject

/**
 * Central Misskey custom emoji mapping for user `emojis`, note `emojis`,
 * `reactionEmojis`, and the `/api/emojis` catalog.
 *
 * Local and remote identities are preserved exactly: every entry is indexed by its raw
 * submission identity (for example `:name:` or `:name@host:`), its display name, and
 * every alias in both forms. The display key exists only for metadata resolution and is
 * never submitted.
 */
object MisskeyEmojiMapper {
    fun parseEmojis(json: JSONObject, origin: String): Map<String, CustomEmoji> {
        val result = linkedMapOf<String, CustomEmoji>()
        json.keys().asSequence().forEach { key ->
            parseEntry(key, json.opt(key), origin)?.let { (identity, entry) ->
                result.putIfAbsent(identity, entry)
                identity.trim(':').takeIf { it.isNotBlank() }?.let { result.putIfAbsent(it, entry) }
                entry.aliases.forEach { alias ->
                    result.putIfAbsent(alias, entry)
                    result.putIfAbsent(":$alias:", entry)
                    result.putIfAbsent(alias.trim(':'), entry)
                }
            }
        }
        return result
    }

    fun parseCatalog(body: String, origin: String): List<CustomEmoji> {
        val emojis = runCatching { JSONObject(body).optJSONArray("emojis") }.getOrNull() ?: return emptyList()
        val result = mutableListOf<CustomEmoji>()
        (0 until emojis.length()).forEach { index ->
            emojis.optJSONObject(index)?.let { json ->
                parseCatalogEntry(json, origin)?.let(result::add)
            }
        }
        return result
    }

    private fun parseEntry(key: String, value: Any?, origin: String): Pair<String, CustomEmoji>? {
        val json = when (value) {
            is JSONObject -> value
            is String -> JSONObject().put("name", key).put("url", value)
            else -> return null
        }
        return parseEmoji(key, json, origin)
    }

    private fun parseCatalogEntry(json: JSONObject, origin: String): CustomEmoji? {
        val name = json.optString("name").ifBlank { return null }
        val url = MediaRequestPolicy.validatedWebUrl(json.optString("url"), origin) ?: return null
        val identity = if (name.startsWith(":")) name else ":$name:"
        return CustomEmoji(
            shortcode = name.trim(':'),
            animatedUrl = url,
            staticUrl = url,
            category = json.optString("category").takeIf { it.isNotBlank() },
            aliases = json.optJSONArray("aliases")?.let { array ->
                (0 until array.length()).mapNotNull { array.optString(it).takeIf(String::isNotBlank) }
            }.orEmpty(),
            visibleInPicker = true,
            submissionValue = identity,
        )
    }

    private fun parseEmoji(key: String, json: JSONObject, origin: String): Pair<String, CustomEmoji>? {
        val name = json.optString("name").ifBlank { key.trim(':') }
        val url = MediaRequestPolicy.validatedWebUrl(json.optString("url"), origin) ?: return null
        val identity = if (key.startsWith(":")) key else ":$name:"
        val shortcode = name.trim(':')
        return identity to CustomEmoji(
            shortcode = shortcode,
            animatedUrl = url,
            staticUrl = url,
            category = json.optString("category").takeIf { it.isNotBlank() },
            aliases = json.optJSONArray("aliases")?.let { array ->
                (0 until array.length()).mapNotNull { array.optString(it).takeIf(String::isNotBlank) }
            }.orEmpty(),
            visibleInPicker = true,
            submissionValue = identity,
        )
    }
}
