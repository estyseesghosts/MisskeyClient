package me.foxtails.palustris.data.misskey

import java.time.Instant
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.EntityId
import me.foxtails.palustris.domain.Notification
import me.foxtails.palustris.domain.NotificationActivity
import me.foxtails.palustris.domain.NotificationDestination
import me.foxtails.palustris.domain.NotificationGroup
import me.foxtails.palustris.domain.NotificationGroupId
import me.foxtails.palustris.domain.NotificationReaction
import me.foxtails.palustris.domain.NotificationTarget
import org.json.JSONObject

object MisskeyNotificationMapper {
    fun notification(json: JSONObject, origin: String, receivingAccountId: AccountId): Notification {
        val rawType = json.optString("type").ifBlank { "unknown" }
        val actor = runCatching { json.optJSONObject("user")?.let { MisskeyMapper.account(it, origin) } }.getOrNull()
        val groupedActors = when (rawType) {
            "reaction:grouped" -> json.optJSONArray("reactions")?.let { values ->
                (0 until values.length()).mapNotNull { index ->
                    runCatching {
                        values.optJSONObject(index)?.optJSONObject("user")?.let { MisskeyMapper.account(it, origin) }
                    }.getOrNull()
                }
            }.orEmpty()
            "renote:grouped" -> json.optJSONArray("users")?.let { values ->
                (0 until values.length()).mapNotNull { index ->
                    runCatching { values.optJSONObject(index)?.let { MisskeyMapper.account(it, origin) } }.getOrNull()
                }
            }.orEmpty()
            else -> emptyList()
        }
        val actors = groupedActors.ifEmpty { listOfNotNull(actor) }
        val mappedPost = runCatching { json.optJSONObject("note")?.let { MisskeyMapper.post(it, origin) } }.getOrNull()
        val noteId = json.nullableString("noteId")
        val canonicalPostId = if (rawType == "renote") {
            json.nullableString("targetNoteId") ?: noteId
        } else {
            noteId
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
            NotificationGroup(
                id = NotificationGroupId(receivingAccountId, "$rawType:${canonicalPostId ?: mappedPost?.id?.value ?: "unknown"}"),
                actorPreviews = actors,
                totalCount = actors.size.takeIf { it > 0 },
            )
        } else {
            null
        }
        return Notification(
            id = EntityId(origin, json.getString("id")),
            accountId = receivingAccountId,
            createdAtEpochMillis = json.notificationTimeMillis(),
            activity = rawType.toNotificationActivity(json),
            actors = actors,
            target = target,
            destination = target?.let(NotificationDestination::InApp),
            post = mappedPost,
            rawType = rawType,
            group = group,
        )
    }

    fun chatMessage(json: JSONObject, origin: String, receivingAccountId: AccountId): Notification? {
        val id = json.nullableString("id") ?: return null
        val actorJson = json.optJSONObject("user") ?: json.optJSONObject("fromUser")
        val actor = runCatching { actorJson?.let { MisskeyMapper.account(it, origin) } }.getOrNull()
        val conversationId = json.nullableString("roomId")
            ?: json.nullableString("chatId")
            ?: json.optJSONObject("room")?.nullableString("id")
        return Notification(
            id = EntityId(origin, id),
            accountId = receivingAccountId,
            createdAtEpochMillis = json.notificationTimeMillis(),
            activity = NotificationActivity.DirectMessage,
            actors = listOfNotNull(actor),
            target = conversationId?.let { NotificationTarget.Conversation(EntityId(origin, it)) },
            destination = conversationId?.let { NotificationDestination.InApp(NotificationTarget.Conversation(EntityId(origin, it))) },
            rawType = "newChatMessage",
        )
    }

    private val PROFILE_TARGET_TYPES = setOf("follow", "receiveFollowRequest", "followRequest")
}

private fun JSONObject.notificationTimeMillis(): Long {
    val createdAt = opt("createdAt")
    return when (createdAt) {
        is Number -> createdAt.toLong()
        is String -> createdAt.toLongOrNull() ?: runCatching { Instant.parse(createdAt).toEpochMilli() }.getOrDefault(0L)
        else -> when (val dateTime = opt("dateTime")) {
            is Number -> dateTime.toLong()
            is String -> dateTime.toLongOrNull() ?: 0L
            else -> 0L
        }
    }
}

private fun String.toNotificationActivity(json: JSONObject): NotificationActivity = when (this) {
    "note" -> NotificationActivity.SubscribedPost
    "mention" -> NotificationActivity.Mention
    "reply" -> NotificationActivity.Reply
    "renote", "renote:grouped" -> NotificationActivity.Reshare
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
        NotificationActivity.System.RoleOrAchievement(json.nullableString("achievement") ?: "Account achievement")
    "moderation", "moderationWarning" -> NotificationActivity.System.Moderation("Moderation event")
    "relationship" -> NotificationActivity.System.RelationshipChange("Relationship changed")
    "chatRoomInvitationReceived" -> NotificationActivity.Unknown("Chat invitation is not available")
    "exportCompleted" -> NotificationActivity.System.AppEvent("Export completed")
    "login" -> NotificationActivity.System.AppEvent("New sign-in")
    "createToken" -> NotificationActivity.System.AppEvent("Access token created")
    "test" -> NotificationActivity.System.AppEvent("Test notification")
    else -> NotificationActivity.Unknown("New activity")
}
