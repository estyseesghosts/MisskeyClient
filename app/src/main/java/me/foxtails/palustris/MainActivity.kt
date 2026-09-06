package me.foxtails.palustris

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import me.foxtails.palustris.data.auth.EncryptedSessionStore
import me.foxtails.palustris.data.auth.MisskeyAuth
import me.foxtails.palustris.data.misskey.HttpClientPool
import me.foxtails.palustris.data.misskey.MisskeyApi
import me.foxtails.palustris.data.misskey.MisskeySource
import me.foxtails.palustris.ui.ConnectedApp
import me.foxtails.palustris.ui.SessionViewModel

class MainActivity : ComponentActivity() {
    private val model by viewModels<SessionViewModel> {
        viewModelFactory { initializer {
            val clientPool = HttpClientPool()
            SessionViewModel(EncryptedSessionStore(applicationContext), MisskeyAuth(clientPool), sourceFactory = {
                MisskeySource(it.accountId.connection.origin, it.token,
                    MisskeyApi(clientPool.clientFor(it.accountId.connection)), accountId = it.accountId)
            })
        } }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) intent.dataString?.let(model::callback)
        setContent { ConnectedApp(model) }
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.dataString?.let(model::callback)
    }
}
