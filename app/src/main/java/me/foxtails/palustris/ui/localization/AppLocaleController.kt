package me.foxtails.palustris.ui.localization

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import java.util.Locale
import org.json.JSONObject
import me.foxtails.palustris.domain.AppLanguage

/** Platform bridge for the global language preference. The repository remains the source of truth. */
object AppLocaleController {
    fun persistedLanguage(context: Context): AppLanguage = runCatching {
        val file = java.io.File(context.noBackupFilesDir, "app-preferences.json")
        val name = JSONObject(file.readText(Charsets.UTF_8)).optString("language")
        AppLanguage.entries.firstOrNull { it.name == name } ?: AppLanguage.SystemDefault
    }.getOrDefault(AppLanguage.SystemDefault)

    fun localizedContext(context: Context, language: AppLanguage): Context {
        val tag = language.tag ?: return context
        val configuration = Configuration(context.resources.configuration)
        configuration.setLocale(Locale.forLanguageTag(tag))
        return context.createConfigurationContext(configuration)
    }

    fun applyPlatformLocale(context: Context, language: AppLanguage) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val locales = language.tag?.let { LocaleList(Locale.forLanguageTag(it)) } ?: LocaleList.getEmptyLocaleList()
        context.getSystemService(android.app.LocaleManager::class.java).applicationLocales = locales
    }
}
