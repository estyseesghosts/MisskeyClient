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
import me.foxtails.palustris.domain.EmojiChoice
import me.foxtails.palustris.domain.OwnedPost
import me.foxtails.palustris.domain.PostDraft
import me.foxtails.palustris.domain.PostPreferences
import me.foxtails.palustris.domain.Timeline
import me.foxtails.palustris.ui.AccountManager
import me.foxtails.palustris.ui.FeedViewModel
import me.foxtails.palustris.ui.PalustrisApp
import me.foxtails.palustris.ui.PhotoGridFeed
import me.foxtails.palustris.ui.SavedPostsCollection
import me.foxtails.palustris.ui.SavedPostsViewModel
import me.foxtails.palustris.ui.directmessages.DirectMessagesHost
import me.foxtails.palustris.ui.emoji.EmojiHost
import me.foxtails.palustris.ui.navigation.AppRoute
import me.foxtails.palustris.ui.notifications.NotificationSettingsHost
import me.foxtails.palustris.ui.notifications.NotificationsHost
import me.foxtails.palustris.ui.posts.LocalPostActionOwner
import me.foxtails.palustris.ui.posts.PostActionOwner
import me.foxtails.palustris.ui.profile.ProfileHost
import me.foxtails.palustris.ui.shell.AccountSwitcher
import me.foxtails.palustris.ui.shell.BookmarksContract
import me.foxtails.palustris.ui.shell.ComposerContract
import me.foxtails.palustris.ui.shell.DraftsContract
import me.foxtails.palustris.ui.shell.HomeContract
import me.foxtails.palustris.ui.shell.HomeFeedUiState
import me.foxtails.palustris.ui.shell.LikesContract
import me.foxtails.palustris.ui.shell.PhotoGridContract
import me.foxtails.palustris.ui.shell.PostInteractions
import me.foxtails.palustris.ui.shell.PostProjectionCoordinator
import me.foxtails.palustris.ui.shell.SearchContract
import me.foxtails.palustris.ui.thread.ThreadHost

/**
 * Owns one coherent account/session presentation lifetime.
 *
 * The host resolves the registered source for the active session and composes focused feature
 * hosts. Each feature host owns its model, state, actions, and projection registration. This host
 * keeps only the shared feed owner, the draft owner, the post-action owner, and the projection
 * coordinator. It exposes no token and no source to the shell.
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
    val projectionCoordinator = remember(session.accountId, sessionGeneration) {
        PostProjectionCoordinator(session.accountId, session.sessionRevision)
    }
    val feedModel = hiltViewModel<FeedViewModel, FeedViewModel.Factory>(
        key = "feed-${session.accountId}-$sessionGeneration",
        creationCallback = { factory ->
            factory.create(session.accountId, sharedSource, session.sessionRevision)
        },
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
    DisposableEffect(projectionCoordinator, feedSink, savedSink, likedSink, feedModel) {
        listOf(feedSink, savedSink, likedSink).forEach(projectionCoordinator::register)
        val feedProjection: (OwnedPost) -> Unit = { updated ->
            projectionCoordinator.forwardExternalPost(feedSink, updated)
        }
        feedModel.addPostProjectionListener(feedProjection)
        onDispose {
            feedModel.removePostProjectionListener(feedProjection)
            listOf(feedSink, savedSink, likedSink).forEach(projectionCoordinator::unregister)
        }
    }
    val profile = ProfileHost(
        accountId = session.accountId,
        sessionGeneration = sessionGeneration,
        sessionRevision = session.sessionRevision,
        source = sharedSource,
        accountManager = accountManager,
        coordinator = projectionCoordinator,
    )
    val thread = ThreadHost(
        accountId = session.accountId,
        sessionGeneration = sessionGeneration,
        sessionRevision = session.sessionRevision,
        source = sharedSource,
        coordinator = projectionCoordinator,
        lifecycleOwner = lifecycleOwner,
    )
    val notifications = NotificationsHost(
        accountId = session.accountId,
        sessionGeneration = sessionGeneration,
        sessionRevision = session.sessionRevision,
        source = sharedSource,
        coordinator = projectionCoordinator,
    )
    val directMessages = DirectMessagesHost(
        accountId = session.accountId,
        sessionGeneration = sessionGeneration,
        source = sharedSource,
    )
    val notificationSettings = NotificationSettingsHost(
        accountId = session.accountId,
        sessionGeneration = sessionGeneration,
    )
    val emojiPresentation = EmojiHost(
        accountId = session.accountId,
        sessionGeneration = sessionGeneration,
        source = sharedSource,
    )
    LaunchedEffect(profile.state.account, account) {
        profile.state.account
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
    val postActionOwner = remember(session.accountId, session.sessionRevision, sharedSource, profile) {
        PostActionOwner(
            accountId = session.accountId,
            sessionRevision = session.sessionRevision,
            source = sharedSource,
            scope = settingsScope,
            onRelationshipChanged = { profile.actions.refresh() },
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
