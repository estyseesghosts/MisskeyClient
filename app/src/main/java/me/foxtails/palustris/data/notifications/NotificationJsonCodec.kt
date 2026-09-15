package me.foxtails.palustris.data.notifications

import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.EntityId
import me.foxtails.palustris.domain.Notification
import me.foxtails.palustris.domain.NotificationActivity
import me.foxtails.palustris.domain.NotificationCheckpoint
import me.foxtails.palustris.domain.NotificationDeliveryRecord
import me.foxtails.palustris.domain.NotificationDeliveryState
import me.foxtails.palustris.domain.NotificationDestination
import me.foxtails.palustris.domain.NotificationGroup
import me.foxtails.palustris.domain.NotificationGroupId
import me.foxtails.palustris.domain.NotificationPushRegistrationState
import me.foxtails.palustris.domain.NotificationReaction
import me.foxtails.palustris.domain.NotificationReadState
import me.foxtails.palustris.domain.NotificationReadStatus
import me.foxtails.palustris.domain.NotificationSettings
import me.foxtails.palustris.domain.NotificationSyncCompleteness
import me.foxtails.palustris.domain.NotificationTarget
import me.foxtails.palustris.domain.NotificationUnreadState
import me.foxtails.palustris.domain.ProfileField
import me.foxtails.palustris.domain.Protocol
import me.foxtails.palustris.domain.PushRegistration
import me.foxtails.palustris.domain.PushRegistrationFailureReason
import me.foxtails.palustris.domain.PushRegistrationFailureStage
import me.foxtails.palustris.domain.ValidatedUrl
import org.json.JSONArray
import org.json.JSONObject

internal fun encode(state: NotificationRepositoryState): JSONObject = JSONObject().apply {
    put("version", 2)
    put("items", JSONArray(state.items.map(::encodeNotification)))
    put("unread", encodeUnread(state.unreadState))
    state.checkpoint?.let { checkpoint -> put("checkpoint", encodeCheckpoint(checkpoint)) }
    put("lastSyncedAt", state.lastSyncedAtEpochMillis)
    put("dismissedIds", JSONArray(state.dismissedIds.map(::encodeEntity)))
    put("checkpoints", JSONObject().apply {
        state.checkpoints.forEach { (key, checkpoint) -> put(key, encodeCheckpoint(checkpoint)) }
    })
    put("deliveries", JSONArray(state.deliveries.values.map(::encodeDelivery)))
    put("settings", encodeSettings(state.settings))
    state.pushRegistration?.let { put("pushRegistration", encodePushRegistration(it)) }
}

internal fun decode(json: JSONObject): NotificationRepositoryState {
    val items = json.optJSONArray("items")?.let { values ->
        (0 until values.length()).mapNotNull { index -> runCatching { decodeNotification(values.getJSONObject(index)) }.getOrNull() }
    }.orEmpty()
    return NotificationRepositoryState(
        items = items,
        unreadState = decodeUnread(json.optJSONObject("unread")),
        checkpoint = json.optJSONObject("checkpoint")?.let(::decodeCheckpoint),
        lastSyncedAtEpochMillis = json.optLong("lastSyncedAt", 0),
        dismissedIds = json.optJSONArray("dismissedIds")?.let { values ->
            (0 until values.length()).mapNotNull { index -> runCatching { decodeEntity(values.getJSONObject(index)) }.getOrNull() }
        }?.toSet().orEmpty(),
        checkpoints = json.optJSONObject("checkpoints")?.let { values ->
            values.keys().asSequence().mapNotNull { key -> runCatching { key to decodeCheckpoint(values.getJSONObject(key)) }.getOrNull() }
                .toMap()
        }.orEmpty(),
        deliveries = json.optJSONArray("deliveries")?.let { values ->
            (0 until values.length()).mapNotNull { index -> runCatching { decodeDelivery(values.getJSONObject(index)) }.getOrNull() }
        }?.associateBy { it.notificationId }.orEmpty(),
        settings = decodeSettings(json.optJSONObject("settings")),
        pushRegistration = json.optJSONObject("pushRegistration")?.let(::decodePushRegistration),
    )
}

private fun encodePushRegistration(registration: PushRegistration): JSONObject = JSONObject().apply {
    put("accountId", encodeAccountId(registration.accountId))
    put("generation", registration.generation)
    put("sessionRevision", registration.sessionRevision)
    put("instanceName", registration.instanceName)
    registration.distributorPackage?.let { put("distributorPackage", it) }
    registration.endpoint?.let { put("endpoint", it.value) }
    registration.serverEndpoint?.let { put("serverEndpoint", it.value) }
    registration.serverRemoteId?.let { put("serverRemoteId", it) }
    put("confirmedEndpointGeneration", registration.confirmedEndpointGeneration)
    put("state", registration.state.name)
    put("endpointGeneration", registration.endpointGeneration)
    put("retryCount", registration.retryCount)
    registration.lastErrorCategory?.let { put("lastErrorCategory", it) }
    registration.lastErrorDetail?.let { put("lastErrorDetail", it) }
    registration.failureStage?.let { put("failureStage", it.name) }
    registration.failureReason?.let { put("failureReason", it.name) }
    put("nextRetryAt", registration.nextRetryAtEpochMillis)
}

