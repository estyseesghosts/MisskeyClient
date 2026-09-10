package me.foxtails.palustris.data.mastodon

import me.foxtails.palustris.data.misskey.HttpResponse
import me.foxtails.palustris.data.misskey.MisskeyApi
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.Notification
import me.foxtails.palustris.domain.NotificationAcknowledgement
import me.foxtails.palustris.domain.NotificationCapabilities
import me.foxtails.palustris.domain.NotificationCategory
import me.foxtails.palustris.domain.NotificationCheckpoint
import me.foxtails.palustris.domain.NotificationCursor
import me.foxtails.palustris.domain.NotificationPage
import me.foxtails.palustris.domain.NotificationPageDirection
import me.foxtails.palustris.domain.NotificationQuery
import me.foxtails.palustris.domain.NotificationUnreadState
import me.foxtails.palustris.domain.SourceError
import me.foxtails.palustris.domain.EntityId
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONArray
import org.json.JSONObject

internal class MastodonNotificationService(
    private val origin: String,
    private val token: String,
    private val api: MisskeyApi,
    private val accountId: AccountId,
    private val clock: () -> Long,
) {
    suspend fun notifications(cursor: String?): me.foxtails.palustris.domain.Page<Notification> {
        val page = notifications(NotificationQuery(), cursor?.let(::NotificationCursor))
        return me.foxtails.palustris.domain.Page(page.items, page.olderCursor?.value)
    }

    suspend fun notifications(query: NotificationQuery, cursor: NotificationCursor?): NotificationPage =
        loadNotifications(query, cursor, MastodonNotificationCursorDirection.Older)

    suspend fun fetchNewerNotifications(query: NotificationQuery, checkpoint: NotificationCheckpoint): NotificationPage {
        validateCheckpoint(query, checkpoint)
        val cursor = checkpoint.newerContinuation ?: checkpoint.newest
        if (cursor == null && checkpoint.baselineEstablished) return loadNotifications(query, null, MastodonNotificationCursorDirection.Newer)
        cursor ?: return emptyNotificationPage(query)
        return loadNotifications(query, cursor, MastodonNotificationCursorDirection.Newer)
    }

    suspend fun fetchOlderNotifications(query: NotificationQuery, checkpoint: NotificationCheckpoint): NotificationPage {
        validateCheckpoint(query, checkpoint)
        val cursor = checkpoint.olderContinuation ?: checkpoint.oldest ?: return emptyNotificationPage(query)
        return loadNotifications(query, cursor, MastodonNotificationCursorDirection.Older)
    }

    suspend fun unreadState(): NotificationUnreadState {
        val count = JSONObject(api.get(origin, "v1/notifications/unread_count", token).body).optInt("count", -1)
        return if (count < 0) NotificationUnreadState.Unknown else NotificationUnreadState.AtLeast(count)
    }

    suspend fun acknowledge(): NotificationAcknowledgement {
        val latest = api.get(origin, "v1/notifications?limit=1", token)
            .let { JSONArray(it.body).optJSONObject(0)?.optString("id").orEmpty() }
        if (latest.isBlank()) return NotificationAcknowledgement(accountId, NotificationUnreadState.None, clock())
        api.postForm(origin, "api/v1/markers", listOf("notifications[last_read_id]" to latest), token)
        return NotificationAcknowledgement(accountId, NotificationUnreadState.None, clock())
    }

    suspend fun respondToFollowRequest(targetAccountId: AccountId, accept: Boolean) {
        if (targetAccountId.connection != me.foxtails.palustris.domain.Connection(origin, me.foxtails.palustris.domain.Protocol.MASTODON) ||
            targetAccountId.localId.isBlank()
        ) throw SourceError.Unsupported("notifications.followRequest")
        val action = if (accept) "authorize" else "reject"
        api.postForm(origin, "api/v1/follow_requests/${targetAccountId.localId.encodePathSegment()}/$action", emptyList(), token)
    }

    suspend fun dismiss(id: EntityId) {
        api.postForm(origin, "api/v1/notifications/${id.value.encodePathSegment()}/dismiss", emptyList(), token)
    }

    private suspend fun loadNotifications(query: NotificationQuery, cursor: NotificationCursor?, direction: MastodonNotificationCursorDirection): NotificationPage {
        val variant = if (query.grouped) MastodonNotificationApiVariant.V2 else MastodonNotificationApiVariant.V1
        val decodedCursor = cursor?.let { decodeCursor(it, query, variant, direction) }
        val response = if (decodedCursor == null) api.getUrl(notificationUrl(query, variant).toString(), token)
        else api.getUrl(validateNotificationPaginationUrl(decodedCursor.url, variant).toString(), token)
        return if (variant == MastodonNotificationApiVariant.V1) {
            val values = JSONArray(response.body)
            val items = (0 until values.length()).map { MastodonNotificationMapper.notification(values.getJSONObject(it), origin, accountId) }
            val previousUrl = response.linkHeaderCursor("prev") ?: if (cursor == null && direction == MastodonNotificationCursorDirection.Older) {
                items.firstOrNull()?.id?.value?.let { notificationUrl(query, variant, minId = it).toString() }
            } else null
            pageFromContinuations(query, items, previousUrl, response.linkHeaderCursor("next"), decodedCursor?.url, variant, direction)
        } else {
            val payload = JSONObject(response.body)
            val accounts = payload.optJSONArray("accounts").toAccounts(origin)
            val statuses = payload.optJSONArray("statuses").toPosts(origin)
            val groups = payload.optJSONArray("notification_groups")
                ?: throw SourceError.ServerError("Grouped notifications response was missing notification_groups")
            val items = (0 until groups.length()).map {
                MastodonNotificationMapper.groupedNotification(groups.getJSONObject(it), accounts, statuses, origin, accountId)
            }
            pageFromContinuations(query, items, response.linkHeaderCursor("prev"), response.linkHeaderCursor("next"), decodedCursor?.url, variant, direction)
        }
    }

    private fun pageFromContinuations(
        query: NotificationQuery,
        items: List<Notification>,
        previousUrl: String?,
        nextUrl: String?,
        currentUrl: String?,
        variant: MastodonNotificationApiVariant,
        direction: MastodonNotificationCursorDirection,
    ): NotificationPage {
        val newerCursor = previousUrl?.let { continuation(it, query, variant, MastodonNotificationCursorDirection.Newer, currentUrl) }
        val olderCursor = nextUrl?.let { continuation(it, query, variant, MastodonNotificationCursorDirection.Older, currentUrl) }
        val pageDirection = if (currentUrl == null) NotificationPageDirection.Initial else when (direction) {
            MastodonNotificationCursorDirection.Newer -> NotificationPageDirection.Newer
            MastodonNotificationCursorDirection.Older -> NotificationPageDirection.Older
        }
        val continuation = when (pageDirection) {
            NotificationPageDirection.Newer -> newerCursor
            NotificationPageDirection.Older -> olderCursor
            NotificationPageDirection.Initial -> null
        }
        val newest = newerCursor ?: items.firstOrNull()?.id?.value?.let {
            MastodonNotificationCursorCodec.encode(variant, accountId, query, MastodonNotificationCursorDirection.Newer, notificationUrl(query, variant, minId = it).toString())
        }
        return NotificationPage(
            items = items,
            olderCursor = olderCursor,
            newerCursor = newerCursor,
            checkpoint = NotificationCheckpoint(
                accountId, query, newest = newest, oldest = olderCursor, capturedAtEpochMillis = clock(),
                newerContinuation = if (pageDirection == NotificationPageDirection.Newer) newerCursor else null,
                olderContinuation = if (pageDirection == NotificationPageDirection.Older) olderCursor else null,
            ),
            direction = pageDirection,
            continuation = continuation,
            newestBoundary = newest,
            oldestBoundary = olderCursor,
            reachedBoundary = continuation == null,
        )
    }

    private fun notificationUrl(query: NotificationQuery, variant: MastodonNotificationApiVariant, minId: String? = null, maxId: String? = null): HttpUrl =
        origin.toHttpUrl().newBuilder().addPathSegments(variant.path).apply {
            if (variant == MastodonNotificationApiVariant.V2 || query.limit != DEFAULT_NOTIFICATION_LIMIT) addQueryParameter("limit", query.limit.toString())
            minId?.let { addQueryParameter("min_id", it) }
            maxId?.let { addQueryParameter("max_id", it) }
            query.mastodonTypes().forEach { addQueryParameter("types[]", it) }
            if (variant == MastodonNotificationApiVariant.V2) listOf("favourite", "follow", "reblog", "admin.sign_up").forEach { addQueryParameter("grouped_types[]", it) }
        }.build()

    private fun validateNotificationPaginationUrl(cursor: String, variant: MastodonNotificationApiVariant): HttpUrl {
        val page = cursor.toHttpUrlOrNull() ?: origin.toHttpUrl().resolve(cursor) ?: throw SourceError.Unsupported("notifications.pagination")
        val authenticatedOrigin = origin.toHttpUrl()
        if (page.scheme != authenticatedOrigin.scheme || page.host != authenticatedOrigin.host || page.port != authenticatedOrigin.port ||
            page.username.isNotEmpty() || page.password.isNotEmpty() || page.fragment != null || page.encodedPath != "/${variant.path}"
        ) throw SourceError.Unsupported("notifications.pagination")
        return page
    }

    private fun continuation(url: String, query: NotificationQuery, variant: MastodonNotificationApiVariant, direction: MastodonNotificationCursorDirection, currentUrl: String?): NotificationCursor? {
        val validated = validateNotificationPaginationUrl(url, variant).toString()
        if (validated == currentUrl) return null
        return MastodonNotificationCursorCodec.encode(variant, accountId, query, direction, validated)
    }

    private fun decodeCursor(cursor: NotificationCursor, query: NotificationQuery, variant: MastodonNotificationApiVariant, direction: MastodonNotificationCursorDirection): MastodonNotificationCursorCodec.Decoded =
        MastodonNotificationCursorCodec.decode(cursor, variant, accountId, query, direction).also { validateNotificationPaginationUrl(it.url, variant) }

    private fun validateCheckpoint(query: NotificationQuery, checkpoint: NotificationCheckpoint) {
        if (checkpoint.accountId != accountId || checkpoint.query != query) throw SourceError.Unsupported("notifications.checkpoint")
    }

    private fun emptyNotificationPage(query: NotificationQuery) = NotificationPage(
        items = emptyList(),
        checkpoint = NotificationCheckpoint(accountId, query, capturedAtEpochMillis = clock()),
    )

    private companion object { const val DEFAULT_NOTIFICATION_LIMIT = 30 }
}

