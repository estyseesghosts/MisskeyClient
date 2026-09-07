package me.foxtails.palustris.data.mastodon

import java.io.InputStream
import java.net.URLEncoder
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.foxtails.palustris.data.misskey.ApiFailure
import me.foxtails.palustris.data.misskey.MisskeyApi
import me.foxtails.palustris.domain.Audience
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.CapabilityProbe
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.CreatePostRequest
import me.foxtails.palustris.domain.EntityId
import me.foxtails.palustris.domain.NotificationCheckpoint
import me.foxtails.palustris.domain.NotificationCapabilities
import me.foxtails.palustris.domain.NotificationCursor
import me.foxtails.palustris.domain.NotificationPage
import me.foxtails.palustris.domain.NotificationQuery
import me.foxtails.palustris.domain.Page
import me.foxtails.palustris.domain.Post
import me.foxtails.palustris.domain.PostAction
import me.foxtails.palustris.domain.Protocol
import me.foxtails.palustris.domain.ServerCapabilities
import me.foxtails.palustris.domain.SocialSource
import me.foxtails.palustris.domain.SourceError
import me.foxtails.palustris.domain.Timeline
import me.foxtails.palustris.domain.UpdateProfileRequest
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

    override suspend fun profile(id: AccountId): Account = request {
        MastodonMapper.account(api.get(origin, "v1/accounts/${id.localId}", token).body.toJson(), origin)
    }

    override suspend fun create(post: CreatePostRequest): Post = request {
        if (post.quoteOf != null) throw SourceError.Unsupported("quote")
        if (post.attachments.isNotEmpty()) throw SourceError.Unsupported("create.attachments")
        if (post.poll != null) throw SourceError.Unsupported("create.poll")
        val fields = buildList {
            add("status" to post.text)
            add("visibility" to post.audience.toMastodonVisibility())
            post.contentWarning?.let { add("spoiler_text" to it) }
            post.replyTo?.let { add("in_reply_to_id" to it.value) }
        }
        MastodonMapper.post(api.postForm(origin, "api/v1/statuses", fields, token).body.toJson(), origin)
    }

    override suspend fun updateProfile(profile: UpdateProfileRequest) = request {
        val response = api.patchForm(origin, "api/v1/accounts/update_credentials", listOf(
            "display_name" to profile.displayName,
            "note" to profile.biography,
        ), token)
        MastodonMapper.account(response.body.toJson(), origin)
    }

    override suspend fun favorite(id: EntityId) = request {
        api.postForm(origin, "api/v1/statuses/${id.value}/favourite", emptyList(), token)
        Unit
    }

    override suspend fun renote(id: EntityId) = request {
        api.postForm(origin, "api/v1/statuses/${id.value}/reblog", emptyList(), token)
        Unit
    }

    override suspend fun quote(id: EntityId, text: String) = throw SourceError.Unsupported("quote")

    override suspend fun notifications(cursor: String?): Page<me.foxtails.palustris.domain.Notification> {
        val page = notifications(NotificationQuery(), cursor?.let(::NotificationCursor))
        return Page(page.items, page.olderCursor?.value)
    }

    override suspend fun notifications(query: NotificationQuery, cursor: NotificationCursor?): NotificationPage = request {
        if (query.categories != setOf(me.foxtails.palustris.domain.NotificationCategory.All)) {
            throw SourceError.Unsupported("notifications.filter")
        }
        val endpoint = if (query.limit == DEFAULT_NOTIFICATION_LIMIT) {
            "v1/notifications"
        } else {
            "v1/notifications?limit=\${query.limit}"
        }
        val response = getPage(endpoint, cursor?.value)
        val notifications = JSONArray(response.body)
        val olderCursor = response.linkHeaderCursor()?.let(::NotificationCursor)
        NotificationPage(
            items = (0 until notifications.length()).map { index ->
                MastodonMapper.notification(notifications.getJSONObject(index), origin, accountId)
            },
            olderCursor = olderCursor,
            checkpoint = NotificationCheckpoint(
                accountId = accountId,
                query = query,
                oldest = olderCursor,
                capturedAtEpochMillis = clock(),
            ),
        )
    }

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
        if (now - capabilities.capabilitiesLastUpdated < CAPABILITIES_TTL_MILLIS) return
        try {
            val probed = probe.probeCapabilities(Connection(origin, Protocol.MASTODON))
            _capabilities.value = probed.copy(
                canPublish = probed.canPublish || capabilities.canPublish,
                notifications = probed.notifications.takeVerifiedOr(capabilities.notifications),
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw if (e is SourceError) e else MastodonErrorMapper.map(e)
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
        )
    }
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