private fun decodePushRegistration(json: JSONObject): PushRegistration {
    val endpoint = json.optString("endpoint").takeIf { it.isNotBlank() }?.let { ValidatedUrl.https(it) }
    val state = runCatching { NotificationPushRegistrationState.valueOf(json.optString("state")) }
        .getOrDefault(NotificationPushRegistrationState.Off)
    val serverEndpoint = json.optString("serverEndpoint").takeIf { it.isNotBlank() }
        ?.let { ValidatedUrl.https(it) }
        // Older stores only had one endpoint field. Connected records were written after a
        // successful server response, so they can safely seed the confirmed field on upgrade.
        ?: endpoint.takeIf { state == NotificationPushRegistrationState.Connected }
    return PushRegistration(
        accountId = decodeAccountId(json.getJSONObject("accountId")),
        generation = json.optLong("generation"),
        sessionRevision = json.optLong("sessionRevision", 1L),
        instanceName = json.getString("instanceName"),
        distributorPackage = json.optString("distributorPackage").takeIf { it.isNotBlank() },
        endpoint = endpoint,
        serverEndpoint = serverEndpoint,
        serverRemoteId = json.optString("serverRemoteId").takeIf { it.isNotBlank() },
        confirmedEndpointGeneration = json.optLong("confirmedEndpointGeneration", if (state == NotificationPushRegistrationState.Connected) {
            json.optLong("endpointGeneration")
        } else {
            0L
        }),
        state = state,
        endpointGeneration = json.optLong("endpointGeneration"),
        retryCount = json.optInt("retryCount"),
        lastErrorCategory = json.optString("lastErrorCategory").takeIf { it.isNotBlank() },
        lastErrorDetail = json.optString("lastErrorDetail").takeIf { it.isNotBlank() },
        failureStage = json.optString("failureStage").takeIf { it.isNotBlank() }?.let {
            runCatching { PushRegistrationFailureStage.valueOf(it) }.getOrNull()
        },
        failureReason = json.optString("failureReason").takeIf { it.isNotBlank() }?.let {
            runCatching { PushRegistrationFailureReason.valueOf(it) }.getOrNull()
        },
        nextRetryAtEpochMillis = json.optLong("nextRetryAt"),
    )
}

private fun encodeSettings(settings: NotificationSettings): JSONObject = JSONObject().apply {
    put("alertsEnabled", settings.alertsEnabled)
    put("categories", JSONArray(settings.categories.map { it.name }))
    put("showPreviews", settings.showPreviews)
    settings.quietHoursStartMinutes?.let { put("quietStart", it) }
    settings.quietHoursEndMinutes?.let { put("quietEnd", it) }
    put("periodicFallbackEnabled", settings.periodicFallbackEnabled)
    settings.selectedDistributor?.let { put("selectedDistributor", it) }
}

private fun decodeSettings(json: JSONObject?): NotificationSettings {
    if (json == null) return NotificationSettings()
    val categories = json.optJSONArray("categories")?.let { values ->
        (0 until values.length()).mapNotNull { index ->
            runCatching { me.foxtails.palustris.domain.NotificationCategory.valueOf(values.getString(index)) }.getOrNull()
        }
    }?.toSet()
        ?: setOf(me.foxtails.palustris.domain.NotificationCategory.All)
    return NotificationSettings(
        alertsEnabled = json.optBoolean("alertsEnabled"),
        categories = categories,
        showPreviews = json.optBoolean("showPreviews"),
        quietHoursStartMinutes = json.optInt("quietStart").takeIf { json.has("quietStart") },
        quietHoursEndMinutes = json.optInt("quietEnd").takeIf { json.has("quietEnd") },
        periodicFallbackEnabled = json.optBoolean("periodicFallbackEnabled"),
        selectedDistributor = json.optString("selectedDistributor").takeIf { it.isNotBlank() },
    )
}

private fun encodeDelivery(record: NotificationDeliveryRecord): JSONObject = JSONObject().apply {
    put("accountId", encodeAccountId(record.accountId))
    put("notificationId", encodeEntity(record.notificationId))
    put("state", record.state.name)
    put("androidTag", record.androidTag)
    put("androidId", record.androidId)
    put("attemptCount", record.attemptCount)
    put("lastAttemptAt", record.lastAttemptAtEpochMillis)
    record.lastErrorCategory?.let { put("lastErrorCategory", it) }
    record.claimId?.let { put("claimId", it) }
    put("claimExpiresAt", record.claimExpiresAtEpochMillis)
}

