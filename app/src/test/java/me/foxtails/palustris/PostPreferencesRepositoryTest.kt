package me.foxtails.palustris

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import me.foxtails.palustris.data.preferences.EncryptedPostPreferencesRepository
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.DEFAULT_FAVOURITE_EMOJI
import me.foxtails.palustris.domain.PostPreferences
import me.foxtails.palustris.domain.Protocol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PostPreferencesRepositoryTest {
    private lateinit var file: File
    private val first = AccountId(Connection("https://one.example", Protocol.MISSKEY), "first")
    private val second = AccountId(Connection("https://two.example", Protocol.MISSKEY), "second")

    @Before
    fun clearPreferencesFile() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        file = File(context.noBackupFilesDir, "post-preferences.json")
        file.delete()
        File("${file.path}.new").delete()
        File("${file.path}.bak").delete()
    }

    @Test
    fun valuesAreAccountScopedAndSurviveReload() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repository = EncryptedPostPreferencesRepository(context)
        assertEquals(DEFAULT_FAVOURITE_EMOJI, repository.observe(first).first().favouriteEmoji)

        repository.update(first) { PostPreferences(":blobcat:") }
        repository.update(second) { PostPreferences("🎉") }
        assertEquals(":blobcat:", repository.observe(first).first().favouriteEmoji)
        assertEquals("🎉", repository.observe(second).first().favouriteEmoji)
        assertTrue(file.exists())
        assertFalse(file.readText().contains("token", ignoreCase = true))

        val reloaded = EncryptedPostPreferencesRepository(context)
        assertEquals(":blobcat:", reloaded.observe(first).first().favouriteEmoji)
        assertEquals("🎉", reloaded.observe(second).first().favouriteEmoji)
    }

    @Test
    fun invalidValuesFallBackAndRemovalDoesNotAffectOtherAccounts() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repository = EncryptedPostPreferencesRepository(context)
        repository.update(first) { PostPreferences("\u0000invalid") }
        repository.update(second) { PostPreferences("👍") }

        assertEquals(DEFAULT_FAVOURITE_EMOJI, repository.observe(first).first().favouriteEmoji)
        repository.remove(first)
        assertEquals(DEFAULT_FAVOURITE_EMOJI, repository.observe(first).first().favouriteEmoji)
        assertEquals("👍", repository.observe(second).first().favouriteEmoji)
        assertNotEquals(first, second)
    }
}
