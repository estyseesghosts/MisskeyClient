package me.foxtails.palustris.data.misskey

import me.foxtails.palustris.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

class MisskeySource(
    private val origin: String,
    private val token: String,
    private val api: MisskeyApi,
    private val initialCapabilities: ServerCapabilities = ServerCapabilities(timelines = setOf(Timeline.Home)),
    private val accountId: AccountId? = null,
    private val capabilityProbe: CapabilityProbe = MisskeyCapabilityProbe(api),
    private val capabilityCache: CapabilityCache = CapabilityCache(),
    private val clock: () -> Long = System::currentTimeMillis,
) : SocialSource {
    private val cacheKey = CapabilityCacheKey(origin, accountId ?: AccountId(Connection(origin, Protocol.MISSKEY), "anonymous"))
    private val _capabilities = MutableStateFlow(initialCapabilities)
    val capabilitiesFlow: StateFlow<ServerCapabilities> = _capabilities
    override val capabilities: ServerCapabilities get() = _capabilities.value

    override suspend fun timeline(timeline: Timeline, cursor: String?): Page<Post> = request(invalidateCapabilitiesOnNotFound = true) {
            refreshCapabilities()
            if (timeline !in capabilities.timelines) throw SourceError.Unsupported("timeline:$timeline")
            val params = JSONObject().put("i", token).put("limit", 30)
            if (cursor != null) params.put("untilId", cursor)
            val endpoint = when (timeline) {
                Timeline.Home -> "notes/timeline"
                Timeline.Local -> "notes/local-timeline"
                Timeline.Social -> "notes/hybrid-timeline"
                Timeline.Federated -> "notes/global-timeline"
            }
            val notes = JSONArray(api.post(origin, endpoint, params).body)
            Page((0 until notes.length()).map { MisskeyMapper.post(notes.getJSONObject(it), origin) },
                // Use the OUTER renote ID, not the displayed original note, for pagination.
                if (notes.length() > 0) notes.getJSONObject(notes.length() - 1).getString("id") else null)
    }

    override suspend fun post(id: EntityId): Post = request {
        val response = api.post(origin, "notes/show", JSONObject().put("i", token).put("noteId", id.value))
        MisskeyMapper.post(JSONObject(response.body), origin)
    }

    override suspend fun thread(rootId: EntityId): List<Post> = request {
        val root = post(rootId)
        val ancestors = mutableListOf<Post>()
        val visited = mutableSetOf(root.id)
        var current = root
        while (current.replyTo != null && visited.add(current.replyTo)) {
            val parent = post(current.replyTo!!)
            ancestors += parent
            current = parent
        }
        ancestors.reverse()

        val childrenResponse = api.post(
            origin,
            "notes/children",
            JSONObject().put("i", token).put("noteId", rootId.value).put("limit", 30),
        )
        val descendants = JSONArray(childrenResponse.body).let { children ->
            (0 until children.length()).map { index -> MisskeyMapper.post(children.getJSONObject(index), origin) }
        }
        (ancestors + root + descendants).distinctBy { it.id }
    }

    override suspend fun create(post: CreatePostRequest): Post = request {
        val body = JSONObject()
            .put("i", token)
            .put("text", post.text)
            .put("visibility", post.audience.toMisskeyVisibility())
        post.contentWarning?.let { body.put("cw", it) }
        post.replyTo?.let { body.put("replyId", it.value) }
        post.quoteOf?.let { body.put("renoteId", it.value) }
        post.poll?.let { poll ->
            body.put("poll", JSONObject()
                .put("choices", JSONArray(poll.choices))
                .put("multiple", poll.multiple)
                .apply { poll.expiresAt?.let { put("expiresAt", it) } })
        }
        MisskeyMapper.post(JSONObject(api.post(origin, "notes/create", body).body), origin)
    }

    override suspend fun delete(id: EntityId) = request {
        api.post(origin, "notes/delete", JSONObject().put("i", token).put("noteId", id.value))
        Unit
    }

    private suspend fun <T> request(
        invalidateCapabilitiesOnNotFound: Boolean = false,
        block: suspend () -> T,
    ): T = withContext(Dispatchers.IO) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: SourceError) {
            throw e
        } catch (e: ApiFailure) {
            if (invalidateCapabilitiesOnNotFound && (e.status == 404 || e.code.equals("NOT_SUPPORTED", ignoreCase = true))) {
                capabilityCache.remove(cacheKey)
                _capabilities.value = capabilities.copy(capabilitiesLastUpdated = 0)
            }
            throw MisskeyErrorMapper.map(e)
        } catch (e: Exception) {
            throw MisskeyErrorMapper.map(e)
        }
    }

    private suspend fun refreshCapabilities() {
        val now = clock()
        if (now - capabilities.capabilitiesLastUpdated < CAPABILITIES_TTL_MILLIS) return
        capabilityCache.get(cacheKey)?.takeIf {
            now - it.capabilitiesLastUpdated < CAPABILITIES_TTL_MILLIS
        }?.let {
            _capabilities.value = it.copy(canPublish = it.canPublish || capabilities.canPublish)
            return
        }
        try {
            capabilityProbe.probeCapabilities(Connection(origin, Protocol.MISSKEY)).also {
                val updated = it.copy(canPublish = it.canPublish || capabilities.canPublish)
                _capabilities.value = updated
                capabilityCache.put(cacheKey, updated)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            capabilityCache.remove(cacheKey)
            _capabilities.value = capabilities.copy(capabilitiesLastUpdated = 0)
            throw if (e is SourceError) e else MisskeyErrorMapper.map(e)
        }
    }

    private companion object {
        const val CAPABILITIES_TTL_MILLIS = 5 * 60 * 1000L
    }
}

private fun Audience.toMisskeyVisibility(): String = when (this) {
    Audience.Public -> "public"
    Audience.Unlisted -> "home"
    Audience.Followers -> "followers"
    Audience.Direct -> "specified"
}

object MisskeyMapper {
    fun account(json: JSONObject, origin: String): Account {
        val username = json.getString("username")
        val host = json.nullableString("host") ?: java.net.URI(origin).host
        return Account(AccountId(Connection(origin, Protocol.MISSKEY), json.getString("id")), json.nullableString("name") ?: username,
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