private fun decodeDelivery(json: JSONObject): NotificationDeliveryRecord = NotificationDeliveryRecord(
    accountId = decodeAccountId(json.getJSONObject("accountId")),
    notificationId = decodeEntity(json.getJSONObject("notificationId")),
    state = runCatching { NotificationDeliveryState.valueOf(json.optString("state")) }
        .getOrDefault(NotificationDeliveryState.Pending),
    androidTag = json.optString("androidTag"),
    androidId = json.optInt("androidId"),
    attemptCount = json.optInt("attemptCount"),
    lastAttemptAtEpochMillis = json.optLong("lastAttemptAt"),
    lastErrorCategory = json.optString("lastErrorCategory").takeIf { it.isNotBlank() },
    claimId = json.optString("claimId").takeIf { it.isNotBlank() },
    claimExpiresAtEpochMillis = json.optLong("claimExpiresAt"),
)

private fun encodeNotification(notification: Notification): JSONObject = JSONObject().apply {
    put("id", encodeEntity(notification.id))
    put("accountId", encodeAccountId(notification.accountId))
    put("createdAt", notification.createdAtEpochMillis)
    put("activity", encodeActivity(notification.activity))
    put("actors", JSONArray(notification.actors.map(::encodeAccount)))
    notification.target?.let { put("target", encodeTarget(it)) }
    notification.destination?.let { put("destination", encodeDestination(it)) }
    notification.post?.let { put("post", encodePost(it)) }
    put("readStatus", notification.readState.status.name)
    put("locallySeen", notification.readState.locallySeen)
    put("serverAcknowledged", notification.readState.serverAcknowledged)
    put("androidPresented", notification.readState.androidPresented)
    put("androidDismissed", notification.readState.androidDismissed)
    put("rawType", notification.rawType)
    notification.group?.let { put("group", encodeGroup(it)) }
}

private fun decodeNotification(json: JSONObject): Notification = Notification(
    id = decodeEntity(json.getJSONObject("id")),
    accountId = decodeAccountId(json.getJSONObject("accountId")),
    createdAtEpochMillis = json.optLong("createdAt"),
    activity = decodeActivity(json.optJSONObject("activity") ?: JSONObject()),
    actors = json.optJSONArray("actors")?.let { values ->
        (0 until values.length()).mapNotNull { index -> runCatching { decodeAccount(values.getJSONObject(index)) }.getOrNull() }
    }.orEmpty(),
    target = json.optJSONObject("target")?.let(::decodeTarget),
    destination = json.optJSONObject("destination")?.let(::decodeDestination)
        ?: json.optJSONObject("target")?.let { NotificationDestination.InApp(decodeTarget(it)) },
    post = json.optJSONObject("post")?.let { runCatching { decodePost(it) }.getOrNull() },
    readState = NotificationReadState(
        status = runCatching { NotificationReadStatus.valueOf(json.optString("readStatus")) }
            .getOrDefault(NotificationReadStatus.Unknown),
        locallySeen = json.optBoolean("locallySeen"),
        serverAcknowledged = json.optBoolean("serverAcknowledged"),
        androidPresented = json.optBoolean("androidPresented"),
        androidDismissed = json.optBoolean("androidDismissed"),
    ),
    rawType = json.optString("rawType", "unknown"),
    group = json.optJSONObject("group")?.let(::decodeGroup),
)

private fun encodeDestination(destination: NotificationDestination): JSONObject = JSONObject().apply {
    when (destination) {
        is NotificationDestination.InApp -> put("kind", "in_app").put("target", encodeTarget(destination.target))
        is NotificationDestination.Server -> put("kind", "server").put("url", destination.url.value)
    }
}

private fun decodeDestination(json: JSONObject): NotificationDestination? = when (json.optString("kind")) {
    "in_app" -> json.optJSONObject("target")?.let { NotificationDestination.InApp(decodeTarget(it)) }
    "server" -> json.optString("url").takeIf { it.isNotBlank() }?.let { ValidatedUrl.https(it) }
        ?.let(NotificationDestination::Server)
    else -> null
}

private fun encodeEntity(id: EntityId): JSONObject = JSONObject().put("connection", id.connection).put("value", id.value)

private fun decodeEntity(json: JSONObject): EntityId = EntityId(json.getString("connection"), json.getString("value"))

private fun encodeAccountId(id: AccountId): JSONObject = JSONObject()
    .put("origin", id.connection.origin).put("protocol", id.connection.protocol.name).put("localId", id.localId)

private fun decodeAccountId(json: JSONObject): AccountId = AccountId(
    Connection(json.getString("origin"), Protocol.valueOf(json.getString("protocol"))),
    json.getString("localId"),
)

