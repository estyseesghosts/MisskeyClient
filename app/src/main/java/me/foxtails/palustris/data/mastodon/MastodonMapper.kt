package me.foxtails.palustris.data.mastodon

import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.Attachment
import me.foxtails.palustris.domain.Audience
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.CustomEmoji
import me.foxtails.palustris.domain.EditableProfile
import me.foxtails.palustris.domain.EditableProfileField
import me.foxtails.palustris.domain.EntityId
import me.foxtails.palustris.domain.Notification
import me.foxtails.palustris.domain.NotificationActivity
import me.foxtails.palustris.domain.NotificationDestination
import me.foxtails.palustris.domain.NotificationGroup
import me.foxtails.palustris.domain.NotificationGroupId
import me.foxtails.palustris.domain.NotificationTarget
import me.foxtails.palustris.domain.PollOption
import me.foxtails.palustris.domain.Post
import me.foxtails.palustris.domain.PostAction
import me.foxtails.palustris.domain.MediaKind
import me.foxtails.palustris.domain.ProfileField
import me.foxtails.palustris.domain.ProfileRelationship
import me.foxtails.palustris.domain.Protocol
import org.json.JSONObject
import java.time.Instant

object MastodonMapper {
    fun account(json: JSONObject, origin: String, includeMovedTo: Boolean = true): Account {
        val username = json.optString("username")
        val host = json.optString("acct").substringAfter('@', "").ifBlank {
            java.net.URI(origin).host.orEmpty()
        }
        val emoji = MastodonEmojiMapper.parseEmojis(json.optJSONArray("emojis"), origin)
        val fields = json.optJSONArray("fields")?.let { values ->
            (0 until values.length()).mapNotNull { index ->
                values.optJSONObject(index)?.let { field ->
                    ProfileField(field.optString("name"), field.optString("value").htmlToText(emoji))
                }?.takeIf { it.name.isNotBlank() || it.value.isNotBlank() }
            }.take(4)
        }.orEmpty()
        val account = Account(
            id = AccountId(Connection(origin, Protocol.MASTODON), json.getString("id")),
            displayName = json.optString("display_name").ifBlank { username },
            handle = "@$username@$host",
            avatarUrl = json.nullableString("avatar"),
            biography = json.optString("note").stripHtml(emoji),
            profileFields = fields,
            bannerUrl = json.nullableString("header"),
            followersCount = json.optionalNonNegativeLong("followers_count"),
            followingCount = json.optionalNonNegativeLong("following_count"),
            postsCount = json.optionalNonNegativeLong("statuses_count"),
            locked = json.optBoolean("locked"),
            bot = json.optBoolean("bot"),
            emoji = emoji,
        )
        if (!includeMovedTo) return account
        val destination = json.optJSONObject("moved")?.let { moved ->
            runCatching { account(moved, origin, includeMovedTo = false) }.getOrNull()
        }?.takeIf { it.hasUsableProfileIdentity() }
        return account.copy(movedTo = destination)
    }

    /** API 8 self-profile responses carry raw values; no HTML or Markdown conversion. */
    fun editableProfile(json: JSONObject, origin: String): EditableProfile = EditableProfile(
        id = json.optString("id"),
        displayName = json.optString("display_name"),
        biography = json.optString("note"),
        fields = json.optJSONArray("fields")?.let { values ->
            (0 until values.length()).mapNotNull { index ->
                values.optJSONObject(index)?.let { field ->
                    EditableProfileField(field.optString("name"), field.optString("value"))
                }
            }
        }.orEmpty(),
        avatarUrl = json.nullableString("avatar"),
        avatarDescription = json.nullableString("avatar_description"),
        headerUrl = json.nullableString("header"),
        headerDescription = json.nullableString("header_description"),
        locked = json.optBoolean("locked"),
        bot = json.optBoolean("bot"),
        hideCollections = json.optBoolean("hide_collections"),
        discoverable = json.optBoolean("discoverable"),
        indexable = json.optBoolean("indexable"),
        showMedia = json.optBoolean("show_media"),
        showMediaReplies = json.optBoolean("show_media_replies"),
        showFeatured = json.optBoolean("show_featured"),
        attributionDomains = json.optJSONArray("attribution_domains")?.let { values ->
            (0 until values.length()).mapNotNull { values.optString(it).takeIf(String::isNotBlank) }
        }.orEmpty(),
    )

