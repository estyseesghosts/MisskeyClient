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
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import me.foxtails.palustris.data.AccountSourceRegistry
import me.foxtails.palustris.R
import me.foxtails.palustris.data.SocialSourceFactory
import me.foxtails.palustris.data.auth.DraftStore
import me.foxtails.palustris.data.notifications.NoOpNotificationStreamController
import me.foxtails.palustris.data.notifications.NotificationStreamController
import me.foxtails.palustris.domain.EmojiCapabilities
import me.foxtails.palustris.ui.emoji.EmojiCatalogState
import me.foxtails.palustris.ui.emoji.EmojiCatalogViewModel
import me.foxtails.palustris.ui.navigation.AppRoute
import me.foxtails.palustris.ui.notifications.NotificationLaunchRouter
import me.foxtails.palustris.ui.notifications.NotificationRouteResolver
import me.foxtails.palustris.ui.notifications.NotificationSettingsUiState
import me.foxtails.palustris.ui.notifications.NotificationSettingsViewModel
import me.foxtails.palustris.ui.profile.ProfileUiState
import me.foxtails.palustris.ui.profile.ProfileViewModel
import me.foxtails.palustris.ui.directmessages.DirectMessageUiState
import me.foxtails.palustris.ui.directmessages.DirectMessageViewModel
import me.foxtails.palustris.ui.motion.AnimatedStatePane
import me.foxtails.palustris.ui.motion.LocalPalustrisMotionScheme
import me.foxtails.palustris.ui.motion.palustrisMotionScheme
import me.foxtails.palustris.ui.motion.springPress

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
    val directMessagesModel = activeSession?.let { session ->
        hiltViewModel<DirectMessageViewModel, DirectMessageViewModel.Factory>(
            key = "direct-messages-${session.accountId}-${state.sessionGeneration}",
            creationCallback = { factory -> factory.create(session.accountId, sharedSource!!) },
        )
    }
    val savedPostsModel = activeSession?.let { session ->
        hiltViewModel<SavedPostsViewModel, SavedPostsViewModel.Factory>(
            key = "saved-posts-${session.accountId}-${state.sessionGeneration}",
            creationCallback = { factory -> factory.create(session.accountId, sharedSource!!) },
        )
    }
    val notificationSettingsModel = activeSession?.let { session ->
        hiltViewModel<NotificationSettingsViewModel, NotificationSettingsViewModel.Factory>(
            key = "notification-settings-${session.accountId}-${state.sessionGeneration}",
            creationCallback = { factory -> factory.create(session.accountId) },
        )
    }
    DisposableEffect(state.sessionGeneration, feedModel, savedPostsModel) {
        onDispose {
            feedModel?.stop()
            savedPostsModel?.stop()
        }
    }
    val feed by if (feedModel != null) feedModel.feed.collectAsStateWithLifecycle()
    else remember { mutableStateOf(FeedState()) }
    val notificationState by if (notificationsModel != null) notificationsModel.state.collectAsStateWithLifecycle()
    else remember { mutableStateOf(NotificationsUiState()) }
    val directMessageState by if (directMessagesModel != null) directMessagesModel.state.collectAsStateWithLifecycle()
    else remember { mutableStateOf(DirectMessageUiState()) }
    val savedPostsState by if (savedPostsModel != null) savedPostsModel.state.collectAsStateWithLifecycle()
    else remember { mutableStateOf<SavedPostsUiState?>(null) }
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
    val emojiCatalogModel = activeSession?.let { session ->
        hiltViewModel<EmojiCatalogViewModel, EmojiCatalogViewModel.Factory>(
            key = "emoji-catalog-${session.accountId}-${state.sessionGeneration}",
            creationCallback = { factory -> factory.create(session.accountId, sharedSource!!) },
        )
    }
    val emojiCatalogState by if (emojiCatalogModel != null) emojiCatalogModel.state.collectAsStateWithLifecycle()
    else remember { mutableStateOf(EmojiCatalogState()) }
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
        state.starting -> "startup"
        state.account == null || state.addingAccount -> "signin"
        else -> "app"
    }
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
            "startup" -> PalustrisTheme { Surface(Modifier.fillMaxSize()) {
                Box(contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } }
            "signin" -> PalustrisTheme {
                key(state.addingAccount) {
                    SignInScreen(state, accountManager::signIn, accountManager::finishSignIn, accountManager::reopenBrowser, accountManager::cancelSignIn)
                }
            }
            else -> key(state.account!!.id) {
            PalustrisApp(
                account = state.account,
                feedState = feed,
                onRefresh = { timeline -> feedModel?.refresh(timeline) },
                onLoadMore = { timeline -> feedModel?.loadMore(timeline) },
                onSignOut = accountManager::signOut,
                accounts = accountIndex.accounts,
                onSwitchAccount = accountManager::switchAccount,
                onAddAccount = accountManager::beginAddAccount,
                onPublish = { request, onSuccess -> feedModel?.create(request, onSuccess) },
                onSearchAccounts = feedModel?.let { model -> { query -> model.search(query) } } ?: {},
                onLoadMoreSearch = feedModel?.let { model -> { model.loadMoreSearch() } } ?: {},
                draftStore = draftStore,
                ownedPosts = feed.ownedPosts,
                onReact = { ownedPost -> feedModel?.favorite(ownedPost) },
                onReshare = { ownedPost -> feedModel?.reshare(ownedPost) },
                onBookmark = { ownedPost -> feedModel?.bookmark(ownedPost) },
                onReaction = { ownedPost, emoji -> feedModel?.react(ownedPost, emoji) },
                savedPostsState = savedPostsState,
                onRefreshSavedPosts = { savedPostsModel?.refresh() },
                onLoadMoreSavedPosts = { savedPostsModel?.loadMore() },
                onUnsaveSavedPost = { ownedPost -> savedPostsModel?.unsave(ownedPost) },
                onUpgradeSavedPermissions = { state.account?.id?.let(accountManager::upgradePermissions) },
                notificationState = notificationState,
                onRefreshNotifications = { notificationsModel?.refresh() },
                onLoadMoreNotifications = { notificationsModel?.loadOlder() },
                onMarkAllNotificationsRead = { notificationsModel?.markAllRead() },
                onMarkNotificationSeen = { notification -> notificationsModel?.markSeen(notification?.id) },
                 onDismissNotification = { notification -> notificationsModel?.dismiss(notification) },
                 onFollowRequest = { notification, accept -> notificationsModel?.respondToFollowRequest(notification, accept) },
                 directMessageState = directMessageState,
                 onRefreshDirectMessages = { directMessagesModel?.refresh() },
                 onLoadMoreDirectMessages = { directMessagesModel?.loadMore() },
                 onOpenDirectConversation = { conversation -> directMessagesModel?.openConversation(conversation) },
                 onBackDirectConversation = { directMessagesModel?.closeConversation() },
                 onStartDirectConversation = { profile -> directMessagesModel?.startConversation(profile) },
                 onSendDirectMessage = { text -> directMessagesModel?.send(text) },
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
                onNotificationRetryRegistration = { notificationSettingsModel?.retryRegistration() },
                onNotificationPermissionChanged = { notificationSettingsModel?.refreshPermission() },
                onNotificationRefreshDistributors = { notificationSettingsModel?.refreshDistributors() },
                onNotificationSelectDistributor = { packageName -> notificationSettingsModel?.selectDistributor(packageName) },
                onNotificationPushConnectionTest = { notificationSettingsModel?.runPushConnectionTest() },
                profileState = profileState,
                onProfileShown = { seed -> profileModel?.open(seed) },
                onProfileCategorySelected = { category -> profileModel?.selectCategory(category) },
                onRefreshProfile = { profileModel?.refresh() },
                onLoadMoreProfile = { profileModel?.loadMoreSelected() },
                onFollowProfile = { profileModel?.follow() },
                onUnfollowProfile = { profileModel?.unfollow() },
                onUpdateProfile = { patch, onSuccess ->
                    profileModel?.saveEditor(patch) { updated ->
                        accountManager.updateAccount(updated)
                        onSuccess()
                    }
                },
                onOpenProfileEditor = { profileModel?.openEditor() },
                onCloseEditor = { profileModel?.closeEditor() },
                emojiCatalogState = emojiCatalogState,
                emojiCapabilities = sharedSource?.capabilities?.emoji ?: EmojiCapabilities(),
                 onLoadEmojiCatalog = { emojiCatalogModel?.loadIfNeeded() },
                 onRetryEmojiCatalog = { emojiCatalogModel?.retry() },
                onSavedPostReaction = { ownedPost, choice -> savedPostsModel?.react(ownedPost, choice) },
                onProfilePostReaction = { ownedPost, choice -> profileModel?.react(ownedPost, choice) },
            )
            }
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
    val scheme = LocalPalustrisMotionScheme.current
    Scaffold(
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
                Column(Modifier.navigationBarsPadding().imePadding().padding(16.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Button(onClick = { if (state.pending) onComplete() else onNext(domain) },
                        enabled = !state.busy && (domain.isNotBlank() || state.pending),
                        modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth().height(56.dp)) {
                        AnimatedContent(
                            targetState = state.busy to state.pending,
                            transitionSpec = {
                                if (scheme.reducedMotion) EnterTransition.None togetherWith ExitTransition.None
                                else fadeIn(scheme.fastFadeIn) togetherWith fadeOut(scheme.fastFadeOut)
                            },
                            label = "signInButtonContent",
                        ) { (busy, pending) ->
                            if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            else Text(stringResource(if (pending) R.string.sign_in_button_authorized else R.string.sign_in_button_next))
                        }
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = 560.dp).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
                Spacer(Modifier.height(32.dp))
                Text(stringResource(R.string.app_name), Modifier.align(Alignment.CenterHorizontally), style = MaterialTheme.typography.headlineLarge)
                Spacer(Modifier.height(40.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.primaryContainer) {
                        Icon(AppIcons.Globe, null, Modifier.padding(16.dp).size(28.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(stringResource(R.string.signin_fediverse_title), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.signin_fediverse_subtitle), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.height(24.dp))
                AnimatedStatePane(stateKey = state.pending, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        when {
                            state.pending -> stringResource(R.string.sign_in_pending_title)
                            state.addingAccount -> stringResource(R.string.sign_in_add_account_title)
                            else -> stringResource(R.string.sign_in_title)
                        },
                        style = MaterialTheme.typography.headlineLarge,
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        if (state.pending) stringResource(
                            R.string.sign_in_pending_description,
                            stringResource(R.string.app_name),
                            state.origin?.removePrefix("https://").orEmpty(),
                        )
                        else if (state.addingAccount) stringResource(R.string.sign_in_add_account_description)
                        else stringResource(R.string.sign_in_description),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
                Spacer(Modifier.height(24.dp))
                    if (!state.pending) {
                    OutlinedTextField(value = domain, onValueChange = { domain = it }, enabled = !state.busy,
                        label = { Text(stringResource(R.string.sign_in_instance_url)) },
                        placeholder = { Text(stringResource(R.string.sign_in_instance_placeholder)) },
                        leadingIcon = { Icon(AppIcons.Globe, null) }, singleLine = true, shape = CircleShape,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(28.dp))
                    Text(stringResource(R.string.sign_in_popular_instances), style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.sign_in_choose_instance), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(12.dp))
                    suggestedInstances.chunked(2).forEach { pair ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            pair.forEach { (host, software) ->
                                val interactionSource = remember(host) { MutableInteractionSource() }
                                val selected = domain == host
                                OutlinedButton(
                                    onClick = { domain = host },
                                    enabled = !state.busy,
                                    interactionSource = interactionSource,
                                    modifier = Modifier.weight(1f).springPress(interactionSource),
                                    shape = MaterialTheme.shapes.large,
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 12.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
                                    ),
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        AnimatedContent(
                                            targetState = selected,
                                            transitionSpec = {
                                                if (scheme.reducedMotion) EnterTransition.None togetherWith ExitTransition.None
                                                else fadeIn(scheme.fastFadeIn) togetherWith fadeOut(scheme.fastFadeOut)
                                            },
                                            label = "suggestedInstanceSelection",
                                        ) { isSelected ->
                                            if (isSelected) Icon(AppIcons.Check, null, Modifier.size(16.dp))
                                        }
                                        Spacer(Modifier.width(if (selected) 4.dp else 0.dp))
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(host, style = MaterialTheme.typography.labelLarge)
                                            Text(software, style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                    } else {
                        OutlinedButton(onClick = onReopen, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.sign_in_open_browser_again)) }
                    TextButton(onClick = onCancel, enabled = !state.busy) {
                        Text(stringResource(if (state.addingAccount) R.string.sign_in_cancel else R.string.sign_in_different_instance))
                    }
                    }
                AnimatedStatePane(stateKey = state.error != null, modifier = Modifier.fillMaxWidth()) {
                    state.error?.let {
                        Spacer(Modifier.height(16.dp))
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}