private fun encodeAccount(account: Account): JSONObject = JSONObject()
    .put("id", encodeAccountId(account.id)).put("displayName", account.displayName)
    .put("handle", account.handle).put("avatarUrl", account.avatarUrl)
    .put("biography", account.biography)
    .put("profileFields", JSONArray(account.profileFields.map { field ->
        JSONObject().put("name", field.name).put("value", field.value)
    }))
    .put("bannerUrl", account.bannerUrl)
    .put("followersCount", account.followersCount)
    .put("followingCount", account.followingCount)
    .put("postsCount", account.postsCount)
    .put("locked", account.locked)
    .put("bot", account.bot)
    .put("emoji", encodeEmojiMap(account.emoji))
    .put("movedTo", account.movedTo?.let(::encodeAccount))

private fun decodeAccount(json: JSONObject): Account = Account(
    id = decodeAccountId(json.getJSONObject("id")),
    displayName = json.optString("displayName"),
    handle = json.optString("handle"),
    avatarUrl = json.optString("avatarUrl").takeIf { it.isNotBlank() },
    biography = json.optString("biography"),
    profileFields = json.optJSONArray("profileFields")?.let { values ->
        (0 until values.length()).mapNotNull { index ->
            values.optJSONObject(index)?.let { field ->
                ProfileField(field.optString("name"), field.optString("value"))
            }
        }
    }.orEmpty(),
    bannerUrl = json.optString("bannerUrl").takeIf { it.isNotBlank() },
    followersCount = json.optLongOrNull("followersCount"),
    followingCount = json.optLongOrNull("followingCount"),
    postsCount = json.optLongOrNull("postsCount"),
    locked = json.optBoolean("locked"),
    bot = json.optBoolean("bot"),
    emoji = decodeEmojiMap(json.optJSONObject("emoji")),
    movedTo = json.optJSONObject("movedTo")?.let { runCatching { decodeAccount(it) }.getOrNull() },
)

private fun encodeEmoji(emoji: me.foxtails.palustris.domain.CustomEmoji): JSONObject = JSONObject()
    .put("shortcode", emoji.shortcode)
    .put("animatedUrl", emoji.animatedUrl?.value)
    .put("staticUrl", emoji.staticUrl?.value)
    .put("category", emoji.category)
    .put("aliases", JSONArray(emoji.aliases))
    .put("visibleInPicker", emoji.visibleInPicker)
    .put("submissionValue", emoji.submissionValue)

private fun decodeEmoji(json: JSONObject): me.foxtails.palustris.domain.CustomEmoji? {
    val shortcode = json.optString("shortcode").takeIf { it.isNotBlank() } ?: return null
    return me.foxtails.palustris.domain.CustomEmoji(
        shortcode = shortcode,
        animatedUrl = json.optString("animatedUrl").takeIf { it.isNotBlank() }
            ?.let(me.foxtails.palustris.domain.ValidatedUrl::https),
        staticUrl = json.optString("staticUrl").takeIf { it.isNotBlank() }
            ?.let(me.foxtails.palustris.domain.ValidatedUrl::https),
        category = json.optString("category").takeIf { it.isNotBlank() },
        aliases = json.optJSONArray("aliases")?.let { values ->
            (0 until values.length()).mapNotNull { values.optString(it).takeIf(String::isNotBlank) }
        }.orEmpty(),
        visibleInPicker = json.optBoolean("visibleInPicker", true),
        submissionValue = json.optString("submissionValue").takeIf { it.isNotBlank() } ?: ":$shortcode:",
    )
}

private fun encodeEmojiMap(emoji: Map<String, me.foxtails.palustris.domain.CustomEmoji>): JSONObject =
    JSONObject().apply { emoji.forEach { (key, value) -> put(key, encodeEmoji(value)) } }

private fun decodeEmojiMap(json: JSONObject?): Map<String, me.foxtails.palustris.domain.CustomEmoji> {
    if (json == null) return emptyMap()
    val result = linkedMapOf<String, me.foxtails.palustris.domain.CustomEmoji>()
    json.keys().asSequence().forEach { key ->
        runCatching { decodeEmoji(json.getJSONObject(key)) }.getOrNull()?.let { result[key] = it }
    }
    return result
}

