package me.foxtails.palustris.ui.session

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import me.foxtails.palustris.data.AccountSourceRegistry
import me.foxtails.palustris.data.SocialSourceFactory
import me.foxtails.palustris.data.auth.AccountIndex
import me.foxtails.palustris.data.auth.DraftStore
import me.foxtails.palustris.data.notifications.NotificationStreamController
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.CapabilityStatus
import me.foxtails.palustris.domain.CreatePostRequest
import me.foxtails.palustris.domain.DirectConversation
import me.foxtails.palustris.domain.EditableProfilePatch
import me.foxtails.palustris.domain.EmojiChoice
import me.foxtails.palustris.domain.EntityId
import me.foxtails.palustris.domain.Notification
import me.foxtails.palustris.domain.NotificationCategory
import me.foxtails.palustris.domain.NotificationQuery
import me.foxtails.palustris.domain.OwnedPost
import me.foxtails.palustris.domain.PostDraft
import me.foxtails.palustris.domain.PostPreferences
import me.foxtails.palustris.domain.Timeline
import me.foxtails.palustris.ui.AccountManager
import me.foxtails.palustris.ui.FeedViewModel
import me.foxtails.palustris.ui.NotificationsViewModel
import me.foxtails.palustris.ui.PalustrisApp
import me.foxtails.palustris.ui.PhotoGridFeed
import me.foxtails.palustris.ui.SavedPostsCollection
import me.foxtails.palustris.ui.SavedPostsViewModel
import me.foxtails.palustris.ui.directmessages.DirectMessageViewModel
import me.foxtails.palustris.ui.emoji.EmojiCatalogViewModel
import me.foxtails.palustris.ui.navigation.AppRoute
import me.foxtails.palustris.ui.notifications.NotificationSettingsUiState
import me.foxtails.palustris.ui.notifications.NotificationSettingsViewModel
import me.foxtails.palustris.ui.posts.LocalPostActionOwner
import me.foxtails.palustris.ui.posts.PostActionOwner
import me.foxtails.palustris.ui.profile.ProfileCategory
import me.foxtails.palustris.ui.profile.ProfileViewModel
import me.foxtails.palustris.ui.shell.AccountSwitcher
import me.foxtails.palustris.ui.shell.BookmarksContract
import me.foxtails.palustris.ui.shell.ComposerContract
import me.foxtails.palustris.ui.shell.DirectMessagesContract
import me.foxtails.palustris.ui.shell.DraftsContract
import me.foxtails.palustris.ui.shell.EmojiPresentation
import me.foxtails.palustris.ui.shell.HomeContract
import me.foxtails.palustris.ui.shell.HomeFeedUiState
import me.foxtails.palustris.ui.shell.LikesContract
import me.foxtails.palustris.ui.shell.NotificationSettingsContract
import me.foxtails.palustris.ui.shell.NotificationsContract
import me.foxtails.palustris.ui.shell.PhotoGridContract
import me.foxtails.palustris.ui.shell.PostInteractions
import me.foxtails.palustris.ui.shell.PostProjectionCoordinator
import me.foxtails.palustris.ui.shell.ProfileContract
import me.foxtails.palustris.ui.shell.SearchContract
import me.foxtails.palustris.ui.shell.ThreadContract
import me.foxtails.palustris.ui.thread.PostThreadViewModel

/**
 * Owns one coherent account/session presentation lifetime.
 *
 * The host resolves the registered source for the active session, binds every source-backed feature
 * owner, and releases them on session replacement. It exposes no token and no source to the shell.
 * The composable renders [PalustrisApp] directly so no aggregate contract bag leaves this boundary.
 */
