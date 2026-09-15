package me.foxtails.palustris.ui

import android.content.Intent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.foxtails.palustris.data.AccountSourceRegistry
import me.foxtails.palustris.data.auth.DraftStore
import me.foxtails.palustris.data.notifications.NotificationStreamController
import me.foxtails.palustris.domain.AppPreferencesRepository
import me.foxtails.palustris.domain.AppPreferencesState
import me.foxtails.palustris.domain.PostPreferences
import me.foxtails.palustris.domain.PostPreferencesRepository
import me.foxtails.palustris.ui.links.ExternalLinkHandler
import me.foxtails.palustris.ui.motion.palustrisMotionScheme
import me.foxtails.palustris.ui.navigation.AppRoute
import me.foxtails.palustris.ui.notifications.NotificationLaunchHost
import me.foxtails.palustris.ui.notifications.NotificationLaunchRouter
import me.foxtails.palustris.ui.session.ConnectedEntryStore
import me.foxtails.palustris.ui.session.ConnectedSessionHost
import me.foxtails.palustris.ui.settings.SettingsOverlayHost

@Composable
fun ConnectedApp(
    accountManager: AccountManager,
    sourceRegistry: AccountSourceRegistry,
    draftStore: DraftStore,
    notificationLaunchRouter: NotificationLaunchRouter,
    notificationStreamController: NotificationStreamController,
    appPreferencesRepository: AppPreferencesRepository,
    postPreferencesRepository: PostPreferencesRepository,
) {
    val state by accountManager.session.collectAsStateWithLifecycle()
    val accountIndex by accountManager.accountIndex.collectAsStateWithLifecycle()
    val appPreferences by appPreferencesRepository.observe().collectAsStateWithLifecycle(AppPreferencesState())
    ExternalLinkHandler.cleanTrackingParameters = appPreferences.preferences.cleanTrackingParameters
    val connectedContext by accountManager.connectedContext.collectAsStateWithLifecycle()
    val activeContext = connectedContext?.takeIf { it.accountId == state.account?.id }
    // The entry store is activity-scoped. It survives recreation and retires feature models only
    // when the connected lifetime changes.
    val entryStore = hiltViewModel<ConnectedEntryStore>()
    LaunchedEffect(activeContext?.presentationGeneration) {
        val generation = activeContext?.presentationGeneration
        if (generation == null) entryStore.retireAll() else entryStore.beginEntry(generation)
    }
    val postPreferences by if (activeContext != null) {
        postPreferencesRepository.observe(activeContext.accountId).collectAsStateWithLifecycle(PostPreferences())
    } else {
        remember { mutableStateOf(PostPreferences()) }
    }
    var initialNotificationRoute by remember { mutableStateOf<AppRoute?>(null) }
    var settingsVisible by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    LaunchedEffect(state.browserUrl) {
        state.browserUrl?.let { url ->
            accountManager.browserOpened()
            try { context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) }
            catch (_: android.content.ActivityNotFoundException) { accountManager.browserFailed() }
        }
    }
    NotificationLaunchHost(
        router = notificationLaunchRouter,
        accountManager = accountManager,
        accounts = accountIndex.accounts,
        starting = state.starting,
        activeAccountId = state.account?.id,
        onRoute = { initialNotificationRoute = it },
    )
    val motionScheme = palustrisMotionScheme()
    val topLevelScreen = when {
        state.starting || !appPreferences.loaded -> "startup"
        state.account == null || state.addingAccount -> "signin"
        activeContext == null -> "connecting"
        else -> "app"
    }
    PalustrisTheme(preferences = appPreferences.preferences) {
        CompositionLocalProvider(
             LocalContentWarningRules provides appPreferences.preferences.contentWarningRules.merge(postPreferences.contentWarningRules),
             LocalMutedHashtags provides postPreferences.localMutedHashtags.toSet(),
             LocalHiddenContentPresentation provides appPreferences.preferences.hiddenContentPresentation,
        ) {
        Box(Modifier.fillMaxSize()) {
        AnimatedContent(
        targetState = topLevelScreen,
        transitionSpec = {
            if (motionScheme.reducedMotion) {
                EnterTransition.None togetherWith ExitTransition.None
            } else if (targetState == "app" && initialState != "app") {
                (fadeIn(motionScheme.fastFadeIn) + slideInVertically(motionScheme.spatialOffset) { it / 8 } + scaleIn(initialScale = 0.98f, animationSpec = motionScheme.spatial)) togetherWith
                    (fadeOut(motionScheme.fastFadeOut) + scaleOut(targetScale = 0.98f, animationSpec = motionScheme.spatial))
            } else if (initialState == "app" && targetState != "app") {
                (fadeIn(motionScheme.fastFadeIn) + scaleIn(initialScale = 0.98f, animationSpec = motionScheme.spatial)) togetherWith
                    (fadeOut(motionScheme.fastFadeOut) + slideOutVertically(motionScheme.spatialOffset) { it / 8 } + scaleOut(targetScale = 0.98f, animationSpec = motionScheme.spatial))
            } else {
                fadeIn(motionScheme.fastFadeIn) togetherWith fadeOut(motionScheme.fastFadeOut)
            }
        },
        label = "connectedAppState",
    ) { screen ->
        when (screen) {
            "startup" -> Surface(Modifier.fillMaxSize()) {
                Box(contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            }
            "signin" -> {
                key(state.addingAccount) {
                     SignInScreen(state, accountManager::signIn, accountManager::finishSignIn, accountManager::reopenBrowser, accountManager::cancelSignIn, onOpenSettings = { settingsVisible = true })
                }
            }
            "connecting" -> Surface(Modifier.fillMaxSize()) {
                Box(contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        state.error?.let { message ->
                            Text(
                                text = message,
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }
            else -> key(state.account!!.id) {
                ConnectedSessionHost(
                    accountManager = accountManager,
                    connectedContext = activeContext!!,
                    entryStore = entryStore,
                    draftStore = draftStore,
                    notificationStreamController = notificationStreamController,
                    accountIndex = accountIndex,
                    postPreferences = postPreferences,
                    initialNotificationRoute = initialNotificationRoute,
                    onOpenSettings = { settingsVisible = true },
                )
            }
        }
        }
        SettingsOverlayHost(
            visible = settingsVisible,
            onDismiss = { settingsVisible = false },
            accounts = accountIndex.accounts,
            accountsReady = !state.starting,
            postPreferences = postPreferences,
            activeAccountId = activeContext?.accountId,
            sourceRegistry = sourceRegistry,
            sessionGeneration = activeContext?.presentationGeneration ?: 0L,
            entryStore = entryStore,
        )
        }
        }
    }
}
