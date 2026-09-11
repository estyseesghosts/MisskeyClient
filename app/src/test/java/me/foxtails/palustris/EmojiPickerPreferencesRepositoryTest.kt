package me.foxtails.palustris

import android.content.Context
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import me.foxtails.palustris.data.preferences.FileEmojiPickerPreferencesRepository
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.EmojiPickerGroupIds
import me.foxtails.palustris.domain.Protocol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class EmojiPickerPreferencesRepositoryTest {
    private lateinit var context: Context
    private lateinit var account: AccountId

    @Before
    fun setUp() {
        context = androidx.test.core.app.ApplicationProvider.getApplicationContext()
        account = AccountId(Connection("https://example.org", Protocol.MISSKEY), "person")
    }

    @Test
    fun normalizationKeepsOrderAndValidUnknownServerGroups() = runBlocking {
        val repository = FileEmojiPickerPreferencesRepository(context)
        repository.update(account) {
            it.copy(
                collapsedGroups = setOf(
                    EmojiPickerGroupIds.Favorite,
                    EmojiPickerGroupIds.Recent,
                    "server:unknown",
                    "not-valid",
                    "server:bad\ncategory",
                ),
                pinnedGroups = listOf(
                    "server:first",
                    "server:first",
                    "server:unknown",
                    "favorite",
                    "server:second",
                    "server:third",
                    "server:fourth",
                    "server:fifth",
                    "server:sixth",
                ),
            )
        }

        val result = repository.observe(account).first()

        assertEquals(setOf("favorite", "recent", "server:unknown"), result.collapsedGroups)
        assertEquals(
            listOf("server:first", "server:unknown", "server:second", "server:third", "server:fourth"),
            result.pinnedGroups,
        )
    }

    @Test
    fun preferencesSurviveRepositoryRecreationAndRemoval() = runBlocking {
        val repository = FileEmojiPickerPreferencesRepository(context)
        repository.update(account) { it.copy(collapsedGroups = setOf(EmojiPickerGroupIds.Unicode), pinnedGroups = listOf("server:group")) }

        val restored = FileEmojiPickerPreferencesRepository(context)
        assertEquals(setOf(EmojiPickerGroupIds.Unicode), restored.observe(account).first().collapsedGroups)
        assertEquals(listOf("server:group"), restored.observe(account).first().pinnedGroups)

        restored.remove(account)
        assertTrue(restored.observe(account).first().collapsedGroups.isEmpty())
        assertTrue(restored.observe(account).first().pinnedGroups.isEmpty())
    }
}
