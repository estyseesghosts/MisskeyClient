@file:OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
)

package me.foxtails.palustris.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.LocalIndication
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.launch
import me.foxtails.palustris.data.auth.toAccount
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.EditableProfilePatch
import me.foxtails.palustris.domain.Notification
import me.foxtails.palustris.domain.NotificationQuery
import me.foxtails.palustris.domain.OwnedPost
import me.foxtails.palustris.domain.Post
import me.foxtails.palustris.domain.PostAction
import me.foxtails.palustris.domain.effectiveTargetId
import me.foxtails.palustris.domain.SavedPostsKind
import me.foxtails.palustris.domain.Timeline
import me.foxtails.palustris.ui.navigation.AppRoute
import me.foxtails.palustris.ui.notifications.NotificationSettingsScreen
import me.foxtails.palustris.ui.notifications.NotificationsScreen
import me.foxtails.palustris.ui.profile.ProfileCategory
import me.foxtails.palustris.ui.profile.ProfileUiState
import me.foxtails.palustris.ui.profile.editableProfilePatch
import me.foxtails.palustris.ui.navigation.NavigationMode
import me.foxtails.palustris.ui.navigation.NavigationModeObserver
import me.foxtails.palustris.ui.navigation.ShellBackState
import me.foxtails.palustris.ui.navigation.ShellTopSurface
import me.foxtails.palustris.ui.navigation.edgeSwipeDismiss
import me.foxtails.palustris.ui.navigation.rememberShellNavigator
import me.foxtails.palustris.ui.navigation.topSurfaceForBack
import me.foxtails.palustris.ui.media.LocalMediaTransitionRegistry
import me.foxtails.palustris.ui.media.MediaTransitionRegistry
import me.foxtails.palustris.ui.composer.ComposerOwnerContext
import me.foxtails.palustris.ui.composer.rememberComposerOwner
import me.foxtails.palustris.ui.shell.AccountSwitcher
import me.foxtails.palustris.ui.shell.BookmarksContract
import me.foxtails.palustris.ui.shell.ComposerContract
import me.foxtails.palustris.ui.shell.DestinationDraftCallbacks
import me.foxtails.palustris.ui.shell.DestinationNavigationCallbacks
import me.foxtails.palustris.ui.shell.DestinationPostCallbacks
import me.foxtails.palustris.ui.shell.DirectMessagesContract
import me.foxtails.palustris.ui.shell.DraftsContract
import me.foxtails.palustris.ui.shell.EmojiPresentation
import me.foxtails.palustris.ui.shell.HomeContract
import me.foxtails.palustris.ui.shell.NotificationSettingsContract
import me.foxtails.palustris.ui.shell.NotificationsContract
import me.foxtails.palustris.ui.shell.PhotoGridContract
import me.foxtails.palustris.ui.shell.PostInteractions
import me.foxtails.palustris.ui.shell.ProfileContract
import me.foxtails.palustris.ui.shell.SearchContract
import me.foxtails.palustris.ui.shell.ShellBubbleHost
import me.foxtails.palustris.ui.shell.ShellDestinationContent
import me.foxtails.palustris.ui.shell.ShellOverlayHost
import me.foxtails.palustris.ui.shell.ThreadContract
import me.foxtails.palustris.ui.shell.rememberShellOverlayPresenter
import me.foxtails.palustris.ui.SinglePostScreen
import me.foxtails.palustris.ui.directmessages.DirectMessageConversationScreen
import me.foxtails.palustris.ui.motion.LocalPalustrisMotionScheme
import me.foxtails.palustris.ui.motion.compactFloatingEnter
import me.foxtails.palustris.ui.motion.compactFloatingExit
import me.foxtails.palustris.ui.motion.rememberSelectedColor
import me.foxtails.palustris.ui.motion.rememberSelectedScale
import me.foxtails.palustris.ui.motion.springPress
import me.foxtails.palustris.ui.large.LargeScreenShell
import me.foxtails.palustris.ui.large.largeLayoutMode
import me.foxtails.palustris.ui.large.LargeLayoutMode
import me.foxtails.palustris.ui.thread.PostThreadUiState
import me.foxtails.palustris.ui.posts.LocalPostRepostConfirmationOwner
import me.foxtails.palustris.ui.posts.PostRepostConfirmationOwner
import me.foxtails.palustris.ui.posts.LocalPostActionOwner
import me.foxtails.palustris.ui.components.AccountAvatar
import me.foxtails.palustris.ui.layout.CompactHomeTimelineSpacing
import me.foxtails.palustris.ui.layout.CompactOverlayHorizontalPadding
import me.foxtails.palustris.ui.layout.CompactOverlayVerticalPadding
import me.foxtails.palustris.ui.layout.CompactTimelineTabsHeight
import me.foxtails.palustris.ui.layout.compactGlobalNavigationPositioningInsets
import me.foxtails.palustris.ui.navigation.CompactContextualNavigationBar
import me.foxtails.palustris.ui.navigation.HomeTimelineTabs
import me.foxtails.palustris.ui.navigation.contextualActionFor

