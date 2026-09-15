package me.foxtails.palustris

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import me.foxtails.palustris.data.auth.DraftActions
import me.foxtails.palustris.data.auth.DraftStore
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.PostDraft
import me.foxtails.palustris.domain.Protocol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Draft callbacks rethrow cancellation and keep ordinary storage fallbacks. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DraftActionsTest {
    private val account = AccountId(Connection("https://drafts.example", Protocol.MISSKEY), "author")
    private val draft = PostDraft(accountId = account, text = "hello")

    @Test
    fun loadMigratesThenLists() = runTest {
        val store = GateDraftStore(listResult = listOf(draft))
        val actions = actions(store)
        val results = mutableListOf<List<PostDraft>>()
        actions.load(onResult = { results += it })
        advanceUntilIdle()

        assertEquals(1, store.migrated.size)
        assertEquals(account, store.migrated.single().first)
        assertEquals(listOf(listOf(draft)), results)
    }

    @Test
    fun loadFailureReportsError() = runTest {
        val store = GateDraftStore(listError = IOException("disk gone"))
        val actions = actions(store)
        val results = mutableListOf<List<PostDraft>>()
        val errors = mutableListOf<String>()
        actions.load(onResult = { results += it }, onError = { errors += it })
        advanceUntilIdle()

        assertTrue(results.isEmpty())
        assertEquals(listOf(DraftActions.LOAD_ERROR), errors)
    }

    @Test
    fun loadCancellationReportsNothing() = runTest {
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val store = GateDraftStore(listGate = CompletableDeferred())
        val actions = DraftActions(scope, store, account, ::preferences)
        val results = mutableListOf<List<PostDraft>>()
        val errors = mutableListOf<String>()
        actions.load(onResult = { results += it }, onError = { errors += it })
        runCurrent()
        scope.cancel()
        store.listGate?.complete(Unit)
        advanceUntilIdle()

        assertTrue(results.isEmpty())
        assertTrue(errors.isEmpty())
    }

    @Test
    fun saveFailureReportsError() = runTest {
        val store = GateDraftStore(saveError = IOException("disk gone"))
        val actions = actions(store)
        var results = 0
        var errors = 0
        actions.save(draft, onResult = { results++ }, onError = { errors++ })
        advanceUntilIdle()

        assertEquals(0, results)
        assertEquals(1, errors)
        assertTrue(store.saved.isEmpty())
    }

    @Test
    fun saveCancellationReportsNothing() = runTest {
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val store = GateDraftStore(saveGate = CompletableDeferred())
        val actions = DraftActions(scope, store, account, ::preferences)
        var results = 0
        var errors = 0
        actions.save(draft, onResult = { results++ }, onError = { errors++ })
        runCurrent()
        scope.cancel()
        store.saveGate?.complete(Unit)
        advanceUntilIdle()

        assertEquals(0, results)
        assertEquals(0, errors)
    }

    @Test
    fun deleteFailureStillCompletesAndReports() = runTest {
        val store = GateDraftStore(deleteError = IOException("disk gone"))
        val actions = actions(store)
        var done = 0
        val errors = mutableListOf<String>()
        actions.delete(draftId = "missing", onDone = { done++ }, onError = { errors += it })
        advanceUntilIdle()

        assertEquals(1, done)
        assertEquals(listOf(DraftActions.DELETE_ERROR), errors)
    }

    @Test
    fun deleteCancellationReportsNothing() = runTest {
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val store = GateDraftStore(deleteGate = CompletableDeferred())
        val actions = DraftActions(scope, store, account, ::preferences)
        var done = 0
        val errors = mutableListOf<String>()
        actions.delete(draftId = "draft", onDone = { done++ }, onError = { errors += it })
        runCurrent()
        scope.cancel()
        store.deleteGate?.complete(Unit)
        advanceUntilIdle()

        assertEquals(0, done)
        assertTrue(errors.isEmpty())
    }

    private fun preferences() = ApplicationProvider.getApplicationContext<Context>()
        .getSharedPreferences("local_draft", Context.MODE_PRIVATE)

    private fun TestScope.actions(store: DraftStore): DraftActions = DraftActions(
        scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob()),
        store = store,
        accountId = account,
        legacyPreferences = ::preferences,
    )

    private class GateDraftStore(
        var listResult: List<PostDraft> = emptyList(),
        var listGate: CompletableDeferred<Unit>? = null,
        var listError: Throwable? = null,
        var saveGate: CompletableDeferred<Unit>? = null,
        var saveError: Throwable? = null,
        var deleteGate: CompletableDeferred<Unit>? = null,
        var deleteError: Throwable? = null,
    ) : DraftStore {
        val migrated = mutableListOf<Pair<AccountId?, android.content.SharedPreferences>>()
        val saved = mutableListOf<PostDraft>()
        val deleted = mutableListOf<String>()

        override suspend fun list(accountId: AccountId?): List<PostDraft> {
            listGate?.await()
            listError?.let { throw it }
            return listResult
        }

        override suspend fun save(draft: PostDraft) {
            saveGate?.await()
            saveError?.let { throw it }
            saved += draft
        }

        override suspend fun delete(accountId: AccountId?, draftId: String) {
            deleteGate?.await()
            deleteError?.let { throw it }
            deleted += draftId
        }

        override suspend fun deleteAll(accountId: AccountId?) = Unit

        override suspend fun migrateLegacy(accountId: AccountId?, preferences: android.content.SharedPreferences) {
            migrated += accountId to preferences
        }
    }
}
