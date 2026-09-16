package me.foxtails.palustris.ui.settings

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import me.foxtails.palustris.data.AccountSourceRegistry
import me.foxtails.palustris.data.preferences.InMemoryPostPreferencesRepository
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.Audience
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.ModerationAccount
import me.foxtails.palustris.domain.ModerationCursor
import me.foxtails.palustris.domain.ModerationListKind
import me.foxtails.palustris.domain.ModerationListQuery
import me.foxtails.palustris.domain.ModerationPage
import me.foxtails.palustris.domain.MutedHashtag
import me.foxtails.palustris.domain.NotificationSyncToken
import me.foxtails.palustris.domain.Page
import me.foxtails.palustris.domain.Post
import me.foxtails.palustris.domain.Protocol
import me.foxtails.palustris.domain.ServerCapabilities
import me.foxtails.palustris.domain.SocialSource
import me.foxtails.palustris.domain.SourceError
import me.foxtails.palustris.domain.Timeline
import me.foxtails.palustris.ui.settings.ModerationViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ModerationViewModelTest {
    private val connection = Connection("https://example.org", Protocol.MASTODON)
    private val accountId = AccountId(connection, "owner")

    private fun member(id: String) = ModerationAccount(
        Account(AccountId(connection, id), id, "@$id@example.org"),
    )

    private fun cursor(value: String, kind: ModerationListKind = ModerationListKind.Blocked) =
        ModerationCursor(accountId, ModerationListQuery(kind), "test", value)

    private class GatedModerationSource(
        var hashtagFailure: Exception? = null,
    ) : SocialSource {
        override val capabilities = ServerCapabilities()
        val listCalls = mutableListOf<ModerationCursor?>()
        val removeCalls = mutableListOf<ModerationAccount>()
        private val listPending = ArrayDeque<CompletableDeferred<ModerationPage<ModerationAccount>>>()
        private val removePending = ArrayDeque<CompletableDeferred<Unit>>()

        override suspend fun timeline(timeline: Timeline, cursor: String?): Page<Post> = Page(emptyList())
        override suspend fun searchHashtag(tag: String, cursor: String?): Page<Post> = Page(emptyList())

        override suspend fun blockedAccounts(cursor: ModerationCursor?): ModerationPage<ModerationAccount> {
            listCalls += cursor
            return gated(listPending)
        }

        override suspend fun mutedAccounts(cursor: ModerationCursor?): ModerationPage<ModerationAccount> {
            listCalls += cursor
            return gated(listPending)
        }

        override suspend fun mutedHashtags(cursor: ModerationCursor?): ModerationPage<MutedHashtag> {
            hashtagFailure?.let { throw it }
            throw SourceError.Unsupported("moderation.hashtags")
        }

        override suspend fun removeBlockedAccount(entry: ModerationAccount) {
            removeCalls += entry
            val gate = CompletableDeferred<Unit>()
            removePending += gate
            withContext(NonCancellable) { gate.await() }
        }

        override suspend fun removeMutedAccount(entry: ModerationAccount) {
            removeCalls += entry
            val gate = CompletableDeferred<Unit>()
            removePending += gate
            withContext(NonCancellable) { gate.await() }
        }

        private suspend fun gated(queue: ArrayDeque<CompletableDeferred<ModerationPage<ModerationAccount>>>): ModerationPage<ModerationAccount> {
            val gate = CompletableDeferred<ModerationPage<ModerationAccount>>()
            queue += gate
            return withContext(NonCancellable) { gate.await() }
        }

        fun completeList(index: Int, page: ModerationPage<ModerationAccount>) { listPending[index].complete(page) }
        fun failList(index: Int, error: Exception) { listPending[index].completeExceptionally(error) }
        fun completeRemove(index: Int) { removePending[index].complete(Unit) }
        fun failRemove(index: Int, error: Exception) { removePending[index].completeExceptionally(error) }
    }

    private fun registered(source: GatedModerationSource): AccountSourceRegistry =
        AccountSourceRegistry().also { it.register(NotificationSyncToken(accountId, 1), source) }

    private fun model(
        source: GatedModerationSource,
        registry: AccountSourceRegistry,
        prefs: InMemoryPostPreferencesRepository = InMemoryPostPreferencesRepository(),
        kind: ModerationListKind = ModerationListKind.Blocked,
    ) = ModerationViewModel(accountId, source, kind, registry, prefs)

    @Test
    fun removalDuringRefreshReconcilesOnSuccess() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val source = GatedModerationSource()
            val model = model(source, registered(source))
            advanceUntilIdle()
            source.completeList(0, ModerationPage(listOf(member("a"), member("b"))))
            advanceUntilIdle()

            model.remove(member("a"))
            advanceUntilIdle()
            model.load()
            advanceUntilIdle()
            source.completeRemove(0)
            advanceUntilIdle()
            assertTrue(model.state.value.loading)
            assertTrue(model.state.value.removing.isEmpty())
            source.completeList(2, ModerationPage(listOf(member("b"))))
            advanceUntilIdle()
            source.completeList(1, ModerationPage(listOf(member("late"))))
            advanceUntilIdle()

            assertEquals(listOf("b"), model.state.value.accounts.map { it.account.id.localId })
            assertTrue(model.state.value.removing.isEmpty())
            assertNull(model.state.value.error)
            assertFalse(model.state.value.loading)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun removalDuringRefreshShowsScopedFailure() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val source = GatedModerationSource()
            val model = model(source, registered(source))
            advanceUntilIdle()
            source.completeList(0, ModerationPage(listOf(member("a"), member("b"))))
            advanceUntilIdle()

            model.remove(member("a"))
            advanceUntilIdle()
            model.load()
            advanceUntilIdle()
            source.failRemove(0, java.io.IOException("remove failed"))
            advanceUntilIdle()

            assertEquals(listOf("a", "b"), model.state.value.accounts.map { it.account.id.localId })
            assertTrue(model.state.value.removing.isEmpty())
            assertTrue(model.state.value.error != null)
            assertFalse(model.state.value.unsupported)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun refreshDuringPagingLeavesPagingEnabled() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val source = GatedModerationSource()
            val model = model(source, registered(source))
            advanceUntilIdle()
            source.completeList(0, ModerationPage(listOf(member("a")), nextCursor = cursor("c1")))
            advanceUntilIdle()

            model.loadMore()
            advanceUntilIdle()
            assertTrue(model.state.value.loadingMore)
            model.load()
            advanceUntilIdle()
            assertFalse(model.state.value.loadingMore)
            source.completeList(1, ModerationPage(listOf(member("late"))))
            advanceUntilIdle()
            source.completeList(2, ModerationPage(listOf(member("a"), member("b"))))
            advanceUntilIdle()

            assertEquals(listOf("a", "b"), model.state.value.accounts.map { it.account.id.localId })
            assertFalse(model.state.value.loadingMore)
            assertFalse(model.state.value.loading)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun concurrentRemovalsIsolateMarkers() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val source = GatedModerationSource()
            val model = model(source, registered(source))
            advanceUntilIdle()
            source.completeList(0, ModerationPage(listOf(member("a"), member("b"), member("c"))))
            advanceUntilIdle()

            model.remove(member("a"))
            model.remove(member("b"))
            advanceUntilIdle()
            source.failRemove(0, java.io.IOException("denied"))
            source.completeRemove(1)
            advanceUntilIdle()

            assertEquals(listOf("a", "c"), model.state.value.accounts.map { it.account.id.localId })
            assertTrue(model.state.value.removing.isEmpty())
            assertTrue(model.state.value.error != null)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun sourceReplacementSilencesInFlightWork() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val source = GatedModerationSource()
            val registry = registered(source)
            val model = model(source, registry)
            advanceUntilIdle()
            source.completeList(0, ModerationPage(listOf(member("a")), nextCursor = cursor("c1")))
            advanceUntilIdle()

            model.loadMore()
            model.remove(member("a"))
            advanceUntilIdle()
            registry.remove(accountId)
            source.completeList(1, ModerationPage(listOf(member("late"))))
            source.completeRemove(0)
            advanceUntilIdle()

            assertEquals(listOf("a"), model.state.value.accounts.map { it.account.id.localId })
            assertFalse(model.state.value.loadingMore)
            assertTrue(model.state.value.removing.isEmpty())
            assertNull(model.state.value.error)
            assertEquals(0, source.listCalls.size - 2)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun retryWhileOlderFinishesRunsOnce() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val source = GatedModerationSource()
            val model = model(source, registered(source))
            advanceUntilIdle()
            source.completeList(0, ModerationPage(listOf(member("a"), member("b"))))
            advanceUntilIdle()

            model.remove(member("a"))
            model.remove(member("a"))
            advanceUntilIdle()
            source.completeRemove(0)
            advanceUntilIdle()

            assertEquals(1, source.removeCalls.size)
            assertEquals(listOf("b"), model.state.value.accounts.map { it.account.id.localId })
            assertTrue(model.state.value.removing.isEmpty())
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun stoppedOwnerIgnoresLateListCompletion() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val source = GatedModerationSource()
            val mutedSource = GatedModerationSource()
            val registry = registered(source)
            val blocked = model(source, registry, kind = ModerationListKind.Blocked)
            advanceUntilIdle()
            registry.register(NotificationSyncToken(accountId, 1), mutedSource)
            blocked.stop()
            source.completeList(0, ModerationPage(listOf(member("late"))))
            advanceUntilIdle()

            assertTrue(blocked.state.value.accounts.isEmpty())
            assertFalse(blocked.state.value.loading)
            assertNull(blocked.state.value.error)

            val muted = model(mutedSource, registry, kind = ModerationListKind.Muted)
            advanceUntilIdle()
            mutedSource.completeList(0, ModerationPage(listOf(member("m"))))
            advanceUntilIdle()
            assertEquals(listOf("m"), muted.state.value.accounts.map { it.account.id.localId })
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun hashtagFallbackAppliesOnlyToUnsupported() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val prefs = InMemoryPostPreferencesRepository()
            prefs.update(accountId) { it.copy(localMutedHashtags = listOf("local")) }

            val unsupportedSource = GatedModerationSource(hashtagFailure = null)
            val unsupported = model(unsupportedSource, registered(unsupportedSource), prefs, ModerationListKind.Hashtags)
            advanceUntilIdle()
            assertTrue(unsupported.state.value.localHashtagFallback)
            assertEquals(listOf("local"), unsupported.state.value.hashtags.map { it.value })
            assertNull(unsupported.state.value.error)

            val deniedSource = GatedModerationSource(hashtagFailure = SourceError.AccessDenied("moderation.hashtags"))
            val denied = model(deniedSource, registered(deniedSource), prefs, ModerationListKind.Hashtags)
            advanceUntilIdle()
            assertFalse(denied.state.value.localHashtagFallback)
            assertTrue(denied.state.value.error != null)
            assertFalse(denied.state.value.unsupported)

            val failedSource = GatedModerationSource(hashtagFailure = java.io.IOException("timeout"))
            val failed = model(failedSource, registered(failedSource), prefs, ModerationListKind.Hashtags)
            advanceUntilIdle()
            assertFalse(failed.state.value.localHashtagFallback)
            assertTrue(failed.state.value.error != null)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun localHashtagEditsPreserveOtherPreferences() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val prefs = InMemoryPostPreferencesRepository()
            prefs.update(accountId) {
                it.copy(favouriteEmoji = "⭐", defaultAudience = Audience.Followers, localMutedHashtags = listOf("x"))
            }
            val source = GatedModerationSource()
            val model = model(source, registered(source), prefs, ModerationListKind.Hashtags)
            advanceUntilIdle()

            model.addLocalHashtag("#Y")
            advanceUntilIdle()
            model.removeLocalHashtag("X")
            advanceUntilIdle()

            assertEquals(listOf("y"), model.state.value.hashtags.map { it.value })
            prefs.observe(accountId).first().let { preferences ->
                assertEquals(listOf("y"), preferences.localMutedHashtags)
                assertEquals("⭐", preferences.favouriteEmoji)
                assertEquals(Audience.Followers, preferences.defaultAudience)
            }
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun removedAccountRouteMakesNoRequest() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val source = GatedModerationSource()
            val registry = AccountSourceRegistry()
            val model = model(source, registry)
            advanceUntilIdle()

            assertTrue(source.listCalls.isEmpty())
            assertFalse(model.state.value.loading)
            assertNull(model.state.value.error)
            assertTrue(model.state.value.accounts.isEmpty())
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun hashtagKindNeverReachesAccountRemoval() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val source = GatedModerationSource()
            val model = model(source, registered(source), kind = ModerationListKind.Hashtags)
            advanceUntilIdle()

            model.remove(member("a"))
            advanceUntilIdle()

            assertTrue(source.removeCalls.isEmpty())
            assertTrue(model.state.value.removing.isEmpty())
        } finally {
            Dispatchers.resetMain()
        }
    }
}
