package me.foxtails.palustris.data.preferences

import androidx.test.core.app.ApplicationProvider
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import me.foxtails.palustris.data.preferences.FilePhotoGridPreferencesRepository
import me.foxtails.palustris.data.preferences.InMemoryPhotoGridPreferencesRepository
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.PhotoGridPreferences
import me.foxtails.palustris.domain.Protocol
import me.foxtails.palustris.domain.hashtagIdentity
import me.foxtails.palustris.domain.isExactHashtag
import me.foxtails.palustris.domain.validateExactHashtag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PhotoGridPreferencesRepositoryTest {
    @Test
    fun validatorAcceptsOptionalHashAndRejectsInvalidSyntax() {
        assertEquals("#Photography", validateExactHashtag("  #Photography  "))
        assertEquals("Photography", validateExactHashtag("Photography"))
        assertTrue(isExactHashtag("猫_2026"))
        assertFalse(isExactHashtag("#two words"))
        assertFalse(isExactHashtag("#"))
        assertFalse(isExactHashtag("#one\ntwo"))
        assertFalse(isExactHashtag("##one"))
    }

    @Test
    fun inMemoryRepositoryNormalizesDuplicatesWithoutChangingFirstSpelling() = runBlocking {
        val repository = InMemoryPhotoGridPreferencesRepository()
        val account = account(Protocol.MISSKEY)
        repository.update(account) {
            PhotoGridPreferences(listOf("#Cats", "#cats", "dogs"))
        }

        assertEquals(listOf("#Cats", "dogs"), repository.observe(account).first().hashtags)
        assertEquals(hashtagIdentity("#Cats"), hashtagIdentity("cats"))
    }

    @Test
    fun fileRepositorySurvivesRecreationAndScopesProtocol() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        File(context.noBackupFilesDir, "photo-grid-preferences.json").delete()
        val misskey = account(Protocol.MISSKEY)
        val mastodon = account(Protocol.MASTODON)
        FilePhotoGridPreferencesRepository(context).update(misskey) {
            PhotoGridPreferences(listOf("#Travel"))
        }

        val recreated = FilePhotoGridPreferencesRepository(context)
        assertEquals(listOf("#Travel"), recreated.observe(misskey).first { it.hashtags.isNotEmpty() }.hashtags)
        assertTrue(recreated.observe(mastodon).first().hashtags.isEmpty())
    }

    private fun account(protocol: Protocol) = AccountId(
        Connection("https://preferences.example", protocol),
        "account",
    )
}