private fun NotificationQuery.mastodonTypes(): List<String> {
    if (isAll) return emptyList()
    return categories.flatMap { when (it) {
        NotificationCategory.All -> emptyList()
        NotificationCategory.Mentions, NotificationCategory.Replies -> listOf("mention")
        NotificationCategory.Quotes -> listOf("quote", "quoted_update")
        NotificationCategory.Social -> listOf("reblog", "follow", "follow_request", "favourite", "status")
        NotificationCategory.Polls -> listOf("poll")
        NotificationCategory.System -> listOf("update", "admin.sign_up", "admin.report", "severed_relationships", "added_to_collection", "collection_update")
    } }.distinct().sorted()
}

private fun String.encodePathSegment(): String = java.net.URLEncoder.encode(this, Charsets.UTF_8.name()).replace("+", "%20")
private fun JSONArray?.toAccounts(origin: String): Map<String, Account> = if (this == null) emptyMap() else (0 until length()).mapNotNull { runCatching { MastodonMapper.account(getJSONObject(it), origin) }.getOrNull() }.associateBy { it.id.localId }
private fun JSONArray?.toPosts(origin: String): Map<String, me.foxtails.palustris.domain.Post> = if (this == null) emptyMap() else (0 until length()).mapNotNull { runCatching { MastodonMapper.post(getJSONObject(it), origin) }.getOrNull() }.associateBy { it.id.value }
