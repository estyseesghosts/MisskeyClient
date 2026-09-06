package me.foxtails.palustris.ui

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.foxtails.palustris.data.SocialSourceFactory

@Composable
fun ConnectedApp(
    accountManager: AccountManager,
    sourceFactory: SocialSourceFactory,
) {
    val state by accountManager.session.collectAsStateWithLifecycle()
    val activeSession by accountManager.activeSession.collectAsStateWithLifecycle()
    val feedModel = activeSession?.let { session ->
        hiltViewModel<FeedViewModel, FeedViewModel.Factory>(
            key = "feed-${session.accountId}",
            creationCallback = { factory ->
                factory.create(session.accountId, sourceFactory.create(session))
            },
        )
    }
    val feed by if (feedModel != null) feedModel.feed.collectAsStateWithLifecycle()
    else remember { mutableStateOf(FeedState()) }
    val context = LocalContext.current
    LaunchedEffect(state.browserUrl) {
        state.browserUrl?.let { url ->
            accountManager.browserOpened()
            try { context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) }
            catch (_: android.content.ActivityNotFoundException) { accountManager.browserFailed() }
        }
    }
    when {
        state.starting -> PalustrisTheme { Surface(Modifier.fillMaxSize()) {
            Box(contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } }
        state.account == null -> PalustrisTheme {
            SignInScreen(state, accountManager::signIn, accountManager::finishSignIn, accountManager::reopenBrowser, accountManager::signOut)
        }
        else -> key(state.account!!.id) {
            PalustrisApp(
                account = state.account,
                feedState = feed,
                onRefresh = { feedModel?.refresh() },
                onLoadMore = { feedModel?.loadMore() },
                onSignOut = accountManager::signOut,
                ownedPosts = feed.ownedPosts,
            )
        }
    }
}

private val suggestedInstances = listOf(
    "misskey.io" to "Misskey", "misskey.design" to "Misskey", "misskey.art" to "Misskey",
    "sharkey.world" to "Sharkey", "federation.network" to "Sharkey", "sakurajima.social" to "Sharkey",
)

@Composable
fun SignInScreen(state: SessionUi, onNext: (String) -> Unit, onComplete: () -> Unit, onReopen: () -> Unit, onCancel: () -> Unit) {
    var domain by rememberSaveable { mutableStateOf("") }
    Scaffold(
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
                Column(Modifier.navigationBarsPadding().imePadding().padding(16.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Button(onClick = { if (state.pending) onComplete() else onNext(domain) },
                        enabled = !state.busy && (domain.isNotBlank() || state.pending),
                        modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth().height(56.dp)) {
                        if (state.busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        else Text(if (state.pending) "I've authorized access" else "Next")
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = 560.dp).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
                Spacer(Modifier.height(32.dp))
                Text("Palustris", Modifier.align(Alignment.CenterHorizontally), style = MaterialTheme.typography.headlineLarge)
                Spacer(Modifier.height(40.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.primaryContainer) {
                        Icon(AppIcons.Globe, null, Modifier.padding(16.dp).size(28.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text("Your corner of the fediverse", style = MaterialTheme.typography.titleMedium)
                        Text("Misskey & Sharkey", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.height(24.dp))
                Text(if (state.pending) "One more step" else "Welcome!", style = MaterialTheme.typography.headlineLarge)
                Spacer(Modifier.height(16.dp))
                Text(if (state.pending) "Approve Palustris in your browser on ${state.origin?.removePrefix("https://")}. Then return here to open your home feed."
                    else "To get started, enter your home instance’s domain name below.", style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(24.dp))
                if (!state.pending) {
                    OutlinedTextField(value = domain, onValueChange = { domain = it }, enabled = !state.busy,
                        label = { Text("Instance URL") }, placeholder = { Text("example.social") },
                        leadingIcon = { Icon(AppIcons.Globe, null) }, singleLine = true, shape = CircleShape,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(28.dp))
                    Text("Popular instances", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Text("Choose the instance where you already have an account.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(12.dp))
                    suggestedInstances.chunked(2).forEach { pair ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            pair.forEach { (host, software) ->
                                OutlinedButton(onClick = { domain = host }, enabled = !state.busy, modifier = Modifier.weight(1f),
                                    shape = MaterialTheme.shapes.large, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 12.dp)) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(host, style = MaterialTheme.typography.labelLarge)
                                        Text(software, style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                } else {
                    OutlinedButton(onClick = onReopen, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text("Open browser again") }
                    TextButton(onClick = onCancel, enabled = !state.busy) { Text("Use a different instance") }
                }
                state.error?.let {
                    Spacer(Modifier.height(16.dp))
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}
