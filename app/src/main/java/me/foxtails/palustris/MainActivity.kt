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
import me.foxtails.palustris.ui.AccountManager
import me.foxtails.palustris.ui.ConnectedApp

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val accountManager by viewModels<AccountManager>()
    @Inject lateinit var sourceFactory: SocialSourceFactory

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) intent.dataString?.let(accountManager::callback)
        setContent { ConnectedApp(accountManager, sourceFactory) }
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.dataString?.let(accountManager::callback)
    }
}
