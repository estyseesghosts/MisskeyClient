package me.foxtails.palustris.data.preferences

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Base64
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import me.foxtails.palustris.di.IoDispatcher
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.Audience
import me.foxtails.palustris.domain.PostPreferences
import me.foxtails.palustris.domain.PostPreferencesRepository
import me.foxtails.palustris.domain.normalizeFavouriteEmoji
import me.foxtails.palustris.domain.normalizeLocalMutedHashtags
import org.json.JSONObject

/** Stores non-secret per-account post preferences in no-backup storage. */
class FilePostPreferencesRepository(
    context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : PostPreferencesRepository {
    private val file = File(context.noBackupFilesDir, "post-preferences.json")
    private val mutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)
    private val ready = kotlinx.coroutines.CompletableDeferred<Unit>()
    private val values = MutableStateFlow<Map<String, PostPreferences>>(emptyMap())

    init {
        scope.launch {
            mutex.withLock { values.value = withContext(ioDispatcher) { load() } }
            ready.complete(Unit)
        }
    }

    override fun observe(accountId: AccountId): Flow<PostPreferences> = flow {
        ready.await()
        emitAll(values.map { it[keyFor(accountId)] ?: PostPreferences() }.distinctUntilChanged())
    }

    override suspend fun update(accountId: AccountId, transform: (PostPreferences) -> PostPreferences) {
        ready.await()
        mutex.withLock {
            val key = keyFor(accountId)
            val next = normalizePostPreferences(transform(values.value[key] ?: PostPreferences()))
            val updated = values.value.toMutableMap().apply { put(key, next) }.toMap()
            withContext(ioDispatcher) { persist(updated) }
            values.value = updated
        }
    }

    override suspend fun remove(accountId: AccountId) {
        ready.await()
        mutex.withLock {
            val key = keyFor(accountId)
            if (key !in values.value) return
            val updated = values.value.toMutableMap().apply { remove(key) }.toMap()
            withContext(ioDispatcher) { persist(updated) }
            values.value = updated
        }
    }

    private fun load(): Map<String, PostPreferences> = runCatching {
        if (!file.exists()) return emptyMap()
        val root = JSONObject(file.readText(Charsets.UTF_8))
        val accounts = root.optJSONObject("accounts") ?: return emptyMap()
        accounts.keys().asSequence().mapNotNull { key ->
            val value = accounts.optJSONObject(key) ?: return@mapNotNull null
            key to PostPreferences(
                favouriteEmoji = normalizeFavouriteEmoji(value.optString("favouriteEmoji")),
                defaultAudience = enumOrDefault(value, "defaultAudience", Audience.Public),
                repliesUnlisted = value.optBoolean("repliesUnlisted", false),
                 contentWarningRules = value.optJSONObject("contentWarningRules")?.toContentWarningRules()
                     ?: me.foxtails.palustris.domain.ContentWarningRules(),
                 localMutedHashtags = value.optJSONArray("localMutedHashtags")?.let { hashtags ->
                     (0 until hashtags.length()).mapNotNull { hashtags.optString(it).takeIf(String::isNotBlank) }
                 }.orEmpty().let(::normalizeLocalMutedHashtags),
            )
        }.toMap()
    }.getOrDefault(emptyMap())

    private fun persist(values: Map<String, PostPreferences>) {
        file.parentFile?.mkdirs()
        val accounts = JSONObject()
        values.forEach { (key, preference) ->
            accounts.put(key, JSONObject()
                .put("favouriteEmoji", preference.favouriteEmoji)
                .put("defaultAudience", preference.defaultAudience.name)
                 .put("repliesUnlisted", preference.repliesUnlisted)
                 .put("contentWarningRules", preference.contentWarningRules.toJson())
                 .put("localMutedHashtags", org.json.JSONArray(preference.localMutedHashtags)))
        }
        val root = JSONObject().put("version", 1).put("accounts", accounts)
        val temporary = File("${file.path}.new")
        val stream: FileOutputStream = FileOutputStream(temporary)
        try {
            stream.write(root.toString().toByteArray(Charsets.UTF_8))
            stream.fd.sync()
            stream.close()
            runCatching {
                Files.move(
                    temporary.toPath(),
                    file.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING,
                )
            }.getOrElse {
                Files.move(
                    temporary.toPath(),
                    file.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                )
            }
        } catch (error: Exception) {
            runCatching { stream.close() }
            temporary.delete()
            throw error
        }
    }

    private fun keyFor(accountId: AccountId): String {
        val identity = buildString {
            append(accountId.connection.origin)
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
            values.value = values.value + (accountId to normalizePostPreferences(transform(current)))
    }

    override suspend fun remove(accountId: AccountId) {
        values.value = values.value - accountId
    }
}

private fun normalizePostPreferences(preferences: PostPreferences): PostPreferences = preferences.copy(
    favouriteEmoji = normalizeFavouriteEmoji(preferences.favouriteEmoji),
    contentWarningRules = preferences.contentWarningRules.normalized(),
    localMutedHashtags = normalizeLocalMutedHashtags(preferences.localMutedHashtags),
)

private fun me.foxtails.palustris.domain.ContentWarningRules.toJson(): JSONObject = JSONObject()
    .put("hideAll", hideAll)
    .put("expandAll", expandAll)
    .put("hideKeywords", org.json.JSONArray(hideKeywords))
    .put("hideHashtags", org.json.JSONArray(hideHashtags))
    .put("expandKeywords", org.json.JSONArray(expandKeywords))
    .put("expandHashtags", org.json.JSONArray(expandHashtags))

private fun JSONObject.toContentWarningRules(): me.foxtails.palustris.domain.ContentWarningRules =
    me.foxtails.palustris.domain.ContentWarningRules(
        hideAll = optBoolean("hideAll"),
        expandAll = optBoolean("expandAll"),
        hideKeywords = stringList("hideKeywords"),
        hideHashtags = stringList("hideHashtags"),
        expandKeywords = stringList("expandKeywords"),
        expandHashtags = stringList("expandHashtags"),
    )

private fun JSONObject.stringList(key: String): List<String> = optJSONArray(key)?.let { values ->
    (0 until values.length()).mapNotNull { values.optString(it).trim().takeIf(String::isNotEmpty) }
}.orEmpty()

private inline fun <reified T : Enum<T>> enumOrDefault(json: JSONObject, key: String, default: T): T =
    runCatching { enumValueOf<T>(json.optString(key)) }.getOrDefault(default)
