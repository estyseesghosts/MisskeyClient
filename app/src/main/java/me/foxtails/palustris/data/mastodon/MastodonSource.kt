package me.foxtails.palustris.data.mastodon

import java.io.InputStream
import java.net.URLEncoder
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow
import me.foxtails.palustris.data.misskey.ApiFailure
import me.foxtails.palustris.data.misskey.HttpResponse
import me.foxtails.palustris.data.misskey.MisskeyApi
import me.foxtails.palustris.domain.Audience
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.AccountId
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
import me.foxtails.palustris.domain.EmojiCapabilities
import me.foxtails.palustris.domain.EmojiChoice
import me.foxtails.palustris.domain.EntityId
import me.foxtails.palustris.domain.Event
import me.foxtails.palustris.domain.NotificationCheckpoint
import me.foxtails.palustris.domain.NotificationAcknowledgement
import me.foxtails.palustris.domain.NotificationCategory
import me.foxtails.palustris.domain.NotificationCapabilities
import me.foxtails.palustris.domain.NotificationCursor
import me.foxtails.palustris.domain.NotificationPage
import me.foxtails.palustris.domain.NotificationQuery
import me.foxtails.palustris.domain.NotificationUnreadState
import me.foxtails.palustris.domain.Notification
import me.foxtails.palustris.domain.Page
import me.foxtails.palustris.domain.Post
import me.foxtails.palustris.domain.PostAction
import me.foxtails.palustris.domain.PostActionResult
import me.foxtails.palustris.domain.ProfileCapabilities
import me.foxtails.palustris.domain.ProfileRelationship
import me.foxtails.palustris.domain.ProfileTimelineQuery
import me.foxtails.palustris.domain.Protocol
import me.foxtails.palustris.domain.PushSubscription
import me.foxtails.palustris.domain.PushSubscriptionSpec
import me.foxtails.palustris.domain.PushProviderInfo
import me.foxtails.palustris.domain.ReactionSelectionMode
import me.foxtails.palustris.domain.ServerCapabilities
import me.foxtails.palustris.domain.SocialSource
import me.foxtails.palustris.domain.SourceError
import me.foxtails.palustris.domain.Timeline
import me.foxtails.palustris.domain.ValidatedUrl
import org.json.JSONArray
import org.json.JSONObject

