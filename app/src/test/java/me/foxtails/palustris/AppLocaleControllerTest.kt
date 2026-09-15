package me.foxtails.palustris

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import me.foxtails.palustris.domain.AppLanguage
import me.foxtails.palustris.ui.localization.AppLocaleController
import me.foxtails.palustris.ui.localization.AppLocaleController.PlatformReconciliation.ExportToPlatform
import me.foxtails.palustris.ui.localization.AppLocaleController.PlatformReconciliation.ImportToRepository
import me.foxtails.palustris.ui.localization.AppLocaleController.PlatformReconciliation.NoOp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.w3c.dom.Element

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AppLocaleControllerTest {
    private lateinit var context: Context
    private lateinit var file: File

    @Before
    fun clearPreferencesFile() {
        context = ApplicationProvider.getApplicationContext()
        file = File(context.noBackupFilesDir, "app-preferences.json")
        file.delete()
    }

    @Test
    fun persistedLanguageBridgesStoredNames() {
        file.parentFile?.mkdirs()
        file.writeText("""{"language":"SpanishLatinAmerica"}""")
        assertEquals(AppLanguage.SpanishLatinAmerica, AppLocaleController.persistedLanguage(context))

        file.writeText("""{"language":"CantoneseHongKong"}""")
        assertEquals(AppLanguage.CantoneseHongKong, AppLocaleController.persistedLanguage(context))

        file.writeText("""{"language":"Spanish"}""")
        assertEquals(AppLanguage.Spanish, AppLocaleController.persistedLanguage(context))
    }

    @Test
    fun persistedLanguageFallsBackSafely() {
        assertEquals(AppLanguage.SystemDefault, AppLocaleController.persistedLanguage(context))

        file.parentFile?.mkdirs()
        file.writeText("""{"language":"Klingon"}""")
        assertEquals(AppLanguage.SystemDefault, AppLocaleController.persistedLanguage(context))
    }

    @Test
    fun tagLookupKeepsRegions() {
        val tags = mapOf(
            "en" to AppLanguage.English,
            "de" to AppLanguage.German,
            "es" to AppLanguage.Spanish,
            "es-ES" to AppLanguage.SpanishSpain,
            "es-419" to AppLanguage.SpanishLatinAmerica,
            "fr" to AppLanguage.French,
            "hi" to AppLanguage.Hindi,
            "ja" to AppLanguage.Japanese,
            "ko" to AppLanguage.Korean,
            "pt-BR" to AppLanguage.PortugueseBrazil,
            "pt-PT" to AppLanguage.PortuguesePortugal,
            "yue-HK" to AppLanguage.CantoneseHongKong,
            "zh" to AppLanguage.Chinese,
            "zh-CN" to AppLanguage.ChineseMainland,
            "zh-TW" to AppLanguage.ChineseTaiwan,
        )
        tags.forEach { (tag, language) ->
            assertEquals(tag, language.tag)
            assertEquals(language, AppLocaleController.languageForTag(tag))
        }
        assertEquals(AppLanguage.SystemDefault, AppLocaleController.languageForTag(null))
        assertEquals(AppLanguage.SystemDefault, AppLocaleController.languageForTag(""))
        assertEquals(AppLanguage.SystemDefault, AppLocaleController.languageForTag("klingon"))
    }

    @Test
    fun unloadedStateNeverApplies() {
        assertEquals(null, AppLocaleController.effectiveLanguageAfterLoad(false, AppLanguage.SpanishLatinAmerica))
        assertEquals(null, AppLocaleController.effectiveLanguageAfterLoad(false, AppLanguage.SystemDefault))
        assertEquals(
            AppLanguage.SpanishLatinAmerica,
            AppLocaleController.effectiveLanguageAfterLoad(true, AppLanguage.SpanishLatinAmerica),
        )
        assertEquals(
            AppLanguage.SystemDefault,
            AppLocaleController.effectiveLanguageAfterLoad(true, AppLanguage.SystemDefault),
        )
    }

    @Test
    fun emptyPlatformImportsTheLoadedPreference() {
        assertEquals(
            ExportToPlatform(AppLanguage.SpanishLatinAmerica),
            AppLocaleController.reconcilePlatformSelection(null, AppLanguage.SpanishLatinAmerica),
        )
        assertEquals(
            ExportToPlatform(AppLanguage.French),
            AppLocaleController.reconcilePlatformSelection("", AppLanguage.French),
        )
        assertEquals(
            NoOp,
            AppLocaleController.reconcilePlatformSelection(null, AppLanguage.SystemDefault),
        )
    }

    @Test
    fun explicitPlatformLocaleWinsFirstUpgrade() {
        assertEquals(
            ImportToRepository(AppLanguage.PortugueseBrazil),
            AppLocaleController.reconcilePlatformSelection("pt-BR", AppLanguage.English),
        )
        assertEquals(
            ImportToRepository(AppLanguage.PortugueseBrazil),
            AppLocaleController.reconcilePlatformSelection("pt-BR", AppLanguage.SystemDefault),
        )
        assertEquals(
            ImportToRepository(AppLanguage.SpanishLatinAmerica),
            AppLocaleController.reconcilePlatformSelection("es-419", AppLanguage.German),
        )
    }

    @Test
    fun matchingTagsConvergeWithoutLoops() {
        AppLanguage.entries.filter { it.tag != null }.forEach { language ->
            assertEquals(
                NoOp,
                AppLocaleController.reconcilePlatformSelection(language.tag, language),
            )
        }
    }

    @Test
    fun unknownPlatformTagFallsBackSafely() {
        assertEquals(
            ImportToRepository(AppLanguage.SystemDefault),
            AppLocaleController.reconcilePlatformSelection("klingon", AppLanguage.German),
        )
    }

    @Test
    @Config(sdk = [29])
    fun localizedContextAppliesRegionalLocalesBeforePlatformLocales() {
        val localized = AppLocaleController.localizedContext(context, AppLanguage.SpanishLatinAmerica)
        assertEquals("es-419", localized.resources.configuration.locales.get(0).toLanguageTag())

        val cantonese = AppLocaleController.localizedContext(context, AppLanguage.CantoneseHongKong)
        assertEquals("yue-HK", cantonese.resources.configuration.locales.get(0).toLanguageTag())
    }

    @Test
    fun systemDefaultKeepsTheBaseContext() {
        val localized = AppLocaleController.localizedContext(context, AppLanguage.SystemDefault)
        assertEquals(
            context.resources.configuration.locales.get(0),
            localized.resources.configuration.locales.get(0),
        )
    }

    @Test
    fun everyLocaleResolvesATranslatedValueOrFallback() {
        val resourceRoot = sequenceOf(File("app/src/main/res"), File("src/main/res")).first { it.isDirectory }
        val default = parseStrings(resourceRoot.resolve("values/strings.xml"))
        val directoryTags = mapOf(
            "values-de" to "de",
            "values-es" to "es",
            "values-es-rES" to "es-ES",
            "values-b+es+419" to "es-419",
            "values-fr" to "fr",
            "values-hi" to "hi",
            "values-ja" to "ja",
            "values-ko" to "ko",
            "values-pt-rBR" to "pt-BR",
            "values-pt-rPT" to "pt-PT",
            "values-yue-rHK" to "yue-HK",
            "values-zh" to "zh",
            "values-zh-rCN" to "zh-CN",
            "values-zh-rTW" to "zh-TW",
        )
        AppLanguage.entries.filter { it.tag != null }.forEach { language ->
            val localized = AppLocaleController.localizedContext(context, language)
            if (language.tag == "en") {
                val id = localized.resources.getIdentifier("nav_home", "string", context.packageName)
                assertNotEquals(0, id)
                assertEquals("Home", localized.resources.getString(id))
                return@forEach
            }
            val directory = directoryTags.entries.single { it.value == language.tag }.key
            val translated = parseStrings(resourceRoot.resolve("$directory/strings.xml"))
            val distinct = translated.entries.firstOrNull { (key, value) -> default[key] != value }
            assertTrue(
                "${language.tag} keeps no distinct translated value",
                distinct != null,
            )
            val name = distinct!!.key
            val id = localized.resources.getIdentifier(name, "string", context.packageName)
            assertNotEquals("$name is missing for ${language.tag}", 0, id)
            assertEquals(translated.getValue(name), localized.resources.getString(id))
        }
    }

    @Test
    fun partialCatalogsFallBackToDefault() {
        val localized = AppLocaleController.localizedContext(context, AppLanguage.CantoneseHongKong)
        val id = localized.resources.getIdentifier("nav_home", "string", context.packageName)
        assertNotEquals(0, id)
        assertEquals("Home", localized.resources.getString(id))
    }

    private fun parseStrings(file: File): Map<String, String> {
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val result = linkedMapOf<String, String>()
        val strings = document.getElementsByTagName("string")
        for (index in 0 until strings.length) {
            val element = strings.item(index) as Element
            result[element.getAttribute("name")] = element.textContent
        }
        return result
    }
}
