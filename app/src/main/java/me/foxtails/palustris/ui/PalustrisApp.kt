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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.LocalIndication
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
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
import me.foxtails.palustris.R
import me.foxtails.palustris.data.auth.toAccount
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.CapabilityStatus
import me.foxtails.palustris.domain.EditableProfilePatch
import me.foxtails.palustris.domain.EmojiChoice
import me.foxtails.palustris.domain.Notification
import me.foxtails.palustris.domain.NotificationQuery
import me.foxtails.palustris.domain.OwnedPost
import me.foxtails.palustris.domain.Post
import me.foxtails.palustris.domain.PostAction
import me.foxtails.palustris.domain.effectiveTargetId
import me.foxtails.palustris.domain.SavedPostsKind
import me.foxtails.palustris.domain.Timeline
import me.foxtails.palustris.ui.emoji.ComposerField
import me.foxtails.palustris.ui.emoji.EmojiPickerHost
import me.foxtails.palustris.ui.emoji.EmojiPickerTarget
import me.foxtails.palustris.ui.navigation.AppRoute
import me.foxtails.palustris.ui.notifications.NotificationRouteResolver
import me.foxtails.palustris.ui.notifications.NotificationSettingsScreen
import me.foxtails.palustris.ui.notifications.NotificationSettingsSheet
import me.foxtails.palustris.ui.notifications.NotificationsScreen
import me.foxtails.palustris.ui.profile.ProfileCategory
import me.foxtails.palustris.ui.profile.ProfileScreen
import me.foxtails.palustris.ui.profile.ProfileUiState
import me.foxtails.palustris.ui.profile.editableProfilePatch
import me.foxtails.palustris.ui.media.MediaOpenRequest
import me.foxtails.palustris.ui.media.MediaViewerScreen
import me.foxtails.palustris.ui.media.ImageViewerContent
import me.foxtails.palustris.ui.media.ImageViewerContentScreen
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
import me.foxtails.palustris.ui.composer.ComposerOverlayHost
import me.foxtails.palustris.ui.composer.rememberComposerOwner
import me.foxtails.palustris.ui.shell.AccountSwitcher
import me.foxtails.palustris.ui.shell.BookmarksContract
import me.foxtails.palustris.ui.shell.ComposerContract
import me.foxtails.palustris.ui.shell.DirectMessagesContract
import me.foxtails.palustris.ui.shell.DraftsContract
import me.foxtails.palustris.ui.shell.EmojiPresentation
import me.foxtails.palustris.ui.shell.HomeContract
import me.foxtails.palustris.ui.shell.LikesContract
import me.foxtails.palustris.ui.shell.NotificationSettingsContract
import me.foxtails.palustris.ui.shell.NotificationsContract
import me.foxtails.palustris.ui.shell.PhotoGridContract
import me.foxtails.palustris.ui.shell.PostInteractions
import me.foxtails.palustris.ui.shell.ProfileContract
import me.foxtails.palustris.ui.shell.SearchContract
import me.foxtails.palustris.ui.shell.ThreadContract
import me.foxtails.palustris.ui.SinglePostScreen
import me.foxtails.palustris.ui.directmessages.DirectMessageConversationScreen
import me.foxtails.palustris.ui.motion.AnimatedStatePane
import me.foxtails.palustris.ui.motion.LocalPalustrisMotionScheme
import me.foxtails.palustris.ui.motion.SpringAnimatedContent
import me.foxtails.palustris.ui.motion.compactFloatingEnter
import me.foxtails.palustris.ui.motion.compactFloatingExit
import me.foxtails.palustris.ui.motion.rememberSelectedColor
import me.foxtails.palustris.ui.motion.rememberSelectedScale
import me.foxtails.palustris.ui.motion.springPress
import me.foxtails.palustris.ui.large.LargeBottomDockClearance
import me.foxtails.palustris.ui.large.LargeBottomDock
import me.foxtails.palustris.ui.large.LargeScreenShell
import me.foxtails.palustris.ui.large.LargeTimelineDockContent
import me.foxtails.palustris.ui.large.largeLayoutMode
import me.foxtails.palustris.ui.large.LargeLayoutMode
import me.foxtails.palustris.ui.thread.PostThreadUiState
import me.foxtails.palustris.ui.posts.LocalPostRepostConfirmationOwner
import me.foxtails.palustris.ui.posts.PostRepostConfirmationOwner
import me.foxtails.palustris.ui.posts.LocalPostActionOwner
import me.foxtails.palustris.ui.posts.PostShareSheet
import me.foxtails.palustris.ui.posts.copyPostShareContent
import me.foxtails.palustris.ui.components.AccountAvatar
import me.foxtails.palustris.ui.layout.CompactFilterDockHeight
import me.foxtails.palustris.ui.layout.CompactHomeTimelineSpacing
import me.foxtails.palustris.ui.layout.CompactOverlayHorizontalPadding
import me.foxtails.palustris.ui.layout.CompactOverlayVerticalPadding
import me.foxtails.palustris.ui.layout.CompactSearchDockHeight
import me.foxtails.palustris.ui.layout.CompactTimelineTabsHeight
import me.foxtails.palustris.ui.layout.compactGlobalNavigationPositioningInsets
import me.foxtails.palustris.ui.layout.compactHomeScrollEndClearance
import me.foxtails.palustris.ui.layout.compactScrollEndClearance
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
    likes: LikesContract,
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
    val context = LocalContext.current
    val onReact = postInteractions.actions::favorite
    val onReshare = postInteractions.actions::repost
    val onBookmark = postInteractions.actions::bookmark
    val onReaction = postInteractions.actions::react
    val availableActions = postInteractions.availableActions
    val quoteEnabled = postInteractions.quoteEnabled
    val screenStates = rememberSaveableStateHolder()
    var profileDialog by rememberSaveable { mutableStateOf(false) }
    var signOutDialog by remember { mutableStateOf(false) }
    var mediaRequest by remember { mutableStateOf<MediaOpenRequest?>(null) }
    var profileImageRequest by remember { mutableStateOf<ImageViewerContent?>(null) }
    val homeListState = rememberLazyListState()
    val searchListState = rememberLazyListState()
    val photoGridScrollState = rememberLazyStaggeredGridState()
    val profileListState = rememberLazyListState()
    var emojiPickerTarget by remember { mutableStateOf<EmojiPickerTarget?>(null) }
    var postActionBubbleTarget by remember { mutableStateOf<PostActionBubbleTarget?>(null) }
    var pendingExpandedReactionTarget by remember { mutableStateOf<OwnedPost?>(null) }
    var postReactionHandler by remember { mutableStateOf<((OwnedPost, EmojiChoice) -> Unit)?>(null) }
    var pendingEmojiInsertion by remember { mutableStateOf<Pair<EmojiChoice, ComposerField>?>(null) }
    val motionScheme = LocalPalustrisMotionScheme.current
    val availableTimelines = if (account == null) Timeline.entries.toSet() else home?.state?.availableTimelines ?: setOf(Timeline.Home)
    fun clearPostActionBubble() {
        postActionBubbleTarget = null
        pendingExpandedReactionTarget = null
        postReactionHandler = null
    }
    val navigator = rememberShellNavigator(
        accountId = account?.id,
        initialRoute = initialNotificationRoute,
        availableTimelines = availableTimelines,
        selectedHomeTimeline = home?.state?.selectedTimeline,
        reducedMotion = motionScheme.reducedMotion,
        onClearTransient = ::clearPostActionBubble,
        onSearch = search.actions::search,
        onStartConversation = directMessages.actions::startConversation,
    )
    val modalOverlayOpen = navigator.overlay != null || navigator.sheet != null || profileDialog || signOutDialog || mediaRequest != null || profileImageRequest != null || navigator.singlePost != null || emojiPickerTarget != null
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
        mediaRequest = null
        emojiPickerTarget = null
        postActionBubbleTarget = null
        postReactionHandler = null
        pendingEmojiInsertion = null
        repostConfirmationOwner.dismiss()
        thread.actions.deactivate()
    }
    // A same-account reauthentication changes the durable revision but not the account id.
    // Rebind the popup authority so a stale handler cannot run a later reaction selection.
    LaunchedEffect(sessionGeneration, sessionRevision) {
        postActionBubbleTarget = null
        pendingExpandedReactionTarget = null
        postReactionHandler = null
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
    LaunchedEffect(navigator.destination, navigator.page, navigator.overlayKey, navigator.sheet, profileDialog, signOutDialog, mediaRequest, navigator.singlePost, navigator.notificationRoute) {
        postActionBubbleTarget = null
        postReactionHandler = null
        postActionOwner?.dismiss()
        pendingExpandedReactionTarget = null
        repostConfirmationOwner.dismiss()
        postActionOwner?.dismiss()
    }
    LaunchedEffect(pendingExpandedReactionTarget, postActionBubbleTarget) {
        val pending = pendingExpandedReactionTarget ?: return@LaunchedEffect
        val current = postActionBubbleTarget as? PostActionBubbleTarget.Reaction ?: return@LaunchedEffect
        if (current.ownedPost.fetchedBy == pending.fetchedBy && current.postId == pending.post.id) {
            postActionBubbleTarget = current.copy(mode = ReactionBubbleMode.Expanded)
            pendingExpandedReactionTarget = null
        }
    }

    val handleReply: (OwnedPost) -> Unit = { target ->
        clearPostActionBubble()
        composerOwner.requestReply(target)
    }
    val handleQuote: (OwnedPost) -> Unit = { target ->
        clearPostActionBubble()
        composerOwner.requestQuote(target)
    }

    fun openComposer() {
        clearPostActionBubble()
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
        if (profile.state.editorDirty) profileDialog = true else discardProfileEditor()
    }

    fun openProfileEditor() {
        if (account != null && displayedProfile?.id == account.id && profile.state.editableSupported) {
            clearPostActionBubble()
            profile.actions.openEditor()
            navigator.openEditProfileOverlay()
        }
    }
    fun closeNotificationSettings() { navigator.closeOverlay() }
    fun openHashtagBubble(ownedPost: OwnedPost, hashtags: List<String>, bounds: Rect) {
        postReactionHandler = null
        postActionBubbleTarget = PostActionBubbleTarget.HashtagList(
            postId = ownedPost.post.id,
            hashtags = hashtags,
            anchorBounds = bounds,
        )
    }
    fun openReactionBubble(
        ownedPost: OwnedPost,
        bounds: Rect,
        handler: (OwnedPost, EmojiChoice) -> Unit,
    ) {
        val owner = account ?: return
        if (ownedPost.fetchedBy != owner.id || ownedPost.sessionRevision != sessionRevision ||
            emojiPresentation.capabilities.reactionMutation != CapabilityStatus.Supported
        ) {
            return
        }
        postReactionHandler = handler
        postActionBubbleTarget = PostActionBubbleTarget.Reaction(ownedPost, bounds)
    }
    fun expandReactionPicker(target: OwnedPost) {
        pendingExpandedReactionTarget = target
        val current = postActionBubbleTarget as? PostActionBubbleTarget.Reaction ?: return
        if (current.ownedPost.fetchedBy == target.fetchedBy && current.postId == target.post.id) {
            postActionBubbleTarget = current.copy(mode = ReactionBubbleMode.Expanded)
            pendingExpandedReactionTarget = null
        }
    }
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

    fun openMedia(request: MediaOpenRequest) {
        if (account?.id != null && request.ownedPost.fetchedBy != account.id) return
        if (request.attachmentIndex !in request.ownedPost.post.attachments.indices) return
        clearPostActionBubble()
        mediaRequest = request
    }

    fun openProfileImage(url: String) {
        if (url.isBlank()) return
        val owner = navigator.viewedProfile?.id ?: account?.id
        profileImageRequest = ImageViewerContent(
            url = url,
            identity = "profile:${owner?.connection?.origin}:${owner?.localId}:$url",
        )
        clearPostActionBubble()
    }

    fun openSinglePost(post: OwnedPost, origin: LargePostOrigin = LargePostOrigin.Other) {
        mediaRequest = null
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
        val likedPosts = likes.state?.posts.orEmpty()
        val profilePosts = profile.state.pinnedPosts +
            profile.state.pages.values.flatMap { it.posts }
        val candidates = when (navigator.singlePostOrigin) {
            LargePostOrigin.Home -> homePosts + photoGridPosts + savedPosts + likedPosts + profilePosts
            LargePostOrigin.PhotoGrid -> photoGridPosts + homePosts + savedPosts + likedPosts + profilePosts
            LargePostOrigin.Saved -> savedPosts + homePosts + photoGridPosts + likedPosts + profilePosts
            LargePostOrigin.Liked -> likedPosts + homePosts + photoGridPosts + savedPosts + profilePosts
            LargePostOrigin.Profile -> profilePosts + homePosts + photoGridPosts + savedPosts + likedPosts
            else -> homePosts + photoGridPosts + savedPosts + likedPosts + profilePosts
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
            mediaViewerOpen = mediaRequest != null || profileImageRequest != null,
            largePresentation = largePresentation,
        )
        fun backState() = ShellBackState(
            mediaViewerOpen = mediaRequest != null,
            profileImageOpen = profileImageRequest != null,
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
                ShellTopSurface.ProfileImage -> profileImageRequest = null
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
                @Composable
                fun destinationScaffold(paneModifier: Modifier) {
                Scaffold(
                    modifier = paneModifier.fillMaxSize(),
                    // Compact page bodies receive top/horizontal system insets only.
                    // Content must measure through the floating assembly; scrollables
                    // add end clearance inside their scroll range instead.
                    contentWindowInsets = when {
                        navigator.page == null && navigator.notificationRoute == null && navigator.destination == Destination.Profile ->
                            WindowInsets.systemBars.only(WindowInsetsSides.Horizontal)
                        !largePresentation && navigator.page == null && navigator.notificationRoute == null ->
                            WindowInsets.systemBars.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
                        else -> ScaffoldDefaults.contentWindowInsets
                    },
                    topBar = {
                    AppDestinationTopBar(
                        page = navigator.page,
                        notificationRoute = navigator.notificationRoute,
                        savedTitle = savedTitle,
                        onBack = { clearPostActionBubble(); if (navigator.page != null) navigator.page = null else navigator.notificationRoute = null },
                    )
                }                    ) { padding ->
                    Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
                        SpringAnimatedContent(
                            stateKey = navigator.destination,
                            direction = navigator.destinationTransitionDirection,
                            modifier = Modifier.fillMaxSize(),
                        ) { animatedDestination ->
                        screenStates.SaveableStateProvider(animatedDestination.name) {
                        AnimatedStatePane(
                            stateKey = navigator.notificationRoute ?: navigator.page?.name ?: "${animatedDestination.name}:content",
                            modifier = Modifier.fillMaxSize(),
                        ) {
                        if (navigator.notificationRoute != null) {
                            AppNotificationDetailContent(
                                route = navigator.notificationRoute!!,
                                items = notifications.state.items,
                                onSearchHashtag = navigator::openHashtagSearch,
                                onOpenHashtagBubble = ::openHashtagBubble,
                                 onOpenPost = { post -> openSinglePost(post, LargePostOrigin.Notification) },
                                 onOpenTarget = (navigator.notificationRoute as? AppRoute.Profile)?.let { route ->
                                      { openNotificationTarget(route) }
                                  },
                                 availableActions = availableActions,
                                 onReact = onReact,
                                 onReply = handleReply,
                                 onReshare = onReshare,
                                 onBookmark = onBookmark,
                                 onReaction = onReaction,
                                 onQuote = handleQuote,
                                 quoteEnabled = quoteEnabled,
                                  onOpenReactionBubble = { post, bounds -> openReactionBubble(post, bounds, onReaction) },
                                  sessionRevision = sessionRevision,
                                 largeLayout = largePresentation,
                             )
                          } else if (navigator.page != null) {
                              AppLocalPageContent(
                                  page = navigator.page,
                                 savedPostsState = bookmarks.state,
                                 likedPostsState = likes.state,
                                 drafts = composerOwner.drafts,
                                 onLoadDraft = { item -> clearPostActionBubble(); composerOwner.requestDraft(item) },
                                 onDeleteDraft = { item -> composerOwner.deleteDraft(item) },
                                 onRefreshSavedPosts = bookmarks.actions::refresh,
                                 onLoadMoreSavedPosts = bookmarks.actions::loadMore,
                                 onUnsaveSavedPost = bookmarks.actions::remove,
                                 onRefreshLikedPosts = likes.actions::refresh,
                                 onLoadMoreLikedPosts = likes.actions::loadMore,
                                 onUnsaveLikedPost = likes.actions::toggle,
                                 onUpgradeSavedPermissions = bookmarks.actions::upgradePermissions,
                                 onReact = onReact,
                                 onReply = handleReply,
                                 onReshare = onReshare,
                                 onBookmark = onBookmark,
                                 onSavedPostReaction = bookmarks.actions::react,
                                 onLikedPostReaction = likes.actions::react,
                                  onOpenSavedReactionBubble = { post, bounds -> openReactionBubble(post, bounds, bookmarks.actions::react) },
                                  onOpenLikedReactionBubble = { post, bounds -> openReactionBubble(post, bounds, likes.actions::react) },
                                  onOpenReactionPicker = ::expandReactionPicker,
                                 onOpenMedia = ::openMedia,
                                 onOpenPost = ::openSinglePost,
                                 onOpenProfile = navigator::openProfile,
                                 onSearchHashtag = navigator::openHashtagSearch,
                                 onOpenHashtagBubble = ::openHashtagBubble,
                                 onOpenUsername = navigator::openAccountSearch,
                                 availableActions = availableActions,
                                 largeLayout = largePresentation,
                             )
                         } else when (animatedDestination) {
                                   Destination.Home -> if (home != null) HomeFeed(
                                           state = home.state,
                                           compactLayout = !largePresentation,
                                           onRefresh = { home.actions.refresh(navigator.timeline) },
                                           onLoadMore = { home.actions.loadMore(navigator.timeline) },
                                           onSignIn = accountSwitcher.actions::signOut,
                                           availableActions = availableActions,
                                           quoteEnabled = quoteEnabled,
                                           onScrollDirectionChanged = { if (navigator.destination == Destination.Home && animatedDestination == Destination.Home) navigator.navigationVisible = it },
                                          onReact = onReact,
                                          onReply = handleReply,
                                          onReshare = onReshare,
                                          onBookmark = onBookmark,
                                          onReaction = onReaction,
                                           onOpenReactionBubble = { ownedPost, bounds -> openReactionBubble(ownedPost, bounds, onReaction) },
                                           onOpenReactionPicker = ::expandReactionPicker,
                                          onQuote = handleQuote,
                                          onOpenProfile = navigator::openProfile,
                                          onSearchHashtag = navigator::openHashtagSearch,
                                          onOpenHashtagBubble = ::openHashtagBubble,
                                          onOpenMedia = ::openMedia,
                                          onOpenPost = { post -> openSinglePost(post, LargePostOrigin.Home) },
                                          onOpenUsername = navigator::openAccountSearch,
                                          listState = homeListState,
                                          topContentPadding = if (largePresentation) 16.dp else null,
                                          bottomContentClearance = if (largePresentation) LargeBottomDockClearance else null,
                                          refreshIndicatorTopPadding = if (largePresentation) 16.dp else null,
                                           bottomDock = if (largePresentation) ({
                                         LargeTimelineDockContent(availableTimelines, navigator.timeline) { item ->
                                             val changed = item != navigator.timeline
                                             if (changed) navigator.clearSelectedPost()
                                             navigator.timeline = item
                                             if (changed) home.actions.refresh(item)
                                       }
                                     }) else null,
                                      ) else Box(Modifier.fillMaxSize()) {
                                        EmptyState(AppIcons.Home, stringResource(R.string.feed_timeline_empty_title), stringResource(R.string.feed_timeline_empty_subtitle, stringResource(timelineLabelRes(navigator.timeline))))
                                       if (largePresentation) {
                                           LargeBottomDock(modifier = Modifier.align(Alignment.BottomStart), content = {
                                                LargeTimelineDockContent(availableTimelines, navigator.timeline) { item ->
                                                    val changed = item != navigator.timeline
                                                    if (changed) navigator.clearSelectedPost()
                                                    navigator.timeline = item
                                                    if (changed && home != null) home.actions.refresh(item)
                                               }
                                           })
                                       }
                                   }
                                   Destination.Search -> AnimatedStatePane(
                                       stateKey = navigator.searchPanel,
                                       modifier = Modifier.fillMaxSize(),
                                    ) { panel ->
                                       when (panel) {
                                            SearchPanel.Search -> SearchScreen(
                                               accountSearch = search.state,
                                               onSearchAccounts = search.actions::search,
                                               onAccountClick = navigator::openProfile,
                                               availableActions = availableActions,
                                               onReact = onReact,
                                               onReply = handleReply,
                                               onReshare = onReshare,
                                               onBookmark = onBookmark,
                                               onReaction = onReaction,
                                                onOpenReactionBubble = { ownedPost, bounds ->
                                                    openReactionBubble(ownedPost, bounds, onReaction)
                                                },
                                                onOpenReactionPicker = ::expandReactionPicker,
                                               quoteEnabled = quoteEnabled,
                                               onQuote = handleQuote,
                                               onSearchHashtag = navigator::openHashtagSearch,
                                               onOpenHashtagBubble = ::openHashtagBubble,
                                               onLoadMoreSearch = search.actions::loadMore,
                                               initialQuery = navigator.searchPrefill,
                                               sharedQuery = navigator.searchQuery,
                                               sharedTab = navigator.searchCategory,
                                               onSharedQueryChange = { navigator.searchQuery = it },
                                               onSharedTabChange = { navigator.searchCategory = it },
                                               listState = searchListState.takeIf { largePresentation },
                                               largeLayout = largePresentation,
                                               compactLayout = !largePresentation,
                                               compactNavigationVisible = !largePresentation,
                                                mediaOwner = account?.id,
                                                sessionRevision = sessionRevision,
                                               onOpenMedia = ::openMedia,
                                                onOpenPost = { post -> openSinglePost(post, LargePostOrigin.Search) },
                                               onOpenUsername = navigator::openAccountSearch,
                                           )
                                             SearchPanel.PhotoGrid -> PhotoGridScreen(
                                                state = photoGrid.state,
                                                onRefresh = photoGrid.actions::refresh,
                                                onLoadMore = photoGrid.actions::loadMore,
                                                onSelectFeed = photoGrid.actions::selectFeed,
                                                onAddHashtag = photoGrid.actions::addHashtag,
                                                onClearPreferenceError = photoGrid.actions::clearPreferenceError,
                                                 onOpenPost = { post -> openSinglePost(post, LargePostOrigin.PhotoGrid) },
                                               compactLayout = !largePresentation,
                                                 compactNavigationVisible = !largePresentation,
                                                 gridState = photoGridScrollState,
                                            )
                                       }
                                   }
                                   Destination.Notifications -> AppNotificationsDestinationContent(
                                       panel = navigator.notificationsPanel,
                                       account = account,
                                       compactLayout = !largePresentation,
                                       compactNavigationVisible = navigator.navigationVisible,
                                      notificationAccountIdentity = notificationAccountIdentity,
                                      notificationState = notifications.state,
                                      onRefreshNotifications = notifications.actions::refresh,
                                      onLoadMoreNotifications = notifications.actions::loadMore,
                                      onMarkNotificationSeen = notifications.actions::markSeen,
                                      onDismissNotification = notifications.actions::dismiss,
                                      onFollowRequest = notifications.actions::respondToFollowRequest,
                                       onOpenNotification = { notification ->
                                           clearPostActionBubble()
                                           if (largePresentation) navigator.clearSelectedPost()
                                           navigator.notificationRoute = NotificationRouteResolver.resolve(notification)
                                       },
                                      onSelectQuery = notifications.actions::selectQuery,
                                      onMarkAllRead = notifications.actions::markAllRead,
                                       onOpenSettings = {
                                           if (account != null) {
                                               clearPostActionBubble()
                                               navigator.openNotificationSettingsOverlay()
                                           }
                                       },
                                      directMessageState = directMessages.state,
                                      onRefreshDirectMessages = directMessages.actions::refresh,
                                      onLoadMoreDirectMessages = directMessages.actions::loadMore,
                                      onOpenDirectConversation = directMessages.actions::openConversation,
                                      onBackDirectConversation = directMessages.actions::closeConversation,
                                      onEditorTextChange = directMessages.actions::updateEditor,
                                      onSendDirectMessage = directMessages.actions::send,
                                  )
                  Destination.Profile -> ProfileScreen(
                      account = displayedProfile,
                      profileState = profile.state,
                      compactLayout = !largePresentation,
                      largeLayout = largePresentation,
                      largeShowSummary = navigator.singlePost == null,
                      listState = profileListState,
                      compactNavigationVisible = navigator.navigationVisible,
                     authenticatedAccountId = account?.id,
                     onProfileShown = profile.actions::open,
                     onCategorySelected = { category ->
                         if (largePresentation) navigator.clearSelectedPost()
                         profile.actions.selectCategory(category)
                     },
                     onRefresh = profile.actions::refresh,
                     onLoadMore = profile.actions::loadMore,
                     onFollow = profile.actions::follow,
                     onUnfollow = profile.actions::unfollow,
                     onMessage = navigator::openDirectMessage,
                     onOpenProfileImage = ::openProfileImage,
                     onEditProfile = ::openProfileEditor,
                      onOpenDrafts = {
                          if (largePresentation) navigator.clearSelectedPost()
                          if (account != null && displayedProfile?.id == account.id) navigator.page = LocalPage.Drafts
                      },
                      onOpenBookmarks = {
                          if (largePresentation) navigator.clearSelectedPost()
                          if (account != null && displayedProfile?.id == account.id) navigator.page = LocalPage.SavedPosts
                      },
                      onOpenLikes = {
                          if (largePresentation) navigator.clearSelectedPost()
                          if (account != null && displayedProfile?.id == account.id) navigator.page = LocalPage.Likes
                      },
                     onOpenProfile = navigator::openProfile,
                     onSearchHashtag = navigator::openHashtagSearch,
                     onOpenHashtagBubble = ::openHashtagBubble,
                     availableActions = availableActions,
                     onReact = onReact,
                     onReply = handleReply,
                     onReshare = onReshare,
                     onBookmark = onBookmark,
                     onReaction = profile.actions::react,
                      onOpenReactionBubble = { ownedPost, bounds ->
                          openReactionBubble(ownedPost, bounds, profile.actions::react)
                      },
                      onOpenReactionPicker = ::expandReactionPicker,
                     onOpenMedia = ::openMedia,
                      onOpenPost = { post -> openSinglePost(post, LargePostOrigin.Profile) },
                      onOpenUsername = navigator::openAccountSearch,
                      quoteEnabled = quoteEnabled,
                      onQuote = handleQuote,
                  )
                               }
                          }
                          }
                      }
                  }
                 }
                }
                if (largePresentation) {
                    LargeScreenShell(
                        windowWidth = windowWidth,
                        selectedTarget = largeTargetFor(navigator.destination, navigator.searchPanel, navigator.notificationsPanel),
                        account = account,
                        hasDetail = navigator.singlePost != null,
                        twoPane = presentationMode == LargeLayoutMode.Expanded &&
                            (navigator.destination == Destination.Home || (navigator.destination == Destination.Profile && navigator.singlePost != null)),
                        onTargetSelected = navigator::selectLargeTarget,
                        onOpenAccounts = { clearPostActionBubble(); navigator.sheet = "Accounts" },
                        onCompose = ::openComposer,
                        primaryContent = { paneModifier -> destinationScaffold(paneModifier) },
                         detailContent = { paneModifier ->
                             val threadEnabled = selectedThreadState != null && navigator.singlePostOrigin.supportsComments()
                             val detail = detailActionsFor(
                                 origin = navigator.singlePostOrigin,
                                 threadActive = threadEnabled,
                                 thread = thread,
                                 profile = profile,
                                 bookmarks = bookmarks,
                                 likes = likes,
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
                                 onOpenHashtagBubble = ::openHashtagBubble,
                                 onOpenReactionBubble = { post, bounds, handler -> openReactionBubble(post, bounds, handler) },
                                 onOpenMedia = ::openMedia,
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
                    destinationScaffold(Modifier.fillMaxSize())
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
                                              clearPostActionBubble()
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
                                     onOpenAccounts = { clearPostActionBubble(); navigator.sheet = "Accounts" },
                                    onDestinationSelected = navigator::selectDestination,
                                )
                            }
                        }
                    }
                }
            }
        }
         val hashtagBottomClearance = if (largePresentation || navigator.page != null || navigator.notificationRoute != null) {
            0.dp
        } else {
            when (navigator.destination) {
                 Destination.Home -> compactHomeScrollEndClearance()
                 Destination.Search -> compactScrollEndClearance(
                     controlStackHeight = CompactSearchDockHeight,
                    navigationVisible = navigator.navigationVisible,
                    ime = WindowInsets.ime,
                )
                 Destination.Notifications, Destination.Profile -> compactScrollEndClearance(
                     controlStackHeight = CompactFilterDockHeight,
                    navigationVisible = navigator.navigationVisible,
                )
            }
        }
          PostActionBubbleHost(
            target = postActionBubbleTarget,
            emojiCatalog = emojiPresentation.catalog,
             emojiCapabilities = emojiPresentation.capabilities,
             onLoadEmojiCatalog = emojiPresentation.actions::loadCatalog,
             onRetryEmojiCatalog = emojiPresentation.actions::retryCatalog,
             onToggleEmojiGroupCollapsed = emojiPresentation.actions::toggleGroupCollapsed,
             onToggleEmojiGroupPinned = emojiPresentation.actions::toggleGroupPinned,
             onTogglePinnedEmoji = emojiPresentation.actions::togglePinnedEmoji,
             onDismiss = ::clearPostActionBubble,
            onHashtagSelected = { hashtag ->
                clearPostActionBubble()
                navigator.openHashtagSearch(hashtag)
            },
            onReactionSelected = { target, choice ->
                val owner = account
                val handler = postReactionHandler
                if (owner != null && target.fetchedBy == owner.id && target.sessionRevision == sessionRevision &&
                    emojiPresentation.capabilities.reactionMutation == CapabilityStatus.Supported
                ) {
                    handler?.invoke(target, choice)
                }
                clearPostActionBubble()
            },
            onReactionModeChanged = { expanded -> postActionBubbleTarget = expanded },
             hashtagBottomClearance = hashtagBottomClearance,
          )
          postActionOwner?.target?.let { target ->
              PostShareSheet(
                  target = target,
                  relationship = postActionOwner.relationship,
                  report = postActionOwner.report,
                  onDismiss = postActionOwner::dismiss,
                  onRelationshipAction = postActionOwner::mutate,
                  onSubmitReport = postActionOwner::submitReport,
                  onOpenDirectMessage = {
                      val recipient = target.author
                      postActionOwner.dismiss()
                      directMessages.actions.startConversation(recipient)
                  },
                  onCopyLink = { copyPostShareContent(context, target.post) },
                  onShare = {
                      sharePost(context, target.post)
                      postActionOwner.dismiss()
                  },
              )
          }
         if (!largePresentation) {
             navigator.singlePost?.let { post ->
                 val threadEnabled = selectedThreadState != null && navigator.singlePostOrigin.supportsComments()
                 val detail = detailActionsFor(
                     origin = navigator.singlePostOrigin,
                     threadActive = threadEnabled,
                     thread = thread,
                     profile = profile,
                     bookmarks = bookmarks,
                     likes = likes,
                     fallback = DetailActions(onReact, handleReply, onReshare, onBookmark, onReaction),
                 )
                  SinglePostScreen(
                      ownedPost = post,
                      presentation = navigator.singlePostOrigin.singlePostPresentation(),
                      onClose = { navigator.clearSelectedPost() },
                      availableActions = availableActions +
                           if (navigator.singlePostOrigin == LargePostOrigin.Liked) setOf(PostAction.Favorite) else emptySet(),
                       onReact = detail.favorite,
                      onReply = detail.reply,
                      onReshare = detail.reshare,
                      onBookmark = detail.bookmark,
                       onReaction = detail.react,
                     onOpenProfile = navigator::openProfile,
                     onSearchHashtag = navigator::openHashtagSearch,
                     onOpenHashtagBubble = ::openHashtagBubble,
                     onOpenMedia = ::openMedia,
                      onOpenUsername = navigator::openAccountSearch,
                      threadState = selectedThreadState.takeIf { threadEnabled },
                      onThreadRefresh = thread.actions::refresh,
                      onThreadContinue = thread.actions::continueAcquisition,
                  )
             }
         }
     }

     mediaRequest?.let { request ->
         MediaViewerScreen(
            request = request,
            onClose = { mediaRequest = null },
            onReact = onReact,
            onReply = handleReply,
            onReshare = onReshare,
         )
     }

     profileImageRequest?.let { request ->
         ImageViewerContentScreen(request, onClose = { profileImageRequest = null })
     }

       if (navigator.sheet != null) AppSelectionSheet(
           account = account,
           accounts = accountSwitcher.accounts,
           onDismiss = { navigator.sheet = null },
           onSwitchAccount = accountSwitcher.actions::switchTo,
           onAddAccount = accountSwitcher.actions::addAccount,
           onOpenSettings = accountSwitcher.actions::openSettings,
           onSignOut = { signOutDialog = true },
       )

    if (navigator.overlay == Overlay.Composer) ComposerOverlayHost(
        owner = composerOwner,
        contract = composer,
        account = account,
        onDismiss = ::closeComposer,
        onClose = { navigator.closeOverlay() },
        onRequestEmoji = { field -> emojiPickerTarget = EmojiPickerTarget.Composer(field) },
        pendingEmojiInsertion = pendingEmojiInsertion,
        onEmojiInsertionApplied = { pendingEmojiInsertion = null },
    )

    if (navigator.overlay == Overlay.EditProfile && account != null) me.foxtails.palustris.ui.profile.EditProfileSheet(
        account = account,
        editor = profile.state.editorDraft,
        editorBase = profile.state.editorBase,
        capabilities = profile.state.editorCapabilities,
        emoji = profile.state.account?.emoji ?: emptyMap(),
        loading = profile.state.editableLoading,
        saving = profile.state.savingProfile,
        error = profile.state.editError ?: profile.state.editableError,
        onEditorChange = profile.actions::updateEditor,
        onSave = { patch ->
            profile.actions.saveEditor(patch) {
                navigator.closeOverlay()
            }
        },
        onClose = ::closeProfile,
    )

    if (emojiPickerTarget != null) {
        EmojiPickerHost(
            target = emojiPickerTarget,
            catalog = emojiPresentation.catalog,
            selectionMode = emojiPresentation.capabilities.selectionMode,
            mutationSupported = emojiPresentation.capabilities.reactionMutation == CapabilityStatus.Supported,
            onLoadCatalog = emojiPresentation.actions::loadCatalog,
            onRetryCatalog = emojiPresentation.actions::retryCatalog,
            onToggleGroupCollapsed = emojiPresentation.actions::toggleGroupCollapsed,
            onToggleGroupPinned = emojiPresentation.actions::toggleGroupPinned,
            onTogglePinnedEmoji = emojiPresentation.actions::togglePinnedEmoji,
            onDismiss = {
                emojiPickerTarget = null
            },
            onEmojiSelected = { choice ->
                val target = emojiPickerTarget
                when (target) {
                    is EmojiPickerTarget.Reaction -> Unit
                    is EmojiPickerTarget.Composer -> pendingEmojiInsertion = choice to target.field
                    null -> Unit
                }
                emojiPickerTarget = null
            },
        )
    }

    if (navigator.overlay == Overlay.NotificationSettings && account != null) NotificationSettingsSheet(
        state = notificationSettings.state,
        onDismiss = ::closeNotificationSettings,
        onAlertsEnabled = notificationSettings.actions::setAlertsEnabled,
        onShowPreviews = notificationSettings.actions::setShowPreviews,
        onPeriodicFallback = notificationSettings.actions::setPeriodicFallback,
        onQuietHours = notificationSettings.actions::setQuietHours,
        onCategoryChanged = notificationSettings.actions::setCategoryEnabled,
        onRunLocalTest = notificationSettings.actions::runLocalTest,
        onRetryRegistration = notificationSettings.actions::retryRegistration,
        onPermissionChanged = notificationSettings.actions::refreshPermission,
        onRefreshDistributors = notificationSettings.actions::refreshDistributors,
        onSelectDistributor = notificationSettings.actions::selectDistributor,
        onRunPushConnectionTest = notificationSettings.actions::runPushConnectionTest,
        onRetryStorage = notificationSettings.actions::retryStorage,
        onResetStorage = notificationSettings.actions::resetStorage,
    )

    BackHandler(enabled = navigator.overlay == Overlay.NotificationSettings) {
        closeNotificationSettings()
    }

    AppDialogs(
        profileDialog = profileDialog,
        onProfileDialogDismiss = { profileDialog = false },
        onDiscardProfile = ::discardProfileEditor,
        signOutDialog = signOutDialog,
        onSignOutDialogDismiss = { signOutDialog = false },
        onSignOut = accountSwitcher.actions::signOut,
    )
    }
}
