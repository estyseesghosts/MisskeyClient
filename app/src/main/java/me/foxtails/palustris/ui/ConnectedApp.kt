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
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import me.foxtails.palustris.data.AccountSourceRegistry
import me.foxtails.palustris.R
import me.foxtails.palustris.data.SocialSourceFactory
import me.foxtails.palustris.data.auth.DraftStore
import me.foxtails.palustris.data.notifications.NoOpNotificationStreamController
import me.foxtails.palustris.data.notifications.NotificationStreamController
import me.foxtails.palustris.domain.EmojiCapabilities
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.NotificationCategory
import me.foxtails.palustris.domain.Notification
import me.foxtails.palustris.domain.NotificationQuery
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.DirectConversation
import me.foxtails.palustris.domain.EditableProfilePatch
import me.foxtails.palustris.domain.CreatePostRequest
import me.foxtails.palustris.domain.EntityId
import me.foxtails.palustris.ui.PhotoGridFeed
import me.foxtails.palustris.domain.EmojiChoice
import me.foxtails.palustris.domain.OwnedPost
import me.foxtails.palustris.domain.AppColorScheme
import me.foxtails.palustris.domain.AppPreferencesRepository
import me.foxtails.palustris.domain.AppPreferencesState
import me.foxtails.palustris.domain.PostPreferences
import me.foxtails.palustris.domain.PostPreferencesRepository
import me.foxtails.palustris.data.preferences.InMemoryAppPreferencesRepository
import me.foxtails.palustris.ui.emoji.EmojiCatalogState
import me.foxtails.palustris.ui.emoji.EmojiCatalogViewModel
import me.foxtails.palustris.ui.navigation.AppRoute
import me.foxtails.palustris.ui.notifications.NotificationLaunchRouter
import me.foxtails.palustris.ui.notifications.NotificationRouteResolver
import me.foxtails.palustris.ui.notifications.NotificationSettingsUiState
import me.foxtails.palustris.ui.notifications.NotificationSettingsViewModel
import me.foxtails.palustris.ui.settings.SettingsHost
import me.foxtails.palustris.ui.shell.AccountSwitcher
import me.foxtails.palustris.ui.shell.BookmarksContract
import me.foxtails.palustris.ui.shell.DirectMessagesContract
import me.foxtails.palustris.ui.shell.EmojiPresentation
import me.foxtails.palustris.ui.shell.LikesContract
import me.foxtails.palustris.ui.shell.NotificationSettingsContract
import me.foxtails.palustris.ui.shell.NotificationsContract
import me.foxtails.palustris.ui.shell.PhotoGridContract
import me.foxtails.palustris.ui.shell.ProfileContract
import me.foxtails.palustris.ui.shell.PostProjectionCoordinator
import me.foxtails.palustris.ui.shell.ThreadContract
import me.foxtails.palustris.ui.settings.ModerationViewModel
import me.foxtails.palustris.ui.settings.ModerationKind
import me.foxtails.palustris.domain.ModerationListKind
import me.foxtails.palustris.ui.settings.SettingsRoute
import me.foxtails.palustris.ui.profile.ProfileCategory
import me.foxtails.palustris.ui.profile.ProfileUiState
import me.foxtails.palustris.ui.profile.ProfileViewModel
import me.foxtails.palustris.ui.thread.PostThreadUiState
import me.foxtails.palustris.ui.thread.PostThreadViewModel
import me.foxtails.palustris.ui.directmessages.DirectMessageUiState
import me.foxtails.palustris.ui.directmessages.DirectMessageViewModel
import me.foxtails.palustris.ui.motion.AnimatedStatePane
import me.foxtails.palustris.ui.motion.LocalPalustrisMotionScheme
import me.foxtails.palustris.ui.motion.palustrisMotionScheme
import me.foxtails.palustris.ui.motion.springPress

