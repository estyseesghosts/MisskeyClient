package me.foxtails.palustris.data.preferences

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import dagger.hilt.android.qualifiers.ApplicationContext
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.EmojiPickerGroupIds
import me.foxtails.palustris.domain.EmojiPickerPreferences
import me.foxtails.palustris.domain.EmojiPickerPreferencesRepository
import org.json.JSONArray
import org.json.JSONObject

/** Stores non-secret account-scoped picker preferences in no-backup storage. */
@Singleton
class FileEmojiPickerPreferencesRepository @Inject constructor(
    @ApplicationContext context: Context,
) : EmojiPickerPreferencesRepository {
    private val file = File(context.noBackupFilesDir, "emoji-picker-preferences.json")
    private val mutex = Mutex()
    private val values = MutableStateFlow(load())

    override fun observe(accountId: AccountId): Flow<EmojiPickerPreferences> = values
        .map { it[keyFor(accountId)] ?: EmojiPickerPreferences() }
        .distinctUntilChanged()

    override suspend fun update(
        accountId: AccountId,
        transform: (EmojiPickerPreferences) -> EmojiPickerPreferences,
    ) {
        mutex.withLock {
            val key = keyFor(accountId)
            val next = normalize(transform(values.value[key] ?: EmojiPickerPreferences()))
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

    private fun load(): Map<String, EmojiPickerPreferences> = runCatching {
        if (!file.exists()) return emptyMap()
        val accounts = JSONObject(file.readText(Charsets.UTF_8)).optJSONObject("accounts") ?: return emptyMap()
        accounts.keys().asSequence().mapNotNull { key ->
            val value = accounts.optJSONObject(key) ?: return@mapNotNull null
            key to normalize(
                EmojiPickerPreferences(
                    collapsedGroups = value.optJSONArray("collapsedGroups").strings().toSet(),
                    pinnedGroups = value.optJSONArray("pinnedGroups").strings(),
                ),
            )
        }.toMap()
    }.getOrDefault(emptyMap())

    private fun persist(values: Map<String, EmojiPickerPreferences>) {
        file.parentFile?.mkdirs()
        val accounts = JSONObject()
        values.forEach { (key, preference) ->
            accounts.put(
                key,
                JSONObject()
                    .put("collapsedGroups", JSONArray(preference.collapsedGroups.toList()))
                    .put("pinnedGroups", JSONArray(preference.pinnedGroups)),
            )
        }
        val root = JSONObject().put("version", 1).put("accounts", accounts)
        val temporary = File("${file.path}.new")
        val stream = FileOutputStream(temporary)
        try {
            stream.use {
                it.write(root.toString().toByteArray(Charsets.UTF_8))
                it.fd.sync()
            }
            runCatching {
                Files.move(
                    temporary.toPath(),
                    file.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE,
                )
            }.getOrElse {
                Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            temporary.delete()
        }
    }

    private fun keyFor(accountId: AccountId): String = Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString("${accountId.connection.origin}\u0000${accountId.localId}".toByteArray(Charsets.UTF_8))
}

private const val MAX_PINS = 5

private fun normalize(preferences: EmojiPickerPreferences): EmojiPickerPreferences = EmojiPickerPreferences(
    collapsedGroups = preferences.collapsedGroups
        .filter(::isValidCollapsedGroup)
        .toSet(),
    pinnedGroups = preferences.pinnedGroups
        .asSequence()
        .filter(::isValidPinnedGroup)
        .distinct()
        .take(MAX_PINS)
        .toList(),
)

private fun isValidCollapsedGroup(value: String): Boolean = value.isNotBlank() &&
    value.none(Char::isISOControl) &&
    (value == EmojiPickerGroupIds.Favorite ||
        value == EmojiPickerGroupIds.Recent ||
        value == EmojiPickerGroupIds.Unicode ||
        value == EmojiPickerGroupIds.PostSpecific ||
        EmojiPickerGroupIds.isServer(value))

private fun isValidPinnedGroup(value: String): Boolean = value.isNotBlank() &&
    value.none(Char::isISOControl) && EmojiPickerGroupIds.isServer(value)

private fun JSONArray?.strings(): List<String> = if (this == null) emptyList() else {
    (0 until length()).mapNotNull { optString(it).takeIf(String::isNotBlank) }
}

class InMemoryEmojiPickerPreferencesRepository : EmojiPickerPreferencesRepository {
    private val values = MutableStateFlow<Map<AccountId, EmojiPickerPreferences>>(emptyMap())

    override fun observe(accountId: AccountId): Flow<EmojiPickerPreferences> = values
        .map { it[accountId] ?: EmojiPickerPreferences() }
        .distinctUntilChanged()

    override suspend fun update(
        accountId: AccountId,
        transform: (EmojiPickerPreferences) -> EmojiPickerPreferences,
    ) {
        val current = values.value[accountId] ?: EmojiPickerPreferences()
        val next = normalize(transform(current))
        values.value = values.value + (accountId to next)
    }

    override suspend fun remove(accountId: AccountId) {
        values.value = values.value - accountId
    }
}
