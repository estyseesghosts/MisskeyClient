package me.foxtails.palustris.data.auth

import android.content.Context
import android.util.AtomicFile
import me.foxtails.palustris.data.misskey.MisskeyMapper
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.AccessGrant
import me.foxtails.palustris.domain.AccessScope
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.Protocol
import me.foxtails.palustris.domain.PushSessionState
import me.foxtails.palustris.domain.ProfileField
import me.foxtails.palustris.domain.ServerCapabilities
import me.foxtails.palustris.domain.Session
import me.foxtails.palustris.domain.ValidatedUrl
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** These values never enter Compose state, saved-instance bundles, logs, or backups. */
class LoginSession(
    val origin: String,
    val token: String,
    val user: JSONObject,
    val protocol: Protocol = Protocol.MISSKEY,
    val canPublish: Boolean = false,
    val access: AccessGrant = AccessGrant(),
    val capabilities: ServerCapabilities = ServerCapabilities(),
) {
    val account: Account
        get() = when (protocol) {
            Protocol.MISSKEY -> MisskeyMapper.account(user, origin)
            Protocol.MASTODON -> me.foxtails.palustris.data.mastodon.MastodonMapper.account(user, origin)
        }
}

data class PendingLogin(
    val origin: String,
    val id: String,
    val createdAt: Long,
    val protocol: Protocol = Protocol.MISSKEY,
    val clientId: String? = null,
    val clientSecret: String? = null,
    val codeVerifier: String? = null,
    val codeChallenge: String? = null,
    val scope: String = "read",
    val authorizationCode: String? = null,
    val requestedAccess: Set<AccessScope> = emptySet(),
    val replacingAccountId: AccountId? = null,
) {
    fun isFresh(now: Long) = now - createdAt in 0..(15 * 60 * 1000L)
}

interface SessionStore {
    fun read(accountId: AccountId): Session?
    fun write(accountId: AccountId, session: Session)
    fun delete(accountId: AccountId)
    fun readIndex(): AccountIndex
    fun writeIndex(index: AccountIndex)
    fun clear()

    /** Serializes storage read/modify/write workflows without spanning network requests. */
    fun <T> transaction(block: () -> T): T = block()

    fun writePushInstance(accountId: AccountId, instanceName: String) = Unit

    /** Atomically replaces encrypted callback state only when the instance still owns the account. */
    fun recordPushEndpoint(
        accountId: AccountId,
        instanceName: String,
        endpoint: ValidatedUrl,
        publicKey: String,
        authSecret: String,
        callbackAtEpochMillis: Long = System.currentTimeMillis(),
    ): Boolean = transaction {
        val session = read(accountId) ?: return@transaction false
        if (session.pushInstanceName != instanceName || publicKey.isBlank() || authSecret.isBlank()) return@transaction false
        val old = session.pushState
        val changed = old.endpoint != endpoint || old.publicKey != publicKey || old.authSecret != authSecret
        write(accountId, session.copy(
            pushState = old.copy(
                endpoint = endpoint,
                publicKey = publicKey,
                authSecret = authSecret,
                endpointGeneration = if (changed) old.endpointGeneration + 1 else old.endpointGeneration,
                endpointCallbackPending = true,
                lastCallbackAtEpochMillis = callbackAtEpochMillis,
            ),
        ))
        true
    }

    fun recordPushMessageHint(accountId: AccountId, instanceName: String): Boolean = transaction {
        val session = read(accountId) ?: return@transaction false
        if (session.pushInstanceName != instanceName) return@transaction false
        val state = session.pushState
        write(accountId, session.copy(pushState = state.copy(
            messageHintPending = true,
            messageGeneration = state.messageGeneration + 1,
        )))
        true
    }

    fun clearPushEndpointPending(
        accountId: AccountId,
        instanceName: String,
        generation: Long? = null,
    ): Boolean = transaction {
        val session = read(accountId) ?: return@transaction false
        if (session.pushInstanceName != instanceName) return@transaction false
        val state = session.pushState
        if (generation != null && state.endpointGeneration != generation) return@transaction false
        write(accountId, session.copy(pushState = state.copy(endpointCallbackPending = false)))
        true
    }

