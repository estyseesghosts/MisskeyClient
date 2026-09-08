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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import me.foxtails.palustris.data.AccountSourceRegistry
import me.foxtails.palustris.data.SocialSourceFactory
import me.foxtails.palustris.data.auth.DraftStore
import me.foxtails.palustris.data.notifications.NoOpNotificationStreamController
import me.foxtails.palustris.data.notifications.NotificationStreamController
import me.foxtails.palustris.ui.navigation.AppRoute
import me.foxtails.palustris.ui.notifications.NotificationLaunchRouter
import me.foxtails.palustris.ui.notifications.NotificationRouteResolver
import me.foxtails.palustris.ui.notifications.NotificationSettingsUiState
import me.foxtails.palustris.ui.notifications.NotificationSettingsViewModel
import me.foxtails.palustris.ui.profile.ProfileUiState
import me.foxtails.palustris.ui.profile.ProfileViewModel

@Composable
fun ConnectedApp(
    accountManager: AccountManager,
    sourceFactory: SocialSourceFactory,
    sourceRegistry: AccountSourceRegistry,
    draftStore: DraftStore,
    notificationLaunchRouter: NotificationLaunchRouter,
    notificationStreamController: NotificationStreamController = NoOpNotificationStreamController(),
) {
    val state by accountManager.session.collectAsStateWithLifecycle()
    val accountIndex by accountManager.accountIndex.collectAsStateWithLifecycle()
    val activeSession by accountManager.activeSession.collectAsStateWithLifecycle()
    val pendingNotificationLaunch by notificationLaunchRouter.pending.collectAsStateWithLifecycle()
    var initialNotificationRoute by remember { mutableStateOf<AppRoute?>(null) }
    val sharedSource = activeSession?.let { session ->
        sourceRegistry.sourceFor(session.accountId) ?: sourceFactory.create(session)
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(activeSession?.accountId, lifecycleOwner) {
        val accountId = activeSession?.accountId
        val observer = LifecycleEventObserver { _, event ->
            if (accountId == null) return@LifecycleEventObserver
            when (event) {
                Lifecycle.Event.ON_START -> notificationStreamController.start(accountId)
                Lifecycle.Event.ON_STOP -> notificationStreamController.stop(accountId)
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (accountId != null && lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            notificationStreamController.start(accountId)
        }
        onDispose {
            if (accountId != null) notificationStreamController.stop(accountId)
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    val feedModel = activeSession?.let { session ->
        hiltViewModel<FeedViewModel, FeedViewModel.Factory>(
            key = "feed-${session.accountId}-${state.sessionGeneration}",
            creationCallback = { factory ->
                factory.create(session.accountId, sharedSource!!)
            },
        )
    }
    val notificationsModel = activeSession?.let { session ->
        hiltViewModel<NotificationsViewModel, NotificationsViewModel.Factory>(
            key = "notifications-${session.accountId}-${state.sessionGeneration}",
            creationCallback = { factory ->
                factory.create(session.accountId, sharedSource!!)
            },
        )
    }
    val notificationSettingsModel = activeSession?.let { session ->
        hiltViewModel<NotificationSettingsViewModel, NotificationSettingsViewModel.Factory>(
            key = "notification-settings-${session.accountId}-${state.sessionGeneration}",
            creationCallback = { factory -> factory.create(session.accountId) },
        )
    }
    DisposableEffect(state.sessionGeneration, feedModel) {
        onDispose { feedModel?.stop() }
    }
    val feed by if (feedModel != null) feedModel.feed.collectAsStateWithLifecycle()
    else remember { mutableStateOf(FeedState()) }
    val notificationState by if (notificationsModel != null) notificationsModel.state.collectAsStateWithLifecycle()
    else remember { mutableStateOf(NotificationsUiState()) }
    val notificationSettingsState by if (notificationSettingsModel != null) notificationSettingsModel.state.collectAsStateWithLifecycle()
    else remember { mutableStateOf(NotificationSettingsUiState()) }
    val profileModel = activeSession?.let { session ->
        hiltViewModel<ProfileViewModel, ProfileViewModel.Factory>(
            key = "profile-${session.accountId}-${state.sessionGeneration}",
            creationCallback = { factory -> factory.create(session.accountId, sharedSource!!) },
        )
    }
    DisposableEffect(state.sessionGeneration, profileModel) {
        onDispose { profileModel?.stop() }
    }
    val profileState by if (profileModel != null) profileModel.state.collectAsStateWithLifecycle()
    else remember { mutableStateOf(ProfileUiState()) }
    LaunchedEffect(profileState.account, state.account) {
        profileState.account
            ?.takeIf { it.id == state.account?.id && it != state.account }
            ?.let(accountManager::updateAccount)
    }
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
        initialNotificationRoute = if (accountIndex.accounts.any { it.accountId == launch.accountId }) {
            NotificationRouteResolver.detail(launch.accountId, launch.notificationId)
        } else {
            AppRoute.AccountUnavailable(launch.accountId, launch.notificationId)
        }
        notificationLaunchRouter.clear()
    }
    when {
        state.starting -> PalustrisTheme { Surface(Modifier.fillMaxSize()) {
            Box(contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } }
        state.account == null || state.addingAccount -> PalustrisTheme {
            key(state.addingAccount) {
                SignInScreen(state, accountManager::signIn, accountManager::finishSignIn, accountManager::reopenBrowser, accountManager::cancelSignIn)
            }
        }
        else -> key(state.account!!.id) {
            PalustrisApp(
                account = state.account,
                feedState = feed,
                profile = feed.profile ?: state.account,
                onRefresh = { timeline -> feedModel?.refresh(timeline) },
                onLoadMore = { timeline -> feedModel?.loadMore(timeline) },
                onSignOut = accountManager::signOut,
                accounts = accountIndex.accounts,
                onSwitchAccount = accountManager::switchAccount,
                onAddAccount = accountManager::beginAddAccount,
                onPublish = { request, onSuccess -> feedModel?.create(request, onSuccess) },
                onUpdateProfile = { request, onSuccess -> feedModel?.updateProfile(request) { updated -> accountManager.updateAccount(updated); onSuccess() } },
                onSearchAccounts = feedModel?.let { model -> { query -> model.search(query) } } ?: {},
                onLoadMoreSearch = feedModel?.let { model -> { model.loadMoreSearch() } } ?: {},
                draftStore = draftStore,
                ownedPosts = feed.ownedPosts,
                onReact = { ownedPost -> feedModel?.favorite(ownedPost) },
                onReshare = { ownedPost -> feedModel?.reshare(ownedPost) },
                onReaction = { ownedPost, emoji -> feedModel?.react(ownedPost, emoji) },
                notificationState = notificationState,
                onRefreshNotifications = { notificationsModel?.refresh() },
                onLoadMoreNotifications = { notificationsModel?.loadOlder() },
                onMarkAllNotificationsRead = { notificationsModel?.markAllRead() },
                onMarkNotificationSeen = { notification -> notificationsModel?.markSeen(notification?.id) },
                onDismissNotification = { notification -> notificationsModel?.dismiss(notification) },
                onFollowRequest = { notification, accept -> notificationsModel?.respondToFollowRequest(notification, accept) },
                onSelectNotificationQuery = { query -> notificationsModel?.selectQuery(query) },
                initialNotificationRoute = initialNotificationRoute,
                notificationSettingsState = notificationSettingsState,
                onNotificationAlertsEnabled = { enabled -> notificationSettingsModel?.setAlertsEnabled(enabled) },
                onNotificationShowPreviews = { enabled -> notificationSettingsModel?.setShowPreviews(enabled) },
                onNotificationPeriodicFallback = { enabled -> notificationSettingsModel?.setPeriodicFallbackEnabled(enabled) },
                onNotificationQuietHours = { enabled -> notificationSettingsModel?.setQuietHours(enabled) },
                onNotificationCategoryChanged = { category, enabled ->
                    notificationSettingsModel?.setCategoryEnabled(category, enabled)
                },
                onNotificationLocalTest = { notificationSettingsModel?.runLocalPresentationTest() },
                onNotificationPermissionChanged = { notificationSettingsModel?.refreshPermission() },
                profileState = profileState,
                onProfileShown = { seed -> profileModel?.open(seed) },
                onProfileCategorySelected = { category -> profileModel?.selectCategory(category) },
                onRefreshProfile = { profileModel?.refresh() },
                onLoadMoreProfile = { profileModel?.loadMoreSelected() },
                onFollowProfile = { profileModel?.follow() },
                onUnfollowProfile = { profileModel?.unfollow() },
                onUpdateProfile = { request, onSuccess ->
                    profileModel?.updateSelf(request) { updated ->
                        accountManager.updateAccount(updated)
                        onSuccess()
                    }
                },
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
                Text(
                    when {
                        state.pending -> "One more step"
                        state.addingAccount -> "Add another account"
                        else -> "Welcome!"
                    },
                    style = MaterialTheme.typography.headlineLarge,
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    if (state.pending) "Approve Palustris in your browser on ${state.origin?.removePrefix("https://")}. Then return here to open your home feed."
                    else if (state.addingAccount) "Sign in to another account. Your current account will stay connected."
                    else "To get started, enter your home instance’s domain name below.",
                    style = MaterialTheme.typography.bodyLarge,
                )
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
                    TextButton(onClick = onCancel, enabled = !state.busy) {
                        Text(if (state.addingAccount) "Cancel" else "Use a different instance")
                    }
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