    /** Legacy verify-credentials responses prefer plaintext source values over rendered ones. */
    fun legacyEditableProfile(json: JSONObject, origin: String): EditableProfile {
        val source = json.optJSONObject("source")
        val renderedFields = json.optJSONArray("fields")?.let { values ->
            (0 until values.length()).mapNotNull { index ->
                values.optJSONObject(index)?.let { field ->
                    EditableProfileField(field.optString("name"), field.optString("value"))
                }
            }
        }.orEmpty()
        val sourceFields = source?.optJSONArray("fields")?.let { values ->
            (0 until values.length()).mapNotNull { index ->
                values.optJSONObject(index)?.let { field ->
                    EditableProfileField(field.optString("name"), field.optString("value"))
                }
            }
        }
        return EditableProfile(
            id = json.optString("id"),
            displayName = source?.optString("display_name")?.ifBlank { null }
                ?: json.optString("display_name"),
            biography = source?.optString("note")?.ifBlank { null } ?: json.optString("note"),
            fields = sourceFields ?: renderedFields,
            avatarUrl = json.nullableString("avatar"),
            avatarDescription = json.nullableString("avatar_description"),
            headerUrl = json.nullableString("header"),
            headerDescription = json.nullableString("header_description"),
            locked = json.optBoolean("locked"),
            bot = json.optBoolean("bot"),
        )
    }

    fun relationship(json: JSONObject, profileId: AccountId): ProfileRelationship = ProfileRelationship(
        profileId = profileId,
        following = json.optBoolean("following"),
        followedBy = json.optBoolean("followed_by"),
        requested = json.optBoolean("requested"),
        muting = json.optBoolean("muting"),
        blocking = json.optBoolean("blocking"),
    )

    fun post(json: JSONObject, origin: String, depth: Int = 0): Post {
        val id = EntityId(origin, json.getString("id"))
        val reblog = json.optJSONObject("reblog")
        if (reblog != null && depth < MAX_NESTING_DEPTH) {
            val resharedPost = post(reblog, origin, depth + 1)
            return resharedPost.copy(
                id = id,
                resharedBy = account(json.getJSONObject("account"), origin),
                url = json.nullableString("url") ?: resharedPost.url,
                reposted = json.optBoolean("reblogged", resharedPost.reposted),
                favourited = json.optBoolean("favourited", resharedPost.favourited),
                saved = json.optBoolean("bookmarked", resharedPost.saved),
                actionTargetId = resharedPost.actionTargetId ?: resharedPost.id,
            )
        }

        val poll = json.optJSONObject("poll")
        val quotedStatus = quotedStatus(json)
        val statusSensitive = json.optBoolean("sensitive")
        val emoji = MastodonEmojiMapper.parseEmojis(json.optJSONArray("emojis"), origin)
        val extensionReactions = MastodonReactionExtensionMapper.reactions(json, emoji)
        val selectedReactions = MastodonReactionExtensionMapper.selectedChoices(
            json, emoji, independentSelection = true,
        ).orEmpty()
        return Post(
            id = id,
            author = account(json.getJSONObject("account"), origin),
            text = json.optString("content").htmlToMarkdown(emoji),
            publishedAtEpochMillis = parseInstant(json.optString("created_at")),
            audience = when (json.optString("visibility")) {
                "unlisted" -> Audience.Unlisted
                "private" -> Audience.Followers
                "direct" -> Audience.Direct
                else -> Audience.Public
            },
            attachments = json.optJSONArray("media_attachments")?.let { media ->
                (0 until media.length()).map { attachment(media.getJSONObject(it), statusSensitive) }
            }.orEmpty(),
            contentWarning = json.nullableString("spoiler_text"),
            replyTo = json.nullableString("in_reply_to_id")?.let { EntityId(origin, it) },
            replyToAuthorId = json.nullableString("in_reply_to_account_id")
                ?.let { AccountId(Connection(origin, Protocol.MASTODON), it) },
            availableActions = MASTODON_ACTIONS,
            url = json.nullableString("url") ?: json.nullableString("uri"),
            replyCount = json.optInt("replies_count"),
            reshareCount = json.optInt("reblogs_count"),
            quote = quotedStatus?.takeIf { depth < MAX_NESTING_DEPTH }?.let { post(it, origin, depth + 1) },
            pollOptions = poll?.optJSONArray("options")?.let { options ->
                (0 until options.length()).map { option ->
                    options.getJSONObject(option).let {
                        PollOption(it.optString("title"), it.optInt("votes_count"))
                    }
                }
            }.orEmpty(),
            reposted = json.optBoolean("reblogged"),
            favourited = json.optBoolean("favourited"),
            saved = json.optBoolean("bookmarked"),
            reactions = extensionReactions.orEmpty(),
            selectedReactions = selectedReactions,
            myReaction = selectedReactions.singleOrNull()?.submissionValue,
            emoji = emoji,
        )
    }