private fun encodePost(post: me.foxtails.palustris.domain.Post): JSONObject = JSONObject().apply {
    put("id", encodeEntity(post.id))
    put("author", encodeAccount(post.author))
    put("text", post.text)
    put("publishedAt", post.publishedAtEpochMillis)
    put("audience", post.audience.name)
    put("attachments", JSONArray(post.attachments.map(::encodeAttachment)))
    post.contentWarning?.let { put("contentWarning", it) }
    post.resharedBy?.let { put("resharedBy", encodeAccount(it)) }
    post.replyTo?.let { put("replyTo", encodeEntity(it)) }
    post.replyToAuthorId?.let { put("replyToAuthorId", encodeAccountId(it)) }
    put("reactions", JSONArray(post.reactions.map(::encodeReaction)))
    put("availableActions", JSONArray(post.availableActions.map { it.name }))
    post.url?.let { put("url", it) }
    post.interactionCounts.replyCount?.let { put("replyCount", it) }
    post.interactionCounts.repostCount?.let { put("reshareCount", it) }
    post.interactionCounts.favouriteCount?.let { put("favouriteCount", it) }
    post.interactionCounts.reactionCount?.let { put("reactionCount", it) }
    post.interactionCounts.quoteRepostCount?.let { put("quoteRepostCount", it) }
    post.quote?.let { put("quote", encodePost(it)) }
    put("pollOptions", JSONArray(post.pollOptions.map { option ->
        JSONObject().put("text", option.text).put("votes", option.votes)
    }))
    put("reposted", post.reposted)
    put("favourited", post.favourited)
    put("saved", post.saved)
    post.myReaction?.let { put("myReaction", it) }
    put("selectedReactions", JSONArray(post.selectedReactions.map(::encodeEmojiChoice)))
    put("emoji", encodeEmojiMap(post.emoji))
    post.ownRepostId?.let { put("ownRepostId", encodeEntity(it)) }
    post.actionTargetId?.let { put("actionTargetId", encodeEntity(it)) }
}

private fun decodePost(json: JSONObject): me.foxtails.palustris.domain.Post =
    me.foxtails.palustris.domain.Post(
        id = decodeEntity(json.getJSONObject("id")),
        author = decodeAccount(json.getJSONObject("author")),
        text = json.optString("text"),
        publishedAtEpochMillis = json.optLong("publishedAt"),
        audience = runCatching { me.foxtails.palustris.domain.Audience.valueOf(json.optString("audience")) }
            .getOrDefault(me.foxtails.palustris.domain.Audience.Public),
        attachments = json.optJSONArray("attachments")?.let { values ->
            (0 until values.length()).mapNotNull { index ->
                runCatching { decodeAttachment(values.getJSONObject(index)) }.getOrNull()
            }
        }.orEmpty(),
        contentWarning = json.optString("contentWarning").takeIf { it.isNotBlank() },
        resharedBy = json.optJSONObject("resharedBy")?.let { runCatching { decodeAccount(it) }.getOrNull() },
        replyTo = json.optJSONObject("replyTo")?.let(::decodeEntity),
        replyToAuthorId = json.optJSONObject("replyToAuthorId")?.let(::decodeAccountId),
        reactions = json.optJSONArray("reactions")?.let { values ->
            (0 until values.length()).mapNotNull { index ->
                runCatching { decodeReaction(values.getJSONObject(index)) }.getOrNull()
            }
        }.orEmpty(),
        availableActions = json.optJSONArray("availableActions")?.let { values ->
            (0 until values.length()).mapNotNull { index ->
                runCatching { me.foxtails.palustris.domain.PostAction.valueOf(values.getString(index)) }.getOrNull()
            }
        }?.toSet().orEmpty(),
        url = json.optString("url").takeIf { it.isNotBlank() },
        interactionCounts = me.foxtails.palustris.domain.PostInteractionCounts(
            favouriteCount = json.optionalNonNegativeInt("favouriteCount"),
            reactionCount = json.optionalNonNegativeInt("reactionCount"),
            repostCount = json.optionalNonNegativeInt("reshareCount"),
            quoteRepostCount = json.optionalNonNegativeInt("quoteRepostCount"),
            replyCount = json.optionalNonNegativeInt("replyCount"),
        ),
        quote = json.optJSONObject("quote")?.let { runCatching { decodePost(it) }.getOrNull() },
        pollOptions = json.optJSONArray("pollOptions")?.let { values ->
            (0 until values.length()).mapNotNull { index ->
                values.optJSONObject(index)?.let { option ->
                    me.foxtails.palustris.domain.PollOption(option.optString("text"), option.optInt("votes"))
                }
            }
        }.orEmpty(),
        reposted = json.optBoolean("reposted"),
        favourited = json.optBoolean("favourited"),
        saved = json.optBoolean("saved"),
        myReaction = json.optString("myReaction").takeIf { it.isNotBlank() },
        selectedReactions = json.optJSONArray("selectedReactions")?.let { values ->
            (0 until values.length()).mapNotNull { index ->
                runCatching { decodeEmojiChoice(values.getJSONObject(index)) }.getOrNull()
            }
        }.orEmpty(),
        emoji = decodeEmojiMap(json.optJSONObject("emoji")),
        ownRepostId = json.optJSONObject("ownRepostId")?.let(::decodeEntity),
        actionTargetId = json.optJSONObject("actionTargetId")?.let(::decodeEntity),
    )

