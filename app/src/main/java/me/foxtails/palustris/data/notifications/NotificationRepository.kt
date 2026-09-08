package me.foxtails.palustris.data.notifications

import android.content.Context
import android.util.AtomicFile
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.EntityId
import me.foxtails.palustris.domain.Notification
import me.foxtails.palustris.domain.NotificationAcknowledgement
import me.foxtails.palustris.domain.NotificationActivity
import me.foxtails.palustris.domain.NotificationCheckpoint
import me.foxtails.palustris.domain.NotificationDestination
import me.foxtails.palustris.domain.NotificationGroup
import me.foxtails.palustris.domain.NotificationGroupId
import me.foxtails.palustris.domain.NotificationPage
import me.foxtails.palustris.domain.NotificationReadState
import me.foxtails.palustris.domain.NotificationReadStatus
import me.foxtails.palustris.domain.NotificationReaction
import me.foxtails.palustris.domain.NotificationTarget
import me.foxtails.palustris.domain.NotificationSyncToken
import me.foxtails.palustris.domain.NotificationUnreadState
import me.foxtails.palustris.domain.ProfileField
import me.foxtails.palustris.domain.Protocol
import org.json.JSONArray
import org.json.JSONObject

data class NotificationRepositoryState(
    val items: List<Notification> = emptyList(),
    val unreadState: NotificationUnreadState = NotificationUnreadState.Unknown,
    val checkpoint: NotificationCheckpoint? = null,
    val lastSyncedAtEpochMillis: Long = 0,
)

interface NotificationStore {
    fun read(accountId: AccountId): NotificationRepositoryState?
    fun write(accountId: AccountId, state: NotificationRepositoryState)
    fun delete(accountId: AccountId)
}

/** A process-local store used by unit tests and constructor compatibility helpers. */
class InMemoryNotificationStore : NotificationStore {
    private val values = mutableMapOf<AccountId, NotificationRepositoryState>()

    override fun read(accountId: AccountId): NotificationRepositoryState? = values[accountId]

    override fun write(accountId: AccountId, state: NotificationRepositoryState) {
        values[accountId] = state
    }

    override fun delete(accountId: AccountId) {
        values.remove(accountId)
    }
}

/** App-private, no-backup notification summaries. Tokens and session secrets never enter this store. */
class FileNotificationStore @javax.inject.Inject constructor(
    @ApplicationContext context: Context,
) : NotificationStore {
    private val directory = File(context.noBackupFilesDir, "notifications")

    override fun read(accountId: AccountId): NotificationRepositoryState? = runCatching {
        val file = fileFor(accountId)
        if (!file.baseFile.exists()) return null
        decode(JSONObject(String(file.readFully(), Charsets.UTF_8)))
    }.getOrNull()

    override fun write(accountId: AccountId, state: NotificationRepositoryState) {
        directory.mkdirs()
        val file = fileFor(accountId)
        val stream = file.startWrite()
        try {
            stream.write(encode(state).toString().toByteArray(Charsets.UTF_8))
            file.finishWrite(stream)
        } catch (error: Exception) {
            file.failWrite(stream)
            throw error
        }
    }

    override fun delete(accountId: AccountId) {
        fileFor(accountId).delete()
    }

    private fun fileFor(accountId: AccountId): AtomicFile = AtomicFile(
        File(directory, "${accountId.stableFileName()}.json"),
    )
}

/**
 * One account-scoped merge point for REST pages, cache state, local visibility, and unread knowledge.
 * Writes require the source generation that produced them, so late requests cannot recreate removed state.
 */
