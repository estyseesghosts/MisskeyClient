package me.foxtails.palustris.data.misskey

import java.time.Instant
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.Attachment
import me.foxtails.palustris.domain.Audience
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.CustomEmoji
import me.foxtails.palustris.domain.EntityId
import me.foxtails.palustris.domain.PollOption
import me.foxtails.palustris.domain.Post
import me.foxtails.palustris.domain.PostAction
import me.foxtails.palustris.domain.ProfileField
import me.foxtails.palustris.domain.ProfileRelationship
import me.foxtails.palustris.domain.Protocol
import me.foxtails.palustris.domain.Reaction
import me.foxtails.palustris.domain.MediaKind
import me.foxtails.palustris.domain.mediaKindForMimeType
import org.json.JSONArray
import org.json.JSONObject

object MisskeyMapper {
    fun account(json: JSONObject, origin: String, movedTo: Account? = null): Account {
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
            movedTo = movedTo,
            emoji = MisskeyEmojiMapper.parseEmojis(json.optJSONObject("emojis") ?: JSONObject(), origin),
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
        val emojiMetadata = MisskeyEmojiMapper.parseEmojis(
            json.optJSONObject("reactionEmojis") ?: JSONObject(), origin,
        ) + MisskeyEmojiMapper.parseEmojis(json.optJSONObject("emojis") ?: JSONObject(), origin)
        val myReaction = json.nullableString("myReaction")
        val poll = json.optJSONObject("poll")?.optJSONArray("choices")
        val replyToAuthorId = json.nullableString("replyUserId")
            ?: json.optJSONObject("reply")?.nullableString("userId")
            ?: json.optJSONObject("reply")?.optJSONObject("user")?.nullableString("id")
        val myChoice = myReaction?.let { identity ->
            me.foxtails.palustris.domain.EmojiChoice(identity, identity, reactionMetadata(identity, emojiMetadata))
        }
        return Post(
            id = id,
            author = account(json.getJSONObject("user"), origin),
            text = if (json.optBoolean("isHidden")) "This post is not available to your account." else json.nullableString("text").orEmpty(),
            publishedAtEpochMillis = runCatching { Instant.parse(json.getString("createdAt")).toEpochMilli() }.getOrDefault(0),
            audience = when (json.optString("visibility")) { "home" -> Audience.Unlisted; "followers" -> Audience.Followers; "specified" -> Audience.Direct; else -> Audience.Public },
            attachments = (0 until files.length()).mapNotNull { i -> attachment(files.optJSONObject(i)) },
            contentWarning = if (json.isNull("cw")) null else json.optString("cw"),
            replyTo = json.nullableString("replyId")?.let { EntityId(origin, it) },
            replyToAuthorId = replyToAuthorId?.let { AccountId(Connection(origin, Protocol.MISSKEY), it) },
            reactions = reactionJson.keys().asSequence().map { emoji ->
                Reaction(
                    emoji,
                    reactionJson.optInt(emoji).coerceAtLeast(0),
                    myReaction == emoji,
                    reactionMetadata(emoji, emojiMetadata),
                )
            }.toList(),
            url = json.nullableString("url") ?: json.nullableString("uri") ?: "$origin/notes/${id.value}",
            replyCount = json.optInt("repliesCount"), reshareCount = json.optInt("renoteCount"),
            quote = if (renote != null && depth < MAX_NESTING_DEPTH) post(renote, origin, depth + 1) else null,
            pollOptions = if (poll == null) emptyList() else (0 until poll.length()).map { poll.getJSONObject(it).let { option ->
                PollOption(option.getString("text"), option.optInt("votes"))
            } },
            availableActions = MISSKEY_ACTIONS,
            myReaction = myReaction,
            selectedReactions = listOfNotNull(myChoice),
            emoji = MisskeyEmojiMapper.parseEmojis(json.optJSONObject("emojis") ?: JSONObject(), origin),
            saved = json.optBoolean("isFavorited", json.optBoolean("isBookmarked")),
            reposted = json.optString("myRenoteId").takeIf { it.isNotBlank() } != null,
            ownRepostId = json.optString("myRenoteId").takeIf { it.isNotBlank() }
                ?.let { EntityId(origin, it) },
        )
    }

    private fun reactionMetadata(identity: String, emoji: Map<String, CustomEmoji>): CustomEmoji? =
        emoji[identity] ?: emoji[identity.trim(':')] ?: identity.takeIf { it.startsWith(":") }?.let {
            CustomEmoji(
                shortcode = it.trim(':'),
                animatedUrl = null,
                staticUrl = null,
                submissionValue = it,
                visibleInPicker = false,
            )
        }

    private fun attachment(json: JSONObject?): Attachment? {
        if (json == null) return null
        val mimeType = json.nullableString("type") ?: "application/octet-stream"
        val properties = json.optJSONObject("properties")
        val kind = when {
            mimeType.equals("image/gif", ignoreCase = true) || mimeType.equals("image/apng", ignoreCase = true) -> MediaKind.AnimatedImage
            else -> mediaKindForMimeType(mimeType)
        }
        return Attachment(
            id = json.nullableString("id"),
            url = json.nullableString("url"),
            mimeType = mimeType,
            kind = kind,
            description = json.nullableString("comment"),
            previewUrl = json.nullableString("thumbnailUrl"),
            sensitive = json.optBoolean("isSensitive"),
            width = properties?.positiveInt("width"),
            height = properties?.positiveInt("height"),
            blurhash = json.nullableString("blurhash"),
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

private fun JSONObject.positiveInt(key: String): Int? = when (val value = opt(key)) {
    is Number -> value.toInt().takeIf { it > 0 }
    is String -> value.toIntOrNull()?.takeIf { it > 0 }
    else -> null
}
