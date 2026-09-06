package me.foxtails.palustris.data.misskey

import me.foxtails.palustris.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

class MisskeySource(private val origin: String, private val token: String, private val api: MisskeyApi) : SocialSource {
    override val capabilities = ServerCapabilities(timelines = setOf(Timeline.Home))
    override suspend fun timeline(timeline: Timeline, cursor: String?): Page<Post> = withContext(Dispatchers.IO) {
        require(timeline in capabilities.timelines)
        val params = JSONObject().put("i", token).put("limit", 30)
        if (cursor != null) params.put("untilId", cursor)
        val notes = JSONArray(api.post(origin, "notes/timeline", params))
        Page((0 until notes.length()).map { MisskeyMapper.post(notes.getJSONObject(it), origin) },
            // Use the OUTER renote ID, not the displayed original note, for pagination.
            if (notes.length() > 0) notes.getJSONObject(notes.length() - 1).getString("id") else null)
    }
}

object MisskeyMapper {
    fun account(json: JSONObject, origin: String): Account {
        val username = json.getString("username")
        val host = json.nullableString("host") ?: java.net.URI(origin).host
        return Account(EntityId(origin, json.getString("id")), json.nullableString("name") ?: username,
            "@$username@$host", json.nullableString("avatarUrl"), json.nullableString("description").orEmpty())
    }

    fun post(json: JSONObject, origin: String, depth: Int = 0): Post {
        val renote = json.optJSONObject("renote")
        val files = json.optJSONArray("files") ?: JSONArray()
        val pureReshare = renote != null && json.nullableString("text") == null && files.length() == 0 && json.optJSONObject("poll") == null && json.nullableString("cw") == null
        if (pureReshare && depth < 3) return post(renote!!, origin, depth + 1).copy(
            id = EntityId(origin, json.getString("id")), resharedBy = account(json.getJSONObject("user"), origin))
        val id = EntityId(origin, json.getString("id"))
        val reactionJson = json.optJSONObject("reactions") ?: JSONObject()
        val reactionImages = json.optJSONObject("reactionEmojis") ?: JSONObject()
        val poll = json.optJSONObject("poll")?.optJSONArray("choices")
        return Post(
            id = id,
            author = account(json.getJSONObject("user"), origin),
            text = if (json.optBoolean("isHidden")) "This post is not available to your account." else json.nullableString("text").orEmpty(),
            publishedAtEpochMillis = runCatching { Instant.parse(json.getString("createdAt")).toEpochMilli() }.getOrDefault(0),
            audience = when (json.optString("visibility")) { "home" -> Audience.Unlisted; "followers" -> Audience.Followers; "specified" -> Audience.Direct; else -> Audience.Public },
            attachments = (0 until files.length()).map { i -> files.getJSONObject(i).let {
                Attachment(it.getString("url"), it.optString("type", "application/octet-stream"), it.nullableString("comment"),
                    it.nullableString("thumbnailUrl"), it.optBoolean("isSensitive"))
            } },
            contentWarning = if (json.isNull("cw")) null else json.optString("cw"),
            replyTo = json.nullableString("replyId")?.let { EntityId(origin, it) },
            reactions = reactionJson.keys().asSequence().map { emoji -> Reaction(emoji, reactionJson.optInt(emoji),
                json.nullableString("myReaction") == emoji, reactionImages.nullableString(emoji.trim(':'))) }.toList(),
            url = json.nullableString("url") ?: json.nullableString("uri") ?: "$origin/notes/${id.value}",
            replyCount = json.optInt("repliesCount"), reshareCount = json.optInt("renoteCount"),
            quote = if (renote != null && depth < 3) post(renote, origin, depth + 1) else null,
            pollOptions = if (poll == null) emptyList() else (0 until poll.length()).map { poll.getJSONObject(it).let { option ->
                PollOption(option.getString("text"), option.optInt("votes"))
            } },
        )
    }
}