class NotificationRepository @javax.inject.Inject constructor(
    private val store: NotificationStore,
) {
    constructor() : this(InMemoryNotificationStore())

    private val states = mutableMapOf<AccountId, MutableStateFlow<NotificationRepositoryState>>()
    private val generations = mutableMapOf<AccountId, Long>()

    @Synchronized
    fun observe(accountId: AccountId): StateFlow<NotificationRepositoryState> = states.getOrPut(accountId) {
        MutableStateFlow(store.read(accountId) ?: NotificationRepositoryState())
    }.asStateFlow()

    @Synchronized
    fun activate(token: NotificationSyncToken) {
        val current = generations[token.accountId]
        if (current == null || token.generation >= current) generations[token.accountId] = token.generation
        states.getOrPut(token.accountId) { MutableStateFlow(store.read(token.accountId) ?: NotificationRepositoryState()) }
    }

    @Synchronized
    fun currentToken(accountId: AccountId): NotificationSyncToken? = generations[accountId]?.let {
        NotificationSyncToken(accountId, it)
    }

    @Synchronized
    fun invalidate(accountId: AccountId, generation: Long) {
        val next = maxOf(generations[accountId] ?: 0L, generation + 1L)
        generations[accountId] = next
    }

    suspend fun ingest(token: NotificationSyncToken, page: NotificationPage): Boolean {
        val next = synchronized(this) {
            if (!isCurrentLocked(token)) return false
            val state = stateForLocked(token.accountId).value
            val previous = state.items.associateBy(Notification::id)
            val merged = (page.items + state.items)
                .distinctBy(Notification::id)
                .map { item -> item.mergeReadState(previous[item.id]?.readState) }
                .sortedWith(compareByDescending<Notification> { it.createdAtEpochMillis }.thenByDescending { it.id.value })
                .take(MAX_ITEMS)
            state.copy(
                items = merged,
                unreadState = page.unreadState.takeIf { it !is NotificationUnreadState.Unknown } ?: state.unreadState,
                checkpoint = page.checkpoint ?: state.checkpoint,
                lastSyncedAtEpochMillis = page.checkpoint?.capturedAtEpochMillis ?: state.lastSyncedAtEpochMillis,
            ).also { stateForLocked(token.accountId).value = it }
        }
        persistIfCurrent(token, next)
        return true
    }

    suspend fun updateUnreadState(token: NotificationSyncToken, unreadState: NotificationUnreadState): Boolean {
        val next = synchronized(this) {
            if (!isCurrentLocked(token)) return false
            stateForLocked(token.accountId).value.copy(unreadState = unreadState).also {
                stateForLocked(token.accountId).value = it
            }
        }
        persistIfCurrent(token, next)
        return true
    }

    suspend fun markSeen(token: NotificationSyncToken, id: EntityId? = null): Boolean {
        val next = synchronized(this) {
            if (!isCurrentLocked(token)) return false
            val current = stateForLocked(token.accountId).value
            val items = current.items.map { item ->
                if (id == null || item.id == id) item.copy(readState = item.readState.copy(locallySeen = true)) else item
            }
            current.copy(items = items).also { stateForLocked(token.accountId).value = it }
        }
        persistIfCurrent(token, next)
        return true
    }

    suspend fun markPresented(token: NotificationSyncToken, id: EntityId): Boolean {
        val next = synchronized(this) {
            if (!isCurrentLocked(token)) return false
            val current = stateForLocked(token.accountId).value
            val items = current.items.map { item ->
                if (item.id == id) item.copy(readState = item.readState.copy(androidPresented = true)) else item
            }
            current.copy(items = items).also { stateForLocked(token.accountId).value = it }
        }
        persistIfCurrent(token, next)
        return true
    }

    suspend fun acknowledge(
        token: NotificationSyncToken,
        acknowledgement: NotificationAcknowledgement,
    ): Boolean {
        val next = synchronized(this) {
            if (!isCurrentLocked(token) || acknowledgement.accountId != token.accountId) return false
            val current = stateForLocked(token.accountId).value
            val items = when (acknowledgement.readState) {
                NotificationUnreadState.None,
                is NotificationUnreadState.Exact,
                -> current.items.map { item ->
                    item.copy(readState = item.readState.copy(
                        status = NotificationReadStatus.Read,
                        serverAcknowledged = true,
                    ))
                }
                else -> current.items.map { item ->
                    item.copy(readState = item.readState.copy(serverAcknowledged = true))
                }
            }
            current.copy(items = items, unreadState = acknowledgement.readState)
                .also { stateForLocked(token.accountId).value = it }
        }
        persistIfCurrent(token, next)
        return true
    }

    suspend fun dismiss(token: NotificationSyncToken, id: EntityId): Boolean {
        val next = synchronized(this) {
            if (!isCurrentLocked(token)) return false
            val current = stateForLocked(token.accountId).value
            current.copy(items = current.items.filterNot { it.id == id }).also {
                stateForLocked(token.accountId).value = it
            }
        }
        persistIfCurrent(token, next)
        return true
    }

    @Synchronized
    fun remove(accountId: AccountId) {
        states.remove(accountId)
        generations.remove(accountId)
        store.delete(accountId)
    }

    private suspend fun persistIfCurrent(token: NotificationSyncToken, @Suppress("UNUSED_PARAMETER") state: NotificationRepositoryState) {
        withContext(Dispatchers.IO) {
            synchronized(this@NotificationRepository) {
                if (isCurrentLocked(token)) store.write(token.accountId, stateForLocked(token.accountId).value)
            }
        }
    }

    private fun Notification.mergeReadState(previous: NotificationReadState?): Notification {
        val known = readState.status.takeUnless { it == NotificationReadStatus.Unknown }
            ?: previous?.status ?: NotificationReadStatus.Unknown
        return copy(readState = readState.copy(
            status = known,
            locallySeen = readState.locallySeen || previous?.locallySeen == true,
            serverAcknowledged = readState.serverAcknowledged || previous?.serverAcknowledged == true,
            androidPresented = readState.androidPresented || previous?.androidPresented == true,
        ))
    }

    private fun stateForLocked(accountId: AccountId): MutableStateFlow<NotificationRepositoryState> =
        states.getOrPut(accountId) { MutableStateFlow(store.read(accountId) ?: NotificationRepositoryState()) }

    private fun isCurrentLocked(token: NotificationSyncToken): Boolean =
        (token.generation == 0L && token.accountId !in generations) || generations[token.accountId] == token.generation

    private companion object {
        const val MAX_ITEMS = 500
    }
}