@Composable
fun ConnectedSessionHost(
    accountManager: AccountManager,
    sourceFactory: SocialSourceFactory,
    sourceRegistry: AccountSourceRegistry,
    draftStore: DraftStore,
    notificationStreamController: NotificationStreamController,
    account: Account,
    sessionGeneration: Long,
    accountIndex: AccountIndex,
    postPreferences: PostPreferences,
    initialNotificationRoute: AppRoute?,
    onOpenSettings: () -> Unit,
) {
    val activeSession by accountManager.activeSession.collectAsStateWithLifecycle()
    val session = activeSession?.takeIf { it.accountId == account.id }
    if (session == null) {
        Surface(Modifier.fillMaxSize()) {
            Box(contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        }
        return
    }

    val settingsScope = rememberCoroutineScope()
    val context = LocalContext.current
    // Resolve once per connected session so recomposition cannot create replacement sources.
    val sharedSource = remember(session.accountId, sessionGeneration) {
        sourceRegistry.sourceFor(session.accountId) ?: sourceFactory.create(session)
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(session.accountId, lifecycleOwner) {
        val accountId = session.accountId
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> notificationStreamController.start(accountId)
                Lifecycle.Event.ON_STOP -> notificationStreamController.stop(accountId)
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            notificationStreamController.start(accountId)
        }
        onDispose {
            notificationStreamController.stop(accountId)
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    val accountSwitcherActions = remember(accountManager, onOpenSettings) {
        object : AccountSwitcher.Actions {
            override fun switchTo(accountId: AccountId) = accountManager.switchAccount(accountId)
            override fun addAccount() = accountManager.beginAddAccount()
            override fun openSettings() = onOpenSettings()
            override fun signOut() = accountManager.signOut()
        }
    }
    val accountSwitcher = remember(accountIndex.accounts, accountSwitcherActions) {
        AccountSwitcher(accounts = accountIndex.accounts, actions = accountSwitcherActions)
    }
    val feedModel = hiltViewModel<FeedViewModel, FeedViewModel.Factory>(
        key = "feed-${session.accountId}-$sessionGeneration",
        creationCallback = { factory ->
            factory.create(session.accountId, sharedSource, session.sessionRevision)
        },
    )
    val notificationsModel = hiltViewModel<NotificationsViewModel, NotificationsViewModel.Factory>(
        key = "notifications-${session.accountId}-$sessionGeneration",
        creationCallback = { factory -> factory.create(session.accountId, sharedSource) },
    )
    val directMessagesModel = hiltViewModel<DirectMessageViewModel, DirectMessageViewModel.Factory>(
        key = "direct-messages-${session.accountId}-$sessionGeneration",
        creationCallback = { factory -> factory.create(session.accountId, sharedSource) },
    )
    val savedPostsModel = hiltViewModel<SavedPostsViewModel, SavedPostsViewModel.Factory>(
        key = "saved-posts-${session.accountId}-$sessionGeneration",
        creationCallback = { factory ->
            factory.create(session.accountId, sharedSource, SavedPostsCollection.Bookmarks, session.sessionRevision)
        },
    )
    val likedPostsModel = hiltViewModel<SavedPostsViewModel, SavedPostsViewModel.Factory>(
        key = "liked-posts-${session.accountId}-$sessionGeneration",
        creationCallback = { factory ->
            factory.create(session.accountId, sharedSource, SavedPostsCollection.Likes, session.sessionRevision)
        },
    )
    val notificationSettingsModel = hiltViewModel<NotificationSettingsViewModel, NotificationSettingsViewModel.Factory>(
        key = "notification-settings-${session.accountId}-$sessionGeneration",
        creationCallback = { factory -> factory.create(session.accountId) },
    )
    DisposableEffect(sessionGeneration, feedModel, savedPostsModel, likedPostsModel) {
        onDispose {
            feedModel.stop()
            savedPostsModel.stop()
            likedPostsModel.stop()
        }
    }
    val feed by feedModel.feed.collectAsStateWithLifecycle()
    val homeActions = remember(feedModel) {
        object : HomeContract.Actions {
            override fun refresh(timeline: Timeline) { feedModel.refresh(timeline) }
            override fun loadMore(timeline: Timeline) { feedModel.loadMore(timeline) }
        }
    }
    val home = remember(feed, homeActions) {
        HomeContract(
            state = HomeFeedUiState(
                ownedPosts = feed.ownedPosts,
                posts = feed.posts,
                loading = feed.loading,
                loadingMore = feed.loadingMore,
                nextCursor = feed.nextCursor,
                error = feed.error,
                needsSignIn = feed.needsSignIn,
                selectedTimeline = feed.timeline,
                availableTimelines = feed.timelines,
            ),
            actions = homeActions,
        )
    }
    val searchActions = remember(feedModel) {
        object : SearchContract.Actions {
            override fun search(query: String) { feedModel.search(query) }
            override fun loadMore() { feedModel.loadMoreSearch() }
        }
    }
    val search = remember(feed.accountSearch, searchActions) {
        SearchContract(state = feed.accountSearch, actions = searchActions)
    }
    val postInteractionsActions = remember(feedModel) {
        object : PostInteractions.Actions {
            override fun favorite(post: OwnedPost) { feedModel.favorite(post) }
            override fun repost(post: OwnedPost) { feedModel.reshare(post) }
            override fun bookmark(post: OwnedPost) { feedModel.bookmark(post) }
            override fun react(post: OwnedPost, choice: EmojiChoice) { feedModel.react(post, choice) }
        }
    }
    val postInteractions = remember(feed.actions, feed.quoteStatus, postInteractionsActions) {
        PostInteractions(
            availableActions = feed.actions,
            quoteEnabled = feed.quoteStatus == CapabilityStatus.Supported,
            actions = postInteractionsActions,
        )
    }
    val photoGridFeed by feedModel.photoGridFeed.collectAsStateWithLifecycle()
    val photoGridActions = remember(feedModel) {
        object : PhotoGridContract.Actions {
            override fun ensureLoaded() { feedModel.ensurePhotoGridLoaded() }
            override fun selectFeed(feed: PhotoGridFeed) { feedModel.selectPhotoGridFeed(feed) }
            override fun refresh() { feedModel.refreshPhotoGrid() }
            override fun loadMore() { feedModel.loadMorePhotoGrid() }
            override fun addHashtag(value: String, onSuccess: () -> Unit) { feedModel.addPhotoGridHashtag(value, onSuccess) }
            override fun clearPreferenceError() { feedModel.clearPhotoGridPreferenceError() }
        }
    }
    val photoGrid = remember(photoGridFeed, photoGridActions) {
        PhotoGridContract(state = photoGridFeed, actions = photoGridActions)
    }
    val notificationState by notificationsModel.state.collectAsStateWithLifecycle()
    val notificationsActions = remember(notificationsModel) {
        object : NotificationsContract.Actions {
            override fun refresh() { notificationsModel.refresh() }
            override fun loadMore() { notificationsModel.loadOlder() }
            override fun markAllRead() { notificationsModel.markAllRead() }
            override fun markSeen(notification: Notification?) { notificationsModel.markSeen(notification?.id) }
            override fun dismiss(notification: Notification) { notificationsModel.dismiss(notification) }
            override fun respondToFollowRequest(notification: Notification, accept: Boolean) {
                notificationsModel.respondToFollowRequest(notification, accept)
            }
            override fun selectQuery(query: NotificationQuery) { notificationsModel.selectQuery(query) }
        }
    }
    val notifications = remember(notificationState, notificationsActions) {
        NotificationsContract(notificationState, notificationsActions)
    }
    val directMessageState by directMessagesModel.state.collectAsStateWithLifecycle()
    val directMessagesActions = remember(directMessagesModel) {
        object : DirectMessagesContract.Actions {
            override fun refresh() { directMessagesModel.refresh() }
            override fun loadMore() { directMessagesModel.loadMore() }
            override fun openConversation(conversation: DirectConversation) {
                directMessagesModel.openConversation(conversation)
            }
            override fun closeConversation() { directMessagesModel.closeConversation() }
            override fun startConversation(account: Account) { directMessagesModel.startConversation(account) }
            override fun send(text: String) { directMessagesModel.send(text) }
        }
    }
    val directMessages = remember(directMessageState, directMessagesActions) {
        DirectMessagesContract(directMessageState, directMessagesActions)
    }
    val savedPostsState by savedPostsModel.state.collectAsStateWithLifecycle()
    val likedPostsState by likedPostsModel.state.collectAsStateWithLifecycle()
    val bookmarksActions = remember(savedPostsModel, accountManager) {
        object : BookmarksContract.Actions {
            override fun refresh() { savedPostsModel.refresh() }
            override fun loadMore() { savedPostsModel.loadMore() }
            override fun remove(post: OwnedPost) { savedPostsModel.unsave(post) }
            override fun upgradePermissions() { accountManager.upgradePermissions(account.id) }
            override fun react(post: OwnedPost, choice: EmojiChoice) { savedPostsModel.react(post, choice) }
        }
    }
    val bookmarks = remember(savedPostsState, bookmarksActions) {
        BookmarksContract(state = savedPostsState, actions = bookmarksActions)
    }
    val likesActions = remember(likedPostsModel, feedModel) {
        object : LikesContract.Actions {
            override fun refresh() { likedPostsModel.refresh() }
            override fun loadMore() { likedPostsModel.loadMore() }
            override fun toggle(post: OwnedPost) { likedPostsModel.toggleFavourite(post) }
            override fun react(post: OwnedPost, choice: EmojiChoice) { feedModel.react(post, choice) }
        }
    }
    val likes = remember(likedPostsState, likesActions) {
        LikesContract(state = likedPostsState, actions = likesActions)
    }
    val notificationSettingsState by notificationSettingsModel.state.collectAsStateWithLifecycle()
    val notificationSettingsActions = remember(notificationSettingsModel) {
        object : NotificationSettingsContract.Actions {
            override fun setAlertsEnabled(enabled: Boolean) { notificationSettingsModel.setAlertsEnabled(enabled) }
            override fun setShowPreviews(enabled: Boolean) { notificationSettingsModel.setShowPreviews(enabled) }
            override fun setPeriodicFallback(enabled: Boolean) { notificationSettingsModel.setPeriodicFallbackEnabled(enabled) }
            override fun setQuietHours(enabled: Boolean) { notificationSettingsModel.setQuietHours(enabled) }
            override fun setCategoryEnabled(category: NotificationCategory, enabled: Boolean) {
                notificationSettingsModel.setCategoryEnabled(category, enabled)
            }
            override fun runLocalTest() { notificationSettingsModel.runLocalPresentationTest() }
            override fun retryRegistration() { notificationSettingsModel.retryRegistration() }
            override fun refreshPermission() { notificationSettingsModel.refreshPermission() }
            override fun refreshDistributors() { notificationSettingsModel.refreshDistributors() }
            override fun selectDistributor(packageName: String) { notificationSettingsModel.selectDistributor(packageName) }
            override fun runPushConnectionTest() { notificationSettingsModel.runPushConnectionTest() }
        }
    }
    val notificationSettings = remember(session.accountId, notificationSettingsState, notificationSettingsActions) {
        NotificationSettingsContract(
            accountId = session.accountId,
            state = notificationSettingsState,
            actions = notificationSettingsActions,
        )
    }
    val profileModel = hiltViewModel<ProfileViewModel, ProfileViewModel.Factory>(
        key = "profile-${session.accountId}-$sessionGeneration",
        creationCallback = { factory ->
            factory.create(session.accountId, sharedSource, session.sessionRevision)
        },
    )
    val threadModel = hiltViewModel<PostThreadViewModel, PostThreadViewModel.Factory>(
        key = "thread-${session.accountId}-$sessionGeneration",
        creationCallback = { factory ->
            factory.create(session.accountId, sharedSource, session.sessionRevision)
        },
    )
    val threadState by threadModel.state.collectAsStateWithLifecycle()
    val threadActions = remember(threadModel) {
        object : ThreadContract.Actions {
            override fun activate(post: OwnedPost?, enabled: Boolean) { threadModel.activate(post, enabled) }
            override fun deactivate() { threadModel.deactivate() }
            override fun refresh() { threadModel.refresh() }
            override fun continueAcquisition() { threadModel.continueAcquisition() }
            override fun favorite(post: OwnedPost) { threadModel.favorite(post) }
            override fun repost(post: OwnedPost) { threadModel.reshare(post) }
            override fun bookmark(post: OwnedPost) { threadModel.bookmark(post) }
            override fun react(post: OwnedPost, choice: EmojiChoice) { threadModel.react(post, choice) }
        }
    }
    val thread = remember(threadState, threadActions) {
        ThreadContract(state = threadState, actions = threadActions)
    }
    DisposableEffect(sessionGeneration, threadModel) {
        onDispose { threadModel.stop() }
    }
    val projectionCoordinator = remember(session.accountId, sessionGeneration) {
        PostProjectionCoordinator(session.accountId, session.sessionRevision)
    }
    val feedSink = remember(feedModel) {
        object : PostProjectionCoordinator.Sink {
            override fun applyExternalPost(updated: OwnedPost) { feedModel.applyExternalPost(updated) }
            override fun applyPublishedPost(request: CreatePostRequest) { feedModel.applyPublishedPost(request) }
        }
    }
    val savedSink = remember(savedPostsModel) {
        object : PostProjectionCoordinator.Sink {
            override fun applyExternalPost(updated: OwnedPost) { savedPostsModel.applyExternalPost(updated) }
            override fun applyPublishedPost(request: CreatePostRequest) { savedPostsModel.applyPublishedPost(request) }
        }
    }
    val likedSink = remember(likedPostsModel) {
        object : PostProjectionCoordinator.Sink {
            override fun applyExternalPost(updated: OwnedPost) { likedPostsModel.applyExternalPost(updated) }
            override fun applyPublishedPost(request: CreatePostRequest) { likedPostsModel.applyPublishedPost(request) }
        }
    }
    val profileSink = remember(profileModel) {
        object : PostProjectionCoordinator.Sink {
            override fun applyExternalPost(updated: OwnedPost) { profileModel.applyExternalPost(updated) }
            override fun applyPublishedPost(request: CreatePostRequest) { profileModel.applyPublishedPost(request) }
        }
    }
    val notificationsSink = remember(notificationsModel) {
        object : PostProjectionCoordinator.Sink {
            override fun applyExternalPost(updated: OwnedPost) { notificationsModel.applyExternalPost(updated) }
            override fun applyPublishedPost(request: CreatePostRequest) { notificationsModel.applyPublishedPost(request) }
        }
    }
    val threadSink = remember(threadModel) {
        object : PostProjectionCoordinator.Sink {
            override fun applyExternalPost(updated: OwnedPost) { threadModel.applyExternalPost(updated) }
            override fun acceptPublishedReply(created: OwnedPost) { threadModel.acceptPublishedReply(created) }
            override fun acceptPublishedQuote(target: EntityId?) { threadModel.acceptPublishedQuote(target) }
        }
    }
    DisposableEffect(projectionCoordinator, feedSink, savedSink, likedSink, profileSink, notificationsSink, threadSink, feedModel, threadModel) {
        val sinks = listOf(feedSink, savedSink, likedSink, profileSink, notificationsSink, threadSink)
        sinks.forEach(projectionCoordinator::register)
        val feedProjection: (OwnedPost) -> Unit = { updated ->
            projectionCoordinator.forwardExternalPost(feedSink, updated)
        }
        feedModel.addPostProjectionListener(feedProjection)
        val threadProjection: (OwnedPost) -> Unit = { updated ->
            projectionCoordinator.forwardExternalPost(threadSink, updated)
        }
        threadModel.setPostUpdateListener(threadProjection)
        onDispose {
            threadModel.setPostUpdateListener(null)
            feedModel.removePostProjectionListener(feedProjection)
            sinks.forEach(projectionCoordinator::unregister)
        }
    }
    DisposableEffect(threadModel, lifecycleOwner) {
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
    DisposableEffect(sessionGeneration, profileModel) {
        onDispose { profileModel.stop() }
    }
    val profileState by profileModel.state.collectAsStateWithLifecycle()
    val profileActions = remember(profileModel, accountManager) {
        object : ProfileContract.Actions {
            override fun open(account: Account) { profileModel.open(account) }
            override fun selectCategory(category: ProfileCategory) { profileModel.selectCategory(category) }
            override fun refresh() { profileModel.refresh() }
            override fun loadMore() { profileModel.loadMoreSelected() }
            override fun follow() { profileModel.follow() }
            override fun unfollow() { profileModel.unfollow() }
            override fun react(post: OwnedPost, choice: EmojiChoice) { profileModel.react(post, choice) }
            override fun saveEditor(patch: EditableProfilePatch, onSuccess: () -> Unit) {
                profileModel.saveEditor(patch) { updated ->
                    accountManager.updateAccount(updated)
                    onSuccess()
                }
            }
            override fun openEditor() { profileModel.openEditor() }
            override fun closeEditor() { profileModel.closeEditor() }
        }
    }
    val profile = remember(profileState, profileActions) {
        ProfileContract(state = profileState, actions = profileActions)
    }
    val emojiCatalogModel = hiltViewModel<EmojiCatalogViewModel, EmojiCatalogViewModel.Factory>(
        key = "emoji-catalog-${session.accountId}-$sessionGeneration",
        creationCallback = { factory -> factory.create(session.accountId, sharedSource) },
    )
    val emojiCatalogState by emojiCatalogModel.state.collectAsStateWithLifecycle()
    DisposableEffect(sessionGeneration, emojiCatalogModel) {
        onDispose { emojiCatalogModel.stop() }
    }
    val emojiActions = remember(emojiCatalogModel) {
        object : EmojiPresentation.Actions {
            override fun loadCatalog() { emojiCatalogModel.loadIfNeeded() }
            override fun retryCatalog() { emojiCatalogModel.retry() }
            override fun toggleGroupCollapsed(groupId: String) { emojiCatalogModel.toggleGroupCollapsed(groupId) }
            override fun toggleGroupPinned(groupId: String) { emojiCatalogModel.toggleGroupPinned(groupId) }
            override fun togglePinnedEmoji(identity: String) { emojiCatalogModel.togglePinnedEmoji(identity) }
        }
    }
    val emojiPresentation = remember(emojiCatalogState, sharedSource.capabilities.emoji, emojiActions) {
        EmojiPresentation(
            catalog = emojiCatalogState,
            capabilities = sharedSource.capabilities.emoji,
            actions = emojiActions,
        )
    }
    LaunchedEffect(profileState.account, account) {
        profileState.account
            ?.takeIf { it.id == account.id && it != account }
            ?.let(accountManager::updateAccount)
    }
    val draftsContract = remember(draftStore, context, settingsScope) {
        DraftsContract(
            actions = object : DraftsContract.Actions {
                override fun load(accountId: AccountId?, onResult: (List<PostDraft>) -> Unit) {
                    settingsScope.launch {
                        val result = runCatching {
                            draftStore.migrateLegacy(accountId, context.getSharedPreferences("local_draft", android.content.Context.MODE_PRIVATE))
                            draftStore.list(accountId)
                        }.getOrElse { emptyList() }
                        onResult(result)
                    }
                }

                override fun save(draft: PostDraft, onResult: (PostDraft) -> Unit, onError: () -> Unit) {
                    settingsScope.launch {
                        runCatching { draftStore.save(draft) }
                            .onSuccess { onResult(draft) }
                            .onFailure { onError() }
                    }
                }

                override fun delete(accountId: AccountId?, draftId: String, onDone: () -> Unit) {
                    settingsScope.launch {
                        runCatching { draftStore.delete(accountId, draftId) }
                        onDone()
                    }
                }
            },
        )
    }
    val postActionOwner = remember(session.accountId, session.sessionRevision, sharedSource) {
        PostActionOwner(
            accountId = session.accountId,
            sessionRevision = session.sessionRevision,
            source = sharedSource,
            scope = settingsScope,
            onRelationshipChanged = { profileModel.refresh() },
        )
    }
    val composerActions = remember(feedModel, feedSink, projectionCoordinator) {
        object : ComposerContract.Actions {
            override fun publish(request: CreatePostRequest, onAccepted: (OwnedPost) -> Unit) {
                feedModel.create(request) { created ->
                    feedModel.applyPublishedPost(request)
                    projectionCoordinator.forwardPublishedPost(feedSink, request, created)
                    onAccepted(created)
                }
            }
        }
    }
    val composer = remember(postPreferences, feed.audiences, feed.canPublish, feed.publishing, feed.error, composerActions) {
        ComposerContract(
            postPreferences = postPreferences,
            availableAudiences = feed.audiences,
            canPublish = feed.canPublish,
            publishing = feed.publishing,
            error = feed.error,
            actions = composerActions,
        )
    }

    CompositionLocalProvider(LocalPostActionOwner provides postActionOwner) {
        PalustrisApp(
            account = account,
            sessionGeneration = sessionGeneration,
            sessionRevision = session.sessionRevision,
            home = home,
            photoGrid = photoGrid,
            accountSwitcher = accountSwitcher,
            composer = composer,
            thread = thread,
            search = search,
            draftsContract = draftsContract,
            postInteractions = postInteractions,
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
