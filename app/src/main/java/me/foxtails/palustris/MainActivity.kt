package me.foxtails.palustris

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import me.foxtails.palustris.data.AccountSourceRegistry
import me.foxtails.palustris.data.auth.DraftStore
import me.foxtails.palustris.data.auth.DraftWriteAuthority
import me.foxtails.palustris.data.notifications.ForegroundNotificationStreamController
import me.foxtails.palustris.domain.AppLanguage
import me.foxtails.palustris.domain.AppPreferencesRepository
import me.foxtails.palustris.domain.PostPreferencesRepository
import me.foxtails.palustris.ui.AccountManager
import me.foxtails.palustris.ui.ConnectedApp
import me.foxtails.palustris.ui.notifications.NotificationLaunchRouter
import me.foxtails.palustris.ui.display.RefreshRateController
import me.foxtails.palustris.ui.localization.AppLocaleController
import me.foxtails.palustris.ui.localization.AppLocaleOwner
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val accountManager by viewModels<AccountManager>()
    @Inject lateinit var sourceRegistry: AccountSourceRegistry
    @Inject lateinit var draftStore: DraftStore
    @Inject lateinit var draftWriteAuthority: DraftWriteAuthority
    @Inject lateinit var notificationLaunchRouter: NotificationLaunchRouter
    @Inject lateinit var notificationStreamController: ForegroundNotificationStreamController
    @Inject lateinit var appPreferencesRepository: AppPreferencesRepository
    @Inject lateinit var postPreferencesRepository: PostPreferencesRepository
    private lateinit var refreshRateController: RefreshRateController
    private var appliedLanguage = AppLanguage.SystemDefault
    private val localeOwner = AppLocaleOwner()
    private val localeMutex = Mutex()

    override fun attachBaseContext(newBase: android.content.Context) {
        val language = readBaseLanguage(newBase)
        appliedLanguage = language
        super.attachBaseContext(AppLocaleController.localizedContext(newBase, language))
    }

    /**
     * Reads the base-context language. On Android 13 and later the platform already
     * localizes the base context from the per-application locales, so a present platform
     * value wins over a stale repository override. Otherwise the stored preference wins.
     * Failures fall back to the stored preference; the startup reconciliation converges.
     */
    private fun readBaseLanguage(newBase: android.content.Context): AppLanguage {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            val tag = runCatching { AppLocaleController.platformTag(newBase) }.getOrNull()
            if (!tag.isNullOrBlank()) return AppLocaleController.languageForTag(tag)
        }
        return AppLocaleController.persistedLanguage(newBase)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        refreshRateController = RefreshRateController(window)
        enableEdgeToEdge()
        if (savedInstanceState == null) handleIntent(intent)
        setContent {
            ConnectedApp(
                accountManager,
                sourceRegistry,
                draftStore,
                draftWriteAuthority,
                notificationLaunchRouter,
                notificationStreamController,
                appPreferencesRepository,
                postPreferencesRepository,
            )
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                appPreferencesRepository.observe()
                    .map { it.loaded to (it.preferences.request60Hz to it.preferences.language) }
                    .distinctUntilChanged()
                    .collect { (loaded, values) ->
                        val (request60Hz, language) = values
                        refreshRateController.apply(request60Hz)
                        applyLocaleState(loaded, language)
                    }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // An external Android App Languages change does not emit through the repository.
        // Reconcile on return so the platform value and the stored value converge.
        lifecycleScope.launch {
            val state = appPreferencesRepository.observe()
                .first { it.loaded }
            applyLocaleState(true, state.preferences.language)
        }
    }

    /**
     * Applies one locale event through the locale owner. A moved repository exports the
     * in-app choice to the platform. A moved platform imports the external choice into
     * the repository. The mutex serializes each decision with its side effect, so a
     * delayed import cannot land after a newer export. A failed import keeps the
     * repository error visible and retries on the next event instead of recreating
     * in a loop.
     */
    private suspend fun applyLocaleState(loaded: Boolean, language: AppLanguage) {
        localeMutex.withLock {
            // Never apply an unloaded System default over a persisted explicit language.
            val next = AppLocaleController.effectiveLanguageAfterLoad(loaded, language) ?: return
            val platformTag =
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    AppLocaleController.platformTag(this@MainActivity)
                } else {
                    null
                }
            when (val action = localeOwner.resolve(platformTag, next)) {
                is AppLocaleController.PlatformReconciliation.ImportToRepository -> {
                    try {
                        appPreferencesRepository.update { it.copy(language = action.language) }
                    } catch (error: CancellationException) {
                        throw error
                    } catch (_: Exception) {
                        return
                    }
                    if (appliedLanguage != action.language) {
                        appliedLanguage = action.language
                        recreate()
                    }
                }
                is AppLocaleController.PlatformReconciliation.ExportToPlatform -> {
                    AppLocaleController.applyPlatformLocale(this@MainActivity, action.language)
                    if (appliedLanguage != action.language) {
                        appliedLanguage = action.language
                        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) {
                            recreate()
                        }
                    }
                }
                AppLocaleController.PlatformReconciliation.NoOp -> appliedLanguage = next
            }
        }
    }

    override fun onDestroy() {
        refreshRateController.restore()
        super.onDestroy()
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        if (notificationLaunchRouter.parse(intent) != null) {
            notificationLaunchRouter.accept(intent)
        } else {
            intent.dataString?.let(accountManager::callback)
        }
    }
}
