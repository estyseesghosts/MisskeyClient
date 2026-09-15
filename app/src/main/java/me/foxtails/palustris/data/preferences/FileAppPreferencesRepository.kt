package me.foxtails.palustris.data.preferences

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import me.foxtails.palustris.domain.AppBackground
import me.foxtails.palustris.domain.AppColorPalette
import me.foxtails.palustris.domain.AppColorScheme
import me.foxtails.palustris.domain.AppFont
import me.foxtails.palustris.domain.AppLanguage
import me.foxtails.palustris.domain.AppPreferences
import me.foxtails.palustris.domain.AppPreferencesRepository
import me.foxtails.palustris.domain.AppPreferencesState
import me.foxtails.palustris.domain.AppTextSize
import me.foxtails.palustris.domain.ContentWarningRules
import me.foxtails.palustris.domain.HiddenContentPresentation
import me.foxtails.palustris.di.IoDispatcher
import org.json.JSONArray
import org.json.JSONObject

/** Versioned, non-secret global preferences stored outside device backup. */
class FileAppPreferencesRepository(
    context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : AppPreferencesRepository {
    private val file = File(context.noBackupFilesDir, "app-preferences.json")
    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)
    private val mutex = Mutex()
    private val ready = CompletableDeferred<Unit>()
    private val values = MutableStateFlow(AppPreferencesState())

    init {
        scope.launch {
            val loaded = runCatching { read() }
            values.value = loaded.fold(
                onSuccess = { AppPreferencesState(loaded = true, preferences = it) },
                onFailure = { error ->
                    AppPreferencesState(
                        loaded = true,
                        preferences = AppPreferences(),
                        error = error.message ?: "Application preferences could not be loaded.",
                    )
                },
            )
            ready.complete(Unit)
        }
    }

    override fun observe(): Flow<AppPreferencesState> = values.asStateFlow()

    override suspend fun update(transform: (AppPreferences) -> AppPreferences) {
        ready.await()
        mutex.withLock {
            val current = values.value.preferences
            val next = transform(current)
            try {
                withContext(ioDispatcher) { persist(next) }
                values.value = AppPreferencesState(loaded = true, preferences = next)
            } catch (error: Throwable) {
                // Cancellation asks the write to stop; it is not a save failure.
                if (error is kotlinx.coroutines.CancellationException) throw error
                values.value = values.value.copy(
                    loaded = true,
                    error = error.message ?: "Application preferences could not be saved.",
                )
                throw error
            }
        }
    }

    private fun read(): AppPreferences {
        if (!file.exists()) return AppPreferences()
        val json = JSONObject(file.readText(Charsets.UTF_8))
        val warning = json.optJSONObject("contentWarningRules")
        val storedColorScheme = json.optString("colorScheme")
        return AppPreferences(
            colorScheme = when (storedColorScheme) {
                "SystemMonochrome", "Monochrome" -> AppColorScheme.SystemMonochrome
                "Palette", "Pastel", "Vibrant" -> AppColorScheme.Palette
                else -> AppColorScheme.System
            },
            colorPalette = when {
                json.has("colorPalette") -> enumOrDefault(json, "colorPalette", AppColorPalette.PastelIndigo)
                storedColorScheme == "Vibrant" -> AppColorPalette.VibrantBlue
                else -> AppColorPalette.PastelIndigo
            },
            background = enumOrDefault(json, "background", AppBackground.Default),
            textSize = enumOrDefault(json, "textSize", AppTextSize.Device),
            font = enumOrDefault(json, "font", AppFont.Device),
            request60Hz = json.optBoolean("request60Hz", false),
            language = AppLanguage.fromNameOrDefault(json.optString("language")),
            cleanTrackingParameters = json.optBoolean("cleanTrackingParameters", false),
            contentWarningRules = warning?.toContentWarningRules() ?: ContentWarningRules(),
            hiddenContentPresentation = enumOrDefault(json, "hiddenContentPresentation", HiddenContentPresentation.Placeholder),
        )
    }

    private fun persist(preferences: AppPreferences) {
        file.parentFile?.mkdirs()
        val json = JSONObject()
            .put("version", VERSION)
            .put("colorScheme", preferences.colorScheme.name)
            .put("colorPalette", preferences.colorPalette.name)
            .put("background", preferences.background.name)
            .put("textSize", preferences.textSize.name)
            .put("font", preferences.font.name)
            .put("request60Hz", preferences.request60Hz)
            .put("language", preferences.language.name)
            .put("cleanTrackingParameters", preferences.cleanTrackingParameters)
            .put("contentWarningRules", preferences.contentWarningRules.toJson())
            .put("hiddenContentPresentation", preferences.hiddenContentPresentation.name)
        val temporary = File("${file.path}.new")
        FileOutputStream(temporary).use { stream ->
            stream.write(json.toString().toByteArray(Charsets.UTF_8))
            stream.fd.sync()
        }
        try {
            Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (_: Exception) {
            Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private companion object {
        const val VERSION = 1
    }
}

/** Deterministic repository for previews and constructor-based tests. */
class InMemoryAppPreferencesRepository(
    initial: AppPreferences = AppPreferences(),
) : AppPreferencesRepository {
    private val values = MutableStateFlow(AppPreferencesState(loaded = true, preferences = initial))

    override fun observe(): Flow<AppPreferencesState> = values.asStateFlow()

    override suspend fun update(transform: (AppPreferences) -> AppPreferences) {
        values.value = values.value.copy(preferences = transform(values.value.preferences), error = null)
    }
}

private inline fun <reified T : Enum<T>> enumOrDefault(json: JSONObject, key: String, default: T): T =
    runCatching { enumValueOf<T>(json.optString(key)) }.getOrDefault(default)

private fun ContentWarningRules.toJson(): JSONObject = JSONObject()
    .put("hideAll", hideAll)
    .put("expandAll", expandAll)
    .put("hideKeywords", JSONArray(hideKeywords))
    .put("hideHashtags", JSONArray(hideHashtags))
    .put("expandKeywords", JSONArray(expandKeywords))
    .put("expandHashtags", JSONArray(expandHashtags))

private fun JSONObject.toContentWarningRules(): ContentWarningRules = ContentWarningRules(
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
