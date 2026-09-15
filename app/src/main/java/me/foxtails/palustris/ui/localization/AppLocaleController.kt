package me.foxtails.palustris.ui.localization

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import java.util.Locale
import org.json.JSONObject
import me.foxtails.palustris.domain.AppLanguage

/** Platform bridge for the global language preference. The repository remains the source of truth. */
object AppLocaleController {
    /** Read-only early bridge for base-context setup. Shares decoding with the repository. */
    fun persistedLanguage(context: Context): AppLanguage = runCatching {
        val file = java.io.File(context.noBackupFilesDir, "app-preferences.json")
        val name = JSONObject(file.readText(Charsets.UTF_8)).optString("language")
        AppLanguage.fromNameOrDefault(name)
    }.getOrDefault(AppLanguage.SystemDefault)

    /** Tag lookup for platform values. Unknown tags fall back safely. */
    fun languageForTag(tag: String?): AppLanguage =
        if (tag.isNullOrBlank()) AppLanguage.SystemDefault
        else AppLanguage.entries.firstOrNull { it.tag == tag } ?: AppLanguage.SystemDefault

    /**
     * Returns the stored language once loaded. Null while unloaded so callers never apply
     * an unloaded System default over a persisted explicit language.
     */
    fun effectiveLanguageAfterLoad(loaded: Boolean, stored: AppLanguage): AppLanguage? =
        if (loaded) stored else null

    fun localizedContext(context: Context, language: AppLanguage): Context {
        val tag = language.tag ?: return context
        val configuration = Configuration(context.resources.configuration)
        configuration.setLocale(Locale.forLanguageTag(tag))
        return context.createConfigurationContext(configuration)
    }

    fun applyPlatformLocale(context: Context, language: AppLanguage) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val locales = language.tag?.let { LocaleList(Locale.forLanguageTag(it)) } ?: LocaleList.getEmptyLocaleList()
        context.getSystemService(LocaleManager::class.java).applicationLocales = locales
    }

    /** First platform locale tag, or null when the override is empty or unavailable. */
    fun platformTag(context: Context): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return null
        val locales = context.getSystemService(LocaleManager::class.java)?.applicationLocales
            ?: return null
        if (locales.isEmpty) return null
        return locales.get(0)?.toLanguageTag()
    }

    sealed interface PlatformReconciliation {
        data object NoOp : PlatformReconciliation
        data class ImportToRepository(val language: AppLanguage) : PlatformReconciliation
        data class ExportToPlatform(val language: AppLanguage) : PlatformReconciliation
    }

    /**
     * Reconciles an external Android App Languages value with the loaded repository value.
     * An existing explicit platform locale wins on first upgrade; otherwise the loaded
     * preference is exported. Canonical tags decide; equal tags never act, so the two
     * owners converge without feedback loops.
     */
    fun reconcilePlatformSelection(
        platformTag: String?,
        repository: AppLanguage,
    ): PlatformReconciliation {
        if (platformTag.isNullOrBlank()) {
            return if (repository.tag != null) PlatformReconciliation.ExportToPlatform(repository)
            else PlatformReconciliation.NoOp
        }
        val platform = languageForTag(platformTag)
        return if (platform.tag == repository.tag) PlatformReconciliation.NoOp
        else PlatformReconciliation.ImportToRepository(platform)
    }
}