private fun AccountId.stableFileName(): String {
    val bytes = "$connection\u0000$localId".toByteArray(Charsets.UTF_8)
    return MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}

private fun encode(state: NotificationRepositoryState): JSONObject = JSONObject().apply {
    put("version", 1)
    put("items", JSONArray(state.items.map(::encodeNotification)))
    put("unread", encodeUnread(state.unreadState))
    state.checkpoint?.let { checkpoint -> put("checkpoint", encodeCheckpoint(checkpoint)) }
    put("lastSyncedAt", state.lastSyncedAtEpochMillis)
}

private fun decode(json: JSONObject): NotificationRepositoryState {
    val items = json.optJSONArray("items")?.let { values ->
        (0 until values.length()).mapNotNull { index -> runCatching { decodeNotification(values.getJSONObject(index)) }.getOrNull() }
    }.orEmpty()
    return NotificationRepositoryState(
        items = items,
        unreadState = decodeUnread(json.optJSONObject("unread")),
        checkpoint = json.optJSONObject("checkpoint")?.let(::decodeCheckpoint),
        lastSyncedAtEpochMillis = json.optLong("lastSyncedAt", 0),
    )
}

private fun encodeNotification(notification: Notification): JSONObject = JSONObject().apply {
    put("id", encodeEntity(notification.id))
    put("accountId", encodeAccountId(notification.accountId))
    put("createdAt", notification.createdAtEpochMillis)
    put("activity", encodeActivity(notification.activity))
    put("actors", JSONArray(notification.actors.map(::encodeAccount)))
    notification.target?.let { put("target", encodeTarget(it)) }
    put("readStatus", notification.readState.status.name)
    put("locallySeen", notification.readState.locallySeen)
    put("serverAcknowledged", notification.readState.serverAcknowledged)
    put("androidPresented", notification.readState.androidPresented)
    put("rawType", notification.rawType)
    notification.group?.let { put("group", encodeGroup(it)) }
}

