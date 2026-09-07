package me.foxtails.palustris.data.auth

import android.content.Context
import android.util.AtomicFile
import me.foxtails.palustris.data.misskey.MisskeyMapper
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.Protocol
import me.foxtails.palustris.domain.ServerCapabilities
import me.foxtails.palustris.domain.Session
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
                val session = Session(login.account.id, login.token, ServerCapabilities())
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

    override fun read(accountId: AccountId): Session? {
        migrateFromLegacy()
        return accountFiles.read(accountId)
    }

    override fun write(accountId: AccountId, session: Session) {
        migrateFromLegacy()
        accountFiles.write(accountId, session)
    }

    override fun writeProfile(accountId: AccountId, profile: JSONObject) {
        migrateFromLegacy()
        accountFiles.writeProfile(accountId, profile)
    }

    override fun delete(accountId: AccountId) {
        migrateFromLegacy()
        accountFiles.delete(accountId)
    }

    override fun readIndex(): AccountIndex {
        migrateFromLegacy()
        return readIndexInternal()
    }

    override fun writeIndex(index: AccountIndex) {
        migrateFromLegacy()
        writeIndexInternal(index)
    }

    override fun readPending(): PendingLogin? {
        migrateFromLegacy()
        if (!pendingFile.exists()) return null
        val json = accountFiles.readJson(pendingFile)
        return json.toPendingLogin()
    }

    override fun writePending(pending: PendingLogin) {
        migrateFromLegacy()
        writePendingInternal(pending)
    }

    override fun clearPending() {
        migrateFromLegacy()
        AtomicFile(pendingFile).delete()
    }

    override fun clear() {
        accountFiles.clear()
        legacyFile.delete()
        migrationComplete = true
    }

    private fun readIndexInternal(): AccountIndex {
        if (!indexFile.baseFile.exists()) return AccountIndex()
        val json = JSONObject(String(indexFile.readFully(), Charsets.UTF_8))
        val accounts = json.optJSONArray("accounts")?.let { entries ->
            (0 until entries.length()).map { index ->
                entries.getJSONObject(index).let {
                    AccountRef(it.getJSONObject("accountId").toAccountId(), it.getString("handle"),
                        it.nullableString("avatarUrl"), it.getString("displayName"), it.getString("protocol").let(Protocol::valueOf))
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
        accountFiles.writeJson(pendingFile, JSONObject()
            .put("origin", pending.origin).put("id", pending.id).put("createdAt", pending.createdAt)
            .put("protocol", pending.protocol.name).put("clientId", pending.clientId)
            .put("clientSecret", pending.clientSecret).put("codeVerifier", pending.codeVerifier)
            .put("codeChallenge", pending.codeChallenge).put("scope", pending.scope)
            .put("authorizationCode", pending.authorizationCode))
    }
}

private fun AccountIndex.withAccount(account: Account): AccountIndex {
    val ref = AccountRef(account.id, account.handle, account.avatarUrl, account.displayName)
    return copy(accounts = accounts.filterNot { it.accountId == account.id } + ref)
}

private fun JSONObject.nullableString(key: String): String? = if (isNull(key)) null else optString(key).takeIf { it.isNotEmpty() }

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
)
