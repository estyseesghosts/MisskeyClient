package me.foxtails.palustris.data.preferences

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
                pinnedEmoji = listOf("🎉", ":blob:", ":blob:", "", "bad\nidentity"),
            )
        }

        val result = repository.observe(account).first()

        assertEquals(setOf("favorite", "recent", "server:unknown"), result.collapsedGroups)
        assertEquals(
            listOf("server:first", "server:unknown", "server:second", "server:third", "server:fourth"),
            result.pinnedGroups,
        )
        assertEquals(listOf("🎉", ":blob:"), result.pinnedEmoji)
    }

    @Test
    fun versionOneFilesKeepExistingFieldsAndDefaultPinnedEmoji() = runBlocking {
        val file = java.io.File(context.noBackupFilesDir, "emoji-picker-preferences.json")
        file.parentFile?.mkdirs()
        file.writeText(
            org.json.JSONObject()
                .put("version", 1)
                .put(
                    "accounts",
                    org.json.JSONObject().put(
                        java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(
                            "${account.connection.origin}\u0000${account.localId}".toByteArray(),
                        ),
                        org.json.JSONObject()
                            .put("collapsedGroups", org.json.JSONArray(listOf("unicode")))
                            .put("pinnedGroups", org.json.JSONArray(listOf("server:old"))),
                    ),
                )
                .toString(),
        )

        val result = FileEmojiPickerPreferencesRepository(context).observe(account).first()

        assertEquals(setOf("unicode"), result.collapsedGroups)
        assertEquals(listOf("server:old"), result.pinnedGroups)
        assertTrue(result.pinnedEmoji.isEmpty())
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
        assertTrue(restored.observe(account).first().pinnedEmoji.isEmpty())
    }
}