private fun encodeAttachment(attachment: me.foxtails.palustris.domain.Attachment): JSONObject = JSONObject()
    .put("url", attachment.url)
    .put("mimeType", attachment.mimeType)
    .put("description", attachment.description)
    .put("previewUrl", attachment.previewUrl)
    .put("sensitive", attachment.sensitive)
    .put("id", attachment.id)
    .put("kind", attachment.kind.name)
    .put("width", attachment.width)
    .put("height", attachment.height)
    .put("previewWidth", attachment.previewWidth)
    .put("previewHeight", attachment.previewHeight)
    .put("blurhash", attachment.blurhash)
    .put("remoteOriginalUrl", attachment.remoteOriginalUrl)

private fun decodeAttachment(json: JSONObject): me.foxtails.palustris.domain.Attachment {
    val mimeType = json.optString("mimeType").takeIf { it.isNotBlank() } ?: "application/octet-stream"
    return me.foxtails.palustris.domain.Attachment(
        url = json.optString("url").takeIf { it.isNotBlank() },
        mimeType = mimeType,
        description = json.optString("description").takeIf { it.isNotBlank() },
        previewUrl = json.optString("previewUrl").takeIf { it.isNotBlank() },
        sensitive = json.optBoolean("sensitive"),
        id = json.optString("id").takeIf { it.isNotBlank() },
        kind = runCatching { me.foxtails.palustris.domain.MediaKind.valueOf(json.optString("kind")) }
            .getOrDefault(me.foxtails.palustris.domain.mediaKindForMimeType(mimeType)),
        width = json.optIntOrNull("width"),
        height = json.optIntOrNull("height"),
        previewWidth = json.optIntOrNull("previewWidth"),
        previewHeight = json.optIntOrNull("previewHeight"),
        blurhash = json.optString("blurhash").takeIf { it.isNotBlank() },
        remoteOriginalUrl = json.optString("remoteOriginalUrl").takeIf { it.isNotBlank() },
    )
}

private fun encodeReaction(reaction: me.foxtails.palustris.domain.Reaction): JSONObject = JSONObject()
    .put("emoji", reaction.emoji)
    .put("count", reaction.count)
    .put("selected", reaction.selected)
    .put("emojiMetadata", reaction.emojiMetadata?.let(::encodeEmoji))

private fun decodeReaction(json: JSONObject): me.foxtails.palustris.domain.Reaction =
    me.foxtails.palustris.domain.Reaction(
        emoji = json.optString("emoji"),
        count = json.optInt("count").coerceAtLeast(0),
        selected = json.optBoolean("selected"),
        emojiMetadata = json.optJSONObject("emojiMetadata")?.let(::decodeEmoji),
    )

private fun encodeEmojiChoice(choice: me.foxtails.palustris.domain.EmojiChoice): JSONObject = JSONObject()
    .put("submissionValue", choice.submissionValue)
    .put("displayText", choice.displayText)
    .put("emoji", choice.emoji?.let(::encodeEmoji))

private fun decodeEmojiChoice(json: JSONObject): me.foxtails.palustris.domain.EmojiChoice =
    me.foxtails.palustris.domain.EmojiChoice(
        submissionValue = json.optString("submissionValue"),
        displayText = json.optString("displayText"),
        emoji = json.optJSONObject("emoji")?.let(::decodeEmoji),
    )

private fun JSONObject.optLongOrNull(key: String): Long? =
    if (has(key) && !isNull(key)) optLong(key) else null

private fun JSONObject.optIntOrNull(key: String): Int? =
    if (has(key) && !isNull(key)) optInt(key) else null

private fun encodeTarget(target: NotificationTarget): JSONObject = JSONObject().apply {    when (target) {
        is NotificationTarget.Post -> put("kind", "post").put("id", encodeEntity(target.id))
        is NotificationTarget.Profile -> put("kind", "profile").put("id", encodeAccountId(target.id))
        is NotificationTarget.Poll -> put("kind", "poll").put("id", encodeEntity(target.id))
        is NotificationTarget.Conversation -> put("kind", "conversation").put("id", encodeEntity(target.id))
    }
}

private fun decodeTarget(json: JSONObject): NotificationTarget = when (json.getString("kind")) {
    "post" -> NotificationTarget.Post(decodeEntity(json.getJSONObject("id")))
    "profile" -> NotificationTarget.Profile(decodeAccountId(json.getJSONObject("id")))
    "poll" -> NotificationTarget.Poll(decodeEntity(json.getJSONObject("id")))
    "conversation" -> NotificationTarget.Conversation(decodeEntity(json.getJSONObject("id")))
    else -> error("Unknown notification target")
}