@Composable
fun PalustrisApp(
    account: Account?,
    sessionGeneration: Long,
    sessionRevision: Long,
    home: HomeContract?,
    photoGrid: PhotoGridContract,
    profile: ProfileContract,
    accountSwitcher: AccountSwitcher,
    composer: ComposerContract,
    search: SearchContract,
    postInteractions: PostInteractions,
    thread: ThreadContract,
    draftsContract: DraftsContract,
    emojiPresentation: EmojiPresentation,
    bookmarks: BookmarksContract,
    notifications: NotificationsContract,
    directMessages: DirectMessagesContract,
    initialNotificationRoute: AppRoute?,
    notificationSettings: NotificationSettingsContract,
) {
    val mediaTransitionRegistry = remember { MediaTransitionRegistry() }
    val repostConfirmationOwner = remember(account?.id, sessionGeneration, sessionRevision) { PostRepostConfirmationOwner() }
    val scope = rememberCoroutineScope()
    val postActionOwner = LocalPostActionOwner.current
    CompositionLocalProvider(
        LocalMediaTransitionRegistry provides mediaTransitionRegistry,
        LocalPostRepostConfirmationOwner provides repostConfirmationOwner,
    ) {
    val onReact = postInteractions.actions::favorite
    val onReshare = postInteractions.actions::repost
    val onBookmark = postInteractions.actions::bookmark
    val onReaction = postInteractions.actions::react
    val availableActions = postInteractions.availableActions
    val quoteEnabled = postInteractions.quoteEnabled
    val overlay = rememberShellOverlayPresenter(
        account = account,
        sessionRevision = sessionRevision,
        reactionMutation = emojiPresentation.capabilities.reactionMutation,
    )
    val screenStates = rememberSaveableStateHolder()
    val homeListState = rememberLazyListState()
    val searchListState = rememberLazyListState()
    val photoGridScrollState = rememberLazyStaggeredGridState()
    val profileListState = rememberLazyListState()
    val motionScheme = LocalPalustrisMotionScheme.current
    val availableTimelines = if (account == null) Timeline.entries.toSet() else home?.state?.availableTimelines ?: setOf(Timeline.Home)
    val navigator = rememberShellNavigator(
        accountId = account?.id,
        initialRoute = initialNotificationRoute,
        availableTimelines = availableTimelines,
        selectedHomeTimeline = home?.state?.selectedTimeline,
        reducedMotion = motionScheme.reducedMotion,
        onClearTransient = overlay::clearPostActionBubble,
        onSearch = search.actions::search,
        onStartConversation = directMessages.actions::startConversation,
    )
    val modalOverlayOpen = navigator.overlay != null || navigator.sheet != null || overlay.profileDialog || overlay.signOutDialog || overlay.mediaRequest != null || overlay.profileImageRequest != null || navigator.singlePost != null || overlay.emojiPickerTarget != null
    val profileTargetId = navigator.viewedProfile?.id ?: account?.id
    val refreshedProfile = profile.state.account?.takeIf { it.id == profileTargetId }
    val displayedProfile = refreshedProfile ?: navigator.viewedProfile ?: account
    val savedKind = bookmarks.state?.kind
    val savedTitle = savedCollectionTitle(savedKind)
    val notificationAccountIdentity = account?.id?.let { "${it.connection.origin}\u0000${it.localId}" } ?: "preview"
    val composerOwner = rememberComposerOwner(
        context = ComposerOwnerContext(
            account = account,
            contract = composer,
            canReply = PostAction.Reply in availableActions,
            canQuote = quoteEnabled,
            composerOpen = navigator.overlay == Overlay.Composer,
            overlayOpen = navigator.overlay != null && navigator.overlay != Overlay.Composer,
        ),
        draftsContract = draftsContract,
        sessionGeneration = sessionGeneration,
        sessionRevision = sessionRevision,
    )
    val hasDraftChanges = composerOwner.hasChanges

    LaunchedEffect(composerOwner.navigation) {
        if (composerOwner.navigation != null) {
            navigator.openComposerOverlay()
            composerOwner.consumeNavigation()
        }
    }

    LaunchedEffect(account?.id) {
        mediaTransitionRegistry.endActive()
        overlay.clearForAccountChange()
        repostConfirmationOwner.dismiss()
        thread.actions.deactivate()
    }
    // A same-account reauthentication changes the durable revision but not the account id.
    // Rebind the popup authority so a stale handler cannot run a later reaction selection.
    LaunchedEffect(sessionGeneration, sessionRevision) {
        overlay.clearForSessionChange()
    }
    LaunchedEffect(navigator.destination, navigator.searchPanel, account?.id, sessionGeneration) {
        if (navigator.destination == Destination.Search && navigator.searchPanel == SearchPanel.PhotoGrid) {
            photoGrid.actions.ensureLoaded()
        }
    }
    LaunchedEffect(photoGrid.state.selectedFeed, account?.id, sessionGeneration) {
        photoGridScrollState.scrollToItem(0)
        if (navigator.singlePostOrigin == LargePostOrigin.PhotoGrid) navigator.clearSelectedPost()
    }
    LaunchedEffect(navigator.singlePost?.post?.id, navigator.singlePostOrigin, navigator.singlePostOrigin.supportsComments()) {
        thread.actions.activate(navigator.singlePost, navigator.singlePostOrigin.supportsComments())
    }
    LaunchedEffect(navigator.destination, navigator.page, navigator.overlayKey, navigator.sheet, overlay.profileDialog, overlay.signOutDialog, overlay.mediaRequest, navigator.singlePost, navigator.notificationRoute) {
        overlay.clearPostActionBubble()
        postActionOwner?.dismiss()
        repostConfirmationOwner.dismiss()
        postActionOwner?.dismiss()
    }
    LaunchedEffect(overlay.pendingExpandedReactionTarget, overlay.postActionBubbleTarget) {
        overlay.promotePendingExpansion()
    }

    val handleReply: (OwnedPost) -> Unit = { target ->
        overlay.clearPostActionBubble()
        composerOwner.requestReply(target)
    }
    val handleQuote: (OwnedPost) -> Unit = { target ->
        overlay.clearPostActionBubble()
        composerOwner.requestQuote(target)
    }

    fun openComposer() {
        overlay.clearPostActionBubble()
        composerOwner.requestNew()
    }

    fun closeComposer() {
        if (composer.publishing || composerOwner.closing) return
        if (hasDraftChanges) composerOwner.save { navigator.closeOverlay() } else navigator.closeOverlay()
    }
    fun discardProfileEditor() {
        navigator.closeOverlay()
        profile.actions.closeEditor()
    }

    fun closeProfile() {
        if (profile.state.savingProfile) return
        if (profile.state.editorDirty) overlay.profileDialog = true else discardProfileEditor()
    }

    fun openProfileEditor() {
        if (account != null && displayedProfile?.id == account.id && profile.state.editableSupported) {
            overlay.clearPostActionBubble()
            profile.actions.openEditor()
            navigator.openEditProfileOverlay()
        }
    }
    fun closeNotificationSettings() { navigator.closeOverlay() }
    fun openNotificationTarget(route: AppRoute) {
        if (route !is AppRoute.Profile) {
            navigator.notificationRoute = null
            return
        }
        val target = notifications.state.items
            .firstOrNull { it.target == me.foxtails.palustris.domain.NotificationTarget.Profile(route.profileId) }
            ?.actors
            ?.firstOrNull { it.id == route.profileId }
        if (target != null) navigator.openProfile(target) else navigator.notificationRoute = null
    }

    fun openSinglePost(post: OwnedPost, origin: LargePostOrigin = LargePostOrigin.Other) {
        overlay.mediaRequest = null
        navigator.openSinglePost(post, origin)
    }

    fun latestSelectedPost(): OwnedPost? {
        val selected = navigator.singlePost ?: return null
        // Resolve through the origin first. Unrelated collections stay as fallback
        // sources only, so the origin snapshot wins when several collections hold
        // the same post. Ownership still filters every candidate below.
        val homePosts = home?.state?.ownedPosts.orEmpty()
        val photoGridPosts = photoGrid.state.posts
        val savedPosts = bookmarks.state?.posts.orEmpty()
        val profilePosts = profile.state.pinnedPosts +
            profile.state.pages.values.flatMap { it.posts }
        val candidates = when (navigator.singlePostOrigin) {
            LargePostOrigin.Home -> homePosts + photoGridPosts + savedPosts + profilePosts
            LargePostOrigin.PhotoGrid -> photoGridPosts + homePosts + savedPosts + profilePosts
            LargePostOrigin.Saved -> savedPosts + homePosts + photoGridPosts + profilePosts
            LargePostOrigin.Profile -> profilePosts + homePosts + photoGridPosts + savedPosts
            else -> homePosts + photoGridPosts + savedPosts + profilePosts
        }
        return candidates.firstOrNull {
            it.fetchedBy == selected.fetchedBy &&
                it.sessionRevision == selected.sessionRevision &&
                it.post.id == selected.post.id
        } ?: selected
    }

    val selectedThreadState = thread.state?.takeIf { state ->
        val selected = navigator.singlePost ?: return@takeIf false
        state.focal?.effectiveTargetId() == selected.effectiveTargetId()
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val presentationMode = largeLayoutMode(maxWidth.value)
        val largePresentation = presentationMode != LargeLayoutMode.Compact
        val windowWidth = maxWidth
        val navigationMode = NavigationModeObserver.current(LocalView.current)
        SystemBars(
            mediaViewerOpen = overlay.mediaRequest != null || overlay.profileImageRequest != null,
            largePresentation = largePresentation,
        )
        fun backState() = ShellBackState(
            mediaViewerOpen = overlay.mediaRequest != null,
            profileImageOpen = overlay.profileImageRequest != null,
            largePresentation = largePresentation,
            notificationSettingsOpen = navigator.overlay == Overlay.NotificationSettings,
            composerOpen = navigator.overlay == Overlay.Composer,
            editProfileOpen = navigator.overlay == Overlay.EditProfile,
            singlePostOpen = navigator.singlePost != null,
            notificationRouteOpen = navigator.notificationRoute != null,
            pageOpen = navigator.page != null,
            atHome = navigator.destination == Destination.Home,
        )
        val backSurface = topSurfaceForBack(backState())
        fun dismissTopSurface() {
            when (topSurfaceForBack(backState())) {
                ShellTopSurface.ProfileImage -> overlay.profileImageRequest = null
                ShellTopSurface.NotificationSettings -> closeNotificationSettings()
                ShellTopSurface.Composer -> closeComposer()
                ShellTopSurface.EditProfile -> closeProfile()
                ShellTopSurface.SinglePost -> navigator.clearSelectedPost()
                ShellTopSurface.NotificationRoute -> navigator.notificationRoute = null
                ShellTopSurface.Page -> navigator.page = null
                ShellTopSurface.Home -> navigator.selectDestination(Destination.Home)
                null -> Unit
            }
        }
        BackHandler(enabled = backSurface != null, onBack = ::dismissTopSurface)
        Row(
            Modifier.fillMaxSize().edgeSwipeDismiss(
                enabled = navigationMode == NavigationMode.NonGesture && backSurface != null,
                onDismiss = ::dismissTopSurface,
            ),
        ) {
            Box(Modifier.weight(1f).fillMaxHeight()) {
                val postCallbacks = DestinationPostCallbacks(
                    availableActions = availableActions,
                    quoteEnabled = quoteEnabled,
                    onReact = onReact,
                    onReply = handleReply,
                    onReshare = onReshare,
                    onBookmark = onBookmark,
                    onReaction = onReaction,
                    onQuote = handleQuote,
                )
                val draftCallbacks = DestinationDraftCallbacks(
                    drafts = composerOwner.drafts,
                    onLoadDraft = { item -> overlay.clearPostActionBubble(); composerOwner.requestDraft(item) },
                    onDeleteDraft = { item -> composerOwner.deleteDraft(item) },
                )
                val navigationCallbacks = DestinationNavigationCallbacks(
                    onOpenPost = ::openSinglePost,
                    onOpenNotificationTarget = ::openNotificationTarget,
                    onEditProfile = ::openProfileEditor,
                )
                if (largePresentation) {
                    LargeScreenShell(
                        windowWidth = windowWidth,
                        selectedTarget = largeTargetFor(navigator.destination, navigator.searchPanel, navigator.notificationsPanel),
                        account = account,
                        hasDetail = navigator.singlePost != null,
                        twoPane = presentationMode == LargeLayoutMode.Expanded &&
                            (navigator.destination == Destination.Home || (navigator.destination == Destination.Profile && navigator.singlePost != null)),
                        onTargetSelected = navigator::selectLargeTarget,
                        onOpenAccounts = { overlay.clearPostActionBubble(); navigator.sheet = "Accounts" },
                        onCompose = ::openComposer,
                        primaryContent = { paneModifier ->
                            ShellDestinationContent(
                                paneModifier = paneModifier,
                                navigator = navigator,
                                overlay = overlay,
                                screenStates = screenStates,
                                homeListState = homeListState,
                                searchListState = searchListState,
                                photoGridScrollState = photoGridScrollState,
                                profileListState = profileListState,
                                largePresentation = largePresentation,
                                account = account,
                                displayedProfile = displayedProfile,
                                savedTitle = savedTitle,
                                notificationAccountIdentity = notificationAccountIdentity,
                                availableTimelines = availableTimelines,
                                sessionRevision = sessionRevision,
                                home = home,
                                photoGrid = photoGrid,
                                profile = profile,
                                search = search,
                                bookmarks = bookmarks,
                                notifications = notifications,
                                directMessages = directMessages,
                                accountSwitcher = accountSwitcher,
                                postCallbacks = postCallbacks,
                                draftCallbacks = draftCallbacks,
                                navigationCallbacks = navigationCallbacks,
                            )
                        },
                         detailContent = { paneModifier ->
                             val threadEnabled = selectedThreadState != null && navigator.singlePostOrigin.supportsComments()
                              val detail = detailActionsFor(
                                  origin = navigator.singlePostOrigin,
                                  profile = profile,
                                 bookmarks = bookmarks,
                                 fallback = DetailActions(onReact, handleReply, onReshare, onBookmark, onReaction),
                             )
                             AppLargeDetailPane(
                                 selected = selectedThreadState?.focal ?: latestSelectedPost(),
                                 origin = navigator.singlePostOrigin,
                                 availableActions = availableActions,
                                 threadState = selectedThreadState,
                                 onClose = { navigator.clearSelectedPost() },
                                 onReact = detail.favorite,
                                 onReply = detail.reply,
                                 onReshare = detail.reshare,
                                 onBookmark = detail.bookmark,
                                 onReaction = detail.react,
                                 onOpenProfile = navigator::openProfile,
                                 onSearchHashtag = navigator::openHashtagSearch,
                                 onOpenHashtagBubble = overlay::openHashtagBubble,
                                  onOpenReactionBubble = { post, bounds, handler -> overlay.openReactionBubble(post, bounds, handler) },
                                  onOpenReactionPicker = overlay::expandReactionPicker,
                                  onOpenMedia = overlay::openMedia,
                                 onOpenUsername = navigator::openAccountSearch,
                                 onThreadRefresh = thread.actions::refresh,
                                 onThreadContinue = thread.actions::continueAcquisition,
                                 quoteEnabled = quoteEnabled,
                                 onQuote = handleQuote,
                                 modifier = paneModifier,
                             )
                         },
                    )
                } else {
                    ShellDestinationContent(
                        paneModifier = Modifier.fillMaxSize(),
                        navigator = navigator,
                        overlay = overlay,
                        screenStates = screenStates,
                        homeListState = homeListState,
                        searchListState = searchListState,
                        photoGridScrollState = photoGridScrollState,
                        profileListState = profileListState,
                        largePresentation = largePresentation,
                        account = account,
                        displayedProfile = displayedProfile,
                        savedTitle = savedTitle,
                        notificationAccountIdentity = notificationAccountIdentity,
                        availableTimelines = availableTimelines,
                        sessionRevision = sessionRevision,
                        home = home,
                        photoGrid = photoGrid,
                        profile = profile,
                        search = search,
                        bookmarks = bookmarks,
                        notifications = notifications,
                        directMessages = directMessages,
                        accountSwitcher = accountSwitcher,
                        postCallbacks = postCallbacks,
                        draftCallbacks = draftCallbacks,
                        navigationCallbacks = navigationCallbacks,
                    )
                }
                 if (!largePresentation && navigator.page == null && !modalOverlayOpen) {
                     androidx.compose.animation.AnimatedVisibility(
                         visible = navigator.navigationVisible,
                        enter = motionScheme.compactFloatingEnter(bottom = true),
                        exit = motionScheme.compactFloatingExit(bottom = true),
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .zIndex(1f),
                    ) {
                        // This branch only positions the overlay. Page content remains
                        // full-size behind it; only scroll content owns end clearance.
                         Box(Modifier.fillMaxWidth().windowInsetsPadding(compactGlobalNavigationPositioningInsets()).padding(horizontal = CompactOverlayHorizontalPadding, vertical = CompactOverlayVerticalPadding), contentAlignment = Alignment.Center) {
                            Column(
                                modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth(),
                                 horizontalAlignment = Alignment.End,
                             ) {
                                 if (navigator.destination == Destination.Home) {
                                      HomeTimelineTabs(
                                          timelines = availableTimelines,
                                          selected = navigator.timeline,
                                          modifier = Modifier.height(CompactTimelineTabsHeight),
                                          onSelect = { item ->
                                              overlay.clearPostActionBubble()
                                              val changed = item != navigator.timeline
                                              navigator.timeline = item
                                              if (changed) home?.actions?.refresh(item)
                                          },
                                      )
                                      Spacer(Modifier.height(CompactHomeTimelineSpacing))
                                 }
                                 CompactContextualNavigationBar(
                                    destination = navigator.destination,
                                    searchPanel = navigator.searchPanel,
                                     action = contextualActionFor(
                                        destination = navigator.destination,
                                        searchPanel = navigator.searchPanel,
                                        notificationsPanel = navigator.notificationsPanel,
                                        profileTarget = displayedProfile,
                                        authenticatedAccountId = account?.id,
                                        profileState = profile.state,
                                        onCompose = ::openComposer,
                                        onSearchToggle = {
                                            navigator.searchPanelName = if (navigator.searchPanel == SearchPanel.Search) {
                                                SearchPanel.PhotoGrid.name
                                            } else {
                                                SearchPanel.Search.name
                                            }
                                        },
                                        onNotificationsToggle = {
                                            navigator.notificationsPanelName = if (navigator.notificationsPanel == NotificationsPanel.Notifications) {
                                                NotificationsPanel.DirectMessages.name
                                            } else {
                                                NotificationsPanel.Notifications.name
                                            }
                                        },
                                        onEditProfile = ::openProfileEditor,
                                        onFollowProfile = profile.actions::follow,
                                        onUnfollowProfile = profile.actions::unfollow,
                                    ),
                                    account = account,
                                     onOpenAccounts = { overlay.clearPostActionBubble(); navigator.sheet = "Accounts" },
                                    onDestinationSelected = navigator::selectDestination,
                                )
                            }
                        }
                    }
                }
            }
        }
          ShellBubbleHost(
              navigator = navigator,
              overlay = overlay,
              postActionOwner = postActionOwner,
              emojiPresentation = emojiPresentation,
              largePresentation = largePresentation,
              account = account,
              sessionRevision = sessionRevision,
              directMessages = directMessages,
          )
         if (!largePresentation) {
             navigator.singlePost?.let { post ->
                 val threadEnabled = selectedThreadState != null && navigator.singlePostOrigin.supportsComments()
                  val detail = detailActionsFor(
                      origin = navigator.singlePostOrigin,
                      profile = profile,
                     bookmarks = bookmarks,
                     fallback = DetailActions(onReact, handleReply, onReshare, onBookmark, onReaction),
                 )
                  SinglePostScreen(
                      ownedPost = post,
                      presentation = navigator.singlePostOrigin.singlePostPresentation(),
                      onClose = { navigator.clearSelectedPost() },
                      availableActions = availableActions,
                       onReact = detail.favorite,
                      onReply = detail.reply,
                      onReshare = detail.reshare,
                      onBookmark = detail.bookmark,
                       onReaction = detail.react,
                     onOpenProfile = navigator::openProfile,
                     onSearchHashtag = navigator::openHashtagSearch,
                      onOpenHashtagBubble = overlay::openHashtagBubble,
                      onOpenReactionBubble = { post, bounds -> overlay.openReactionBubble(post, bounds, onReaction) },
                      onOpenReactionPicker = overlay::expandReactionPicker,
                      onOpenMedia = overlay::openMedia,
                      onOpenUsername = navigator::openAccountSearch,
                      threadState = selectedThreadState.takeIf { threadEnabled },
                      onThreadRefresh = thread.actions::refresh,
                      onThreadContinue = thread.actions::continueAcquisition,
                  )
             }
         }
     }

      ShellOverlayHost(
          navigator = navigator,
          overlay = overlay,
          emojiPresentation = emojiPresentation,
          account = account,
          onReact = onReact,
          onReply = handleReply,
          onReshare = onReshare,
          composerOwner = composerOwner,
          composer = composer,
          profile = profile,
          notificationSettings = notificationSettings,
          accountSwitcher = accountSwitcher,
          onCloseComposer = ::closeComposer,
          onCloseProfile = ::closeProfile,
          onCloseNotificationSettings = ::closeNotificationSettings,
          onDiscardProfileEditor = ::discardProfileEditor,
      )
    }
}
