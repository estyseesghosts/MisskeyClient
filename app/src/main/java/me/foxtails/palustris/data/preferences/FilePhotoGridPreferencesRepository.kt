package me.foxtails.palustris.data.preferences

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.PhotoGridPreferences
import me.foxtails.palustris.domain.PhotoGridPreferencesRepository
import me.foxtails.palustris.domain.hashtagIdentity
import me.foxtails.palustris.domain.validateExactHashtag
import org.json.JSONArray
import org.json.JSONObject

/** Stores validated, non-secret Photo Grid preferences in no-backup storage. */
@Singleton
class FilePhotoGridPreferencesRepository @Inject constructor(
    @ApplicationContext context: Context,
) : PhotoGridPreferencesRepository {
    private val file = File(context.noBackupFilesDir, "photo-grid-preferences.json")
    private val mutex = Mutex()
    private val values = MutableStateFlow<Map<String, PhotoGridPreferences>>(emptyMap())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var loaded = false

    init {
        scope.launch { mutex.withLock { loadLocked() } }
    }

    override fun observe(accountId: AccountId): Flow<PhotoGridPreferences> = values
        .map { it[keyFor(accountId)] ?: PhotoGridPreferences() }
        .distinctUntilChanged()

    override suspend fun update(
        accountId: AccountId,
        transform: (PhotoGridPreferences) -> PhotoGridPreferences,
    ) = withContext(Dispatchers.IO) {
        mutex.withLock {
            loadLocked()
            val key = keyFor(accountId)
            val next = normalize(transform(values.value[key] ?: PhotoGridPreferences()))
            val updated = values.value.toMutableMap().apply { put(key, next) }.toMap()
            persist(updated)
            values.value = updated
        }
    }

    override suspend fun remove(accountId: AccountId) = withContext(Dispatchers.IO) {
        mutex.withLock {
            loadLocked()
            val key = keyFor(accountId)
            if (key !in values.value) return@withLock
            val updated = values.value.toMutableMap().apply { remove(key) }.toMap()
            persist(updated)
            values.value = updated
        }
    }

    private fun loadLocked() {
        if (loaded) return
        val loadedValues = runCatching {
            if (!file.exists()) return@runCatching emptyMap()
            val accounts = JSONObject(file.readText(Charsets.UTF_8)).optJSONObject("accounts") ?: return@runCatching emptyMap()
            buildMap {
                accounts.keys().forEach { key ->
                    val tags = accounts.optJSONObject(key)?.optJSONArray("hashtags")?.let(::validHashtags).orEmpty()
                    if (tags.isNotEmpty()) put(key, PhotoGridPreferences(tags))
                }
            }
        }.getOrDefault(emptyMap())
        values.value = loadedValues
        loaded = true
    }

    private fun persist(values: Map<String, PhotoGridPreferences>) {
        file.parentFile?.mkdirs()
        val accounts = JSONObject()
        values.forEach { (key, preferences) ->
            accounts.put(key, JSONObject().put("hashtags", JSONArray(preferences.hashtags)))
        }
        val root = JSONObject().put("version", 1).put("accounts", accounts)
        val temporary = File("${file.path}.new")
        FileOutputStream(temporary).use { stream ->
            stream.write(root.toString().toByteArray(Charsets.UTF_8))
            stream.fd.sync()
        }
        try {
            Files.move(
                temporary.toPath(),
                file.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
            Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
        } finally {
            temporary.delete()
        }
    }

    private fun keyFor(accountId: AccountId): String = Base64.getUrlEncoder().withoutPadding().encodeToString(
        "${accountId.connection.origin}\u0000${accountId.connection.protocol.name}\u0000${accountId.localId}"
            .toByteArray(Charsets.UTF_8),
    )
}

private fun normalize(preferences: PhotoGridPreferences): PhotoGridPreferences = PhotoGridPreferences(
    preferences.hashtags.mapNotNull { runCatching { validateExactHashtag(it) }.getOrNull() }
        .distinctBy(::hashtagIdentity),
)

private fun validHashtags(values: JSONArray): List<String> = (0 until values.length())
    .mapNotNull { index -> values.optString(index).takeIf(String::isNotBlank) }
    .mapNotNull { runCatching { validateExactHashtag(it) }.getOrNull() }
    .distinctBy(::hashtagIdentity)

class InMemoryPhotoGridPreferencesRepository : PhotoGridPreferencesRepository {
    private val values = MutableStateFlow<Map<AccountId, PhotoGridPreferences>>(emptyMap())
    private val mutex = Mutex()

    override fun observe(accountId: AccountId): Flow<PhotoGridPreferences> = values
        .map { it[accountId] ?: PhotoGridPreferences() }
        .distinctUntilChanged()

    override suspend fun update(
        accountId: AccountId,
        transform: (PhotoGridPreferences) -> PhotoGridPreferences,
    ) = mutex.withLock {
        val next = normalize(transform(values.value[accountId] ?: PhotoGridPreferences()))
        values.value = values.value + (accountId to next)
    }

    override suspend fun remove(accountId: AccountId) = mutex.withLock {
        values.value = values.value - accountId
    }
}
