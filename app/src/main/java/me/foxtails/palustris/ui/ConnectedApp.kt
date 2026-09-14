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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import me.foxtails.palustris.data.AccountSourceRegistry
import me.foxtails.palustris.data.SocialSourceFactory
import me.foxtails.palustris.data.auth.DraftStore
import me.foxtails.palustris.data.notifications.NoOpNotificationStreamController
import me.foxtails.palustris.data.notifications.NotificationStreamController
import me.foxtails.palustris.data.preferences.InMemoryAppPreferencesRepository
import me.foxtails.palustris.domain.AppColorScheme
import me.foxtails.palustris.domain.AppPreferences
import me.foxtails.palustris.domain.AppPreferencesRepository
import me.foxtails.palustris.domain.AppPreferencesState
import me.foxtails.palustris.domain.ModerationListKind
import me.foxtails.palustris.domain.PostPreferences
import me.foxtails.palustris.domain.PostPreferencesRepository
import me.foxtails.palustris.ui.links.ExternalLinkHandler
import me.foxtails.palustris.ui.motion.palustrisMotionScheme
import me.foxtails.palustris.ui.navigation.AppRoute
import me.foxtails.palustris.ui.notifications.NotificationLaunchRouter
import me.foxtails.palustris.ui.notifications.NotificationRouteResolver
import me.foxtails.palustris.ui.notifications.NotificationSettingsUiState
import me.foxtails.palustris.ui.notifications.NotificationSettingsViewModel
import me.foxtails.palustris.ui.session.ConnectedSessionHost
import me.foxtails.palustris.ui.settings.ModerationKind
import me.foxtails.palustris.ui.settings.ModerationViewModel
import me.foxtails.palustris.ui.settings.SettingsHost
import me.foxtails.palustris.ui.settings.SettingsRoute

private fun settingsViewModelUpdate(
    scope: CoroutineScope,
    repository: AppPreferencesRepository,
    transform: (AppPreferences) -> AppPreferences,
) {
    scope.launch { runCatching { repository.update(transform) } }
}

private fun ModerationKind.toModerationListKind() = when (this) {
    ModerationKind.Blocked -> ModerationListKind.Blocked
    ModerationKind.Muted -> ModerationListKind.Muted
    ModerationKind.Hashtags -> ModerationListKind.Hashtags
}

