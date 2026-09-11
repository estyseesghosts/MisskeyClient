package me.foxtails.palustris.ui

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.CustomEmoji
import me.foxtails.palustris.domain.EmojiCatalogRepository
import me.foxtails.palustris.domain.EmojiCatalogSnapshot
import me.foxtails.palustris.domain.Page
import me.foxtails.palustris.domain.Protocol
import me.foxtails.palustris.domain.ServerCapabilities
import me.foxtails.palustris.domain.SocialSource
import me.foxtails.palustris.domain.SourceError
import me.foxtails.palustris.domain.Timeline
import me.foxtails.palustris.domain.ValidatedUrl
import me.foxtails.palustris.domain.Post
import me.foxtails.palustris.ui.emoji.EmojiCatalogViewModel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EmojiCatalogViewModelTest {
    private val account = AccountId(Connection("https://example.org", Protocol.MISSKEY), "account")
    private val oldEmoji = emoji("old")
    private val newEmoji = emoji("new")
    private val now = 2 * DAY

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun freshSnapshotAvoidsNetworkRequest() = runTest {
        val repository = FakeRepository(EmojiCatalogSnapshot(listOf(oldEmoji), now - HOUR))
        val model = model(repository)

        model.loadIfNeeded()
        advanceUntilIdle()

        assertEquals(listOf(oldEmoji), model.state.value.items)
        assertEquals(0, repository.refreshCalls)
        assertFalse(model.state.value.initialLoading)
        assertFalse(model.state.value.refreshing)
    }

    @Test
    fun staleSnapshotIsPublishedBeforeRefreshCompletes() = runTest {
        val gate = CompletableDeferred<Unit>()
        val repository = FakeRepository(EmojiCatalogSnapshot(listOf(oldEmoji), now - DAY - HOUR), gate = gate)
        val model = model(repository)

        model.loadIfNeeded()
        runCurrent()

        assertEquals(listOf(oldEmoji), model.state.value.items)
        assertTrue(model.state.value.refreshing)
        assertEquals(1, repository.refreshCalls)

        gate.complete(Unit)
        advanceUntilIdle()
        assertEquals(listOf(newEmoji), model.state.value.items)
        assertNull(model.state.value.error)
    }

    @Test
    fun staleSnapshotSurvivesOrdinaryRefreshFailure() = runTest {
        val repository = FakeRepository(
            EmojiCatalogSnapshot(listOf(oldEmoji), now - DAY - HOUR),
            failure = IllegalStateException("offline"),
        )
        val model = model(repository)

        model.loadIfNeeded()
        advanceUntilIdle()

        assertEquals(listOf(oldEmoji), model.state.value.items)
        assertFalse(model.state.value.refreshing)
        assertNotNull(model.state.value.error)
    }

    @Test
    fun unsupportedRefreshHidesCatalogButKeepsStateUsable() = runTest {
        val repository = FakeRepository(
            EmojiCatalogSnapshot(listOf(oldEmoji), now - DAY - HOUR),
            failure = SourceError.Unsupported("emoji"),
        )
        val model = model(repository)

        model.loadIfNeeded()
        advanceUntilIdle()

        assertTrue(model.state.value.unsupported)
        assertTrue(model.state.value.items.isEmpty())
        assertFalse(model.state.value.initialLoading)
    }

    private fun model(repository: EmojiCatalogRepository) = EmojiCatalogViewModel(
        accountId = account,
        source = Source(),
        repository = repository,
        clock = Clock.fixed(Instant.ofEpochMilli(now), ZoneOffset.UTC),
    )

    private fun emoji(shortcode: String) = CustomEmoji(
        shortcode = shortcode,
        animatedUrl = ValidatedUrl.https("https://cdn.example/$shortcode.gif"),
        staticUrl = ValidatedUrl.https("https://cdn.example/$shortcode.png"),
        submissionValue = ":$shortcode:",
    )

    private class FakeRepository(
        private var snapshot: EmojiCatalogSnapshot?,
        private val gate: CompletableDeferred<Unit>? = null,
        private val failure: Exception? = null,
    ) : EmojiCatalogRepository {
        var refreshCalls = 0
            private set

        override suspend fun read(accountId: AccountId): EmojiCatalogSnapshot? = snapshot

        override suspend fun refresh(accountId: AccountId, source: SocialSource): EmojiCatalogSnapshot {
            refreshCalls += 1
            gate?.await()
            failure?.let { throw it }
            snapshot = EmojiCatalogSnapshot(listOf(emoji("new")), 2 * DAY)
            return snapshot!!
        }

        override suspend fun remove(accountId: AccountId) {
            snapshot = null
        }

        private fun emoji(shortcode: String) = CustomEmoji(
            shortcode = shortcode,
            animatedUrl = ValidatedUrl.https("https://cdn.example/$shortcode.gif"),
            staticUrl = ValidatedUrl.https("https://cdn.example/$shortcode.png"),
            submissionValue = ":$shortcode:",
        )
    }

    private class Source : SocialSource {
        override val capabilities = ServerCapabilities()
        override suspend fun timeline(timeline: Timeline, cursor: String?): Page<Post> = Page(emptyList())
    }

    private companion object {
        const val HOUR = 60L * 60L * 1000L
        const val DAY = 24L * HOUR
    }
}
