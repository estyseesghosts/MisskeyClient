package me.foxtails.palustris.data.auth

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import me.foxtails.palustris.data.misskey.MisskeyMapper
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** These values never enter Compose state, saved-instance bundles, logs, or backups. */
class LoginSession(val origin: String, val token: String, val user: JSONObject) {
    val account get() = MisskeyMapper.account(user, origin)
}
data class PendingLogin(val origin: String, val id: String, val createdAt: Long) {
    fun isFresh(now: Long) = now - createdAt in 0..(15 * 60 * 1000L)
}
class StoredLogin(val session: LoginSession? = null, val pending: PendingLogin? = null)
interface SessionStore {
    fun read(): StoredLogin
    fun write(value: StoredLogin)
    fun clear()
}

/** AES-GCM with a non-exportable Android Keystore key; ciphertext is in noBackupFilesDir. */
class EncryptedSessionStore(context: Context) : SessionStore {
    private val file = AtomicFile(File(context.noBackupFilesDir, "session.enc"))
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey("palustris.session", null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("palustris.session", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    override fun read(): StoredLogin {
        if (!file.baseFile.exists()) return StoredLogin()
        val bytes = file.readFully()
        require(bytes.size > 28)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        val json = JSONObject(String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8))
        val session = json.optJSONObject("session")?.let {
            LoginSession(it.getString("origin"), it.getString("token"), it.getJSONObject("user"))
        }
        val pending = json.optJSONObject("pending")?.let {
            PendingLogin(it.getString("origin"), it.getString("id"), it.getLong("createdAt"))
        }
        return StoredLogin(session, pending)
    }
    override fun write(value: StoredLogin) {
        val json = JSONObject()
        value.session?.let { json.put("session", JSONObject().put("origin", it.origin).put("token", it.token).put("user", it.user)) }
        value.pending?.let { json.put("pending", JSONObject().put("origin", it.origin).put("id", it.id).put("createdAt", it.createdAt)) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val bytes = cipher.iv + cipher.doFinal(json.toString().toByteArray(Charsets.UTF_8))
        val stream = file.startWrite()
        try { stream.write(bytes); file.finishWrite(stream) } catch (e: Exception) { file.failWrite(stream); throw e }
    }
    override fun clear() { file.delete() }
}
