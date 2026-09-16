package me.foxtails.palustris.ui.settings

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import me.foxtails.palustris.data.preferences.InMemoryAppPreferencesRepository
import me.foxtails.palustris.data.preferences.InMemoryPostPreferencesRepository
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.AppBackground
import me.foxtails.palustris.domain.AppColorScheme
import me.foxtails.palustris.domain.AppPreferences
import me.foxtails.palustris.domain.AppPreferencesRepository
import me.foxtails.palustris.domain.AppPreferencesState
import me.foxtails.palustris.domain.Audience
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.Protocol
import me.foxtails.palustris.ui.settings.SettingsViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    private val accountA = AccountId(Connection("https://a.example", Protocol.MISSKEY), "a")
    private val accountB = AccountId(Connection("https://b.example", Protocol.MASTODON), "b")

    @Test
    fun rapidAppCommandsRetainBothFields() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val repository = InMemoryAppPreferencesRepository()
            val model = SettingsViewModel(repository, InMemoryPostPreferencesRepository())

            model.setColorScheme(AppColorScheme.Palette)
            model.setBackground(AppBackground.Dark)
            advanceUntilIdle()

            val preferences = repository.observe().first().preferences
            assertEquals(AppColorScheme.Palette, preferences.colorScheme)
            assertEquals(AppBackground.Dark, preferences.background)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun postCommandsTransformOnlyTheirOwnFieldForTheCapturedAccount() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val postRepository = InMemoryPostPreferencesRepository()
            val model = SettingsViewModel(InMemoryAppPreferencesRepository(), postRepository)

            model.setPostDefaultAudience(accountA, Audience.Followers)
            model.setPostRepliesUnlisted(accountA, true)
            model.setPostDefaultAudience(accountB, Audience.Unlisted)
            advanceUntilIdle()

            val a = postRepository.observe(accountA).first()
            val b = postRepository.observe(accountB).first()
            assertEquals(Audience.Followers, a.defaultAudience)
            assertTrue(a.repliesUnlisted)
            assertEquals(Audience.Unlisted, b.defaultAudience)
            assertFalse(b.repliesUnlisted)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun failedCommandExposesErrorAndKeepsCommittedValue() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val repository = FailingAppPreferencesRepository(AppPreferences(colorScheme = AppColorScheme.System))
            val model = SettingsViewModel(repository, InMemoryPostPreferencesRepository())

            model.setColorScheme(AppColorScheme.Palette)
            advanceUntilIdle()

            assertEquals(AppColorScheme.System, repository.current.preferences.colorScheme)
            assertNotNull(model.commandError.value)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun cancelledCommandIsNotReportedAsASaveError() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val repository = CancellingAppPreferencesRepository()
            val model = SettingsViewModel(repository, InMemoryPostPreferencesRepository())

            model.setColorScheme(AppColorScheme.Palette)
            advanceUntilIdle()

            assertNull(model.commandError.value)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun clearErrorRemovesTheVisibleError() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val model = SettingsViewModel(
                FailingAppPreferencesRepository(AppPreferences()),
                InMemoryPostPreferencesRepository(),
            )
            model.setColorScheme(AppColorScheme.Palette)
            advanceUntilIdle()
            assertNotNull(model.commandError.value)

            model.clearError()

            assertNull(model.commandError.value)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun postCommandForRemovedAccountWritesNothingAndReportsUnavailable() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val postRepository = InMemoryPostPreferencesRepository()
            val model = SettingsViewModel(InMemoryAppPreferencesRepository(), postRepository)
            model.setValidAccountIds(setOf(accountA))

            model.setPostDefaultAudience(accountB, Audience.Followers)
            advanceUntilIdle()

            assertEquals(Audience.Public, postRepository.observe(accountB).first().defaultAudience)
            assertEquals(
                me.foxtails.palustris.ui.settings.SettingsCommandError.AccountUnavailable,
                model.commandError.value,
            )
            assertEquals(false, model.canRetry.value)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun queuedPostCommandAfterRemovalWritesNothingAndReportsNothing() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val postRepository = InMemoryPostPreferencesRepository()
            val model = SettingsViewModel(InMemoryAppPreferencesRepository(), postRepository)
            model.setValidAccountIds(setOf(accountA))

            model.setPostDefaultAudience(accountA, Audience.Followers)
            // The removal lands before the queued command runs.
            model.setValidAccountIds(emptySet())
            advanceUntilIdle()

            assertEquals(Audience.Public, postRepository.observe(accountA).first().defaultAudience)
            assertNull(model.commandError.value)
            assertEquals(false, model.canRetry.value)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun failedCommandRetriesAfterRecovery() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val repository = ToggleFailingAppPreferencesRepository(AppPreferences())
            val model = SettingsViewModel(repository, InMemoryPostPreferencesRepository())

            model.setBackground(AppBackground.Dark)
            advanceUntilIdle()
            assertEquals(
                me.foxtails.palustris.ui.settings.SettingsCommandError.SaveFailed("write failed"),
                model.commandError.value,
            )
            assertEquals(true, model.canRetry.value)

            repository.failing = false
            model.retryFailedCommand()
            advanceUntilIdle()

            assertEquals(AppBackground.Dark, repository.current.preferences.background)
            assertNull(model.commandError.value)
            assertEquals(false, model.canRetry.value)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun dismissKeepsRetryUntilTheNextCommand() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val repository = ToggleFailingAppPreferencesRepository(AppPreferences())
            val model = SettingsViewModel(repository, InMemoryPostPreferencesRepository())

            model.setBackground(AppBackground.Dark)
            advanceUntilIdle()
            model.clearError()
            assertNull(model.commandError.value)
            assertEquals(true, model.canRetry.value)

            repository.failing = false
            model.retryFailedCommand()
            advanceUntilIdle()

            assertEquals(AppBackground.Dark, repository.current.preferences.background)
            assertEquals(false, model.canRetry.value)
        } finally {
            Dispatchers.resetMain()
        }
    }

    private class ToggleFailingAppPreferencesRepository(initial: AppPreferences) : AppPreferencesRepository {
        private val values = MutableStateFlow(AppPreferencesState(loaded = true, preferences = initial))
        val current: AppPreferencesState get() = values.value
        var failing = true

        override fun observe(): Flow<AppPreferencesState> = values.asStateFlow()

        override suspend fun update(transform: (AppPreferences) -> AppPreferences) {
            if (failing) throw java.io.IOException("write failed")
            values.value = values.value.copy(preferences = transform(values.value.preferences))
        }
    }

    private class FailingAppPreferencesRepository(initial: AppPreferences) : AppPreferencesRepository {
        private val values = MutableStateFlow(AppPreferencesState(loaded = true, preferences = initial))
        val current: AppPreferencesState get() = values.value

        override fun observe(): Flow<AppPreferencesState> = values.asStateFlow()

        override suspend fun update(transform: (AppPreferences) -> AppPreferences) {
            throw java.io.IOException("write failed")
        }
    }

    private class CancellingAppPreferencesRepository : AppPreferencesRepository {
        private val values = MutableStateFlow(AppPreferencesState(loaded = true))

        override fun observe(): Flow<AppPreferencesState> = values.asStateFlow()

        override suspend fun update(transform: (AppPreferences) -> AppPreferences) {
            throw CancellationException("cancelled")
        }
    }
}
