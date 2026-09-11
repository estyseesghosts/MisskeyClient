package me.foxtails.palustris

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import me.foxtails.palustris.data.emoji.EmojiCacheDatabase
import me.foxtails.palustris.data.emoji.RoomEmojiCatalogRepository
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.Audience
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.CustomEmoji
import me.foxtails.palustris.domain.Page
import me.foxtails.palustris.domain.Protocol
import me.foxtails.palustris.domain.ServerCapabilities
import me.foxtails.palustris.domain.SocialSource
import me.foxtails.palustris.domain.Timeline
import me.foxtails.palustris.domain.ValidatedUrl
import me.foxtails.palustris.domain.Post
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class EmojiCatalogRepositoryTest {
    private lateinit var database: EmojiCacheDatabase
    private lateinit var repository: RoomEmojiCatalogRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            EmojiCacheDatabase::class.java,
        ).allowMainThreadQueries().build()
        repository = RoomEmojiCatalogRepository(database, kotlinx.coroutines.Dispatchers.IO)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun refreshAndReadPreserveAliasesHiddenEntriesAndOrder() = runBlocking {
        val first = emoji("first", category = "one", aliases = listOf("primary", "one"))
        val hidden = emoji("hidden", visible = false)
        val account = account("a")

        repository.refresh(account, Source(listOf(first, hidden)))
        val snapshot = repository.read(account)

        assertEquals(listOf(":first:", ":hidden:"), snapshot?.items?.map { it.submissionValue })
        assertEquals(listOf("primary", "one"), snapshot?.items?.first()?.aliases)
        assertFalse(snapshot?.items?.last()?.visibleInPicker ?: true)
    }

    @Test
    fun failedRefreshLeavesPreviousSnapshotUntouched() = runBlocking {
        val account = account("a")
        repository.refresh(account, Source(listOf(emoji("old"))))

        try {
            repository.refresh(account, Source(failure = IllegalStateException("offline")))
        } catch (_: IllegalStateException) {
        }

        assertEquals(listOf(":old:"), repository.read(account)?.items?.map { it.submissionValue })
    }

    @Test
    fun emptySuccessfulRefreshCreatesPersistentSnapshot() = runBlocking {
        val account = account("a")
        val refreshed = repository.refresh(account, Source(emptyList()))

        assertTrue(refreshed.items.isEmpty())
        assertEquals(emptyList<CustomEmoji>(), repository.read(account)?.items)
        assertTrue(repository.read(account)!!.refreshedAtEpochMillis > 0L)
    }

    @Test
    fun accountsAreIsolatedAndRemovalDeletesOnlyOneCatalog() = runBlocking {
        val first = account("a")
        val second = account("b")
        repository.refresh(first, Source(listOf(emoji("first"))))
        repository.refresh(second, Source(listOf(emoji("second"))))

        repository.remove(first)

        assertNull(repository.read(first))
        assertEquals(listOf(":second:"), repository.read(second)?.items?.map { it.submissionValue })
    }

    private fun account(id: String) = AccountId(Connection("https://example.org", Protocol.MISSKEY), id)

    private fun emoji(
        shortcode: String,
        category: String? = null,
        aliases: List<String> = emptyList(),
        visible: Boolean = true,
    ) = CustomEmoji(
        shortcode = shortcode,
        animatedUrl = ValidatedUrl.https("https://cdn.example/$shortcode.gif"),
        staticUrl = ValidatedUrl.https("https://cdn.example/$shortcode.png"),
        category = category,
        aliases = aliases,
        visibleInPicker = visible,
        submissionValue = ":$shortcode:",
    )

    private class Source(
        private val values: List<CustomEmoji> = emptyList(),
        private val failure: Exception? = null,
    ) : SocialSource {
        override val capabilities = ServerCapabilities()

        override suspend fun customEmojis(): List<CustomEmoji> {
            failure?.let { throw it }
            return values
        }

        override suspend fun timeline(timeline: Timeline, cursor: String?): Page<Post> = Page(emptyList())
    }
}
