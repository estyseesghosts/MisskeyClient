package me.foxtails.palustris.data.misskey

import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.Notification
import me.foxtails.palustris.domain.NotificationAcknowledgement
import me.foxtails.palustris.domain.NotificationCategory
import me.foxtails.palustris.domain.NotificationCheckpoint
import me.foxtails.palustris.domain.NotificationCursor
import me.foxtails.palustris.domain.NotificationPage
import me.foxtails.palustris.domain.NotificationPageDirection
import me.foxtails.palustris.domain.NotificationQuery
import me.foxtails.palustris.domain.NotificationUnreadState
import me.foxtails.palustris.domain.SourceError
import me.foxtails.palustris.domain.Protocol
import me.foxtails.palustris.domain.NotificationReadState
import me.foxtails.palustris.domain.NotificationReadStatus
import org.json.JSONArray
import org.json.JSONObject

internal class MisskeyNotificationService(
    private val origin: String,
    private val token: String,
    private val api: MisskeyApi,
    private val accountId: AccountId?,
    private val clock: () -> Long,
) {
    suspend fun notifications(cursor: String?): me.foxtails.palustris.domain.Page<Notification> {
        val page = notifications(NotificationQuery(), cursor?.let(::NotificationCursor)); return me.foxtails.palustris.domain.Page(page.items, page.olderCursor?.value)
    }
    suspend fun notifications(query: NotificationQuery, cursor: NotificationCursor?) = loadNotifications(query, cursor, MisskeyNotificationCursorDirection.Older)
    suspend fun fetchNewer(query: NotificationQuery, checkpoint: NotificationCheckpoint): NotificationPage {
        validateCheckpoint(query, checkpoint)
        val stableSinceId = checkpoint.newest?.let { decode(it, requireAccountId(), query, MisskeyNotificationCursorDirection.Newer).rawId }
        val cursor = checkpoint.newerContinuation ?: checkpoint.newest ?: return emptyNotificationPage(query)
        return loadNotifications(query, cursor, MisskeyNotificationCursorDirection.Newer, stableSinceId)
    }
    suspend fun fetchOlder(query: NotificationQuery, checkpoint: NotificationCheckpoint): NotificationPage {
        validateCheckpoint(query, checkpoint); val cursor = checkpoint.olderContinuation ?: checkpoint.oldest ?: return emptyNotificationPage(query)
        return loadNotifications(query, cursor, MisskeyNotificationCursorDirection.Older)
    }
    suspend fun unreadState(): NotificationUnreadState {
        val json = JSONObject(api.post(origin, "i", JSONObject().put("i", token)).body)
        return when {
            json.has("notificationCount") && !json.isNull("notificationCount") -> NotificationUnreadState.Exact(json.optInt("notificationCount").coerceAtLeast(0))
            json.has("hasUnreadNotification") && !json.isNull("hasUnreadNotification") -> if (json.optBoolean("hasUnreadNotification")) NotificationUnreadState.Present else NotificationUnreadState.None
            else -> NotificationUnreadState.Unknown
        }
    }
    suspend fun acknowledge(): NotificationAcknowledgement {
        val account = requireAccountId(); api.post(origin, "notifications/mark-all-as-read", JSONObject().put("i", token))
        return NotificationAcknowledgement(account, NotificationUnreadState.None, clock())
    }
    suspend fun respondToFollowRequest(targetAccountId: AccountId, accept: Boolean) {
        if (targetAccountId.connection != Connection(origin, Protocol.MISSKEY) || targetAccountId.localId.isBlank()) {
            throw SourceError.Unsupported("notifications.followRequest")
        }
        val endpoint = if (accept) "following/requests/accept" else "following/requests/reject"
        api.post(origin, endpoint, JSONObject().put("i", token).put("userId", targetAccountId.localId))
    }
    private suspend fun loadNotifications(query: NotificationQuery, cursor: NotificationCursor?, direction: MisskeyNotificationCursorDirection, stableSinceId: String? = null): NotificationPage {
        val account = requireAccountId(); val types = query.misskeyTypes(); if (!query.isAll && types.isEmpty()) return emptyNotificationPage(query)
        val decoded = cursor?.let { decode(it, account, query, direction) }
        val body = JSONObject().put("i", token).put("limit", query.limit).put("markAsRead", false)
        if (!query.isAll) body.put("includeTypes", JSONArray(types))
        decoded?.rawId?.let { if (direction == MisskeyNotificationCursorDirection.Newer) { body.put("sinceId", stableSinceId ?: it); if (stableSinceId != null && stableSinceId != it) body.put("untilId", it) } else body.put("untilId", it) }
        val endpoint = if (query.grouped) "i/notifications-grouped" else "i/notifications"
        val values = JSONArray(api.post(origin, endpoint, body).body)
        val items = (0 until values.length()).map { MisskeyNotificationMapper.notification(values.getJSONObject(it), origin, account) }
        val newest = items.firstOrNull()?.let { encode(account, query, MisskeyNotificationCursorDirection.Newer, it.id.value) } ?: if (direction == MisskeyNotificationCursorDirection.Newer) cursor else null
        val newerContinuation = if (direction == MisskeyNotificationCursorDirection.Newer && items.size >= query.limit) items.lastOrNull()?.let { encode(account, query, MisskeyNotificationCursorDirection.Newer, it.id.value) } else null
        val oldest = items.lastOrNull()?.let { if (it.id.value == decoded?.rawId && direction == MisskeyNotificationCursorDirection.Older) null else encode(account, query, MisskeyNotificationCursorDirection.Older, it.id.value) }
        return NotificationPage(items, if (direction == MisskeyNotificationCursorDirection.Older) oldest else null, if (direction == MisskeyNotificationCursorDirection.Newer) newerContinuation else newest,
            checkpoint = NotificationCheckpoint(account, query, newest, oldest ?: if (direction == MisskeyNotificationCursorDirection.Older) cursor else null, clock(), newerContinuation = newerContinuation),
            unreadState = NotificationUnreadState.Unknown,
            direction = if (cursor == null) NotificationPageDirection.Initial else if (direction == MisskeyNotificationCursorDirection.Older) NotificationPageDirection.Older else NotificationPageDirection.Newer,
            if (direction == MisskeyNotificationCursorDirection.Older) oldest else newerContinuation, newest, oldest,
            if (direction == MisskeyNotificationCursorDirection.Older) oldest == null else newerContinuation == null)
    }
    private fun requireAccountId() = accountId ?: throw SourceError.Unsupported("notifications.account")
    private fun validateCheckpoint(query: NotificationQuery, checkpoint: NotificationCheckpoint) { if (checkpoint.accountId != requireAccountId() || checkpoint.query != query) throw SourceError.Unsupported("notifications.checkpoint") }
    private fun emptyNotificationPage(query: NotificationQuery) = NotificationPage(
        items = emptyList(),
        checkpoint = NotificationCheckpoint(requireAccountId(), query, capturedAtEpochMillis = clock()),
    )
    private fun encode(account: AccountId, query: NotificationQuery, direction: MisskeyNotificationCursorDirection, rawId: String) = MisskeyNotificationCursorCodec.encode(account, query, direction, rawId)
    private fun decode(cursor: NotificationCursor, account: AccountId, query: NotificationQuery, direction: MisskeyNotificationCursorDirection) = MisskeyNotificationCursorCodec.decode(cursor, account, query, direction)
}
private fun NotificationQuery.misskeyTypes(): List<String> {
    if (isAll) return emptyList()
    return categories.flatMap { when (it) {
        NotificationCategory.All -> emptyList(); NotificationCategory.Mentions -> listOf("mention", "reply"); NotificationCategory.Replies -> listOf("reply"); NotificationCategory.Quotes -> listOf("quote")
        NotificationCategory.Social -> listOf("note", "renote", "reaction", "follow", "receiveFollowRequest", "followRequestAccepted"); NotificationCategory.Polls -> listOf("pollEnded")
        NotificationCategory.System -> listOf("scheduledNotePosted", "scheduledNotePostFailed", "roleAssigned", "achievementEarned", "exportCompleted", "login", "createToken", "app", "test", "chatRoomInvitationReceived")
    } }.distinct()
}
