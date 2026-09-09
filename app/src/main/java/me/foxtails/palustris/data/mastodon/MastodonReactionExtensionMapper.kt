package me.foxtails.palustris.data.mastodon

import me.foxtails.palustris.domain.CustomEmoji
import me.foxtails.palustris.domain.EmojiChoice
import me.foxtails.palustris.domain.MediaRequestPolicy
import me.foxtails.palustris.domain.Reaction
import me.foxtails.palustris.domain.ValidatedUrl
import org.json.JSONArray
import org.json.JSONObject

/**
 * Maps fixture-confirmed Pleroma/Akkoma `emoji_reactions` summaries. The raw `name` is
 * the opaque submission identity and is never rewritten. An image resolves only from
 * the entry's own URL or the status's local emoji metadata; unresolved custom
 * identities stay visible as text. Standard `emojis` arrays are never converted into
 * reactions here.
 */
object MastodonReactionExtensionMapper {
    fun reactions(json: JSONObject, statusEmojis: Map<String, CustomEmoji>): List<Reaction>? {
        val array = reactionsArray(json) ?: return null
        return (0 until array.length()).mapNotNull { index ->
            entry(array.optJSONObject(index), statusEmojis)?.let { (identity, count, me, metadata) ->
                Reaction(identity, count, me, metadata)
            }
        }
    }

    fun selectedChoices(
        json: JSONObject,
        statusEmojis: Map<String, CustomEmoji>,
        independentSelection: Boolean,
    ): List<EmojiChoice>? {
        val array = reactionsArray(json) ?: return null
        val selected = (0 until array.length()).mapNotNull { index ->
            val parsed = entry(array.optJSONObject(index), statusEmojis) ?: return@mapNotNull null
            if (!parsed.me) return@mapNotNull null
            EmojiChoice(parsed.identity, parsed.identity, parsed.metadata)
        }
        return if (selected.isEmpty()) emptyList() else if (independentSelection) selected else selected.takeLast(1)
    }

    private fun reactionsArray(json: JSONObject): JSONArray? = json.optJSONArray("emoji_reactions")
        ?: json.optJSONObject("pleroma")?.optJSONArray("emoji_reactions")
        ?: json.optJSONObject("akkoma")?.optJSONArray("emoji_reactions")

    private data class Entry(
        val identity: String,
        val count: Int,
        val me: Boolean,
        val metadata: CustomEmoji?,
    )

    private fun entry(json: JSONObject?, statusEmojis: Map<String, CustomEmoji>): Entry? {
        if (json == null) return null
        val identity = json.optString("name").takeIf { it.isNotBlank() } ?: return null
        val count = json.optInt("count", 0).coerceAtLeast(0)
        val me = json.optBoolean("me", false)
        val image = reactionImage(json, identity, statusEmojis)
        val metadata = if (image != null) {
            CustomEmoji(
                shortcode = identity.trim(':'),
                animatedUrl = image,
                staticUrl = image,
                visibleInPicker = false,
                submissionValue = identity,
            )
        } else {
            null
        }
        return Entry(identity, count, me, metadata)
    }

    private fun reactionImage(
        json: JSONObject,
        identity: String,
        statusEmojis: Map<String, CustomEmoji>,
    ): ValidatedUrl? {
        val direct = MediaRequestPolicy.validatedWebUrl(json.optString("url"))
        if (direct != null) return direct
        return statusEmojis[identity]?.let(MediaRequestPolicy::emojiImage)?.url
            ?: statusEmojis[identity.trim(':')]?.let(MediaRequestPolicy::emojiImage)?.url
    }
}