    fun clearPushMessageHint(
        accountId: AccountId,
        instanceName: String,
        generation: Long? = null,
    ): Boolean = transaction {
        val session = read(accountId) ?: return@transaction false
        if (session.pushInstanceName != instanceName) return@transaction false
        val state = session.pushState
        if (generation != null && state.messageGeneration != generation) return@transaction false
        write(accountId, session.copy(pushState = state.copy(messageHintPending = false)))
        true
    }

    fun updateCapabilities(
        accountId: AccountId,
        update: (ServerCapabilities) -> ServerCapabilities,
    ): Boolean = transaction {
        val session = read(accountId) ?: return@transaction false
        write(accountId, session.copy(capabilities = update(session.capabilities)))
        true
    }

    fun updatePushState(
        accountId: AccountId,
        instanceName: String,
        update: (PushSessionState) -> PushSessionState,
    ): Boolean = transaction {
        val session = read(accountId) ?: return@transaction false
        if (session.pushInstanceName != instanceName) return@transaction false
        write(accountId, session.copy(pushState = update(session.pushState)))
        true
    }

    /** Pending authentication is intentionally separate from committed account sessions. */
    fun readPending(): PendingLogin? = null
    fun writePending(pending: PendingLogin) = Unit
    fun clearPending() = Unit
    fun writeProfile(accountId: AccountId, profile: JSONObject) = Unit
}

