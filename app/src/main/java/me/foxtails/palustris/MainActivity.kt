package me.foxtails.palustris

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import me.foxtails.palustris.data.SocialSourceFactory
import me.foxtails.palustris.data.AccountSourceRegistry
import me.foxtails.palustris.data.auth.DraftStore
import me.foxtails.palustris.ui.AccountManager
import me.foxtails.palustris.ui.ConnectedApp
import me.foxtails.palustris.ui.notifications.NotificationLaunchRouter

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val accountManager by viewModels<AccountManager>()
    @Inject lateinit var sourceFactory: SocialSourceFactory
    @Inject lateinit var sourceRegistry: AccountSourceRegistry
    @Inject lateinit var draftStore: DraftStore
    @Inject lateinit var notificationLaunchRouter: NotificationLaunchRouter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handleIntent(intent)
        setContent { ConnectedApp(accountManager, sourceFactory, sourceRegistry, draftStore, notificationLaunchRouter) }
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