private fun settingsViewModelUpdate(
    scope: CoroutineScope,
    repository: AppPreferencesRepository,
    transform: (me.foxtails.palustris.domain.AppPreferences) -> me.foxtails.palustris.domain.AppPreferences,
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
    me.foxtails.palustris.ui.links.ExternalLinkHandler.cleanTrackingParameters = appPreferences.preferences.cleanTrackingParameters
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
    val accountSwitcherActions = remember(accountManager) {
        object : AccountSwitcher.Actions {
            override fun switchTo(accountId: AccountId) = accountManager.switchAccount(accountId)
            override fun addAccount() = accountManager.beginAddAccount()
            override fun openSettings() {
                settingsRoute = SettingsRoute.Main
                settingsVisible = true
            }
            override fun signOut() = accountManager.signOut()
        }
    }
    val accountSwitcher = remember(accountIndex.accounts, accountSwitcherActions) {
        AccountSwitcher(accounts = accountIndex.accounts, actions = accountSwitcherActions)
    }
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
                factory.create(session.accountId, sharedSource!!, session.sessionRevision)
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
            creationCallback = { factory ->
                factory.create(session.accountId, sharedSource!!, SavedPostsCollection.Bookmarks, session.sessionRevision)
            },
        )
    }
    val likedPostsModel = activeSession?.let { session ->
        hiltViewModel<SavedPostsViewModel, SavedPostsViewModel.Factory>(
            key = "liked-posts-${session.accountId}-${state.sessionGeneration}",
            creationCallback = { factory ->
                factory.create(session.accountId, sharedSource!!, SavedPostsCollection.Likes, session.sessionRevision)
            },
        )
    }
    val notificationSettingsModel = activeSession?.let { session ->
        hiltViewModel<NotificationSettingsViewModel, NotificationSettingsViewModel.Factory>(
            key = "notification-settings-${session.accountId}-${state.sessionGeneration}",
            creationCallback = { factory -> factory.create(session.accountId) },
        )
    }
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
    DisposableEffect(state.sessionGeneration, feedModel, savedPostsModel, likedPostsModel) {
        onDispose {
            feedModel?.stop()
            savedPostsModel?.stop()
            likedPostsModel?.stop()
        }
    }
    val feed by if (feedModel != null) feedModel.feed.collectAsStateWithLifecycle()
    else remember { mutableStateOf(FeedState()) }
    val photoGridFeed by if (feedModel != null) feedModel.photoGridFeed.collectAsStateWithLifecycle()
    else remember { mutableStateOf(PhotoGridFeedState()) }
    val photoGridActions = remember(feedModel) {
        object : PhotoGridContract.Actions {
            override fun ensureLoaded() { feedModel?.ensurePhotoGridLoaded() }
            override fun selectFeed(feed: PhotoGridFeed) { feedModel?.selectPhotoGridFeed(feed) }
            override fun refresh() { feedModel?.refreshPhotoGrid() }
            override fun loadMore() { feedModel?.loadMorePhotoGrid() }
            override fun addHashtag(value: String, onSuccess: () -> Unit) { feedModel?.addPhotoGridHashtag(value, onSuccess) }
            override fun clearPreferenceError() { feedModel?.clearPhotoGridPreferenceError() }
        }
    }
    val photoGrid = remember(photoGridFeed, photoGridActions) {
        PhotoGridContract(state = photoGridFeed, actions = photoGridActions)
    }
    val notificationState by if (notificationsModel != null) notificationsModel.state.collectAsStateWithLifecycle()
    else remember { mutableStateOf(NotificationsUiState()) }
    val notificationsActions = remember(notificationsModel) {
        object : NotificationsContract.Actions {
            override fun refresh() { notificationsModel?.refresh() }
            override fun loadMore() { notificationsModel?.loadOlder() }
            override fun markAllRead() { notificationsModel?.markAllRead() }
            override fun markSeen(notification: Notification?) { notificationsModel?.markSeen(notification?.id) }
            override fun dismiss(notification: Notification) { notificationsModel?.dismiss(notification) }
            override fun respondToFollowRequest(notification: Notification, accept: Boolean) {
                notificationsModel?.respondToFollowRequest(notification, accept)
            }
            override fun selectQuery(query: NotificationQuery) { notificationsModel?.selectQuery(query) }
        }
    }
    val notifications = remember(notificationState, notificationsActions) {
        NotificationsContract(notificationState, notificationsActions)
    }
    val directMessageState by if (directMessagesModel != null) directMessagesModel.state.collectAsStateWithLifecycle()
    else remember { mutableStateOf(DirectMessageUiState()) }
    val directMessagesActions = remember(directMessagesModel) {
        object : DirectMessagesContract.Actions {
            override fun refresh() { directMessagesModel?.refresh() }
            override fun loadMore() { directMessagesModel?.loadMore() }
            override fun openConversation(conversation: DirectConversation) {
                directMessagesModel?.openConversation(conversation)
            }
            override fun closeConversation() { directMessagesModel?.closeConversation() }
            override fun startConversation(account: Account) { directMessagesModel?.startConversation(account) }
            override fun send(text: String) { directMessagesModel?.send(text) }
        }
    }
    val directMessages = remember(directMessageState, directMessagesActions) {
        DirectMessagesContract(directMessageState, directMessagesActions)
    }
    val savedPostsState by if (savedPostsModel != null) savedPostsModel.state.collectAsStateWithLifecycle()
    else remember { mutableStateOf<SavedPostsUiState?>(null) }
    val likedPostsState by if (likedPostsModel != null) likedPostsModel.state.collectAsStateWithLifecycle()
    else remember { mutableStateOf<SavedPostsUiState?>(null) }
    val bookmarksActions = remember(savedPostsModel, accountManager) {
        object : BookmarksContract.Actions {
            override fun refresh() { savedPostsModel?.refresh() }
            override fun loadMore() { savedPostsModel?.loadMore() }
            override fun remove(post: OwnedPost) { savedPostsModel?.unsave(post) }
            override fun upgradePermissions() { state.account?.id?.let(accountManager::upgradePermissions) }
            override fun react(post: OwnedPost, choice: EmojiChoice) { savedPostsModel?.react(post, choice) }
        }
    }
    val bookmarks = remember(savedPostsState, bookmarksActions) {
        BookmarksContract(state = savedPostsState, actions = bookmarksActions)
    }
    val likesActions = remember(likedPostsModel, feedModel) {
        object : LikesContract.Actions {
            override fun refresh() { likedPostsModel?.refresh() }
            override fun loadMore() { likedPostsModel?.loadMore() }
            override fun toggle(post: OwnedPost) { likedPostsModel?.toggleFavourite(post) }
            override fun react(post: OwnedPost, choice: EmojiChoice) { feedModel?.react(post, choice) }
        }
    }
    val likes = remember(likedPostsState, likesActions) {
        LikesContract(state = likedPostsState, actions = likesActions)
    }
    val notificationSettingsState by if (notificationSettingsModel != null) notificationSettingsModel.state.collectAsStateWithLifecycle()
    else remember { mutableStateOf(NotificationSettingsUiState()) }
    val notificationSettingsActions = remember(notificationSettingsModel) {
        object : NotificationSettingsContract.Actions {
            override fun setAlertsEnabled(enabled: Boolean) { notificationSettingsModel?.setAlertsEnabled(enabled) }
            override fun setShowPreviews(enabled: Boolean) { notificationSettingsModel?.setShowPreviews(enabled) }
            override fun setPeriodicFallback(enabled: Boolean) { notificationSettingsModel?.setPeriodicFallbackEnabled(enabled) }
            override fun setQuietHours(enabled: Boolean) { notificationSettingsModel?.setQuietHours(enabled) }
            override fun setCategoryEnabled(category: NotificationCategory, enabled: Boolean) {
                notificationSettingsModel?.setCategoryEnabled(category, enabled)
            }
            override fun runLocalTest() { notificationSettingsModel?.runLocalPresentationTest() }
            override fun retryRegistration() { notificationSettingsModel?.retryRegistration() }
            override fun refreshPermission() { notificationSettingsModel?.refreshPermission() }
            override fun refreshDistributors() { notificationSettingsModel?.refreshDistributors() }
            override fun selectDistributor(packageName: String) { notificationSettingsModel?.selectDistributor(packageName) }
            override fun runPushConnectionTest() { notificationSettingsModel?.runPushConnectionTest() }
        }
    }
    val notificationSettings = remember(activeSession?.accountId, notificationSettingsState, notificationSettingsActions) {
        NotificationSettingsContract(
            accountId = activeSession?.accountId,
            state = notificationSettingsState,
            actions = notificationSettingsActions,
        )
    }
    val settingsNotificationState by if (settingsNotificationModel != null) settingsNotificationModel.state.collectAsStateWithLifecycle()
    else remember { mutableStateOf(NotificationSettingsUiState()) }
    val moderationState by if (moderationModel != null) moderationModel.state.collectAsStateWithLifecycle()
    else remember { mutableStateOf(me.foxtails.palustris.ui.settings.ModerationUiState()) }
    val profileModel = activeSession?.let { session ->
        hiltViewModel<ProfileViewModel, ProfileViewModel.Factory>(
            key = "profile-${session.accountId}-${state.sessionGeneration}",
            creationCallback = { factory -> factory.create(session.accountId, sharedSource!!, session.sessionRevision) },
        )
    }
    val threadModel = activeSession?.let { session ->
        hiltViewModel<PostThreadViewModel, PostThreadViewModel.Factory>(
            key = "thread-${session.accountId}-${state.sessionGeneration}",
            creationCallback = { factory ->
                factory.create(session.accountId, sharedSource!!, session.sessionRevision)
            },
        )
    }
    val threadState by if (threadModel != null) threadModel.state.collectAsStateWithLifecycle()
    else remember { mutableStateOf(PostThreadUiState()) }
    val threadActions = remember(threadModel) {
        object : ThreadContract.Actions {
            override fun activate(post: OwnedPost?, enabled: Boolean) { threadModel?.activate(post, enabled) }
            override fun deactivate() { threadModel?.deactivate() }
            override fun refresh() { threadModel?.refresh() }
            override fun continueAcquisition() { threadModel?.continueAcquisition() }
            override fun favorite(post: OwnedPost) { threadModel?.favorite(post) }
            override fun repost(post: OwnedPost) { threadModel?.reshare(post) }
            override fun bookmark(post: OwnedPost) { threadModel?.bookmark(post) }
            override fun react(post: OwnedPost, choice: EmojiChoice) { threadModel?.react(post, choice) }
        }
    }
    val thread = remember(threadState, threadActions) {
        ThreadContract(state = threadState, actions = threadActions)
    }
    DisposableEffect(state.sessionGeneration, threadModel) {
        onDispose { threadModel?.stop() }
    }
    val projectionCoordinator = remember(activeSession?.accountId, state.sessionGeneration) {
        PostProjectionCoordinator(activeSession?.accountId, activeSession?.sessionRevision ?: 0L)
    }
    val feedSink = remember(feedModel) {
        feedModel?.let { model ->
            object : PostProjectionCoordinator.Sink {
                override fun applyExternalPost(updated: OwnedPost) { model.applyExternalPost(updated) }
                override fun applyPublishedPost(request: CreatePostRequest) { model.applyPublishedPost(request) }
            }
        }
    }
    val savedSink = remember(savedPostsModel) {
        savedPostsModel?.let { model ->
            object : PostProjectionCoordinator.Sink {
                override fun applyExternalPost(updated: OwnedPost) { model.applyExternalPost(updated) }
                override fun applyPublishedPost(request: CreatePostRequest) { model.applyPublishedPost(request) }
            }
        }
    }
    val likedSink = remember(likedPostsModel) {
        likedPostsModel?.let { model ->
            object : PostProjectionCoordinator.Sink {
                override fun applyExternalPost(updated: OwnedPost) { model.applyExternalPost(updated) }
                override fun applyPublishedPost(request: CreatePostRequest) { model.applyPublishedPost(request) }
            }
        }
    }
    val profileSink = remember(profileModel) {
        profileModel?.let { model ->
            object : PostProjectionCoordinator.Sink {
                override fun applyExternalPost(updated: OwnedPost) { model.applyExternalPost(updated) }
                override fun applyPublishedPost(request: CreatePostRequest) { model.applyPublishedPost(request) }
            }
        }
    }
    val notificationsSink = remember(notificationsModel) {
        notificationsModel?.let { model ->
            object : PostProjectionCoordinator.Sink {
                override fun applyExternalPost(updated: OwnedPost) { model.applyExternalPost(updated) }
                override fun applyPublishedPost(request: CreatePostRequest) { model.applyPublishedPost(request) }
            }
        }
    }
    val threadSink = remember(threadModel) {
        threadModel?.let { model ->
            object : PostProjectionCoordinator.Sink {
                override fun applyExternalPost(updated: OwnedPost) { model.applyExternalPost(updated) }
                override fun acceptPublishedReply(created: OwnedPost) { model.acceptPublishedReply(created) }
                override fun acceptPublishedQuote(target: EntityId?) { model.acceptPublishedQuote(target) }
            }
        }
    }
    DisposableEffect(projectionCoordinator, feedSink, savedSink, likedSink, profileSink, notificationsSink, threadSink, feedModel, threadModel) {
        val sinks = listOfNotNull(feedSink, savedSink, likedSink, profileSink, notificationsSink, threadSink)
        sinks.forEach(projectionCoordinator::register)
        val feedProjection: ((OwnedPost) -> Unit)? = feedSink?.let { sink ->
            { updated -> projectionCoordinator.forwardExternalPost(sink, updated) }
        }
        feedProjection?.let { listener -> feedModel?.addPostProjectionListener(listener) }
        val threadProjection: ((OwnedPost) -> Unit)? = threadSink?.let { sink ->
            { updated -> projectionCoordinator.forwardExternalPost(sink, updated) }
        }
        threadModel?.setPostUpdateListener(threadProjection)
        onDispose {
            threadModel?.setPostUpdateListener(null)
            feedProjection?.let { listener -> feedModel?.removePostProjectionListener(listener) }
            sinks.forEach(projectionCoordinator::unregister)
        }
    }
    DisposableEffect(threadModel, lifecycleOwner) {
        if (threadModel == null) return@DisposableEffect onDispose {}
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> threadModel.setForeground(true)
                Lifecycle.Event.ON_STOP -> threadModel.setForeground(false)
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        threadModel.setForeground(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            threadModel.setForeground(false)
        }
    }
    DisposableEffect(state.sessionGeneration, profileModel) {
        onDispose { profileModel?.stop() }
    }
    val profileState by if (profileModel != null) profileModel.state.collectAsStateWithLifecycle()
    else remember { mutableStateOf(ProfileUiState()) }
    val profileActions = remember(profileModel, accountManager) {
        object : ProfileContract.Actions {
            override fun open(account: Account) { profileModel?.open(account) }
            override fun selectCategory(category: ProfileCategory) { profileModel?.selectCategory(category) }
            override fun refresh() { profileModel?.refresh() }
            override fun loadMore() { profileModel?.loadMoreSelected() }
            override fun follow() { profileModel?.follow() }
            override fun unfollow() { profileModel?.unfollow() }
            override fun react(post: OwnedPost, choice: EmojiChoice) { profileModel?.react(post, choice) }
            override fun saveEditor(patch: EditableProfilePatch, onSuccess: () -> Unit) {
                profileModel?.saveEditor(patch) { updated ->
                    accountManager.updateAccount(updated)
                    onSuccess()
                }
            }
            override fun openEditor() { profileModel?.openEditor() }
            override fun closeEditor() { profileModel?.closeEditor() }
        }
    }
    val profile = remember(profileState, profileActions) {
        ProfileContract(state = profileState, actions = profileActions)
    }
    val emojiCatalogModel = activeSession?.let { session ->
        hiltViewModel<EmojiCatalogViewModel, EmojiCatalogViewModel.Factory>(
            key = "emoji-catalog-${session.accountId}-${state.sessionGeneration}",
            creationCallback = { factory -> factory.create(session.accountId, sharedSource!!) },
        )
    }
    val emojiCatalogState by if (emojiCatalogModel != null) emojiCatalogModel.state.collectAsStateWithLifecycle()
    else remember { mutableStateOf(EmojiCatalogState()) }
    DisposableEffect(state.sessionGeneration, emojiCatalogModel) {
        onDispose { emojiCatalogModel?.stop() }
    }
    val emojiActions = remember(emojiCatalogModel) {
        object : EmojiPresentation.Actions {
            override fun loadCatalog() { emojiCatalogModel?.loadIfNeeded() }
            override fun retryCatalog() { emojiCatalogModel?.retry() }
            override fun toggleGroupCollapsed(groupId: String) { emojiCatalogModel?.toggleGroupCollapsed(groupId) }
            override fun toggleGroupPinned(groupId: String) { emojiCatalogModel?.toggleGroupPinned(groupId) }
            override fun togglePinnedEmoji(identity: String) { emojiCatalogModel?.togglePinnedEmoji(identity) }
        }
    }
    val emojiPresentation = remember(emojiCatalogState, sharedSource?.capabilities?.emoji, emojiActions) {
        EmojiPresentation(
            catalog = emojiCatalogState,
            capabilities = sharedSource?.capabilities?.emoji ?: EmojiCapabilities(),
            actions = emojiActions,
        )
    }
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
             PalustrisApp(
                  account = state.account,
                  sessionGeneration = state.sessionGeneration,
                  sessionRevision = activeSession?.sessionRevision ?: 0L,
                  actionSource = sharedSource,
                 feedState = feed,
                  postPreferences = postPreferences,
                  contentWarningRules = appPreferences.preferences.contentWarningRules.merge(postPreferences.contentWarningRules),
                 photoGrid = photoGrid,
                 onRefresh = { timeline -> feedModel?.refresh(timeline) },
                 onLoadMore = { timeline -> feedModel?.loadMore(timeline) },
                accountSwitcher = accountSwitcher,
                onPublish = { request, onSuccess ->
                      feedModel?.create(request) { created ->
                          feedModel?.applyPublishedPost(request)
                          feedSink?.let { projectionCoordinator.forwardPublishedPost(it, request, created) }
                          onSuccess(created)
                      }
                  },
                 thread = thread,
                 onSearchAccounts = feedModel?.let { model -> { query -> model.search(query) } } ?: {},
                onLoadMoreSearch = feedModel?.let { model -> { model.loadMoreSearch() } } ?: {},
                draftStore = draftStore,
                onReact = { ownedPost -> feedModel?.favorite(ownedPost) },
                onReshare = { ownedPost -> feedModel?.reshare(ownedPost) },
                onBookmark = { ownedPost -> feedModel?.bookmark(ownedPost) },
                onReaction = { ownedPost, emoji -> feedModel?.react(ownedPost, emoji) },
                bookmarks = bookmarks,
                likes = likes,
                notifications = notifications,
                 directMessages = directMessages,
                initialNotificationRoute = initialNotificationRoute,
                notificationSettings = notificationSettings,
                profile = profile,
                emojiPresentation = emojiPresentation,
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
