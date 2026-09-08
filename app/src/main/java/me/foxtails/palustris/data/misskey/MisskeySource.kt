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
import java.util.Base64

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

    override suspend fun profile(id: AccountId): Account = request {
        val response = api.post(origin, "users/show", JSONObject().put("i", token).put("userId", id.localId))
        MisskeyMapper.account(JSONObject(response.body), origin)
    }

    override suspend fun searchAccounts(query: String): List<Account> = request {
        val parts = query.trim().removePrefix("@").split('@')
        require(parts.size in 1..2 && parts[0].isNotBlank()) { "Enter a webfinger handle, such as @user@example.org." }
        val body = JSONObject().put("i", token).put("username", parts[0])
        parts.getOrNull(1)?.takeIf { it.isNotBlank() && !it.equals(java.net.URI(origin).host, ignoreCase = true) }
            ?.let { body.put("host", it) }
        listOf(MisskeyMapper.account(JSONObject(api.post(origin, "users/show", body).body), origin))
    }

    override suspend fun searchHashtag(tag: String, cursor: String?): Page<Post> = request {
        val normalized = tag.trim().removePrefix("#")
        require(normalized.matches(Regex("[\\p{L}\\p{N}_](?:[\\p{L}\\p{N}\\p{M}_])*"))) {
            "Enter one exact hashtag, such as #photography."
        }
        val body = JSONObject().put("i", token).put("tag", normalized).put("limit", 30)
        cursor?.let { body.put("untilId", it) }
        val notes = JSONArray(api.post(origin, "notes/search-by-tag", body).body)
        Page(
            items = (0 until notes.length()).map { MisskeyMapper.post(notes.getJSONObject(it), origin) },
            nextCursor = notes.optJSONObject(notes.length() - 1)?.optString("id")?.takeIf { it.isNotBlank() },
        )
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
        if (post.attachments.isNotEmpty()) throw SourceError.Unsupported("create.attachments")
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
                .apply { poll.expiresAt?.let { put("expiresAt", it.toEpochMilli()) } })
        }
        val response = JSONObject(api.post(origin, "notes/create", body).body)
        MisskeyMapper.post(response.getJSONObject("createdNote"), origin)
    }

    override suspend fun updateProfile(profile: UpdateProfileRequest) = request {
        val body = JSONObject().put("i", token).put("name", profile.displayName).put("description", profile.biography)
        MisskeyMapper.account(JSONObject(api.post(origin, "i/update", body).body), origin)
    }

    override suspend fun delete(id: EntityId) = request {
        api.post(origin, "notes/delete", JSONObject().put("i", token).put("noteId", id.value))
        Unit
    }

    override suspend fun notifications(cursor: String?): Page<Notification> {
        val page = notifications(NotificationQuery(), cursor?.let(::NotificationCursor))
        return Page(page.items, page.olderCursor?.value)
    }

    override suspend fun notifications(query: NotificationQuery, cursor: NotificationCursor?): NotificationPage = request {
        loadNotifications(query, cursor, NotificationCursorDirection.Older)
    }

    override suspend fun fetchNewerNotifications(
        query: NotificationQuery,
        checkpoint: NotificationCheckpoint,
    ): NotificationPage = request {
        validateCheckpoint(query, checkpoint)
        val cursor = checkpoint.newest ?: return@request emptyNotificationPage(query)
        loadNotifications(query, cursor, NotificationCursorDirection.Newer)
    }

    override suspend fun fetchOlderNotifications(
        query: NotificationQuery,
        checkpoint: NotificationCheckpoint,
    ): NotificationPage = request {
        validateCheckpoint(query, checkpoint)
        val cursor = checkpoint.oldest ?: return@request emptyNotificationPage(query)
        loadNotifications(query, cursor, NotificationCursorDirection.Older)
    }

    override suspend fun notificationUnreadState(): NotificationUnreadState = request {
        val json = JSONObject(api.post(origin, "i", JSONObject().put("i", token)).body)
        when {
            json.has("notificationCount") && !json.isNull("notificationCount") ->
                NotificationUnreadState.Exact(json.optInt("notificationCount").coerceAtLeast(0))
            json.has("hasUnreadNotification") && !json.isNull("hasUnreadNotification") ->
                if (json.optBoolean("hasUnreadNotification")) NotificationUnreadState.Present else NotificationUnreadState.None
            else -> NotificationUnreadState.Unknown
        }
    }

    override suspend fun acknowledgeNotifications(): NotificationAcknowledgement = request {
        val account = requireAccountId()
        api.post(origin, "notifications/mark-all-as-read", JSONObject().put("i", token))
        NotificationAcknowledgement(account, NotificationUnreadState.None, clock())
    }

    override suspend fun respondToFollowRequest(targetAccountId: AccountId, accept: Boolean) = request {
        validateFollowRequestTarget(targetAccountId)
        val endpoint = if (accept) "following/requests/accept" else "following/requests/reject"
        api.post(origin, endpoint, JSONObject().put("i", token).put("userId", targetAccountId.localId))
        Unit
    }

    private fun validateFollowRequestTarget(targetAccountId: AccountId) {
        if (targetAccountId.connection != Connection(origin, Protocol.MISSKEY) || targetAccountId.localId.isBlank()) {
            throw SourceError.Unsupported("notifications.followRequest")
        }
    }

    private suspend fun loadNotifications(
        query: NotificationQuery,
        cursor: NotificationCursor?,
        direction: NotificationCursorDirection,
    ): NotificationPage {
        val account = requireAccountId()
        val types = query.misskeyTypes()
        if (!query.isAll && types.isEmpty()) return emptyNotificationPage(query)
        val decoded = cursor?.let { MisskeyNotificationCursorCodec.decode(it, account, query, direction) }
        val body = JSONObject()
            .put("i", token)
            .put("limit", query.limit)
            // Misskey defaults this to true and performs account-wide acknowledgement.
            .put("markAsRead", false)
        if (!query.isAll) body.put("includeTypes", JSONArray(types))
        decoded?.rawId?.let {
            if (direction == NotificationCursorDirection.Newer) body.put("sinceId", it)
            else body.put("untilId", it)
        }
        val endpoint = if (query.grouped) "i/notifications-grouped" else "i/notifications"
        val values = JSONArray(api.post(origin, endpoint, body).body)
        val items = (0 until values.length()).map { index ->
            MisskeyMapper.notification(values.getJSONObject(index), origin, account)
        }
        val newest = items.firstOrNull()?.let {
            MisskeyNotificationCursorCodec.encode(account, query, NotificationCursorDirection.Newer, it.id.value)
        } ?: if (direction == NotificationCursorDirection.Newer) cursor else null
        val oldest = items.lastOrNull()?.let {
            if (it.id.value == decoded?.rawId && direction == NotificationCursorDirection.Older) null
            else MisskeyNotificationCursorCodec.encode(account, query, NotificationCursorDirection.Older, it.id.value)
        }
        return NotificationPage(
            items = items,
            olderCursor = oldest,
            newerCursor = newest,
            checkpoint = NotificationCheckpoint(
                accountId = account,
                query = query,
                newest = newest,
                oldest = oldest ?: if (direction == NotificationCursorDirection.Older) cursor else null,
                capturedAtEpochMillis = clock(),
            ),
        )
    }

    private fun requireAccountId(): AccountId = accountId ?: throw SourceError.Unsupported("notifications.account")

    private fun validateCheckpoint(query: NotificationQuery, checkpoint: NotificationCheckpoint) {
        if (checkpoint.accountId != requireAccountId() || checkpoint.query != query) {
            throw SourceError.Unsupported("notifications.checkpoint")
        }
    }

    private fun emptyNotificationPage(query: NotificationQuery) = NotificationPage(
        items = emptyList(),
        checkpoint = NotificationCheckpoint(requireAccountId(), query, capturedAtEpochMillis = clock()),
    )

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
            _capabilities.value = it.copy(
                canPublish = it.canPublish || capabilities.canPublish,
                notifications = it.notifications.takeVerifiedOr(capabilities.notifications),
            )
            return
        }
        try {
            capabilityProbe.probeCapabilities(Connection(origin, Protocol.MISSKEY)).also {
                val updated = it.copy(
                    canPublish = it.canPublish || capabilities.canPublish,
                    notifications = it.notifications.takeVerifiedOr(capabilities.notifications),
                )
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

private fun NotificationCapabilities.takeVerifiedOr(previous: NotificationCapabilities): NotificationCapabilities =
    if (this == NotificationCapabilities()) previous else this

private enum class NotificationCursorDirection { Older, Newer }

private object MisskeyNotificationCursorCodec {
    data class Decoded(val rawId: String)

    fun encode(
        accountId: AccountId,
        query: NotificationQuery,
        direction: NotificationCursorDirection,
        rawId: String,
    ): NotificationCursor {
        val payload = JSONObject()
            .put("origin", accountId.connection.origin)
            .put("account", accountId.localId)
            .put("query", query.fingerprint())
            .put("direction", direction.name)
            .put("id", rawId)
        return NotificationCursor(Base64.getUrlEncoder().withoutPadding()
            .encodeToString(payload.toString().toByteArray(Charsets.UTF_8)))
    }

    fun decode(
        cursor: NotificationCursor,
        accountId: AccountId,
        query: NotificationQuery,
        direction: NotificationCursorDirection,
    ): Decoded {
        val json = runCatching {
            JSONObject(String(Base64.getUrlDecoder().decode(cursor.value), Charsets.UTF_8))
        }.getOrElse { throw SourceError.Unsupported("notifications.cursor") }
        if (json.optString("origin") != accountId.connection.origin ||
            json.optString("account") != accountId.localId ||
            json.optString("query") != query.fingerprint() ||
            json.optString("direction") != direction.name
        ) {
            throw SourceError.Unsupported("notifications.cursor")
        }
        return Decoded(json.optString("id").takeIf(String::isNotBlank)
            ?: throw SourceError.Unsupported("notifications.cursor"))
    }
}

private fun NotificationQuery.fingerprint(): String = buildString {
    append(categories.map { it.name }.sorted().joinToString(","))
    append('|').append(limit).append('|').append(grouped)
}

private fun NotificationQuery.misskeyTypes(): List<String> {
    if (isAll) return emptyList()
    return categories.flatMap { category ->
        when (category) {
            NotificationCategory.All -> emptyList()
            NotificationCategory.Mentions -> listOf("mention", "reply")
            NotificationCategory.Replies -> listOf("reply")
            NotificationCategory.Quotes -> listOf("quote")
            NotificationCategory.Social -> listOf(
                "note", "renote", "reaction", "follow", "receiveFollowRequest", "followRequestAccepted",
            )
            NotificationCategory.Polls -> listOf("pollEnded")
            NotificationCategory.System -> listOf(
                "scheduledNotePosted", "scheduledNotePostFailed", "roleAssigned", "achievementEarned",
                "exportCompleted", "login", "createToken", "app", "test", "chatRoomInvitationReceived",
            )
        }
    }.distinct()
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
        val fields = (json.optJSONArray("fields") ?: json.optJSONObject("profile")?.optJSONArray("fields"))
            ?.let { values ->
                (0 until values.length()).mapNotNull { index ->
                    values.optJSONObject(index)?.let { field ->
                        ProfileField(field.optString("name"), field.optString("value"))
                    }?.takeIf { it.name.isNotBlank() || it.value.isNotBlank() }
                }.take(4)
            }.orEmpty()
        return Account(AccountId(Connection(origin, Protocol.MISSKEY), json.getString("id")), json.nullableString("name") ?: username,
            "@$username@$host", json.nullableString("avatarUrl"), json.nullableString("description").orEmpty(), fields)
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

    fun notification(json: JSONObject, origin: String, receivingAccountId: AccountId): Notification {
        val rawType = json.optString("type").ifBlank { "unknown" }
        val actor = runCatching { json.optJSONObject("user")?.let { account(it, origin) } }.getOrNull()
        val groupedActors = when (rawType) {
            "reaction:grouped" -> json.optJSONArray("reactions")?.let { values ->
                (0 until values.length()).mapNotNull { index ->
                    runCatching { values.optJSONObject(index)?.optJSONObject("user")?.let { account(it, origin) } }
                        .getOrNull()
                }
            }.orEmpty()
            "renote:grouped" -> json.optJSONArray("users")?.let { values ->
                (0 until values.length()).mapNotNull { index ->
                    runCatching { values.optJSONObject(index)?.let { account(it, origin) } }.getOrNull()
                }
            }.orEmpty()
            else -> emptyList()
        }
        val actors = groupedActors.ifEmpty { listOfNotNull(actor) }
        val mappedPost = runCatching { json.optJSONObject("note")?.let { post(it, origin) } }.getOrNull()
        val noteId = json.nullableString("noteId")
        val canonicalPostId = when {
            rawType == "renote" -> json.nullableString("targetNoteId") ?: noteId
            else -> noteId
        }
        val canonicalTarget = canonicalPostId?.let { NotificationTarget.Post(EntityId(origin, it)) }
        val target = when {
            canonicalTarget != null -> canonicalTarget
            mappedPost != null -> NotificationTarget.Post(mappedPost.id)
            actors.firstOrNull() != null && rawType in PROFILE_TARGET_TYPES ->
                NotificationTarget.Profile(actors.first().id)
            else -> null
        }
        val group = if (rawType.endsWith(":grouped")) {
            val groupTarget = canonicalPostId ?: mappedPost?.id?.value ?: "unknown"
            NotificationGroup(
                id = NotificationGroupId(receivingAccountId, "$rawType:$groupTarget"),
                actorPreviews = actors,
                totalCount = actors.size.takeIf { it > 0 },
            )
        } else null
        return Notification(
            id = EntityId(origin, json.getString("id")),
            accountId = receivingAccountId,
            createdAtEpochMillis = runCatching {
                Instant.parse(json.optString("createdAt")).toEpochMilli()
            }.getOrDefault(0L),
            activity = rawType.toNotificationActivity(json),
            actors = actors,
            target = target,
            destination = target?.let(NotificationDestination::InApp),
            post = mappedPost,
            rawType = rawType,
            group = group,
        )
    }

    private val PROFILE_TARGET_TYPES = setOf("follow", "receiveFollowRequest", "followRequest")
}

private fun String.toNotificationActivity(json: JSONObject): NotificationActivity = when (this) {
    "note" -> NotificationActivity.SubscribedPost
    "mention" -> NotificationActivity.Mention
    "reply" -> NotificationActivity.Reply
    "renote" -> NotificationActivity.Reshare
    "renote:grouped" -> NotificationActivity.Reshare
    "quote" -> NotificationActivity.Quote
    "reaction", "reaction:grouped" -> {
        val firstReaction = json.optJSONArray("reactions")?.optJSONObject(0)
        val identity = json.nullableString("reaction")
            ?: json.nullableString("emoji")
            ?: firstReaction?.nullableString("reaction")
            ?: "reaction"
        val imageUrl = json.optJSONObject("customEmoji")?.nullableString("url")
            ?: json.optJSONObject("emoji")?.nullableString("url")
        NotificationActivity.EmojiReaction(NotificationReaction(
            identity = identity,
            fallbackText = identity.trim(':').ifBlank { "Reaction" },
            imageUrl = imageUrl,
        ))
    }
    "follow" -> NotificationActivity.Follow
    "receiveFollowRequest", "followRequest" -> NotificationActivity.FollowRequest
    "followRequestAccepted" -> NotificationActivity.AcceptedRequest
    "pollEnded" -> NotificationActivity.PollResult()
    "scheduledNotePosted" -> NotificationActivity.SubscribedPost
    "scheduledNotePostFailed" -> NotificationActivity.System.AppEvent("Scheduled post failed")
    "app" -> NotificationActivity.System.AppEvent(
        json.nullableString("customHeader") ?: "Application event",
        json.nullableString("customBody"),
    )
    "achievementEarned", "achievement", "roleAssigned", "role" ->
        NotificationActivity.System.RoleOrAchievement(
            json.nullableString("achievement") ?: "Account achievement",
        )
    "moderation", "moderationWarning" -> NotificationActivity.System.Moderation("Moderation event")
    "relationship" -> NotificationActivity.System.RelationshipChange("Relationship changed")
    "chatRoomInvitationReceived" -> NotificationActivity.Unknown("Chat invitation is not available")
    "exportCompleted" -> NotificationActivity.System.AppEvent("Export completed")
    "login" -> NotificationActivity.System.AppEvent("New sign-in")
    "createToken" -> NotificationActivity.System.AppEvent("Access token created")
    "test" -> NotificationActivity.System.AppEvent("Test notification")
    else -> NotificationActivity.Unknown("New activity")
}