private fun decodeNotification(json: JSONObject): Notification = Notification(
    id = decodeEntity(json.getJSONObject("id")),
    accountId = decodeAccountId(json.getJSONObject("accountId")),
    createdAtEpochMillis = json.optLong("createdAt"),
    activity = decodeActivity(json.getJSONObject("activity")),
    actors = json.optJSONArray("actors")?.let { values ->
        (0 until values.length()).mapNotNull { index -> runCatching { decodeAccount(values.getJSONObject(index)) }.getOrNull() }
    }.orEmpty(),
    target = json.optJSONObject("target")?.let(::decodeTarget),
    destination = json.optJSONObject("target")?.let { NotificationDestination.InApp(decodeTarget(it)) },
    readState = NotificationReadState(
        status = runCatching { NotificationReadStatus.valueOf(json.optString("readStatus")) }
            .getOrDefault(NotificationReadStatus.Unknown),
        locallySeen = json.optBoolean("locallySeen"),
        serverAcknowledged = json.optBoolean("serverAcknowledged"),
        androidPresented = json.optBoolean("androidPresented"),
    ),
    rawType = json.optString("rawType", "unknown"),
    group = json.optJSONObject("group")?.let(::decodeGroup),
)

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

private fun decodeAccount(json: JSONObject): Account = Account(
    id = decodeAccountId(json.getJSONObject("id")),
    displayName = json.optString("displayName"),
    handle = json.optString("handle"),
    avatarUrl = json.optString("avatarUrl").takeIf { it.isNotBlank() },
    biography = json.optString("biography"),
    profileFields = emptyList<ProfileField>(),
)

private fun encodeTarget(target: NotificationTarget): JSONObject = JSONObject().apply {
    when (target) {
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
            .put("fallback", activity.reaction.fallbackText).put("imageUrl", activity.reaction.imageUrl)
        NotificationActivity.Follow -> put("kind", "follow")
        NotificationActivity.FollowRequest -> put("kind", "follow_request")
        NotificationActivity.AcceptedRequest -> put("kind", "accepted_request")
        NotificationActivity.SubscribedPost -> put("kind", "subscribed")
        is NotificationActivity.PollResult -> put("kind", "poll_result").put("option", activity.option)
        NotificationActivity.PostUpdate -> put("kind", "post_update")
        NotificationActivity.QuotedPostUpdate -> put("kind", "quoted_post_update")
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
        json.optString("imageUrl").takeIf { it.isNotBlank() },
    ))
    "follow" -> NotificationActivity.Follow
    "follow_request" -> NotificationActivity.FollowRequest
    "accepted_request" -> NotificationActivity.AcceptedRequest
    "subscribed" -> NotificationActivity.SubscribedPost
    "poll_result" -> NotificationActivity.PollResult(json.optString("option").takeIf { it.isNotBlank() })
    "post_update" -> NotificationActivity.PostUpdate
    "quoted_post_update" -> NotificationActivity.QuotedPostUpdate
    "system" -> when (json.optString("systemKind")) {
        "Moderation" -> NotificationActivity.System.Moderation(json.optString("title"), json.optString("detail").takeIf { it.isNotBlank() })
        "RelationshipChange" -> NotificationActivity.System.RelationshipChange(json.optString("title"), json.optString("detail").takeIf { it.isNotBlank() })
        "RoleOrAchievement" -> NotificationActivity.System.RoleOrAchievement(json.optString("title"), json.optString("detail").takeIf { it.isNotBlank() })
        else -> NotificationActivity.System.AppEvent(json.optString("title"), json.optString("detail").takeIf { it.isNotBlank() })
    }
    else -> NotificationActivity.Unknown(json.optString("fallback", "New activity"))
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
    )
}

private fun meFoxtailsNotificationCursor(value: String) = me.foxtails.palustris.domain.NotificationCursor(value)