/** AES-GCM per-account storage with one shared non-exportable Android Keystore key. */
class EncryptedSessionStore private constructor(
    context: Context,
    private val accountFiles: AccountFileStore,
) : SessionStore {
    constructor(context: Context) : this(context, AccountFileStore(context))

    internal constructor(context: Context, key: javax.crypto.SecretKey) :
        this(context, AccountFileStore(context, key))

    private val accountsDirectory = File(context.noBackupFilesDir, "accounts")
    private val indexFile = AtomicFile(File(accountsDirectory, "index.json"))
    private val pendingFile = File(accountsDirectory, "pending.enc")
    private val legacyFile = AtomicFile(File(context.noBackupFilesDir, "session.enc"))
    private var migrationComplete = false

    @Synchronized
    fun migrateFromLegacy() {
        if (migrationComplete) return
        if (!legacyFile.baseFile.exists()) {
            migrationComplete = true
            return
        }
        try {
            val legacy = accountFiles.readJson(legacyFile.baseFile)
            var index = readIndexInternal()
            legacy.optJSONObject("session")?.let { stored ->
                val login = LoginSession(stored.getString("origin"), stored.getString("token"), stored.getJSONObject("user"))
                val session = Session(login.account.id, login.token, ServerCapabilities(), login.access)
                accountFiles.write(login.account.id, session, login.user)
                index = index.withAccount(login.account).copy(activeAccountId = login.account.id)
            }
            legacy.optJSONObject("pending")?.let { stored ->
                writePendingInternal(PendingLogin(stored.getString("origin"), stored.getString("id"), stored.getLong("createdAt")))
            }
            writeIndexInternal(index)
            legacyFile.delete()
            migrationComplete = true
        } catch (e: Exception) {
            migrationComplete = false
            throw e
        }
    }

    @Synchronized
    override fun <T> transaction(block: () -> T): T {
        migrateFromLegacy()
        return block()
    }

    @Synchronized
    override fun read(accountId: AccountId): Session? {
        migrateFromLegacy()
        return accountFiles.read(accountId)
    }

    @Synchronized
    override fun write(accountId: AccountId, session: Session) {
        migrateFromLegacy()
        accountFiles.write(accountId, session)
    }

    @Synchronized
    override fun writeProfile(accountId: AccountId, profile: JSONObject) {
        migrateFromLegacy()
        accountFiles.writeProfile(accountId, profile)
    }

    @Synchronized
    override fun delete(accountId: AccountId) {
        migrateFromLegacy()
        accountFiles.delete(accountId)
    }

    @Synchronized
    override fun readIndex(): AccountIndex {
        migrateFromLegacy()
        return readIndexInternal()
    }

    @Synchronized
    override fun writeIndex(index: AccountIndex) {
        migrateFromLegacy()
        writeIndexInternal(index)
    }

    @Synchronized
    override fun readPending(): PendingLogin? {
        migrateFromLegacy()
        if (!pendingFile.exists()) return null
        val json = accountFiles.readJson(pendingFile)
        return json.toPendingLogin()
    }

    @Synchronized
    override fun writePending(pending: PendingLogin) {
        migrateFromLegacy()
        writePendingInternal(pending)
    }

    @Synchronized
    override fun clearPending() {
        migrateFromLegacy()
        AtomicFile(pendingFile).delete()
    }

    @Synchronized
    override fun clear() {
        accountFiles.clear()
        AtomicFile(pendingFile).delete()
        indexFile.delete()
        legacyFile.delete()
        migrationComplete = true
    }

    @Synchronized
    override fun writePushInstance(accountId: AccountId, instanceName: String) {
        migrateFromLegacy()
        accountFiles.writePushInstance(accountId, instanceName)
    }

    @Synchronized
    override fun updatePushState(
        accountId: AccountId,
        instanceName: String,
        update: (PushSessionState) -> PushSessionState,
    ): Boolean {
        migrateFromLegacy()
        return accountFiles.updatePushState(accountId, instanceName, update)
    }

    @Synchronized
    override fun updateCapabilities(
        accountId: AccountId,
        update: (ServerCapabilities) -> ServerCapabilities,
    ): Boolean {
        migrateFromLegacy()
        return accountFiles.updateCapabilities(accountId, update)
    }

    private fun readIndexInternal(): AccountIndex {
        if (!indexFile.baseFile.exists()) return AccountIndex()
        val json = JSONObject(String(indexFile.readFully(), Charsets.UTF_8))
        val accounts = json.optJSONArray("accounts")?.let { entries ->
            (0 until entries.length()).map { index ->
                entries.getJSONObject(index).let {
                    AccountRef(it.getJSONObject("accountId").toAccountId(), it.getString("handle"),
                        it.nullableString("avatarUrl"), it.getString("displayName"), it.getString("protocol").let(Protocol::valueOf),
                            it.nullableString("biography").orEmpty(), it.profileFields(),
                            it.nullableString("bannerUrl"), it.nullableNonNegativeLong("followersCount"),
                            it.nullableNonNegativeLong("followingCount"), it.nullableNonNegativeLong("postsCount"),
                            it.optBoolean("locked"), it.optBoolean("bot"))
                }
            }
        }.orEmpty()
        val schemaVersion = json.optInt("schemaVersion", json.optInt("version", 1))
        return AccountIndex(json.optInt("version", schemaVersion), accounts,
            json.optJSONObject("activeAccountId")?.toAccountId(), schemaVersion)
    }

    private fun writeIndexInternal(index: AccountIndex) {
        accountsDirectory.mkdirs()
        val json = JSONObject()
            .put("version", index.version)
            .put("schemaVersion", index.schemaVersion)
            .put("accounts", JSONArray(index.accounts.map { ref ->
                JSONObject()
                    .put("accountId", ref.accountId.toIndexJson())
                    .put("handle", ref.handle)
                    .put("avatarUrl", ref.avatarUrl)
                    .put("displayName", ref.displayName)
                    .put("protocol", ref.protocol.name)
                    .put("biography", ref.biography)
                    .put("profileFields", JSONArray(ref.profileFields.map { field ->
                        JSONObject().put("name", field.name).put("value", field.value)
                    }))
                    .put("bannerUrl", ref.bannerUrl)
                    .put("followersCount", ref.followersCount)
                    .put("followingCount", ref.followingCount)
                    .put("postsCount", ref.postsCount)
                    .put("locked", ref.locked)
                    .put("bot", ref.bot)
            }))
            .put("activeAccountId", index.activeAccountId?.toIndexJson())
        val stream = indexFile.startWrite()
        try {
            stream.write(json.toString().toByteArray(Charsets.UTF_8))
            indexFile.finishWrite(stream)
        } catch (e: Exception) {
            indexFile.failWrite(stream)
            throw e
        }
    }

    private fun writePendingInternal(pending: PendingLogin) {
        val json = JSONObject()
            .put("origin", pending.origin).put("id", pending.id).put("createdAt", pending.createdAt)
            .put("protocol", pending.protocol.name).put("clientId", pending.clientId)
            .put("clientSecret", pending.clientSecret).put("codeVerifier", pending.codeVerifier)
            .put("codeChallenge", pending.codeChallenge).put("scope", pending.scope)
            .put("authorizationCode", pending.authorizationCode)
            .put("requestedAccess", JSONArray(pending.requestedAccess.map { it.name }))
            .put("replacingAccountId", pending.replacingAccountId?.toIndexJson())
            .put("replacingAccountOrigin", pending.replacingAccountId?.connection?.origin)
            .put("replacingAccountLocalId", pending.replacingAccountId?.localId)
            .put("replacingAccountProtocol", pending.replacingAccountId?.connection?.protocol?.name)
        accountFiles.writeJson(pendingFile, json)
    }
}

