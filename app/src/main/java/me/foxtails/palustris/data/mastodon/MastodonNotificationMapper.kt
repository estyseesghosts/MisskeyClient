package me.foxtails.palustris.data.mastodon

import java.time.Instant
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.EntityId
import me.foxtails.palustris.domain.Notification
import me.foxtails.palustris.domain.NotificationActivity
import me.foxtails.palustris.domain.NotificationDestination
import me.foxtails.palustris.domain.NotificationGroup
import me.foxtails.palustris.domain.NotificationGroupId
import me.foxtails.palustris.domain.NotificationTarget
import me.foxtails.palustris.domain.Post
import org.json.JSONObject

object MastodonNotificationMapper {
    fun notification(json: JSONObject, origin: String, receivingAccountId: AccountId): Notification {
        val rawType = json.optString("type").ifBlank { "unknown" }
        val actor = runCatching { json.optJSONObject("account")?.let { MastodonMapper.account(it, origin) } }.getOrNull()
        val mappedPost = runCatching { json.optJSONObject("status")?.let { MastodonMapper.post(it, origin) } }.getOrNull()
        val target = when {
            mappedPost != null -> NotificationTarget.Post(mappedPost.id)
            actor != null && rawType in PROFILE_TARGET_TYPES -> NotificationTarget.Profile(actor.id)
            else -> null
        }
        return Notification(
            id = EntityId(origin, json.getString("id")),
            accountId = receivingAccountId,
            createdAtEpochMillis = parseInstant(json.optString("created_at")),
            activity = rawType.toNotificationActivity(
                isReplyToReceivingAccount = json.optJSONObject("status")
                    ?.optString("in_reply_to_account_id") == receivingAccountId.localId,
            ),
            actors = listOfNotNull(actor),
            target = target,
            destination = target?.let(NotificationDestination::InApp),
            post = mappedPost,
            rawType = rawType,
        )
    }

    fun groupedNotification(
        json: JSONObject,
        accounts: Map<String, Account>,
        statuses: Map<String, Post>,
        origin: String,
        receivingAccountId: AccountId,
    ): Notification {
        val rawType = json.optString("type").ifBlank { "unknown" }
        val groupKey = json.optString("group_key").ifBlank {
            "ungrouped-${json.optString("most_recent_notification_id").ifBlank { json.optString("latest_page_notification_id") }}"
        }
        val actorPreviews = json.optJSONArray("sample_account_ids")?.let { ids ->
            (0 until ids.length()).mapNotNull { ids.optString(it).takeIf(String::isNotBlank) }.mapNotNull(accounts::get)
        }.orEmpty()
        val statusId = json.optString("status_id").takeIf(String::isNotBlank)
        val mappedPost = statusId?.let(statuses::get)
            ?: runCatching { json.optJSONObject("status")?.let { MastodonMapper.post(it, origin) } }.getOrNull()
        val fallback = json.optJSONObject("fallback")?.let { runCatching { notification(it, origin, receivingAccountId) }.getOrNull() }
        val actors = actorPreviews.ifEmpty { fallback?.actors.orEmpty() }
        val target = mappedPost?.let { NotificationTarget.Post(it.id) }
            ?: actors.firstOrNull()?.takeIf { rawType in PROFILE_TARGET_TYPES }?.let { NotificationTarget.Profile(it.id) }
        val notificationId = json.optString("most_recent_notification_id").ifBlank {
            json.optString("latest_page_notification_id")
        }.takeIf(String::isNotBlank) ?: throw IllegalArgumentException("Grouped notification did not contain an event identity")
        val group = NotificationGroup(
            id = NotificationGroupId(receivingAccountId, groupKey),
            actorPreviews = actors,
            totalCount = json.optInt("notifications_count").takeIf { it > 0 },
        )
        return Notification(
            id = EntityId(origin, notificationId),
            accountId = receivingAccountId,
            createdAtEpochMillis = parseInstant(json.optString("created_at")).takeIf { it > 0 }
                ?: mappedPost?.publishedAtEpochMillis ?: fallback?.createdAtEpochMillis ?: 0L,
            activity = fallback?.activity ?: rawType.toNotificationActivity(),
            actors = actors,
            target = target,
            destination = target?.let(NotificationDestination::InApp),
            post = mappedPost ?: fallback?.post,
            rawType = rawType,
            group = group,
        )
    }

    private fun parseInstant(value: String): Long = runCatching { Instant.parse(value).toEpochMilli() }.getOrDefault(0)
    private val PROFILE_TARGET_TYPES = setOf("follow", "follow_request")
}

private fun String.toNotificationActivity(isReplyToReceivingAccount: Boolean = false): NotificationActivity = when (this) {
    "mention" -> if (isReplyToReceivingAccount) NotificationActivity.Reply else NotificationActivity.Mention
    "reply" -> NotificationActivity.Reply
    "reblog", "boost" -> NotificationActivity.Reshare
    "quote" -> NotificationActivity.Quote
    "favourite", "favorite" -> NotificationActivity.Favourite
    "follow" -> NotificationActivity.Follow
    "follow_request" -> NotificationActivity.FollowRequest
    "status" -> NotificationActivity.SubscribedPost
    "poll" -> NotificationActivity.PollResult()
    "update" -> NotificationActivity.PostUpdate
    "quoted_update" -> NotificationActivity.QuotedPostUpdate
    "admin.sign_up", "admin.report", "moderated", "moderation_warning", "moderation" ->
        NotificationActivity.System.Moderation("Moderation event")
    "severed_relationships" -> NotificationActivity.System.RelationshipChange("Relationship changed")
    "annual_report", "added_to_collection", "collection_update" ->
        NotificationActivity.System.AppEvent("Account event")
    else -> NotificationActivity.Unknown("New activity")
}

