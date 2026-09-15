package me.foxtails.palustris

import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.EmojiCatalogRepository
import me.foxtails.palustris.domain.EmojiCatalogSnapshot
import me.foxtails.palustris.domain.EmojiPickerPreferences
import me.foxtails.palustris.domain.EmojiPickerPreferencesRepository
import me.foxtails.palustris.domain.EntityId
import me.foxtails.palustris.domain.Page
import me.foxtails.palustris.domain.Post
import me.foxtails.palustris.domain.Protocol
import me.foxtails.palustris.domain.ServerCapabilities
import me.foxtails.palustris.domain.SocialSource
import me.foxtails.palustris.domain.Timeline
import me.foxtails.palustris.ui.emoji.EmojiCatalogViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EmojiCatalogViewModelTest {
    private val connection = Connection("https://example.org", Protocol.MASTODON)
    private val accountId = AccountId(connection, "owner")

    private class GatedCatalogRepository : EmojiCatalogRepository {
        var readGate: CompletableDeferred<EmojiCatalogSnapshot?>? = null
        var readResult: EmojiCatalogSnapshot? = null
        val refreshCalls = mutableListOf<AccountId>()

        override suspend fun read(accountId: AccountId): EmojiCatalogSnapshot? {
            val gate = readGate
            if (gate != null) return gate.await()
            return readResult
        }

        override suspend fun refresh(accountId: AccountId, source: SocialSource): EmojiCatalogSnapshot {
            refreshCalls += accountId
            return EmojiCatalogSnapshot(emptyList(), 0L)
        }

        override suspend fun remove(accountId: AccountId) = Unit
    }

    private class InertPreferencesRepository : EmojiPickerPreferencesRepository {
        private val state = MutableStateFlow(EmojiPickerPreferences())

        override fun observe(accountId: AccountId): Flow<EmojiPickerPreferences> = state

        override suspend fun update(
            accountId: AccountId,
            transform: (EmojiPickerPreferences) -> EmojiPickerPreferences,
        ) {
            state.value = transform(state.value)
        }

        override suspend fun remove(accountId: AccountId) = Unit
    }

    private object EmptySource : SocialSource {
        override val capabilities = ServerCapabilities()

        override suspend fun timeline(timeline: Timeline, cursor: String?): Page<Post> = Page(emptyList())
    }

    private fun viewModel(repository: GatedCatalogRepository) = EmojiCatalogViewModel(
        accountId = accountId,
        source = EmptySource,
        repository = repository,
        clock = Clock.fixed(Instant.EPOCH, ZoneId.of("UTC")),
        preferencesRepository = InertPreferencesRepository(),
    )

    @Test
    fun cancelledReadNeverRefreshes() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val repository = GatedCatalogRepository()
            repository.readGate = CompletableDeferred()
            val model = viewModel(repository)

            model.loadIfNeeded()
            advanceUntilIdle()
            // The cached read is in flight. Stopping cancels it. Cancellation stays
            // cancellation: no refresh follows and no error is reported.
            model.stop()
            advanceUntilIdle()

            assertTrue(repository.refreshCalls.isEmpty())
            assertFalse(model.state.value.initialLoading)
            assertFalse(model.state.value.refreshing)
            assertNull(model.state.value.error)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun failedReadFallsBackToRefresh() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val repository = GatedCatalogRepository()
            repository.readGate = CompletableDeferred<EmojiCatalogSnapshot?>().apply {
                completeExceptionally(java.io.IOException("cache down"))
            }
            val model = viewModel(repository)

            model.loadIfNeeded()
            advanceUntilIdle()

            assertEquals(listOf(accountId), repository.refreshCalls)
        } finally {
            Dispatchers.resetMain()
        }
    }
}
