package me.foxtails.palustris.data.mastodon

import java.io.InputStream
import java.net.URLEncoder
import java.util.Base64
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.channels.awaitClose
import me.foxtails.palustris.data.misskey.ApiFailure
import me.foxtails.palustris.data.misskey.HttpResponse
import me.foxtails.palustris.data.misskey.MisskeyApi
import me.foxtails.palustris.domain.Audience
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.CapabilityProbe
import me.foxtails.palustris.domain.CapabilityStatus
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.CreatePostRequest
import me.foxtails.palustris.domain.CustomEmoji
import me.foxtails.palustris.domain.EditableProfile
import me.foxtails.palustris.domain.EditableProfilePatch
import me.foxtails.palustris.domain.EmojiCapabilities
import me.foxtails.palustris.domain.EmojiChoice
import me.foxtails.palustris.domain.EntityId
import me.foxtails.palustris.domain.Event
import me.foxtails.palustris.domain.SocialEvent
import me.foxtails.palustris.domain.NotificationCheckpoint
import me.foxtails.palustris.domain.NotificationAcknowledgement
import me.foxtails.palustris.domain.NotificationCategory
import me.foxtails.palustris.domain.NotificationCapabilities
import me.foxtails.palustris.domain.NotificationCursor
import me.foxtails.palustris.domain.NotificationPage
import me.foxtails.palustris.domain.NotificationPageDirection
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
) : SocialSource {
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

    override suspend fun notifications(cursor: String?): Page<me.foxtails.palustris.domain.Notification> {
        val page = notifications(NotificationQuery(), cursor?.let(::NotificationCursor))
        return Page(page.items, page.olderCursor?.value)
    }

    override suspend fun notifications(query: NotificationQuery, cursor: NotificationCursor?): NotificationPage = request {
        loadNotifications(query, cursor, cursorDirection = CursorDirection.Older)
    }

    override suspend fun fetchNewerNotifications(
        query: NotificationQuery,
        checkpoint: NotificationCheckpoint,
    ): NotificationPage = request {
        validateCheckpoint(query, checkpoint)
        val cursor = checkpoint.newerContinuation ?: checkpoint.newest
        if (cursor == null && checkpoint.baselineEstablished) {
            return@request loadNotifications(query, null, CursorDirection.Newer)
        }
        cursor ?: return@request emptyNotificationPage(query)
        loadNotifications(query, cursor, CursorDirection.Newer)
    }

    override suspend fun fetchOlderNotifications(
        query: NotificationQuery,
        checkpoint: NotificationCheckpoint,
    ): NotificationPage = request {
        validateCheckpoint(query, checkpoint)
        val cursor = checkpoint.olderContinuation ?: checkpoint.oldest
            ?: return@request emptyNotificationPage(query)
        loadNotifications(query, cursor, CursorDirection.Older)
    }

    override suspend fun notificationUnreadState(): NotificationUnreadState = request {
        val count = JSONObject(api.get(origin, "v1/notifications/unread_count", token).body).optInt("count", -1)
        if (count < 0) NotificationUnreadState.Unknown else NotificationUnreadState.AtLeast(count)
    }

    override suspend fun pushProviderInfo(): PushProviderInfo = PushProviderInfo(
        status = CapabilityStatus.Supported,
    )

    override suspend fun acknowledgeNotifications(): NotificationAcknowledgement = request {
        val latest = runCatching {
            val response = api.get(origin, "v1/notifications?limit=1", token)
            JSONArray(response.body).optJSONObject(0)?.optString("id").orEmpty()
        }.getOrElse { throw it }
        if (latest.isBlank()) return@request NotificationAcknowledgement(accountId, NotificationUnreadState.None, clock())
        api.postForm(
            origin,
            "api/v1/markers",
            listOf("notifications[last_read_id]" to latest),
            token,
        )
        NotificationAcknowledgement(accountId, NotificationUnreadState.None, clock())
    }

    override suspend fun respondToFollowRequest(targetAccountId: AccountId, accept: Boolean) = request {
        validateFollowRequestTarget(targetAccountId)
        val action = if (accept) "authorize" else "reject"
        api.postForm(origin, "api/v1/follow_requests/${targetAccountId.localId.encodePathSegment()}/$action", emptyList(), token)
        Unit
    }

    private fun validateFollowRequestTarget(targetAccountId: AccountId) {
        if (targetAccountId.connection != Connection(origin, Protocol.MASTODON) || targetAccountId.localId.isBlank()) {
            throw SourceError.Unsupported("notifications.followRequest")
        }
    }

    private fun validatePostId(id: EntityId, feature: String) {
        if (id.connection != origin || id.value.isBlank()) throw SourceError.Unsupported(feature)
    }

    override suspend fun dismissNotification(id: EntityId) = request {
        api.postForm(origin, "api/v1/notifications/${id.value.encodePathSegment()}/dismiss", emptyList(), token)
        Unit
    }

    override suspend fun queryOwnedPushSubscription(knownEndpoint: ValidatedUrl?): PushSubscription? = request {
        val subscription = readOwnedPushSubscription()
        if (knownEndpoint != null && subscription != null && subscription.endpoint != knownEndpoint) {
            throw SourceError.ServerError("notifications.push.identity-changed")
        }
        subscription
    }

    override suspend fun createOrReplacePushSubscription(
        spec: PushSubscriptionSpec,
        previous: PushSubscription?,
    ): PushSubscription = request {
        validatePushSpec(spec)
        previous?.let(::validatePushSubscription)
        // Mastodon POST owns the account's single subscription and is the replacement operation.
        confirmedPushSubscription(
            api.postForm(origin, "api/v1/push/subscription", pushCreateFields(spec), token).body,
            expectedEndpoint = spec.endpoint,
        )
    }

    override suspend fun updatePushAlertPolicy(
        subscription: PushSubscription,
        alerts: Set<NotificationCategory>,
    ): PushSubscription = request {
        validatePushSubscription(subscription)
        val confirmed = confirmedPushSubscription(
            api.putForm(origin, "api/v1/push/subscription", pushAlertFields(alerts), token).body,
            expectedEndpoint = subscription.endpoint,
        )
        if (!samePushIdentity(confirmed, subscription)) {
            throw SourceError.ServerError("notifications.push.identity-changed")
        }
        confirmed
    }

    override suspend fun removePushSubscription(subscription: PushSubscription) = request {
        validatePushSubscription(subscription)
        val current = readOwnedPushSubscription() ?: return@request Unit
        if (!samePushIdentity(current, subscription)) {
            throw SourceError.ServerError("notifications.push.identity-changed")
        }
        api.delete(origin, "api/v1/push/subscription", token)
        Unit
    }

    private fun validatePushSpec(spec: PushSubscriptionSpec) {
        if (spec.accountId != accountId || spec.publicKey.isBlank() || spec.authSecret.isBlank()) {
            throw SourceError.Unsupported("notifications.push.spec")
        }
    }

    private suspend fun readOwnedPushSubscription(): PushSubscription? = try {
        confirmedPushSubscription(api.get(origin, "v1/push/subscription", token).body)
    } catch (error: ApiFailure) {
        if (error.status == 404) null else throw error
    }

    private fun confirmedPushSubscription(body: String, expectedEndpoint: ValidatedUrl? = null): PushSubscription {
        val json = JSONObject(body)
        val endpoint = ValidatedUrl.https(json.optString("endpoint"))
            ?: throw SourceError.ServerError("notifications.push.confirmation")
        if (expectedEndpoint != null && endpoint != expectedEndpoint) {
            throw SourceError.ServerError("notifications.push.confirmation")
        }
        val remoteId = json.optString("id").takeIf { it.isNotBlank() }
            ?: throw SourceError.ServerError("notifications.push.confirmation")
        return PushSubscription(
            accountId = accountId,
            endpoint = endpoint,
            remoteId = remoteId,
        )
    }

    private fun validatePushSubscription(subscription: PushSubscription) {
        if (subscription.accountId != accountId || subscription.endpoint.value.isBlank()) {
            throw SourceError.AccountMismatch
        }
    }

    private fun samePushIdentity(first: PushSubscription, second: PushSubscription): Boolean =
        first.accountId == second.accountId && first.endpoint == second.endpoint && first.remoteId == second.remoteId

    private fun pushCreateFields(spec: PushSubscriptionSpec): List<Pair<String, String>> = buildList {
        add("subscription[endpoint]" to spec.endpoint.value)
        add("subscription[keys][p256dh]" to spec.publicKey)
        add("subscription[keys][auth]" to spec.authSecret)
        add("subscription[standard]" to spec.standardWebPush.toString())
        addAll(pushAlertFields(spec.alerts))
    }

    private fun pushAlertFields(alerts: Set<NotificationCategory>): List<Pair<String, String>> {
        val all = NotificationCategory.All in alerts
        fun enabled(category: NotificationCategory): String = (all || category in alerts).toString()
        return listOf(
            "data[alerts][mention]" to enabled(NotificationCategory.Mentions),
            "data[alerts][quote]" to enabled(NotificationCategory.Quotes),
            "data[alerts][reblog]" to enabled(NotificationCategory.Social),
            "data[alerts][follow]" to enabled(NotificationCategory.Social),
            "data[alerts][follow_request]" to enabled(NotificationCategory.Social),
            "data[alerts][favourite]" to enabled(NotificationCategory.Social),
            "data[alerts][poll]" to enabled(NotificationCategory.Polls),
            "data[alerts][status]" to enabled(NotificationCategory.Social),
            "data[alerts][update]" to enabled(NotificationCategory.Social),
            "data[alerts][quoted_update]" to enabled(NotificationCategory.Quotes),
        )
    }

    override fun streamEvents(): Flow<Event> = callbackFlow {
        val socket = api.webSocket(
            origin,
            "/api/v1/streaming/user",
            headers = mapOf("Authorization" to "Bearer $token"),
            listener = object : okhttp3.WebSocketListener() {
                override fun onOpen(webSocket: okhttp3.WebSocket, response: okhttp3.Response) {
                    trySend(Event(accountId, SocialEvent.Other("stream.ready")))
                }

                override fun onMessage(webSocket: okhttp3.WebSocket, text: String) {
                    runCatching {
                        val message = JSONObject(text)
                        when (message.optString("event")) {
                            "notification" -> {
                                val payload = message.optString("payload").takeIf { it.isNotBlank() }
                                    ?.let(::JSONObject) ?: return@runCatching
                                trySend(Event(accountId, SocialEvent.NotificationReceived(
                                    MastodonNotificationMapper.notification(payload, origin, accountId),
                                )))
                            }
                            "delete", "filters_changed" -> trySend(Event(accountId, SocialEvent.Other("notification.refresh")))
                        }
                    }.onFailure { trySend(Event(accountId, SocialEvent.Other("notification.refresh"))) }
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

    private suspend fun loadNotifications(
        query: NotificationQuery,
        cursor: NotificationCursor?,
        cursorDirection: CursorDirection,
    ): NotificationPage {
        val account = accountId
        val variant = if (query.grouped) NotificationApiVariant.V2 else NotificationApiVariant.V1
        val decodedCursor = cursor?.let { decodeCursor(it, query, variant, cursorDirection) }
        val response = getNotificationPage(query, variant, decodedCursor?.url)
        return if (variant == NotificationApiVariant.V1) {
            val values = JSONArray(response.body)
            val items = (0 until values.length()).map { index ->
                MastodonNotificationMapper.notification(values.getJSONObject(index), origin, account)
            }
            val previousUrl = response.linkHeaderCursor("prev")
                ?: if (cursor == null && cursorDirection == CursorDirection.Older) {
                    items.firstOrNull()?.id?.value?.let { notificationUrl(query, variant, minId = it).toString() }
                } else {
                    null
                }
            val nextUrl = response.linkHeaderCursor("next")
            pageFromContinuations(query, items, previousUrl, nextUrl, decodedCursor?.url, variant, cursorDirection)
        } else {
            val payload = JSONObject(response.body)
            val accounts = payload.optJSONArray("accounts").toAccounts(origin)
            val statuses = payload.optJSONArray("statuses").toPosts(origin)
            val groups = payload.optJSONArray("notification_groups")
                ?: throw SourceError.ServerError("Grouped notifications response was missing notification_groups")
            val items = (0 until groups.length()).map { index ->
                MastodonNotificationMapper.groupedNotification(groups.getJSONObject(index), accounts, statuses, origin, account)
            }
            val previousUrl = response.linkHeaderCursor("prev")
            val nextUrl = response.linkHeaderCursor("next")
            pageFromContinuations(query, items, previousUrl, nextUrl, decodedCursor?.url, variant, cursorDirection)
        }
    }

    private fun pageFromContinuations(
        query: NotificationQuery,
        items: List<Notification>,
        previousUrl: String?,
        nextUrl: String?,
        currentUrl: String?,
        variant: NotificationApiVariant,
        cursorDirection: CursorDirection,
    ): NotificationPage {
        val newerCursor = previousUrl?.let { continuation(it, query, variant, CursorDirection.Newer, currentUrl) }
        val olderCursor = nextUrl?.let { continuation(it, query, variant, CursorDirection.Older, currentUrl) }
        val direction = if (currentUrl == null) NotificationPageDirection.Initial else when (cursorDirection) {
            CursorDirection.Newer -> NotificationPageDirection.Newer
            CursorDirection.Older -> NotificationPageDirection.Older
        }
        val continuation = when (direction) {
            NotificationPageDirection.Newer -> newerCursor
            NotificationPageDirection.Older -> olderCursor
            NotificationPageDirection.Initial -> null
        }
        return NotificationPage(
            items = items,
            olderCursor = olderCursor,
            newerCursor = newerCursor,
            checkpoint = NotificationCheckpoint(
                accountId = accountId,
                query = query,
                newest = newerCursor ?: items.firstOrNull()?.id?.value?.let {
                    NotificationCursorCodec.encode(
                        variant,
                        accountId,
                        query,
                        CursorDirection.Newer,
                        notificationUrl(query, variant, minId = it).toString(),
                    )
                },
                oldest = olderCursor,
                capturedAtEpochMillis = clock(),
                newerContinuation = if (direction == NotificationPageDirection.Newer) newerCursor else null,
                olderContinuation = if (direction == NotificationPageDirection.Older) olderCursor else null,
            ),
            direction = direction,
            continuation = continuation,
            newestBoundary = newerCursor ?: items.firstOrNull()?.id?.value?.let {
                NotificationCursorCodec.encode(
                    variant,
                    accountId,
                    query,
                    CursorDirection.Newer,
                    notificationUrl(query, variant, minId = it).toString(),
                )
            },
            oldestBoundary = olderCursor,
            reachedBoundary = continuation == null,
        )
    }

    private suspend fun getNotificationPage(
        query: NotificationQuery,
        variant: NotificationApiVariant,
        cursorUrl: String?,
    ): me.foxtails.palustris.data.misskey.HttpResponse {
        if (cursorUrl == null) return api.getUrl(notificationUrl(query, variant).toString(), token)
        return api.getUrl(validateNotificationPaginationUrl(cursorUrl, variant).toString(), token)
    }

    private fun notificationUrl(
        query: NotificationQuery,
        variant: NotificationApiVariant,
        minId: String? = null,
        maxId: String? = null,
    ): HttpUrl = origin.toHttpUrl().newBuilder()
        .addPathSegments(variant.path)
        .apply {
            if (variant == NotificationApiVariant.V2 || query.limit != DEFAULT_NOTIFICATION_LIMIT) {
                addQueryParameter("limit", query.limit.toString())
            }
            minId?.let { addQueryParameter("min_id", it) }
            maxId?.let { addQueryParameter("max_id", it) }
            query.mastodonTypes().forEach { addQueryParameter("types[]", it) }
            if (variant == NotificationApiVariant.V2) {
                listOf("favourite", "follow", "reblog", "admin.sign_up").forEach {
                    addQueryParameter("grouped_types[]", it)
                }
            }
        }
        .build()

    private fun validateNotificationPaginationUrl(cursor: String, variant: NotificationApiVariant): HttpUrl {
        val page = cursor.toHttpUrlOrNull() ?: origin.toHttpUrl().resolve(cursor)
            ?: throw SourceError.Unsupported("notifications.pagination")
        val authenticatedOrigin = origin.toHttpUrl()
        if (page.scheme != authenticatedOrigin.scheme || page.host != authenticatedOrigin.host ||
            page.port != authenticatedOrigin.port || page.username.isNotEmpty() || page.password.isNotEmpty() ||
            page.fragment != null || page.encodedPath != "/${variant.path}"
        ) {
            throw SourceError.Unsupported("notifications.pagination")
        }
        return page
    }

    private fun continuation(
        url: String,
        query: NotificationQuery,
        variant: NotificationApiVariant,
        direction: CursorDirection,
        currentUrl: String?,
    ): NotificationCursor? {
        val validated = validateNotificationPaginationUrl(url, variant).toString()
        if (validated == currentUrl) return null
        return NotificationCursorCodec.encode(variant, accountId, query, direction, validated)
    }

    private fun decodeCursor(
        cursor: NotificationCursor,
        query: NotificationQuery,
        variant: NotificationApiVariant,
        direction: CursorDirection,
    ): NotificationCursorCodec.Decoded {
        return NotificationCursorCodec.decode(cursor, variant, accountId, query, direction).also {
            validateNotificationPaginationUrl(it.url, variant)
        }
    }

    private fun validateCheckpoint(query: NotificationQuery, checkpoint: NotificationCheckpoint) {
        if (checkpoint.accountId != accountId || checkpoint.query != query) {
            throw SourceError.Unsupported("notifications.checkpoint")
        }
    }

    private fun emptyNotificationPage(query: NotificationQuery) = NotificationPage(
        items = emptyList(),
        checkpoint = NotificationCheckpoint(accountId, query, capturedAtEpochMillis = clock()),
    )

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

private enum class CursorDirection { Older, Newer }

private enum class NotificationApiVariant(val path: String, val tag: String) {
    V1("api/v1/notifications", "v1"),
    V2("api/v2/notifications", "v2"),
}

private object NotificationCursorCodec {
    data class Decoded(val url: String)

    fun encode(
        variant: NotificationApiVariant,
        accountId: AccountId,
        query: NotificationQuery,
        direction: CursorDirection,
        url: String,
    ): NotificationCursor {
        val payload = JSONObject()
            .put("variant", variant.tag)
            .put("origin", accountId.connection.origin)
            .put("account", accountId.localId)
            .put("query", query.fingerprint())
            .put("direction", direction.name)
            .put("url", url)
        return NotificationCursor(Base64.getUrlEncoder().withoutPadding()
            .encodeToString(payload.toString().toByteArray(Charsets.UTF_8)))
    }

    fun decode(
        cursor: NotificationCursor,
        variant: NotificationApiVariant,
        accountId: AccountId,
        query: NotificationQuery,
        direction: CursorDirection,
    ): Decoded {
        val json = runCatching {
            JSONObject(String(Base64.getUrlDecoder().decode(cursor.value), Charsets.UTF_8))
        }.getOrElse { throw SourceError.Unsupported("notifications.cursor") }
        if (json.optString("variant") != variant.tag ||
            json.optString("origin") != accountId.connection.origin ||
            json.optString("account") != accountId.localId ||
            json.optString("query") != query.fingerprint() ||
            json.optString("direction") != direction.name
        ) {
            throw SourceError.Unsupported("notifications.cursor")
        }
        return Decoded(json.optString("url").takeIf(String::isNotBlank)
            ?: throw SourceError.Unsupported("notifications.cursor"))
    }
}

private fun NotificationQuery.fingerprint(): String = buildString {
    append(categories.map { it.name }.sorted().joinToString(","))
    append('|').append(limit).append('|').append(grouped)
}

private fun NotificationQuery.mastodonTypes(): List<String> {
    if (isAll) return emptyList()
    return categories.flatMap { category ->
        when (category) {
            NotificationCategory.All -> emptyList()
            NotificationCategory.Mentions, NotificationCategory.Replies -> listOf("mention")
            NotificationCategory.Quotes -> listOf("quote", "quoted_update")
            NotificationCategory.Social -> listOf("reblog", "follow", "follow_request", "favourite", "status")
            NotificationCategory.Polls -> listOf("poll")
            NotificationCategory.System -> listOf(
                "update", "admin.sign_up", "admin.report", "severed_relationships",
                "added_to_collection", "collection_update",
            )
        }
    }.distinct().sorted()
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

private fun JSONArray?.toAccounts(origin: String): Map<String, Account> {
    if (this == null) return emptyMap()
    return (0 until length()).mapNotNull { index ->
        runCatching { MastodonMapper.account(getJSONObject(index), origin) }.getOrNull()
    }.associateBy { it.id.localId }
}

private fun JSONArray?.toPosts(origin: String): Map<String, Post> {
    if (this == null) return emptyMap()
    return (0 until length()).mapNotNull { index ->
        runCatching { MastodonMapper.post(getJSONObject(index), origin) }.getOrNull()
    }.associateBy { it.id.value }
}

private fun NotificationCapabilities.takeVerifiedOr(previous: NotificationCapabilities): NotificationCapabilities =
    if (this == NotificationCapabilities()) previous else this

private fun String.toJson(): JSONObject = JSONObject(this)

private fun Audience.toMastodonVisibility(): String = when (this) {
    Audience.Public -> "public"
    Audience.Unlisted -> "unlisted"
    Audience.Followers -> "private"
    Audience.Direct -> "direct"
}
