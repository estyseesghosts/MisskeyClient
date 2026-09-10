package me.foxtails.palustris.data.misskey

import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.Audience
import me.foxtails.palustris.domain.CapabilityProbe
import me.foxtails.palustris.domain.CapabilityStatus
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.ConversationId
import me.foxtails.palustris.domain.CreatePostRequest
import me.foxtails.palustris.domain.CustomEmoji
import me.foxtails.palustris.domain.DirectConversation
import me.foxtails.palustris.domain.DirectMessageRequest
import me.foxtails.palustris.domain.DirectMessageSource
import me.foxtails.palustris.domain.EditableProfile
import me.foxtails.palustris.domain.EditableProfilePatch
import me.foxtails.palustris.domain.EmojiChoice
import me.foxtails.palustris.domain.EntityId
import me.foxtails.palustris.domain.Notification
import me.foxtails.palustris.domain.NotificationAcknowledgement
import me.foxtails.palustris.domain.NotificationCapabilities
import me.foxtails.palustris.domain.NotificationCategory
import me.foxtails.palustris.domain.NotificationCheckpoint
import me.foxtails.palustris.domain.NotificationCursor
import me.foxtails.palustris.domain.NotificationPage
import me.foxtails.palustris.domain.NotificationQuery
import me.foxtails.palustris.domain.NotificationReadSemantics
import me.foxtails.palustris.domain.NotificationUnreadPrecision
import me.foxtails.palustris.domain.NotificationUnreadState
import me.foxtails.palustris.domain.Page
import me.foxtails.palustris.domain.Post
import me.foxtails.palustris.domain.PostAction
import me.foxtails.palustris.domain.ProfileCapabilities
import me.foxtails.palustris.domain.PostActionResult
import me.foxtails.palustris.domain.ProfileRelationship
import me.foxtails.palustris.domain.ProfileTimelineQuery
import me.foxtails.palustris.domain.Protocol
import me.foxtails.palustris.domain.PushSubscription
import me.foxtails.palustris.domain.PushSubscriptionSpec
import me.foxtails.palustris.domain.PushProviderInfo
import me.foxtails.palustris.domain.ServerCapabilities
import me.foxtails.palustris.domain.SocialSource
import me.foxtails.palustris.domain.SourceError
import me.foxtails.palustris.domain.Timeline
import me.foxtails.palustris.domain.ValidatedUrl
import me.foxtails.palustris.domain.normalizeFavouriteEmoji
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.Flow
import me.foxtails.palustris.domain.Event
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class MisskeySource(
    private val origin: String,
    private val token: String,
    private val api: MisskeyApi,
    private val initialCapabilities: ServerCapabilities = ServerCapabilities(timelines = setOf(Timeline.Home)),
    private val accountId: AccountId? = null,
    private val capabilityProbe: CapabilityProbe = MisskeyCapabilityProbe(api),
    private val capabilityCache: CapabilityCache = CapabilityCache(),
    private val clock: () -> Long = System::currentTimeMillis,
) : SocialSource, DirectMessageSource {
    private val cacheKey = CapabilityCacheKey(origin, accountId ?: AccountId(Connection(origin, Protocol.MISSKEY), "anonymous"))
    private val _capabilities = MutableStateFlow(initialCapabilities)
    val capabilitiesFlow: StateFlow<ServerCapabilities> = _capabilities
    private val profileService = MisskeyProfileService(origin, token, api, accountId)
    private val directMessageService = MisskeyDirectMessageService(origin, token, api, accountId) { id -> post(id) }
    private val notificationService = MisskeyNotificationService(origin, token, api, accountId, clock)
    private val pushService = MisskeyPushService(origin, token, api, accountId)
    private val streamService = MisskeyStreamService(origin, token, api, accountId)
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

    override suspend fun profile(id: AccountId): Account = request { profileService.profile(id) }

    override suspend fun profileTimeline(query: ProfileTimelineQuery, cursor: String?): Page<Post> = request {
        profileService.timeline(query, cursor)
    }

    override suspend fun profileRelationship(id: AccountId): ProfileRelationship = request {
        profileService.relationship(id)
    }

    override suspend fun followProfile(id: AccountId): ProfileRelationship = request {
        profileService.follow(id)
    }

    override suspend fun unfollowProfile(id: AccountId): ProfileRelationship = request {
        profileService.unfollow(id)
    }

    override suspend fun pinnedPosts(id: AccountId): List<Post> = request { profileService.pinnedPosts(id) }

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
        post.quoteOf?.let {
            if (it.connection != origin) throw SourceError.Unsupported("create.quote-origin")
            body.put("renoteId", it.value)
        }
        post.poll?.let { poll ->
            body.put("poll", JSONObject()
                .put("choices", JSONArray(poll.choices))
                .put("multiple", poll.multiple)
                .apply { poll.expiresAt?.let { put("expiresAt", it.toEpochMilli()) } })
        }
        val response = JSONObject(api.post(origin, "notes/create", body).body)
        MisskeyMapper.post(response.getJSONObject("createdNote"), origin)
    }

    override suspend fun conversations(cursor: String?): Page<DirectConversation> = request { directMessageService.conversations(cursor) }

    override suspend fun conversationThread(id: ConversationId): List<Post> = request { directMessageService.conversationThread(id) }

    override suspend fun sendDirectMessage(request: DirectMessageRequest): Post = request { directMessageService.send(request) }

    override suspend fun markConversationRead(id: ConversationId) = request { directMessageService.markConversationRead(id) }

    override suspend fun react(id: EntityId, emoji: String) = request {
        validatePostId(id, "react")
        val reaction = emoji.trim().takeIf { it.isNotBlank() }
            ?: throw SourceError.Unsupported("react.emoji")
        api.post(origin, "notes/reactions/create", JSONObject()
            .put("i", token)
            .put("noteId", id.value)
            .put("reaction", reaction))
        Unit
    }

    override suspend fun react(id: EntityId, choice: EmojiChoice) = request {
        validatePostId(id, "react")
        val reaction = choice.submissionValue.takeIf { it.isNotBlank() }
            ?: throw SourceError.Unsupported("react.emoji")
        api.post(origin, "notes/reactions/create", JSONObject()
            .put("i", token)
            .put("noteId", id.value)
            .put("reaction", reaction))
        Unit
    }

    override suspend fun removeReaction(id: EntityId, emoji: String) = request {
        validatePostId(id, "react")
        api.post(origin, "notes/reactions/delete", JSONObject().put("i", token).put("noteId", id.value))
        Unit
    }

    override suspend fun removeReaction(id: EntityId, choice: EmojiChoice) = request {
        validatePostId(id, "react")
        api.post(origin, "notes/reactions/delete", JSONObject().put("i", token).put("noteId", id.value))
        Unit
    }

    override suspend fun favorite(id: EntityId) = favorite(id, me.foxtails.palustris.domain.DEFAULT_FAVOURITE_EMOJI)

    override suspend fun favorite(id: EntityId, favouriteEmoji: String) = request {
        validatePostId(id, "favorite")
        react(id, normalizeFavouriteEmoji(favouriteEmoji))
    }

    override suspend fun unfavorite(id: EntityId, favouriteEmoji: String?) = request {
        validatePostId(id, "favorite")
        removeReaction(id, favouriteEmoji.orEmpty())
    }

    override suspend fun setPrimaryFavourite(
        id: EntityId,
        favouriteEmoji: String,
        selected: Boolean,
    ): PostActionResult {
        if (selected) favorite(id, favouriteEmoji) else unfavorite(id, favouriteEmoji)
        return PostActionResult(selected = selected)
    }

    override suspend fun renote(id: EntityId) = request {
        validatePostId(id, "renote")
        api.post(origin, "notes/create", JSONObject().put("i", token).put("renoteId", id.value))
        Unit
    }

    override suspend fun unrenote(id: EntityId, ownRepostId: EntityId?) = request {
        val repostId = ownRepostId ?: throw SourceError.Unsupported("renote.undo")
        validatePostId(repostId, "renote.undo")
        api.post(origin, "notes/delete", JSONObject().put("i", token).put("noteId", repostId.value))
        Unit
    }

    override suspend fun setReshared(id: EntityId, selected: Boolean, ownRepostId: EntityId?): PostActionResult = request {
        validatePostId(id, "renote")
        if (!selected) {
            unrenote(id, ownRepostId)
            return@request PostActionResult(selected = false)
        }
        val response = JSONObject(api.post(origin, "notes/create", JSONObject()
            .put("i", token)
            .put("renoteId", id.value)).body)
        val created = response.optJSONObject("createdNote")
            ?: throw SourceError.ServerError("Misskey did not return the created renote")
        val createdId = created.optString("id").takeIf { it.isNotBlank() }?.let { EntityId(origin, it) }
        PostActionResult(selected = true, createdRepostId = createdId)
    }

    override suspend fun save(id: EntityId) = request {
        validatePostId(id, "save")
        api.post(origin, "notes/favorites/create", JSONObject().put("i", token).put("noteId", id.value))
        Unit
    }

    override suspend fun unsave(id: EntityId) = request {
        validatePostId(id, "save")
        api.post(origin, "notes/favorites/delete", JSONObject().put("i", token).put("noteId", id.value))
        Unit
    }

    override suspend fun setSaved(id: EntityId, selected: Boolean): PostActionResult {
        if (selected) save(id) else unsave(id)
        return PostActionResult(selected = selected)
    }

    override suspend fun savedPosts(cursor: String?): Page<Post> = request {
        val body = JSONObject().put("i", token).put("limit", 30)
        cursor?.takeIf(String::isNotBlank)?.let { body.put("untilId", it) }
        val values = JSONArray(api.post(origin, "i/favorites", body).body)
        val items = (0 until values.length()).mapNotNull { index ->
            val wrapper = values.optJSONObject(index) ?: return@mapNotNull null
            val note = wrapper.optJSONObject("note") ?: wrapper
            runCatching { MisskeyMapper.post(note, origin).copy(saved = true) }.getOrNull()
        }
        val nextCursor = values.optJSONObject(values.length() - 1)?.optString("id")
            ?.takeIf { it.isNotBlank() }
        Page(items, nextCursor)
    }

    private fun validatePostId(id: EntityId, feature: String) {
        if (id.connection != origin || id.value.isBlank()) throw SourceError.Unsupported(feature)
    }

    override suspend fun loadEditableProfile(): EditableProfile = request {
        val localId = requireAccountId().localId
        val json = JSONObject(api.post(origin, "i", JSONObject().put("i", token)).body)
        EditableProfile(
            id = localId,
            displayName = json.optString("name"),
            biography = json.optString("description"),
        )
    }

    override suspend fun updateEditableProfile(patch: EditableProfilePatch): EditableProfile = request {
        val unsupported = patch != EditableProfilePatch(displayName = patch.displayName, biography = patch.biography)
        if (unsupported) throw SourceError.Unsupported("profile.editable.update")
        val body = JSONObject().put("i", token)
        patch.displayName?.let { body.put("name", it) }
        patch.biography?.let { body.put("description", it) }
        val json = JSONObject(api.post(origin, "i/update", body).body)
        val returnedId = json.optString("id").takeIf { it.isNotBlank() }
        val expectedId = accountId?.localId
        if (expectedId != null && returnedId != null && returnedId != expectedId) {
            throw SourceError.AccountMismatch
        }
        EditableProfile(
            id = expectedId ?: returnedId ?: throw SourceError.AccountMismatch,
            displayName = json.optString("name"),
            biography = json.optString("description"),
        )
    }

    override suspend fun customEmojis(): List<CustomEmoji> = request {
        val response = api.post(origin, "emojis", JSONObject().put("i", token))
        MisskeyEmojiMapper.parseCatalog(response.body, origin)
    }

    override suspend fun delete(id: EntityId) = request {
        api.post(origin, "notes/delete", JSONObject().put("i", token).put("noteId", id.value))
        Unit
    }

    override fun streamEvents(): Flow<Event> = streamService.events()

    override suspend fun notifications(cursor: String?): Page<Notification> = request { notificationService.notifications(cursor) }

    override suspend fun notifications(query: NotificationQuery, cursor: NotificationCursor?): NotificationPage = request { notificationService.notifications(query, cursor) }

    override suspend fun fetchNewerNotifications(query: NotificationQuery, checkpoint: NotificationCheckpoint): NotificationPage = request {
        notificationService.fetchNewer(query, checkpoint)
    }

    override suspend fun fetchOlderNotifications(query: NotificationQuery, checkpoint: NotificationCheckpoint): NotificationPage = request {
        notificationService.fetchOlder(query, checkpoint)
    }

    override suspend fun notificationUnreadState(): NotificationUnreadState = request { notificationService.unreadState() }

    override suspend fun pushProviderInfo(): PushProviderInfo = request { pushService.providerInfo() }

    override suspend fun acknowledgeNotifications(): NotificationAcknowledgement = request { notificationService.acknowledge() }

    override suspend fun queryOwnedPushSubscription(knownEndpoint: ValidatedUrl?): PushSubscription? = request { pushService.query(knownEndpoint) }

    override suspend fun createOrReplacePushSubscription(
        spec: PushSubscriptionSpec,
        previous: PushSubscription?,
    ): PushSubscription = request { pushService.createOrReplace(spec, previous) }

    override suspend fun updatePushAlertPolicy(
        subscription: PushSubscription,
        alerts: Set<NotificationCategory>,
    ): PushSubscription = request { pushService.updatePolicy(subscription, alerts) }

    override suspend fun removePushSubscription(subscription: PushSubscription) = request { pushService.remove(subscription) }

    override suspend fun respondToFollowRequest(targetAccountId: AccountId, accept: Boolean) = request {
        notificationService.respondToFollowRequest(targetAccountId, accept)
    }

    private fun requireAccountId(): AccountId = accountId ?: throw SourceError.Unsupported("notifications.account")

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
                profile = it.profile.takeVerifiedOr(capabilities.profile),
            )
            return
        }
        try {
            capabilityProbe.probeCapabilities(Connection(origin, Protocol.MISSKEY)).also {
                val updated = it.copy(
                    canPublish = it.canPublish || capabilities.canPublish,
                    notifications = it.notifications.mergeNotificationCapabilities(capabilities.notifications),
                    profile = it.profile.takeVerifiedOr(capabilities.profile),
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
        const val DIRECT_PAGE_LIMIT = 30
        const val CAPABILITIES_TTL_MILLIS = 5 * 60 * 1000L
        // Misskey's secure push endpoints return ACCESS_DENIED for MiAuth/app
        // credentials. Those tokens can authenticate ordinary API calls, but
        // cannot satisfy an endpoint declared secure:true (native user token).
        val SECURE_CREDENTIAL_FAILURE_CODES = setOf(
            "ACCESS_DENIED",
            "AUTHENTICATION_FAILED",
            "SECURE_CREDENTIAL_REQUIRED",
        )
        val MISSING_PUSH_REGISTRATION_CODES = setOf("NOT_FOUND", "NO_SUCH_REGISTRATION", "REGISTRATION_NOT_FOUND")
    }
}

private fun NotificationCapabilities.takeVerifiedOr(previous: NotificationCapabilities): NotificationCapabilities =
    if (this == NotificationCapabilities()) previous else this

private fun NotificationCapabilities.mergeNotificationCapabilities(
    previous: NotificationCapabilities,
): NotificationCapabilities = copy(
    listing = listing.takeKnown(previous.listing),
    supportedCategories = supportedCategories.ifEmpty { previous.supportedCategories },
    readSemantics = readSemantics.takeKnown(previous.readSemantics),
    unreadCountPrecision = unreadCountPrecision.takeKnown(previous.unreadCountPrecision),
    grouping = grouping.takeKnown(previous.grouping),
    dismissal = dismissal.takeKnown(previous.dismissal),
    policyManagement = policyManagement.takeKnown(previous.policyManagement),
    followRequestActions = followRequestActions.takeKnown(previous.followRequestActions),
    streaming = streaming.takeKnown(previous.streaming),
)

private fun CapabilityStatus.takeKnown(previous: CapabilityStatus): CapabilityStatus =
    if (this == CapabilityStatus.Unknown) previous else this

private fun NotificationReadSemantics.takeKnown(
    previous: NotificationReadSemantics,
) = if (this == NotificationReadSemantics.Unknown) previous else this

private fun NotificationUnreadPrecision.takeKnown(
    previous: NotificationUnreadPrecision,
) = if (this == NotificationUnreadPrecision.Unknown) previous else this

private fun ProfileCapabilities.takeVerifiedOr(previous: ProfileCapabilities): ProfileCapabilities =
    if (this == ProfileCapabilities()) previous else this

private fun Audience.toMisskeyVisibility(): String = when (this) {
    Audience.Public -> "public"
    Audience.Unlisted -> "home"
    Audience.Followers -> "followers"
    Audience.Direct -> "specified"
}

