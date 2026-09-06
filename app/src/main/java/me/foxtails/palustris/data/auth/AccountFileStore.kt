package me.foxtails.palustris.data.auth

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.Audience
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.PostAction
import me.foxtails.palustris.domain.Protocol
import me.foxtails.palustris.domain.ServerCapabilities
import me.foxtails.palustris.domain.Session
import me.foxtails.palustris.domain.Timeline
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Stores one encrypted session file for each account. */
class AccountFileStore(context: Context) {
    private val accountsDirectory = File(context.noBackupFilesDir, "accounts")

    fun read(accountId: AccountId): Session? {
        val file = fileFor(accountId)
        if (!file.exists()) return null
        val json = readJson(file)
        val storedConnection = Connection(json.getString("origin"), Protocol.valueOf(json.getString("protocol")))
        return Session(
            AccountId(storedConnection, json.getString("localId")),
            json.getString("token"),
            json.optJSONObject("capabilities")?.toCapabilities() ?: ServerCapabilities(),
        )
    }

    fun write(accountId: AccountId, session: Session, profile: JSONObject = JSONObject()) {
        require(accountId == session.accountId) { "Session account does not match the requested file." }
        writeJson(fileFor(accountId), JSONObject()
            .put("origin", accountId.connection.origin)
            .put("protocol", accountId.connection.protocol.name)
            .put("localId", accountId.localId)
            .put("token", session.token)
            .put("capabilities", session.capabilities.toJson())
            .put("profile", JSONObject(profile.toString())))
    }

    fun writeProfile(accountId: AccountId, profile: JSONObject) {
        val file = fileFor(accountId)
        val json = readJson(file)
        json.put("profile", JSONObject(profile.toString()))
        writeJson(file, json)
    }

    fun delete(accountId: AccountId) {
        AtomicFile(fileFor(accountId)).delete()
    }

    fun clear() {
        accountsDirectory.deleteRecursively()
    }

    internal fun readJson(file: File): JSONObject {
        val bytes = AtomicFile(file).readFully()
        require(bytes.size > 28) { "Encrypted session file is invalid." }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        return JSONObject(String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8))
    }

    internal fun writeJson(file: File, json: JSONObject) {
        file.parentFile?.mkdirs()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val bytes = cipher.iv + cipher.doFinal(json.toString().toByteArray(Charsets.UTF_8))
        val atomicFile = AtomicFile(file)
        val stream = atomicFile.startWrite()
        try {
            stream.write(bytes)
            atomicFile.finishWrite(stream)
        } catch (e: Exception) {
            atomicFile.failWrite(stream)
            throw e
        }
    }

    private fun fileFor(accountId: AccountId): File {
        val value = "${accountId.connection.origin}\u0000${accountId.localId}"
        val filename = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(value.toByteArray(Charsets.UTF_8))
        return File(accountsDirectory, "$filename.enc")
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey("palustris.session", null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("palustris.session", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build())
        }.generateKey()
    }
}

private fun ServerCapabilities.toJson(): JSONObject = JSONObject()
    .put("timelines", JSONArray(timelines.map { it.name }))
    .put("audiences", JSONArray(audiences.map { it.name }))
    .put("actions", JSONArray(actions.map { it.name }))
    .put("maxPostLength", maxPostLength)
    .put("canPublish", canPublish)
    .put("capabilitiesLastUpdated", capabilitiesLastUpdated)

private fun JSONObject.toCapabilities(): ServerCapabilities = ServerCapabilities(
    timelines = enumSet<Timeline>("timelines"),
    audiences = enumSet<Audience>("audiences"),
    actions = enumSet<PostAction>("actions"),
    maxPostLength = if (isNull("maxPostLength")) null else optInt("maxPostLength"),
    canPublish = optBoolean("canPublish"),
    capabilitiesLastUpdated = optLong("capabilitiesLastUpdated"),
)

private inline fun <reified T : Enum<T>> JSONObject.enumSet(key: String): Set<T> {
    val values = mutableSetOf<T>()
    val names = optJSONArray(key) ?: return values
    for (index in 0 until names.length()) {
        runCatching { values += enumValueOf<T>(names.getString(index)) }
    }
    return values
}