private fun AccountIndex.withAccount(account: Account): AccountIndex {
    val ref = AccountRef(account.id, account.handle, account.avatarUrl, account.displayName,
        biography = account.biography, profileFields = account.profileFields,
        bannerUrl = account.bannerUrl, followersCount = account.followersCount,
        followingCount = account.followingCount, postsCount = account.postsCount,
        locked = account.locked, bot = account.bot)
    return copy(accounts = accounts.filterNot { it.accountId == account.id } + ref)
}

private fun JSONObject.nullableString(key: String): String? = if (isNull(key)) null else optString(key).takeIf { it.isNotEmpty() }

private fun JSONObject.nullableNonNegativeLong(key: String): Long? {
    if (!has(key) || isNull(key)) return null
    val value = opt(key) ?: return null
    val parsed = when (value) {
        is Number -> value.toLong()
        is String -> value.toLongOrNull()
        else -> null
    }
    return parsed?.takeIf { it >= 0L }
}

private fun JSONObject.profileFields(): List<ProfileField> = optJSONArray("profileFields")?.let { fields ->
    (0 until fields.length()).mapNotNull { index ->
        fields.optJSONObject(index)?.let {
            ProfileField(it.optString("name"), it.optString("value"))
        }?.takeIf { it.name.isNotBlank() || it.value.isNotBlank() }
    }.take(4)
}.orEmpty()

private fun JSONObject.toPendingLogin(): PendingLogin = PendingLogin(
    origin = getString("origin"),
    id = getString("id"),
    createdAt = getLong("createdAt"),
    protocol = optString("protocol", Protocol.MISSKEY.name).let(Protocol::valueOf),
    clientId = nullableString("clientId"),
    clientSecret = nullableString("clientSecret"),
    codeVerifier = nullableString("codeVerifier"),
    codeChallenge = nullableString("codeChallenge"),
    scope = optString("scope", "read"),
    authorizationCode = nullableString("authorizationCode"),
    requestedAccess = enumSet("requestedAccess"),
    replacingAccountId = optJSONObject("replacingAccountId")?.toAccountId()
        ?: runCatching {
            val origin = nullableString("replacingAccountOrigin") ?: return@runCatching null
            val localId = nullableString("replacingAccountLocalId") ?: return@runCatching null
            val protocol = nullableString("replacingAccountProtocol")?.let(Protocol::valueOf)
                ?: return@runCatching null
            AccountId(Connection(origin, protocol), localId)
        }.getOrNull(),
)

private inline fun <reified T : Enum<T>> JSONObject.enumSet(key: String): Set<T> {
    val values = mutableSetOf<T>()
    val names = optJSONArray(key) ?: return values
    for (index in 0 until names.length()) {
        runCatching { values += enumValueOf<T>(names.getString(index)) }
    }
    return values
}