private fun encodeActivity(activity: NotificationActivity): JSONObject = JSONObject().apply {
    when (activity) {
        NotificationActivity.Mention -> put("kind", "mention")
        NotificationActivity.Reply -> put("kind", "reply")
        NotificationActivity.Reshare -> put("kind", "reshare")
        NotificationActivity.Quote -> put("kind", "quote")
        NotificationActivity.Favourite -> put("kind", "favourite")
        is NotificationActivity.EmojiReaction -> put("kind", "reaction").put("identity", activity.reaction.identity)
            .put("fallback", activity.reaction.fallbackText)
            .put("emoji", activity.reaction.emoji?.let(::encodeEmoji))
        NotificationActivity.Follow -> put("kind", "follow")
        NotificationActivity.FollowRequest -> put("kind", "follow_request")
        NotificationActivity.AcceptedRequest -> put("kind", "accepted_request")
        NotificationActivity.SubscribedPost -> put("kind", "subscribed")
        is NotificationActivity.PollResult -> put("kind", "poll_result").put("option", activity.option)
        NotificationActivity.PostUpdate -> put("kind", "post_update")
        NotificationActivity.QuotedPostUpdate -> put("kind", "quoted_post_update")
        NotificationActivity.DirectMessage -> put("kind", "direct_message")
        is NotificationActivity.System.Moderation -> put("kind", "system").put("systemKind", "Moderation")
            .put("title", activity.title).put("detail", activity.detail)
        is NotificationActivity.System.RelationshipChange -> put("kind", "system").put("systemKind", "RelationshipChange")
            .put("title", activity.title).put("detail", activity.detail)
        is NotificationActivity.System.RoleOrAchievement -> put("kind", "system").put("systemKind", "RoleOrAchievement")
            .put("title", activity.title).put("detail", activity.detail)
        is NotificationActivity.System.AppEvent -> put("kind", "system").put("systemKind", "AppEvent")
            .put("title", activity.title).put("detail", activity.detail)
        is NotificationActivity.Unknown -> put("kind", "unknown").put("fallback", activity.fallbackText)
            .put("destination", (activity.validatedDestination as? NotificationDestination.Server)?.url?.value)
    }
}

private fun decodeActivity(json: JSONObject): NotificationActivity = when (json.optString("kind")) {
    "mention" -> NotificationActivity.Mention
    "reply" -> NotificationActivity.Reply
    "reshare" -> NotificationActivity.Reshare
    "quote" -> NotificationActivity.Quote
    "favourite" -> NotificationActivity.Favourite
    "reaction" -> NotificationActivity.EmojiReaction(NotificationReaction(
        json.optString("identity", "reaction"), json.optString("fallback", "Reaction"),
        json.optJSONObject("emoji")?.let(::decodeEmoji)
            ?: json.optString("imageUrl").takeIf { it.isNotBlank() }?.let { legacy ->
                me.foxtails.palustris.domain.ValidatedUrl.https(legacy)?.let { url ->
                    me.foxtails.palustris.domain.CustomEmoji(
                        shortcode = json.optString("identity", "reaction").trim(':'),
                        animatedUrl = url,
                        staticUrl = url,
                        visibleInPicker = false,
                        submissionValue = json.optString("identity", "reaction"),
                    )
                }
            },
    ))
    "follow" -> NotificationActivity.Follow
    "follow_request" -> NotificationActivity.FollowRequest
    "accepted_request" -> NotificationActivity.AcceptedRequest
    "subscribed" -> NotificationActivity.SubscribedPost
    "poll_result" -> NotificationActivity.PollResult(json.optString("option").takeIf { it.isNotBlank() })
    "post_update" -> NotificationActivity.PostUpdate
    "quoted_post_update" -> NotificationActivity.QuotedPostUpdate
    "direct_message" -> NotificationActivity.DirectMessage
    "system" -> when (json.optString("systemKind")) {
        "Moderation" -> NotificationActivity.System.Moderation(json.optString("title"), json.optString("detail").takeIf { it.isNotBlank() })
        "RelationshipChange" -> NotificationActivity.System.RelationshipChange(json.optString("title"), json.optString("detail").takeIf { it.isNotBlank() })
        "RoleOrAchievement" -> NotificationActivity.System.RoleOrAchievement(json.optString("title"), json.optString("detail").takeIf { it.isNotBlank() })
        else -> NotificationActivity.System.AppEvent(json.optString("title"), json.optString("detail").takeIf { it.isNotBlank() })
    }
    else -> NotificationActivity.Unknown(
        fallbackText = json.optString("fallback", "New activity"),
        validatedDestination = json.optString("destination")
            .takeIf(String::isNotBlank)
            ?.let(me.foxtails.palustris.domain.ValidatedUrl::https)
            ?.let(me.foxtails.palustris.domain.NotificationDestination::Server),
    )
}

