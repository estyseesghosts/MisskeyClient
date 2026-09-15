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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.foxtails.palustris.data.AccountSourceRegistry
import me.foxtails.palustris.data.SocialSourceFactory
import me.foxtails.palustris.data.auth.AccountIndex
import me.foxtails.palustris.data.auth.DraftStore
import me.foxtails.palustris.data.notifications.NotificationStreamController
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.CreatePostRequest
import me.foxtails.palustris.domain.OwnedPost
import me.foxtails.palustris.domain.PostPreferences
import me.foxtails.palustris.ui.AccountManager
import me.foxtails.palustris.ui.FeedHost
import me.foxtails.palustris.ui.PalustrisApp
import me.foxtails.palustris.ui.SavedCollectionsHost
import me.foxtails.palustris.ui.directmessages.DirectMessagesHost
import me.foxtails.palustris.ui.emoji.EmojiHost
import me.foxtails.palustris.ui.navigation.AppRoute
import me.foxtails.palustris.ui.notifications.NotificationSettingsHost
import me.foxtails.palustris.ui.notifications.NotificationsHost
import me.foxtails.palustris.ui.posts.LocalPostActionOwner
import me.foxtails.palustris.ui.posts.PostActionOwner
import me.foxtails.palustris.ui.profile.ProfileHost
import me.foxtails.palustris.ui.shell.AccountSwitcher
import me.foxtails.palustris.ui.shell.ComposerContract
import me.foxtails.palustris.ui.shell.DraftActions
import me.foxtails.palustris.ui.shell.PostProjectionCoordinator
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
    val feed = FeedHost(
        accountId = session.accountId,
        sessionGeneration = sessionGeneration,
        sessionRevision = session.sessionRevision,
        source = sharedSource,
        coordinator = projectionCoordinator,
    )
    val savedCollections = SavedCollectionsHost(
        accountId = session.accountId,
        sessionGeneration = sessionGeneration,
        sessionRevision = session.sessionRevision,
        source = sharedSource,
        account = account,
        accountManager = accountManager,
        coordinator = projectionCoordinator,
        react = feed.react,
    )
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
        DraftActions(
            settingsScope,
            draftStore,
            legacyPreferences = {
                context.getSharedPreferences("local_draft", android.content.Context.MODE_PRIVATE)
            },
        ).asContract()
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
    val composerActions = remember(feed.publish) {
        object : ComposerContract.Actions {
            override fun publish(request: CreatePostRequest, onAccepted: (OwnedPost) -> Unit) {
                feed.publish(request, onAccepted)
            }
        }
    }
    val composer = remember(postPreferences, feed.composerInputs, composerActions) {
        ComposerContract(
            postPreferences = postPreferences,
            availableAudiences = feed.composerInputs.availableAudiences,
            canPublish = feed.composerInputs.canPublish,
            publishing = feed.composerInputs.publishing,
            error = feed.composerInputs.error,
            actions = composerActions,
        )
    }

    CompositionLocalProvider(LocalPostActionOwner provides postActionOwner) {
        PalustrisApp(
            account = account,
            sessionGeneration = sessionGeneration,
            sessionRevision = session.sessionRevision,
            home = feed.home,
            photoGrid = feed.photoGrid,
            accountSwitcher = accountSwitcher,
            composer = composer,
            thread = thread,
            search = feed.search,
            draftsContract = draftsContract,
            postInteractions = feed.postInteractions,
            bookmarks = savedCollections.bookmarks,
            likes = savedCollections.likes,
            notifications = notifications,
            directMessages = directMessages,
            initialNotificationRoute = initialNotificationRoute,
            notificationSettings = notificationSettings,
            profile = profile,
            emojiPresentation = emojiPresentation,
        )
    }
}