@Composable
fun ConnectedApp(
    accountManager: AccountManager,
    sourceFactory: SocialSourceFactory,
    sourceRegistry: AccountSourceRegistry,
    draftStore: DraftStore,
    notificationLaunchRouter: NotificationLaunchRouter,
    notificationStreamController: NotificationStreamController = NoOpNotificationStreamController(),
    appPreferencesRepository: AppPreferencesRepository = InMemoryAppPreferencesRepository(),
    postPreferencesRepository: PostPreferencesRepository = me.foxtails.palustris.data.preferences.InMemoryPostPreferencesRepository(),
) {
    val state by accountManager.session.collectAsStateWithLifecycle()
    val settingsScope = rememberCoroutineScope()
    val accountIndex by accountManager.accountIndex.collectAsStateWithLifecycle()
    val appPreferences by appPreferencesRepository.observe().collectAsStateWithLifecycle(AppPreferencesState())
    ExternalLinkHandler.cleanTrackingParameters = appPreferences.preferences.cleanTrackingParameters
    val activeSession by accountManager.activeSession.collectAsStateWithLifecycle()
    val postPreferences by if (activeSession != null) {
        postPreferencesRepository.observe(activeSession!!.accountId).collectAsStateWithLifecycle(PostPreferences())
    } else {
        remember { mutableStateOf(PostPreferences()) }
    }
    val pendingNotificationLaunch by notificationLaunchRouter.pending.collectAsStateWithLifecycle()
    var initialNotificationRoute by remember { mutableStateOf<AppRoute?>(null) }
    var settingsVisible by rememberSaveable { mutableStateOf(false) }
    var settingsRoute by remember { mutableStateOf<SettingsRoute>(SettingsRoute.Main) }
    val settingsNotificationAccountId = (settingsRoute as? SettingsRoute.NotificationAccount)?.accountId
    val settingsNotificationModel = settingsNotificationAccountId?.let { accountId ->
        hiltViewModel<NotificationSettingsViewModel, NotificationSettingsViewModel.Factory>(
            key = "settings-notification-settings-$accountId-${state.sessionGeneration}",
            creationCallback = { factory -> factory.create(accountId) },
        )
    }
    val moderationRoute = settingsRoute as? SettingsRoute.Moderation
    val moderationModel = moderationRoute?.let { route ->
        sourceRegistry.sourceFor(route.accountId)?.let { source ->
            hiltViewModel<ModerationViewModel, ModerationViewModel.Factory>(
                key = "moderation-${route.accountId}-${route.kind}-${state.sessionGeneration}",
                creationCallback = { factory ->
                    factory.create(route.accountId, source, route.kind.toModerationListKind())
                },
            )
        }
    }
    val settingsNotificationState by if (settingsNotificationModel != null) settingsNotificationModel.state.collectAsStateWithLifecycle()
    else remember { mutableStateOf(NotificationSettingsUiState()) }
    val moderationState by if (moderationModel != null) moderationModel.state.collectAsStateWithLifecycle()
    else remember { mutableStateOf(me.foxtails.palustris.ui.settings.ModerationUiState()) }
    val context = LocalContext.current
    LaunchedEffect(state.browserUrl) {
        state.browserUrl?.let { url ->
            accountManager.browserOpened()
            try { context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) }
            catch (_: android.content.ActivityNotFoundException) { accountManager.browserFailed() }
        }
    }
    LaunchedEffect(pendingNotificationLaunch, state.starting, accountIndex) {
        val launch = pendingNotificationLaunch ?: return@LaunchedEffect
        if (state.starting) return@LaunchedEffect
        val receivingAccountExists = accountIndex.accounts.any { it.accountId == launch.accountId }
        if (receivingAccountExists && activeSession?.accountId != launch.accountId) {
            accountManager.switchAccount(launch.accountId)
        }
        initialNotificationRoute = if (receivingAccountExists) {
            NotificationRouteResolver.detail(launch.accountId, launch.notificationId)
        } else {
            AppRoute.AccountUnavailable(launch.accountId, launch.notificationId)
        }
        notificationLaunchRouter.clear()
    }
    val motionScheme = palustrisMotionScheme()
    val topLevelScreen = when {
        state.starting || !appPreferences.loaded -> "startup"
        state.account == null || state.addingAccount -> "signin"
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
            else -> key(state.account!!.id) {
                ConnectedSessionHost(
                    accountManager = accountManager,
                    sourceFactory = sourceFactory,
                    sourceRegistry = sourceRegistry,
                    draftStore = draftStore,
                    notificationStreamController = notificationStreamController,
                    account = state.account!!,
                    sessionGeneration = state.sessionGeneration,
                    accountIndex = accountIndex,
                    postPreferences = postPreferences,
                    initialNotificationRoute = initialNotificationRoute,
                    onOpenSettings = { settingsRoute = SettingsRoute.Main; settingsVisible = true },
                )
            }
        }
        }
        if (settingsVisible) {
            SettingsHost(
                state = appPreferences,
                accounts = accountIndex.accounts,
                route = settingsRoute,
                onRoute = { settingsRoute = it },
                 onBack = { settingsVisible = false },
                  onColorScheme = { value -> settingsViewModelUpdate(settingsScope, appPreferencesRepository) { it.copy(colorScheme = value) } },
                  onColorPalette = { value -> settingsViewModelUpdate(settingsScope, appPreferencesRepository) { it.copy(colorScheme = AppColorScheme.Palette, colorPalette = value) } },
                  onBackground = { value -> settingsViewModelUpdate(settingsScope, appPreferencesRepository) { it.copy(background = value) } },
                 onTextSize = { value -> settingsViewModelUpdate(settingsScope, appPreferencesRepository) { it.copy(textSize = value) } },
                 onFont = { value -> settingsViewModelUpdate(settingsScope, appPreferencesRepository) { it.copy(font = value) } },
                 onRequest60Hz = { value -> settingsViewModelUpdate(settingsScope, appPreferencesRepository) { it.copy(request60Hz = value) } },
                 onLanguage = { value -> settingsViewModelUpdate(settingsScope, appPreferencesRepository) { it.copy(language = value) } },
                 onTrackingCleanup = { value -> settingsViewModelUpdate(settingsScope, appPreferencesRepository) { it.copy(cleanTrackingParameters = value) } },
                  onContentWarningRules = { value -> settingsViewModelUpdate(settingsScope, appPreferencesRepository) { it.copy(contentWarningRules = value) } },
                  onHiddenContentPresentation = { value -> settingsViewModelUpdate(settingsScope, appPreferencesRepository) { it.copy(hiddenContentPresentation = value) } },
                 postPreferences = postPreferences,
                 postPreferencesAccountLabel = activeSession?.let { session ->
                     accountIndex.accounts.firstOrNull { it.accountId == session.accountId }?.handle ?: session.accountId.localId
                 } ?: "Current account",
                onNotificationAccount = { accountId -> settingsRoute = SettingsRoute.NotificationAccount(accountId) },
                onModeration = { accountId, kind -> settingsRoute = SettingsRoute.Moderation(accountId, kind) },
                notificationSettingsState = settingsNotificationState,
                onNotificationAlertsEnabled = { enabled -> settingsNotificationModel?.setAlertsEnabled(enabled) },
                onNotificationShowPreviews = { enabled -> settingsNotificationModel?.setShowPreviews(enabled) },
                onNotificationPeriodicFallback = { enabled -> settingsNotificationModel?.setPeriodicFallbackEnabled(enabled) },
                onNotificationQuietHours = { enabled -> settingsNotificationModel?.setQuietHours(enabled) },
                onNotificationCategoryChanged = { category, enabled -> settingsNotificationModel?.setCategoryEnabled(category, enabled) },
                onNotificationLocalTest = { settingsNotificationModel?.runLocalPresentationTest() },
                onNotificationRetryRegistration = { settingsNotificationModel?.retryRegistration() },
                onNotificationPermissionChanged = { settingsNotificationModel?.refreshPermission() },
                onNotificationRefreshDistributors = { settingsNotificationModel?.refreshDistributors() },
                onNotificationSelectDistributor = { packageName -> settingsNotificationModel?.selectDistributor(packageName) },
                onNotificationPushConnectionTest = { settingsNotificationModel?.runPushConnectionTest() },
                moderationState = moderationState,
                onModerationRetry = { moderationModel?.load() },
                  onModerationLoadMore = { moderationModel?.loadMore() },
                  onModerationRemove = { moderationModel?.remove(it) },
                  onModerationAddLocalHashtag = { moderationModel?.addLocalHashtag(it) },
                  onModerationRemoveLocalHashtag = { moderationModel?.removeLocalHashtag(it) },
                 onPostPreferences = { value ->
                     val accountId = activeSession?.accountId
                     settingsScope.launch {
                         runCatching {
                             accountId?.let { postPreferencesRepository.update(it) { value } }
                         }
                     }
                 },
            )
        }
        }
        }
    }
}
