package me.foxtails.palustris

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import me.foxtails.palustris.data.preferences.FileAppPreferencesRepository
import me.foxtails.palustris.domain.AppColorScheme
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
