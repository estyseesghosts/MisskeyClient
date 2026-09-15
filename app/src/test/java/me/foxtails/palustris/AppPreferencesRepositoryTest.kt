package me.foxtails.palustris

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import me.foxtails.palustris.data.preferences.FileAppPreferencesRepository
import me.foxtails.palustris.domain.AppColorScheme
import me.foxtails.palustris.domain.AppLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AppPreferencesRepositoryTest {
    private lateinit var file: File

    @Before
    fun clearPreferencesFile() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        file = File(context.noBackupFilesDir, "app-preferences.json")
        file.delete()
        File("${file.path}.new").delete()
    }

    @Test
    fun malformedFileKeepsTheLoadErrorWithSafeDefaults() = runBlocking {
        file.parentFile?.mkdirs()
        file.writeText("not json")

        val state = FileAppPreferencesRepository(ApplicationProvider.getApplicationContext())
            .observe()
            .first { it.loaded }

        assertEquals(AppColorScheme.System, state.preferences.colorScheme)
        assertNotNull(state.error)
    }

    @Test
    fun everySelectionPersistsAndRestoresByTag() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        AppLanguage.entries.forEach { language ->
            val repository = FileAppPreferencesRepository(context)
            repository.observe().first { it.loaded }
            repository.update { it.copy(language = language) }

            val restored = FileAppPreferencesRepository(context).observe().first { it.loaded }
            assertEquals(language, restored.preferences.language)
            assertEquals(language.tag, restored.preferences.language.tag)
        }
    }

    @Test
    fun unknownStoredLanguageFallsBackToSystemDefault() = runBlocking {
        file.parentFile?.mkdirs()
        file.writeText("""{"language":"Klingon"}""")

        val state = FileAppPreferencesRepository(ApplicationProvider.getApplicationContext())
            .observe()
            .first { it.loaded }

        assertEquals(AppLanguage.SystemDefault, state.preferences.language)
    }

    @Test
    fun successfulUpdateClearsTheLoadError() = runBlocking {
        file.parentFile?.mkdirs()
        file.writeText("not json")
        val repository = FileAppPreferencesRepository(ApplicationProvider.getApplicationContext())
        repository.observe().first { it.loaded }

        repository.update { it.copy(colorScheme = AppColorScheme.SystemMonochrome) }

        val state = repository.observe().first { it.preferences.colorScheme == AppColorScheme.SystemMonochrome }
        assertEquals(null, state.error)
    }
}
