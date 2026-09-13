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
import me.foxtails.palustris.data.SocialSourceFactory
import me.foxtails.palustris.data.AccountSourceRegistry
import me.foxtails.palustris.data.auth.DraftStore
import me.foxtails.palustris.data.notifications.ForegroundNotificationStreamController
import me.foxtails.palustris.domain.AppPreferencesRepository
import me.foxtails.palustris.domain.PostPreferencesRepository
import me.foxtails.palustris.ui.AccountManager
import me.foxtails.palustris.ui.ConnectedApp
import me.foxtails.palustris.ui.notifications.NotificationLaunchRouter
import me.foxtails.palustris.ui.display.RefreshRateController
import me.foxtails.palustris.ui.localization.AppLocaleController
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val accountManager by viewModels<AccountManager>()
    @Inject lateinit var sourceFactory: SocialSourceFactory
    @Inject lateinit var sourceRegistry: AccountSourceRegistry
    @Inject lateinit var draftStore: DraftStore
    @Inject lateinit var notificationLaunchRouter: NotificationLaunchRouter
    @Inject lateinit var notificationStreamController: ForegroundNotificationStreamController
    @Inject lateinit var appPreferencesRepository: AppPreferencesRepository
    @Inject lateinit var postPreferencesRepository: PostPreferencesRepository
    private lateinit var refreshRateController: RefreshRateController
    private var appliedLanguage = me.foxtails.palustris.domain.AppLanguage.SystemDefault

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
                sourceFactory,
                sourceRegistry,
                draftStore,
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
                    .collect { (_, values) ->
                        val (request60Hz, language) = values
                        refreshRateController.apply(request60Hz)
                        AppLocaleController.applyPlatformLocale(this@MainActivity, language)
                        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU && language != appliedLanguage) {
                            appliedLanguage = language
                            recreate()
                        }
                    }
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
