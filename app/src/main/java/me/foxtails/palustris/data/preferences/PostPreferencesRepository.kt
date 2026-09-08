package me.foxtails.palustris.data.preferences

import android.content.Context
import android.util.AtomicFile
import java.io.File
import java.io.FileOutputStream
import java.util.Base64
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.DEFAULT_FAVOURITE_EMOJI
import me.foxtails.palustris.domain.PostPreferences
import me.foxtails.palustris.domain.PostPreferencesRepository
import me.foxtails.palustris.domain.normalizeFavouriteEmoji
import org.json.JSONObject

/** Stores non-secret per-account post preferences in no-backup storage. */
class EncryptedPostPreferencesRepository(context: Context) : PostPreferencesRepository {
    private val file = AtomicFile(File(context.noBackupFilesDir, "post-preferences.json"))
    private val mutex = Mutex()
    private val values = MutableStateFlow(load())

    override fun observe(accountId: AccountId): Flow<PostPreferences> = values
        .map { it[keyFor(accountId)] ?: PostPreferences() }
        .distinctUntilChanged()

    override suspend fun update(accountId: AccountId, transform: (PostPreferences) -> PostPreferences) {
        mutex.withLock {
            val key = keyFor(accountId)
            val next = normalize(transform(values.value[key] ?: PostPreferences()))
            val updated = values.value.toMutableMap().apply { put(key, next) }.toMap()
            persist(updated)
            values.value = updated
        }
    }

    override suspend fun remove(accountId: AccountId) {
        mutex.withLock {
            val key = keyFor(accountId)
            if (key !in values.value) return
            val updated = values.value.toMutableMap().apply { remove(key) }.toMap()
            persist(updated)
            values.value = updated
        }
    }

    private fun load(): Map<String, PostPreferences> = runCatching {
        if (!file.baseFile.exists()) return emptyMap()
        val root = JSONObject(file.readFully().toString(Charsets.UTF_8))
        val accounts = root.optJSONObject("accounts") ?: return emptyMap()
        accounts.keys().asSequence().mapNotNull { key ->
            val value = accounts.optJSONObject(key) ?: return@mapNotNull null
            key to PostPreferences(normalizeFavouriteEmoji(value.optString("favouriteEmoji")))
        }.toMap()
    }.getOrDefault(emptyMap())

    private fun persist(values: Map<String, PostPreferences>) {
        file.baseFile.parentFile?.mkdirs()
        val accounts = JSONObject()
        values.forEach { (key, preference) ->
            accounts.put(key, JSONObject().put("favouriteEmoji", preference.favouriteEmoji))
        }
        val root = JSONObject().put("version", 1).put("accounts", accounts)
        val stream: FileOutputStream = file.startWrite()
        try {
            stream.write(root.toString().toByteArray(Charsets.UTF_8))
            stream.fd.sync()
            file.finishWrite(stream)
        } catch (error: Exception) {
            file.failWrite(stream)
            throw error
        }
    }

    private fun normalize(preference: PostPreferences): PostPreferences =
        PostPreferences(normalizeFavouriteEmoji(preference.favouriteEmoji))

    private fun keyFor(accountId: AccountId): String {
        val identity = buildString {
            append(accountId.connection.origin)
            append('\u0000')
            append(accountId.connection.protocol.name)
            append('\u0000')
            append(accountId.localId)
        }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(identity.toByteArray(Charsets.UTF_8))
    }
}

/** Lightweight repository used by previews and constructor-based unit tests. */
class InMemoryPostPreferencesRepository : PostPreferencesRepository {
    private val values = MutableStateFlow<Map<AccountId, PostPreferences>>(emptyMap())
    override fun observe(accountId: AccountId): Flow<PostPreferences> = values
        .map { it[accountId] ?: PostPreferences() }
        .distinctUntilChanged()

    override suspend fun update(accountId: AccountId, transform: (PostPreferences) -> PostPreferences) {
        val current = values.value[accountId] ?: PostPreferences()
        values.value = values.value + (accountId to PostPreferences(normalizeFavouriteEmoji(transform(current).favouriteEmoji)))
    }

    override suspend fun remove(accountId: AccountId) {
        values.value = values.value - accountId
    }
}