    fun attachment(json: JSONObject, statusSensitive: Boolean = false): Attachment {
        val type = json.optString("type")
        val originalMeta = json.optJSONObject("meta")?.optJSONObject("original")
        val smallMeta = json.optJSONObject("meta")?.optJSONObject("small")
        val mimeType = originalMeta?.nullableString("mime_type") ?: type.toMastodonMimeType()
        return Attachment(
            id = json.nullableString("id"),
            url = json.nullableString("url"),
            mimeType = mimeType,
            kind = type.toMastodonMediaKind(mimeType),
            description = json.nullableString("description"),
            previewUrl = json.nullableString("preview_url"),
            sensitive = statusSensitive || json.optBoolean("sensitive"),
            width = originalMeta?.positiveInt("width"),
            height = originalMeta?.positiveInt("height"),
            previewWidth = smallMeta?.positiveInt("width"),
            previewHeight = smallMeta?.positiveInt("height"),
            blurhash = json.nullableString("blurhash"),
            remoteOriginalUrl = json.nullableString("remote_url"),
        )
    }

    private fun quotedStatus(json: JSONObject): JSONObject? {
        json.optJSONObject("quote")?.let { quote ->
            return if (quote.optString("state") == "accepted") quote.optJSONObject("quoted_status") else null
        }
        return json.optJSONObject("quoted_status")
    }

    private fun parseInstant(value: String): Long =
        runCatching { Instant.parse(value).toEpochMilli() }.getOrDefault(0L)

    private const val MAX_NESTING_DEPTH = 3
    private val MASTODON_ACTIONS = setOf(PostAction.Reply, PostAction.Reshare, PostAction.Favorite, PostAction.Bookmark)
}

private fun Account.hasUsableProfileIdentity(): Boolean = id.localId.isNotBlank() &&
    (displayName.isNotBlank() || handle.removePrefix("@").substringBefore("@").isNotBlank() || avatarUrl != null)

private fun JSONObject.nullableString(key: String): String? =
    if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() }

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

private fun String.toMastodonMimeType(): String = when (this) {
    "image" -> "image/*"
    "video" -> "video/*"
    "gifv" -> "video/*"
    "audio" -> "audio/*"
    else -> if (isBlank()) "application/octet-stream" else this
}

private fun String.toMastodonMediaKind(mimeType: String): MediaKind = when (this) {
    "image" -> MediaKind.Image
    "gifv" -> MediaKind.Video
    "video" -> MediaKind.Video
    "audio" -> MediaKind.Audio
    else -> me.foxtails.palustris.domain.mediaKindForMimeType(mimeType)
}

private fun JSONObject.positiveInt(key: String): Int? = when (val value = opt(key)) {
    is Number -> value.toInt().takeIf { it > 0 }
    is String -> value.toIntOrNull()?.takeIf { it > 0 }
    else -> null
}

private fun String.stripHtml(emoji: Map<String, CustomEmoji> = emptyMap()): String = htmlToText(emoji)

private val htmlAnchor = Regex("<a\\b[^>]*\\bhref\\s*=\\s*[\\\"']([^\\\"']+)[\\\"'][^>]*>(.*?)</a>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))

private val emojiImage = Regex("<img\\b[^>]*\\balt\\s*=\\s*[\"']([^\"']+)[\"'][^>]*/?>", setOf(RegexOption.IGNORE_CASE))

/** Replaces known emoji images with their `:shortcode:` alternate token before tag removal. */
private fun String.preserveEmojiAlts(emoji: Map<String, CustomEmoji>): String {
    if (emoji.isEmpty()) return replace(emojiImage, "")
    val known = buildSet {
        emoji.keys.forEach { key ->
            add(key)
            add(":$key:")
        }
    }
    return replace(emojiImage) { match ->
        val alt = match.groupValues[1].trim()
        if (alt in known) alt else ""
    }
}

private fun String.htmlToMarkdown(emoji: Map<String, CustomEmoji> = emptyMap()): String {
    val linked = preserveEmojiAlts(emoji).replace(htmlAnchor) { match ->
        val label = match.groupValues[2].htmlToText()
        if (label.trimStart().startsWith("@")) label
        else "[${label}](${match.groupValues[1]})"
    }
    return linked.htmlToText()
}

private fun String.htmlToText(emoji: Map<String, CustomEmoji>): String =
    preserveEmojiAlts(emoji).htmlToText()

private fun String.htmlToText(): String = replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
    .replace(Regex("</p\\s*>", RegexOption.IGNORE_CASE), "\n")
    .replace(Regex("<[^>]*>"), "")
    .replace(Regex("&(#x[0-9a-f]+|#\\d+|amp|lt|gt|quot|apos|nbsp);", RegexOption.IGNORE_CASE)) { match ->
        when (val entity = match.value.lowercase()) {
            "&amp;" -> "&"
            "&lt;" -> "<"
            "&gt;" -> ">"
            "&quot;" -> "\""
            "&apos;" -> "'"
            "&nbsp;" -> " "
            else -> entity.removePrefix("&#").removeSuffix(";").let {
                runCatching {
                    val value = if (it.startsWith("x")) it.removePrefix("x").toInt(16) else it.toInt()
                    String(Character.toChars(value))
                }.getOrDefault(match.value)
            }
        }
    }
    .trim()
