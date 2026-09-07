package me.foxtails.palustris.data.mastodon

import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.Attachment
import me.foxtails.palustris.domain.Audience
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.EntityId
import me.foxtails.palustris.domain.Notification
import me.foxtails.palustris.domain.NotificationActivity
import me.foxtails.palustris.domain.NotificationDestination
import me.foxtails.palustris.domain.NotificationTarget
import me.foxtails.palustris.domain.PollOption
import me.foxtails.palustris.domain.Post
import me.foxtails.palustris.domain.PostAction
import me.foxtails.palustris.domain.ProfileField
import me.foxtails.palustris.domain.Protocol
import org.json.JSONObject
import java.time.Instant

object MastodonMapper {
    fun account(json: JSONObject, origin: String): Account {
        val username = json.optString("username")
        val host = json.optString("acct").substringAfter('@', "").ifBlank {
            java.net.URI(origin).host.orEmpty()
        }
        val fields = json.optJSONArray("fields")?.let { values ->
            (0 until values.length()).mapNotNull { index ->
                values.optJSONObject(index)?.let { field ->
                    ProfileField(field.optString("name"), field.optString("value").htmlToText())
                }?.takeIf { it.name.isNotBlank() || it.value.isNotBlank() }
            }.take(4)
        }.orEmpty()
        return Account(
            id = AccountId(Connection(origin, Protocol.MASTODON), json.getString("id")),
            displayName = json.optString("display_name").ifBlank { username },
            handle = "@$username@$host",
            avatarUrl = json.optString("avatar").takeIf { it.isNotBlank() },
            biography = json.optString("note").stripHtml(),
            profileFields = fields,
        )
    }

    fun post(json: JSONObject, origin: String, depth: Int = 0): Post {
        val id = EntityId(origin, json.getString("id"))
        val reblog = json.optJSONObject("reblog")
        if (reblog != null && depth < MAX_NESTING_DEPTH) {
            val resharedPost = post(reblog, origin, depth + 1)
            return resharedPost.copy(
                id = id,
                resharedBy = account(json.getJSONObject("account"), origin),
                url = json.optString("url").takeIf { it.isNotBlank() } ?: resharedPost.url,
            )
        }

        val poll = json.optJSONObject("poll")
        val quotedStatus = quotedStatus(json)
        val statusSensitive = json.optBoolean("sensitive")
        return Post(
            id = id,
            author = account(json.getJSONObject("account"), origin),
            text = json.optString("content").htmlToMarkdown(),
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
            contentWarning = json.optString("spoiler_text").takeIf { it.isNotBlank() },
            replyTo = json.optString("in_reply_to_id").takeIf { it.isNotBlank() }?.let { EntityId(origin, it) },
            availableActions = MASTODON_ACTIONS,
            url = json.optString("url").takeIf { it.isNotBlank() }
                ?: json.optString("uri").takeIf { it.isNotBlank() },
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
        )
    }

    fun notification(json: JSONObject, origin: String, receivingAccountId: AccountId): Notification {
        val rawType = json.optString("type").ifBlank { "unknown" }
        val actor = runCatching { json.optJSONObject("account")?.let { account(it, origin) } }.getOrNull()
        val mappedPost = runCatching { json.optJSONObject("status")?.let { post(it, origin) } }.getOrNull()
        val target = when {
            mappedPost != null -> NotificationTarget.Post(mappedPost.id)
            actor != null && rawType in PROFILE_TARGET_TYPES -> NotificationTarget.Profile(actor.id)
            else -> null
        }
        return Notification(
            id = EntityId(origin, json.getString("id")),
            accountId = receivingAccountId,
            createdAtEpochMillis = parseInstant(json.optString("created_at")),
            activity = rawType.toNotificationActivity(),
            actors = listOfNotNull(actor),
            target = target,
            destination = target?.let(NotificationDestination::InApp),
            post = mappedPost,
            rawType = rawType,
        )
    }

    fun attachment(json: JSONObject, statusSensitive: Boolean = false): Attachment = Attachment(
        url = json.optString("url").takeIf { it.isNotBlank() }
            ?: json.optString("preview_url"),
        mimeType = json.optJSONObject("meta")?.optJSONObject("original")?.optString("mime_type")
            ?.takeIf { it.isNotBlank() } ?: json.optString("type").toMastodonMimeType(),
        description = json.optString("description").takeIf { it.isNotBlank() },
        previewUrl = json.optString("preview_url").takeIf { it.isNotBlank() },
        sensitive = statusSensitive || json.optBoolean("sensitive"),
    )

    private fun quotedStatus(json: JSONObject): JSONObject? {
        json.optJSONObject("quote")?.let { quote ->
            return if (quote.optString("state") == "accepted") quote.optJSONObject("quoted_status") else null
        }
        return json.optJSONObject("quoted_status")
    }

    private fun parseInstant(value: String): Long = runCatching { Instant.parse(value).toEpochMilli() }.getOrDefault(0)

    private const val MAX_NESTING_DEPTH = 3
    private val PROFILE_TARGET_TYPES = setOf("follow", "follow_request")
    private val MASTODON_ACTIONS = setOf(PostAction.Reply, PostAction.Reshare, PostAction.Favorite, PostAction.Bookmark)
}

private fun String.toNotificationActivity(): NotificationActivity = when (this) {
    "mention" -> NotificationActivity.Mention
    "reply" -> NotificationActivity.Reply
    "reblog" -> NotificationActivity.Reshare
    "quote" -> NotificationActivity.Quote
    "favourite" -> NotificationActivity.Favourite
    "follow" -> NotificationActivity.Follow
    "follow_request" -> NotificationActivity.FollowRequest
    "status" -> NotificationActivity.SubscribedPost
    "poll" -> NotificationActivity.PollResult()
    "update", "quoted_update" -> NotificationActivity.PostUpdate
    "admin.sign_up", "admin.report" -> NotificationActivity.System.Moderation("Account event")
    "severed_relationships" -> NotificationActivity.System.RelationshipChange("Relationship changed")
    else -> NotificationActivity.Unknown("New activity")
}

private fun String.toMastodonMimeType(): String = when (this) {
    "image" -> "image/*"
    "video" -> "video/*"
    "audio" -> "audio/*"
    else -> if (isBlank()) "application/octet-stream" else this
}

private fun String.stripHtml(): String = htmlToText()

private val htmlAnchor = Regex("<a\\b[^>]*\\bhref\\s*=\\s*[\\\"']([^\\\"']+)[\\\"'][^>]*>(.*?)</a>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))

private fun String.htmlToMarkdown(): String {
    val linked = replace(htmlAnchor) { match ->
        val label = match.groupValues[2].htmlToText()
        if (label.trimStart().startsWith("@")) label
        else "[${label}](${match.groupValues[1]})"
    }
    return linked.htmlToText()
}

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
