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

    override fun attachBaseContext(newBase: android.content.Context) {
        val language = AppLocaleController.persistedLanguage(newBase)
        appliedLanguage = language
        super.attachBaseContext(AppLocaleController.localizedContext(newBase, language))
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

    private suspend fun applyLocaleState(loaded: Boolean, language: AppLanguage) {
        // Never apply an unloaded System default over a persisted explicit language.
        val next = AppLocaleController.effectiveLanguageAfterLoad(loaded, language) ?: return
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            when (val action = AppLocaleController.reconcilePlatformSelection(
                AppLocaleController.platformTag(this@MainActivity),
                next,
            )) {
                is AppLocaleController.PlatformReconciliation.ImportToRepository ->
                    appPreferencesRepository.update { it.copy(language = action.language) }
                is AppLocaleController.PlatformReconciliation.ExportToPlatform -> {
                    AppLocaleController.applyPlatformLocale(this@MainActivity, action.language)
                    appliedLanguage = action.language
                }
                AppLocaleController.PlatformReconciliation.NoOp -> appliedLanguage = next
            }
        } else if (next != appliedLanguage) {
            AppLocaleController.applyPlatformLocale(this@MainActivity, next)
            appliedLanguage = next
            recreate()
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
