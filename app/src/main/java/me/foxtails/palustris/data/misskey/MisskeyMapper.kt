package me.foxtails.palustris.data.misskey

import java.time.Instant
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.Attachment
import me.foxtails.palustris.domain.Audience
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.EntityId
import me.foxtails.palustris.domain.PollOption
import me.foxtails.palustris.domain.Post
import me.foxtails.palustris.domain.PostAction
import me.foxtails.palustris.domain.ProfileField
import me.foxtails.palustris.domain.ProfileRelationship
import me.foxtails.palustris.domain.Protocol
import me.foxtails.palustris.domain.Reaction
import org.json.JSONArray
import org.json.JSONObject

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
        if (pureReshare && depth < MAX_NESTING_DEPTH) {
            val displayedPost = post(renote!!, origin, depth + 1)
            return displayedPost.copy(
                id = EntityId(origin, json.getString("id")),
                resharedBy = account(json.getJSONObject("user"), origin),
                reposted = json.optString("myRenoteId").takeIf { it.isNotBlank() } != null,
                ownRepostId = json.optString("myRenoteId").takeIf { it.isNotBlank() }
                    ?.let { EntityId(origin, it) },
                actionTargetId = displayedPost.actionTargetId ?: displayedPost.id,
            )
        }
        val id = EntityId(origin, json.getString("id"))
        val reactionJson = json.optJSONObject("reactions") ?: JSONObject()
        val reactionImages = json.optJSONObject("reactionEmojis") ?: JSONObject()
        val myReaction = json.nullableString("myReaction")
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
                myReaction == emoji, reactionImages.nullableString(emoji.trim(':'))) }.toList(),
            url = json.nullableString("url") ?: json.nullableString("uri") ?: "$origin/notes/${id.value}",
            replyCount = json.optInt("repliesCount"), reshareCount = json.optInt("renoteCount"),
            quote = if (renote != null && depth < MAX_NESTING_DEPTH) post(renote, origin, depth + 1) else null,
            pollOptions = if (poll == null) emptyList() else (0 until poll.length()).map { poll.getJSONObject(it).let { option ->
                PollOption(option.getString("text"), option.optInt("votes"))
            } },
            availableActions = MISSKEY_ACTIONS,
            myReaction = myReaction,
            saved = json.optBoolean("isFavorited", json.optBoolean("isBookmarked")),
            reposted = json.optString("myRenoteId").takeIf { it.isNotBlank() } != null,
            ownRepostId = json.optString("myRenoteId").takeIf { it.isNotBlank() }
                ?.let { EntityId(origin, it) },
        )
    }

    private const val MAX_NESTING_DEPTH = 3
    private val MISSKEY_ACTIONS = setOf(PostAction.Reply, PostAction.Reshare, PostAction.Favorite, PostAction.React, PostAction.Bookmark)
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