class MastodonSource(
    private val origin: String,
    private val token: String,
    private val api: MisskeyApi,
    private val accountId: AccountId,
    initialCapabilities: ServerCapabilities = DEFAULT_CAPABILITIES,
    private val capabilityProbe: CapabilityProbe? = null,
    private val clock: () -> Long = System::currentTimeMillis,
) : SocialSource, DirectMessageSource {
    private val _capabilities = kotlinx.coroutines.flow.MutableStateFlow(
        if (initialCapabilities.timelines.isEmpty() && initialCapabilities.actions.isEmpty() &&
            initialCapabilities.audiences.isEmpty() && initialCapabilities.notifications == NotificationCapabilities()
        ) {
            DEFAULT_CAPABILITIES.copy(canPublish = initialCapabilities.canPublish)
        } else {
            initialCapabilities
        },
    )
    private val profileService = MastodonProfileService(origin, token, api, accountId)
    private val selfProfileService = MastodonSelfProfileService(origin, token, api, accountId)
    private val directMessageService = MastodonDirectMessageService(origin, token, api, accountId, profileService)
    private val notificationService = MastodonNotificationService(origin, token, api, accountId, clock)
    private val pushService = MastodonPushService(origin, token, api, accountId)
    private val streamService = MastodonStreamService(origin, token, api, accountId)
    override val capabilities: ServerCapabilities get() = _capabilities.value

    override suspend fun timeline(timeline: Timeline, cursor: String?): Page<Post> = request {
        refreshCapabilities()
        if (timeline !in capabilities.timelines) throw SourceError.Unsupported("timeline:$timeline")
        val endpoint = when (timeline) {
            Timeline.Home -> "v1/timelines/home"
            Timeline.Local -> "v1/timelines/public?local=true"
            Timeline.Federated -> "v1/timelines/public"
            Timeline.Social -> throw SourceError.Unsupported("timeline:$timeline")
        }
        val response = getPage(endpoint, cursor)
        val statuses = JSONArray(response.body)
        Page(
            items = (0 until statuses.length()).map { MastodonMapper.post(statuses.getJSONObject(it), origin) },
            nextCursor = response.linkHeaderCursor(),
        )
    }

    override suspend fun post(id: EntityId): Post = request {
        MastodonMapper.post(api.get(origin, "v1/statuses/${id.value}", token).body.toJson(), origin)
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

    override suspend fun create(post: CreatePostRequest): Post = request {
        if (post.replyTo != null && post.quoteOf != null) throw SourceError.Unsupported("create.reply.quote")
        if (post.quoteOf != null && capabilities.quotes != CapabilityStatus.Supported) {
            throw SourceError.Unsupported("quote")
        }
        if (post.quoteOf != null && (post.attachments.isNotEmpty() || post.poll != null)) {
            throw SourceError.Unsupported("quote.attachments-or-poll")
        }
        if (post.attachments.isNotEmpty()) throw SourceError.Unsupported("create.attachments")
        if (post.poll != null) throw SourceError.Unsupported("create.poll")
        val fields = buildList {
            add("status" to post.text)
            add("visibility" to post.audience.toMastodonVisibility())
            post.contentWarning?.let { add("spoiler_text" to it) }
            post.replyTo?.let { add("in_reply_to_id" to it.value) }
            post.quoteOf?.let { add("quoted_status_id" to it.value) }
        }
        MastodonMapper.post(api.postForm(origin, "api/v1/statuses", fields, token).body.toJson(), origin)
    }

    override suspend fun conversations(cursor: String?): Page<DirectConversation> = request { directMessageService.conversations(cursor) }

    override suspend fun conversationThread(id: ConversationId): List<Post> = request { directMessageService.conversationThread(id) }

    override suspend fun sendDirectMessage(request: DirectMessageRequest): Post = request { directMessageService.sendDirectMessage(request) }

    override suspend fun markConversationRead(id: ConversationId) = request { directMessageService.markConversationRead(id) }

    override suspend fun loadEditableProfile(): EditableProfile = request {
        refreshCapabilities()
        selfProfileService.load(capabilities.profile.editable)
    }

    override suspend fun updateEditableProfile(patch: EditableProfilePatch): EditableProfile = request {
        refreshCapabilities()
        selfProfileService.update(patch, capabilities.profile.editable)
    }

    override suspend fun customEmojis(): List<CustomEmoji> = request {
        MastodonEmojiMapper.parseCatalog(api.get(origin, "v1/custom_emojis", token).body, origin)
    }

    override suspend fun react(id: EntityId, choice: EmojiChoice) = request {
        validatePostId(id, "react")
        requireReactionMutation()
        try {
            mutateEmojiReaction(id, choice.submissionValue, selected = true)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            downgradeReactionMutation()
            throw e
        }
        Unit
    }

    override suspend fun removeReaction(id: EntityId, choice: EmojiChoice) = request {
        validatePostId(id, "react")
        requireReactionMutation()
        try {
            mutateEmojiReaction(id, choice.submissionValue, selected = false)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            downgradeReactionMutation()
            throw e
        }
        Unit
    }

    private fun requireReactionMutation() {
        if (capabilities.emoji.reactionMutation != CapabilityStatus.Supported) {
            throw SourceError.Unsupported("react")
        }
    }

    private fun downgradeReactionMutation() {
        _capabilities.value = _capabilities.value.copy(
            actions = _capabilities.value.actions - PostAction.React,
            emoji = _capabilities.value.emoji.copy(
                reactionMutation = CapabilityStatus.Unsupported,
                selectionMode = ReactionSelectionMode.Unknown,
            ),
        )
    }

    private suspend fun mutateEmojiReaction(
        id: EntityId,
        submission: String,
        selected: Boolean,
    ): PostActionResult {
        val response = if (selected) {
            api.putForm(origin, emojiReactionEndpoint(id, submission), emptyList(), token)
        } else {
            api.delete(origin, emojiReactionEndpoint(id, submission), token)
        }
        return PostActionResult(
            post = response.optionalPost(origin),
            selected = selected,
        )
    }

    private fun emojiReactionEndpoint(id: EntityId, submission: String): String =
        origin.toHttpUrl().newBuilder()
            .addPathSegment("api")
            .addPathSegment("v1")
            .addPathSegment("pleroma")
            .addPathSegment("statuses")
            .addPathSegment(id.value)
            .addPathSegment("reactions")
            .addPathSegment(submission)
            .build()
            .encodedPath
            .removePrefix("/")

    override suspend fun favorite(id: EntityId) = request {
        validatePostId(id, "favorite")
        api.postForm(origin, "api/v1/statuses/${id.value.encodePathSegment()}/favourite", emptyList(), token)
        Unit
    }

    override suspend fun favorite(id: EntityId, favouriteEmoji: String) = favorite(id)

    override suspend fun unfavorite(id: EntityId, favouriteEmoji: String?) = request {
        validatePostId(id, "favorite")
        api.postForm(origin, "api/v1/statuses/${id.value}/unfavourite", emptyList(), token)
        Unit
    }

    override suspend fun setPrimaryFavourite(
        id: EntityId,
        favouriteEmoji: String,
        selected: Boolean,
    ): PostActionResult = request {
        validatePostId(id, "favorite")
        val endpoint = if (selected) "favourite" else "unfavourite"
        val response = api.postForm(origin, "api/v1/statuses/${id.value.encodePathSegment()}/$endpoint", emptyList(), token)
        PostActionResult(
            post = response.optionalPost(origin),
            selected = selected,
        )
    }

    override suspend fun renote(id: EntityId) = request {
        validatePostId(id, "renote")
        api.postForm(origin, "api/v1/statuses/${id.value.encodePathSegment()}/reblog", emptyList(), token)
        Unit
    }

    override suspend fun unrenote(id: EntityId, ownRepostId: EntityId?) = request {
        validatePostId(id, "renote")
        api.postForm(origin, "api/v1/statuses/${id.value.encodePathSegment()}/unreblog", emptyList(), token)
        Unit
    }

    override suspend fun setReshared(id: EntityId, selected: Boolean, ownRepostId: EntityId?): PostActionResult = request {
        validatePostId(id, "renote")
        val endpoint = if (selected) "reblog" else "unreblog"
        val response = api.postForm(origin, "api/v1/statuses/${id.value.encodePathSegment()}/$endpoint", emptyList(), token)
        val mapped = response.optionalPost(origin)
        PostActionResult(
            post = mapped,
            selected = selected,
            createdRepostId = if (selected) mapped?.id else null,
        )
    }

    override suspend fun save(id: EntityId) = request {
        validatePostId(id, "save")
        api.postForm(origin, "api/v1/statuses/${id.value.encodePathSegment()}/bookmark", emptyList(), token)
        Unit
    }

    override suspend fun unsave(id: EntityId) = request {
        validatePostId(id, "save")
        api.postForm(origin, "api/v1/statuses/${id.value.encodePathSegment()}/unbookmark", emptyList(), token)
        Unit
    }

    override suspend fun setSaved(id: EntityId, selected: Boolean): PostActionResult = request {
        validatePostId(id, "save")
        val endpoint = if (selected) "bookmark" else "unbookmark"
        val response = api.postForm(origin, "api/v1/statuses/${id.value.encodePathSegment()}/$endpoint", emptyList(), token)
        val mapped = response.optionalPost(origin)
        PostActionResult(post = mapped, selected = selected)
    }

    @Deprecated("Use create(CreatePostRequest(quoteOf = ...))")
    override suspend fun quote(id: EntityId, text: String) {
        create(CreatePostRequest(text = text, quoteOf = id))
    }

    override suspend fun savedPosts(cursor: String?): Page<Post> = request {
        val response = getPage("v1/bookmarks?limit=40", cursor)
        val statuses = JSONArray(response.body)
        Page(
            items = (0 until statuses.length()).map { MastodonMapper.post(statuses.getJSONObject(it), origin) },
            nextCursor = response.linkHeaderCursor(),
        )
    }

    override suspend fun notifications(cursor: String?): Page<me.foxtails.palustris.domain.Notification> = request { notificationService.notifications(cursor) }

    override suspend fun notifications(query: NotificationQuery, cursor: NotificationCursor?): NotificationPage = request {
        notificationService.notifications(query, cursor)
    }

    override suspend fun fetchNewerNotifications(query: NotificationQuery, checkpoint: NotificationCheckpoint): NotificationPage = request {
        notificationService.fetchNewerNotifications(query, checkpoint)
    }

    override suspend fun fetchOlderNotifications(query: NotificationQuery, checkpoint: NotificationCheckpoint): NotificationPage = request {
        notificationService.fetchOlderNotifications(query, checkpoint)
    }

    override suspend fun notificationUnreadState(): NotificationUnreadState = request { notificationService.unreadState() }

    override suspend fun pushProviderInfo(): PushProviderInfo = request { pushService.providerInfo() }

    override suspend fun acknowledgeNotifications(): NotificationAcknowledgement = request { notificationService.acknowledge() }

    override suspend fun respondToFollowRequest(targetAccountId: AccountId, accept: Boolean) = request {
        notificationService.respondToFollowRequest(targetAccountId, accept)
    }

    private fun validatePostId(id: EntityId, feature: String) {
        if (id.connection != origin || id.value.isBlank()) throw SourceError.Unsupported(feature)
    }

    override suspend fun dismissNotification(id: EntityId) = request { notificationService.dismiss(id) }

    override suspend fun queryOwnedPushSubscription(knownEndpoint: ValidatedUrl?): PushSubscription? = request {
        pushService.query(knownEndpoint)
    }

    override suspend fun createOrReplacePushSubscription(
        spec: PushSubscriptionSpec,
        previous: PushSubscription?,
    ): PushSubscription = request { pushService.createOrReplace(spec, previous) }

    override suspend fun updatePushAlertPolicy(
        subscription: PushSubscription,
        alerts: Set<NotificationCategory>,
    ): PushSubscription = request { pushService.updatePolicy(subscription, alerts) }

    override suspend fun removePushSubscription(subscription: PushSubscription) = request { pushService.remove(subscription) }

    override fun streamEvents(): Flow<Event> = streamService.events()

    override suspend fun uploadMedia(file: InputStream, mimeType: String) = request {
        MastodonMapper.attachment(api.postMultipart(origin, "api/v1/media", file, mimeType, bearerToken = token).body.toJson())
    }

    override suspend fun search(query: String): List<Post> = request {
        val encodedQuery = URLEncoder.encode(query, Charsets.UTF_8.name())
        val statuses = JSONObject(api.get(origin, "v2/search?q=$encodedQuery", token).body)
            .optJSONArray("statuses") ?: JSONArray()
        (0 until statuses.length()).map { MastodonMapper.post(statuses.getJSONObject(it), origin) }
    }

    override suspend fun searchHashtag(tag: String, cursor: String?): Page<Post> = request {
        val normalized = tag.trim().removePrefix("#")
        require(normalized.matches(Regex("[\\p{L}\\p{N}_](?:[\\p{L}\\p{N}\\p{M}_])*"))) {
            "Enter one exact hashtag, such as #photography."
        }
        val encodedTag = URLEncoder.encode(normalized, Charsets.UTF_8.name())
        val response = getPage("v1/timelines/tag/$encodedTag?limit=40", cursor)
        val statuses = JSONArray(response.body)
        Page(
            items = (0 until statuses.length()).map { MastodonMapper.post(statuses.getJSONObject(it), origin) },
            nextCursor = response.linkHeaderCursor(),
        )
    }

    override suspend fun searchAccounts(query: String): List<Account> = request {
        val handle = query.trim().removePrefix("@").takeIf { it.isNotBlank() }
            ?: throw SourceError.Unsupported("account search")
        listOf(MastodonMapper.account(
            api.get(origin, "v1/accounts/lookup?acct=${URLEncoder.encode(handle, Charsets.UTF_8.name())}", token)
                .body.toJson(), origin,
        ))
    }

    private suspend fun getPage(endpoint: String, cursor: String?) = if (cursor == null) {
        api.get(origin, endpoint, token)
    } else if (cursor.startsWith("http://") || cursor.startsWith("https://")) {
        api.getUrl(validatePaginationUrl(cursor).toString(), token)
    } else {
        api.get(origin, cursor.removePrefix("/api/"), token)
    }

    private fun validatePaginationUrl(cursor: String): HttpUrl {
        val page = cursor.toHttpUrlOrNull() ?: throw SourceError.Unsupported("pagination")
        val authenticatedOrigin = origin.toHttpUrl()
        if (page.scheme != authenticatedOrigin.scheme || page.host != authenticatedOrigin.host ||
            page.port != authenticatedOrigin.port || page.username.isNotEmpty() || page.password.isNotEmpty() ||
            page.fragment != null
        ) {
            throw SourceError.Unsupported("pagination")
        }
        return page
    }

    private suspend fun refreshCapabilities() {
        val probe = capabilityProbe ?: return
        val now = clock()
        val schemaCurrent = capabilities.capabilitySchemaVersion >= ServerCapabilities.CURRENT_CAPABILITY_SCHEMA_VERSION
        if (schemaCurrent && now - capabilities.capabilitiesLastUpdated < CAPABILITIES_TTL_MILLIS) return
        try {
            val probed = probe.probeCapabilities(Connection(origin, Protocol.MASTODON))
            _capabilities.value = probed.copy(
                canPublish = probed.canPublish || capabilities.canPublish,
                notifications = probed.notifications.takeVerifiedOr(capabilities.notifications),
                profile = probed.profile.takeVerifiedOr(capabilities.profile),
                emoji = probed.emoji.takeVerifiedOr(capabilities.emoji),
            )
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            _capabilities.value = capabilities.copy(capabilitiesLastUpdated = 0)
        }
    }

    private suspend fun <T> request(block: suspend () -> T): T = withContext(Dispatchers.IO) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: SourceError) {
            throw e
        } catch (e: ApiFailure) {
            throw MastodonErrorMapper.map(e)
        } catch (e: Exception) {
            throw MastodonErrorMapper.map(e)
        }
    }

    private companion object {
        const val DEFAULT_NOTIFICATION_LIMIT = 30
        const val CAPABILITIES_TTL_MILLIS = 5 * 60 * 1000L
        val DEFAULT_CAPABILITIES = ServerCapabilities(
            timelines = setOf(Timeline.Home, Timeline.Local, Timeline.Federated),
            audiences = setOf(Audience.Public, Audience.Unlisted, Audience.Followers, Audience.Direct),
            actions = setOf(PostAction.Reply, PostAction.Reshare, PostAction.Favorite, PostAction.Bookmark),
            emoji = EmojiCapabilities(catalog = CapabilityStatus.Supported),
            primaryFavourite = me.foxtails.palustris.domain.PrimaryFavouriteCapability(
                me.foxtails.palustris.domain.CapabilityStatus.Supported,
                me.foxtails.palustris.domain.PrimaryFavouriteMode.Native,
            ),
            savedPosts = me.foxtails.palustris.domain.SavedPostsCapability(
                me.foxtails.palustris.domain.CapabilityStatus.Supported,
                me.foxtails.palustris.domain.SavedPostsKind.Bookmarks,
            ),
        )
    }
}

private fun String.encodePathSegment(): String =
    URLEncoder.encode(this, Charsets.UTF_8.name()).replace("+", "%20")

private fun HttpResponse.optionalPost(origin: String): Post? = runCatching {
    JSONObject(body).takeIf { it.optString("id").isNotBlank() }?.let { MastodonMapper.post(it, origin) }
}.getOrNull()

private fun ProfileCapabilities.takeVerifiedOr(previous: ProfileCapabilities): ProfileCapabilities =
    if (this == ProfileCapabilities()) previous else this

private fun EmojiCapabilities.takeVerifiedOr(previous: EmojiCapabilities): EmojiCapabilities =
    if (this == EmojiCapabilities()) previous else this

private fun NotificationCapabilities.takeVerifiedOr(previous: NotificationCapabilities): NotificationCapabilities =
    if (this == NotificationCapabilities()) previous else this

private fun String.toJson(): JSONObject = JSONObject(this)

private fun Audience.toMastodonVisibility(): String = when (this) {
    Audience.Public -> "public"
    Audience.Unlisted -> "unlisted"
    Audience.Followers -> "private"
    Audience.Direct -> "direct"
}

private const val DIRECT_CONVERSATION_LIMIT = 40
