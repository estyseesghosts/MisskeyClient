package me.foxtails.palustris.ui.session

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import me.foxtails.palustris.data.auth.AccountIndex
import me.foxtails.palustris.data.auth.DraftStore
import me.foxtails.palustris.data.notifications.NotificationStreamController
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
 * The host reads one accepted [ConnectedSessionContext]. It binds the source-backed feature owners
 * to the registered source in that context. Each feature host owns its model, state, actions, and
 * projection registration. This host keeps only the shared feed owner, the draft owner, the
 * post-action owner, and the projection coordinator. It exposes no token and no source to the shell.
 */
@Composable
fun ConnectedSessionHost(
    accountManager: AccountManager,
    connectedContext: ConnectedSessionContext,
    entryStore: ConnectedEntryStore,
    draftStore: DraftStore,
    notificationStreamController: NotificationStreamController,
    accountIndex: AccountIndex,
    postPreferences: PostPreferences,
    initialNotificationRoute: AppRoute?,
    onOpenSettings: () -> Unit,
) {
    val account = connectedContext.account
    val accountId = connectedContext.accountId
    val sessionRevision = connectedContext.sessionRevision
    val sessionGeneration = connectedContext.presentationGeneration
    // The registered source comes from the accepted context. Recomposition cannot create a
    // replacement source, and an unregistered fallback does not exist.
    val sharedSource = connectedContext.source

    val settingsScope = rememberCoroutineScope()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(accountId, lifecycleOwner) {
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
    val projectionCoordinator = remember(accountId, sessionGeneration) {
        PostProjectionCoordinator(accountId, sessionRevision)
    }
    val feed = FeedHost(
        accountId = accountId,
        sessionGeneration = sessionGeneration,
        sessionRevision = sessionRevision,
        source = sharedSource,
        coordinator = projectionCoordinator,
        entryStore = entryStore,
    )
    val savedCollections = SavedCollectionsHost(
        accountId = accountId,
        sessionGeneration = sessionGeneration,
        sessionRevision = sessionRevision,
        source = sharedSource,
        account = account,
        accountManager = accountManager,
        coordinator = projectionCoordinator,
        react = feed.react,
        entryStore = entryStore,
    )
    val profile = ProfileHost(
        accountId = accountId,
        sessionGeneration = sessionGeneration,
        sessionRevision = sessionRevision,
        source = sharedSource,
        accountManager = accountManager,
        coordinator = projectionCoordinator,
        entryStore = entryStore,
    )
    val thread = ThreadHost(
        accountId = accountId,
        sessionGeneration = sessionGeneration,
        sessionRevision = sessionRevision,
        source = sharedSource,
        coordinator = projectionCoordinator,
        lifecycleOwner = lifecycleOwner,
        entryStore = entryStore,
    )
    val notifications = NotificationsHost(
        accountId = accountId,
        sessionGeneration = sessionGeneration,
        sessionRevision = sessionRevision,
        source = sharedSource,
        coordinator = projectionCoordinator,
        entryStore = entryStore,
    )
    val directMessages = DirectMessagesHost(
        accountId = accountId,
        sessionGeneration = sessionGeneration,
        source = sharedSource,
        writeGeneration = connectedContext.directMessageGeneration,
        entryStore = entryStore,
    )
    val notificationSettings = NotificationSettingsHost(
        accountId = accountId,
        sessionGeneration = sessionGeneration,
    )
    val emojiPresentation = EmojiHost(
        accountId = accountId,
        sessionGeneration = sessionGeneration,
        source = sharedSource,
        entryStore = entryStore,
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
    val postActionOwner = remember(accountId, sessionRevision, sharedSource, profile) {
        PostActionOwner(
            accountId = accountId,
            sessionRevision = sessionRevision,
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
            sessionRevision = sessionRevision,
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
