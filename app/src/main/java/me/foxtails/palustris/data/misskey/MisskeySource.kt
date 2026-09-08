package me.foxtails.palustris.data.misskey

import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.Attachment
import me.foxtails.palustris.domain.Audience
import me.foxtails.palustris.domain.CapabilityProbe
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.CreatePostRequest
import me.foxtails.palustris.domain.EntityId
import me.foxtails.palustris.domain.Notification
import me.foxtails.palustris.domain.NotificationAcknowledgement
import me.foxtails.palustris.domain.NotificationActivity
import me.foxtails.palustris.domain.NotificationCapabilities
import me.foxtails.palustris.domain.NotificationCategory
import me.foxtails.palustris.domain.NotificationCheckpoint
import me.foxtails.palustris.domain.NotificationCursor
import me.foxtails.palustris.domain.NotificationDestination
import me.foxtails.palustris.domain.NotificationGroup
import me.foxtails.palustris.domain.NotificationGroupId
import me.foxtails.palustris.domain.NotificationPage
import me.foxtails.palustris.domain.NotificationPageDirection
import me.foxtails.palustris.domain.NotificationQuery
import me.foxtails.palustris.domain.NotificationReaction
import me.foxtails.palustris.domain.NotificationTarget
import me.foxtails.palustris.domain.NotificationUnreadState
import me.foxtails.palustris.domain.Page
import me.foxtails.palustris.domain.PollOption
import me.foxtails.palustris.domain.Post
import me.foxtails.palustris.domain.PostAction
import me.foxtails.palustris.domain.ProfileField
import me.foxtails.palustris.domain.ProfileRelationship
import me.foxtails.palustris.domain.Protocol
import me.foxtails.palustris.domain.PushSubscription
import me.foxtails.palustris.domain.PushSubscriptionSpec
import me.foxtails.palustris.domain.Reaction
import me.foxtails.palustris.domain.ServerCapabilities
import me.foxtails.palustris.domain.SocialSource
import me.foxtails.palustris.domain.SourceError
import me.foxtails.palustris.domain.Timeline
import me.foxtails.palustris.domain.UpdateProfileRequest
import me.foxtails.palustris.domain.ValidatedUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.channels.awaitClose
import me.foxtails.palustris.domain.Event
import me.foxtails.palustris.domain.SocialEvent
import me.foxtails.palustris.domain.NotificationReadState
import me.foxtails.palustris.domain.NotificationReadStatus
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

    override fun streamEvents(): Flow<Event> = callbackFlow {
        val account = requireAccountId()
        val socket = api.webSocket(
            origin,
            "/streaming",
            headers = mapOf("Authorization" to "Bearer $token"),
            listener = object : okhttp3.WebSocketListener() {
            override fun onOpen(webSocket: okhttp3.WebSocket, response: okhttp3.Response) {
                webSocket.send(JSONObject()
                    .put("type", "connect")
                    .put("body", JSONObject()
                        .put("channel", "main")
                        .put("id", "notifications")
                        .put("params", JSONObject().put("i", token)))
                    .toString())
            }

            override fun onMessage(webSocket: okhttp3.WebSocket, text: String) {
                runCatching {
                    val message = JSONObject(text)
                    if (message.optString("type") != "channel") return@runCatching
                    val body = message.optJSONObject("body") ?: return@runCatching
                    when (body.optString("type")) {
                        "notification" -> {
                            val payload = body.optJSONObject("body") ?: return@runCatching
                            trySend(Event(account, SocialEvent.NotificationReceived(
                                MisskeyNotificationMapper.notification(payload, origin, account),
                            )))
                        }
                        "readAllNotifications" -> trySend(Event(account, SocialEvent.NotificationReadChanged(
                            account,
                            NotificationReadState(NotificationReadStatus.Read, serverAcknowledged = true),
                        )))
                        else -> trySend(Event(account, SocialEvent.Other("notification.refresh")))
                    }
                }.onFailure { trySend(Event(account, SocialEvent.Other("notification.refresh"))) }
            }

            override fun onFailure(webSocket: okhttp3.WebSocket, t: Throwable, response: okhttp3.Response?) {
                close(t)
            }

            override fun onClosed(webSocket: okhttp3.WebSocket, code: Int, reason: String) {
                close()
            }
            },
        )
        awaitClose { socket.cancel() }
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
        val stableSinceId = checkpoint.newest?.let {
            MisskeyNotificationCursorCodec.decode(it, requireAccountId(), query, NotificationCursorDirection.Newer).rawId
        }
        val cursor = checkpoint.newerContinuation ?: checkpoint.newest ?: return@request emptyNotificationPage(query)
        loadNotifications(query, cursor, NotificationCursorDirection.Newer, stableSinceId)
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

    override suspend fun createPushSubscription(spec: PushSubscriptionSpec): PushSubscription = request {
        validatePushSpec(spec)
        val response = JSONObject(api.post(origin, "sw/register", JSONObject()
            .put("i", token)
            .put("endpoint", spec.endpoint.value)
            .put("auth", spec.authSecret)
            .put("publickey", spec.publicKey)
            .put("sendReadMessage", false)).body)
        PushSubscription(
            accountId = spec.accountId,
            endpoint = ValidatedUrl.https(response.optString("endpoint")) ?: spec.endpoint,
            remoteId = response.optString("key").takeIf { it.isNotBlank() },
        )
    }

    override suspend fun updatePushSubscription(spec: PushSubscriptionSpec): PushSubscription = request {
        validatePushSpec(spec)
        val response = JSONObject(api.post(origin, "sw/update-registration", JSONObject()
            .put("i", token)
            .put("endpoint", spec.endpoint.value)
            .put("sendReadMessage", false)).body)
        PushSubscription(
            accountId = spec.accountId,
            endpoint = ValidatedUrl.https(response.optString("endpoint")) ?: spec.endpoint,
        )
    }

    override suspend fun removePushSubscription() = request {
        val endpoint = JSONObject(api.post(origin, "sw/show-registration", JSONObject().put("i", token)).body)
            .optString("endpoint")
        if (endpoint.isNotBlank()) {
            api.post(origin, "sw/unregister", JSONObject().put("i", token).put("endpoint", endpoint))
        }
        Unit
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

    private fun validatePushSpec(spec: PushSubscriptionSpec) {
        if (spec.accountId != requireAccountId() || spec.publicKey.isBlank() || spec.authSecret.isBlank()) {
            throw SourceError.Unsupported("notifications.push.spec")
        }
    }

    private suspend fun loadNotifications(
        query: NotificationQuery,
        cursor: NotificationCursor?,
        direction: NotificationCursorDirection,
        stableSinceId: String? = null,
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
            if (direction == NotificationCursorDirection.Newer) {
                body.put("sinceId", stableSinceId ?: it)
                if (stableSinceId != null && stableSinceId != it) body.put("untilId", it)
            } else {
                body.put("untilId", it)
            }
        }
        val endpoint = if (query.grouped) "i/notifications-grouped" else "i/notifications"
        val values = JSONArray(api.post(origin, endpoint, body).body)
        val items = (0 until values.length()).map { index ->
            MisskeyNotificationMapper.notification(values.getJSONObject(index), origin, account)
        }
        val newest = items.firstOrNull()?.let {
            MisskeyNotificationCursorCodec.encode(account, query, NotificationCursorDirection.Newer, it.id.value)
        } ?: if (direction == NotificationCursorDirection.Newer) cursor else null
        val newerContinuation = if (direction == NotificationCursorDirection.Newer && items.size >= query.limit) {
            items.lastOrNull()?.let {
                MisskeyNotificationCursorCodec.encode(account, query, NotificationCursorDirection.Newer, it.id.value)
            }
        } else {
            null
        }
        val oldest = items.lastOrNull()?.let {
            if (it.id.value == decoded?.rawId && direction == NotificationCursorDirection.Older) null
            else MisskeyNotificationCursorCodec.encode(account, query, NotificationCursorDirection.Older, it.id.value)
        }
        return NotificationPage(
            items = items,
            olderCursor = if (direction == NotificationCursorDirection.Older) oldest else null,
            newerCursor = if (direction == NotificationCursorDirection.Newer) newerContinuation else newest,
            checkpoint = NotificationCheckpoint(
                accountId = account,
                query = query,
                newest = newest,
                oldest = oldest ?: if (direction == NotificationCursorDirection.Older) cursor else null,
                capturedAtEpochMillis = clock(),
                newerContinuation = newerContinuation,
            ),
            direction = if (cursor == null) NotificationPageDirection.Initial else when (direction) {
                NotificationCursorDirection.Older -> NotificationPageDirection.Older
                NotificationCursorDirection.Newer -> NotificationPageDirection.Newer
            },
            continuation = when (direction) {
                NotificationCursorDirection.Older -> oldest
                NotificationCursorDirection.Newer -> newerContinuation
            },
            newestBoundary = newest,
            oldestBoundary = oldest,
            reachedBoundary = when (direction) {
                NotificationCursorDirection.Older -> oldest == null
                NotificationCursorDirection.Newer -> newerContinuation == null
            },
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
        return Account(
            id = AccountId(Connection(origin, Protocol.MISSKEY), json.getString("id")),
            displayName = json.nullableString("name") ?: username,
            handle = "@$username@$host",
            avatarUrl = json.nullableString("avatarUrl"),
            biography = json.nullableString("description").orEmpty(),
            profileFields = fields,
            bannerUrl = json.nullableString("bannerUrl"),
            followersCount = json.optionalNonNegativeLong("followersCount"),
            followingCount = json.optionalNonNegativeLong("followingCount"),
            postsCount = json.optionalNonNegativeLong("notesCount"),
            locked = json.optBoolean("isLocked"),
            bot = json.optBoolean("isBot"),
        )
    }

    fun relationship(json: JSONObject, profileId: AccountId): ProfileRelationship = ProfileRelationship(
        profileId = profileId,
        following = json.optBoolean("isFollowing", json.optBoolean("following")),
        followedBy = json.optBoolean("isFollowed", json.optBoolean("followedBy")),
        requested = json.optBoolean(
            "hasPendingRequestFromYou",
            json.optBoolean("hasPendingFollowRequest", json.optBoolean("requested")),
        ),
        muting = json.optBoolean("isMuted", json.optBoolean("muting")),
        blocking = json.optBoolean("isBlocking", json.optBoolean("blocking")),
    )

    fun post(json: JSONObject, origin: String, depth: Int = 0): Post {
        val renote = json.optJSONObject("renote")
        val files = json.optJSONArray("files") ?: JSONArray()
        val textPresent = json.has("text") && !json.isNull("text")
        val pureReshare = renote != null && !textPresent && files.length() == 0 &&
            json.optJSONObject("poll") == null && json.nullableString("cw") == null
        if (pureReshare && depth < 3) return post(renote!!, origin, depth + 1).copy(
            id = EntityId(origin, json.getString("id")), resharedBy = account(json.getJSONObject("user"), origin))
        val id = EntityId(origin, json.getString("id"))
        val reactionJson = json.optJSONObject("reactions") ?: JSONObject()
        val reactionImages = json.optJSONObject("reactionEmojis") ?: JSONObject()
        val poll = json.optJSONObject("poll")?.optJSONArray("choices")
        val replyToAuthorId = json.nullableString("replyUserId")
            ?: json.optJSONObject("reply")?.nullableString("userId")
            ?: json.optJSONObject("reply")?.optJSONObject("user")?.nullableString("id")
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
            replyToAuthorId = replyToAuthorId?.let { AccountId(Connection(origin, Protocol.MISSKEY), it) },
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

private fun JSONObject.optionalNonNegativeLong(key: String): Long? {
    if (!has(key) || isNull(key)) return null
    val value = opt(key) ?: return null
    val parsed = when (value) {
        is Number -> value.toLong()
        is String -> value.toLongOrNull()
        else -> null
    }
    return parsed?.takeIf { it >= 0L }
}