private fun encodeGroup(group: NotificationGroup): JSONObject = JSONObject().apply {
    put("accountId", encodeAccountId(group.id.accountId)).put("value", group.id.value)
    put("actors", JSONArray(group.actorPreviews.map(::encodeAccount))).put("totalCount", group.totalCount)
}

private fun decodeGroup(json: JSONObject): NotificationGroup = NotificationGroup(
    id = NotificationGroupId(decodeAccountId(json.getJSONObject("accountId")), json.getString("value")),
    actorPreviews = json.optJSONArray("actors")?.let { values ->
        (0 until values.length()).mapNotNull { index -> runCatching { decodeAccount(values.getJSONObject(index)) }.getOrNull() }
    }.orEmpty(),
    totalCount = json.optInt("totalCount").takeIf { it > 0 },
)

private fun encodeUnread(state: NotificationUnreadState): JSONObject = JSONObject().apply {
    when (state) {
        is NotificationUnreadState.Exact -> put("kind", "exact").put("count", state.count)
        is NotificationUnreadState.AtLeast -> put("kind", "at_least").put("count", state.count)
        NotificationUnreadState.Present -> put("kind", "present")
        NotificationUnreadState.None -> put("kind", "none")
        NotificationUnreadState.Unknown -> put("kind", "unknown")
    }
}

private fun decodeUnread(json: JSONObject?): NotificationUnreadState = when (json?.optString("kind")) {
    "exact" -> NotificationUnreadState.Exact(json.optInt("count").coerceAtLeast(0))
    "at_least" -> NotificationUnreadState.AtLeast(json.optInt("count").coerceAtLeast(0))
    "present" -> NotificationUnreadState.Present
    "none" -> NotificationUnreadState.None
    else -> NotificationUnreadState.Unknown
}

private fun encodeCheckpoint(checkpoint: NotificationCheckpoint): JSONObject = JSONObject().apply {
    put("accountId", encodeAccountId(checkpoint.accountId)).put("capturedAt", checkpoint.capturedAtEpochMillis)
    put("query", JSONObject().put("categories", JSONArray(checkpoint.query.categories.map { it.name }))
        .put("limit", checkpoint.query.limit).put("grouped", checkpoint.query.grouped))
    checkpoint.newest?.let { put("newest", it.value) }
    checkpoint.oldest?.let { put("oldest", it.value) }
    checkpoint.newerContinuation?.let { put("newerContinuation", it.value) }
    checkpoint.olderContinuation?.let { put("olderContinuation", it.value) }
    put("completeness", checkpoint.completeness.name)
    put("baselineEstablished", checkpoint.baselineEstablished)
}

private fun decodeCheckpoint(json: JSONObject): NotificationCheckpoint {
    val queryJson = json.getJSONObject("query")
    val categories = queryJson.optJSONArray("categories")?.let { values ->
        (0 until values.length()).mapNotNull { index -> runCatching {
            me.foxtails.palustris.domain.NotificationCategory.valueOf(values.getString(index))
        }.getOrNull() }
    }.orEmpty().toSet()
    return NotificationCheckpoint(
        accountId = decodeAccountId(json.getJSONObject("accountId")),
        query = me.foxtails.palustris.domain.NotificationQuery(categories, queryJson.optInt("limit", 30), queryJson.optBoolean("grouped")),
        newest = json.optString("newest").takeIf { it.isNotBlank() }?.let(::meFoxtailsNotificationCursor),
        oldest = json.optString("oldest").takeIf { it.isNotBlank() }?.let(::meFoxtailsNotificationCursor),
        capturedAtEpochMillis = json.optLong("capturedAt"),
        newerContinuation = json.optString("newerContinuation").takeIf { it.isNotBlank() }?.let(::meFoxtailsNotificationCursor),
        olderContinuation = json.optString("olderContinuation").takeIf { it.isNotBlank() }?.let(::meFoxtailsNotificationCursor),
        completeness = runCatching { NotificationSyncCompleteness.valueOf(json.optString("completeness")) }
            .getOrDefault(NotificationSyncCompleteness.Unknown),
        baselineEstablished = json.optBoolean("baselineEstablished"),
    )
}

private fun meFoxtailsNotificationCursor(value: String) = me.foxtails.palustris.domain.NotificationCursor(value)
private fun JSONObject.optionalNonNegativeInt(key: String): Int? {
    if (!has(key) || isNull(key)) return null
    val value = opt(key) ?: return null
    val raw = when (value) {
        is Number, is String -> value.toString()
        else -> return null
    }
    return runCatching { java.math.BigDecimal(raw).toBigIntegerExact().intValueExact() }
        .getOrNull()
        ?.takeIf { it >= 0 }
}
