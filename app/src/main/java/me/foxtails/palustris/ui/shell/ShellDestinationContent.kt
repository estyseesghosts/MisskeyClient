package me.foxtails.palustris.ui.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.foxtails.palustris.R
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.EmojiChoice
import me.foxtails.palustris.domain.OwnedPost
import me.foxtails.palustris.domain.PostAction
import me.foxtails.palustris.domain.PostDraft
import me.foxtails.palustris.domain.Timeline
import me.foxtails.palustris.ui.AppDestinationTopBar
import me.foxtails.palustris.ui.AppIcons
import me.foxtails.palustris.ui.AppLocalPageContent
import me.foxtails.palustris.ui.AppNotificationDetailContent
import me.foxtails.palustris.ui.AppNotificationsDestinationContent
import me.foxtails.palustris.ui.Destination
import me.foxtails.palustris.ui.EmptyState
import me.foxtails.palustris.ui.feed.HomeFeed
import me.foxtails.palustris.ui.LargePostOrigin
import me.foxtails.palustris.ui.LocalPage
import me.foxtails.palustris.ui.photogrid.PhotoGridScreen
import me.foxtails.palustris.ui.SearchPanel
import me.foxtails.palustris.ui.search.SearchScreen
import me.foxtails.palustris.ui.large.LargeBottomDock
import me.foxtails.palustris.ui.large.LargeBottomDockClearance
import me.foxtails.palustris.ui.large.LargeTimelineDockContent
import me.foxtails.palustris.ui.motion.AnimatedStatePane
import me.foxtails.palustris.ui.motion.SpringAnimatedContent
import me.foxtails.palustris.ui.navigation.AppRoute
import me.foxtails.palustris.ui.navigation.ShellNavigator
import me.foxtails.palustris.ui.notifications.NotificationRouteResolver
import me.foxtails.palustris.ui.profile.ProfileScreen
import me.foxtails.palustris.ui.timelineLabelRes

/**
 * Destination scaffold and branches for the application shell.
 *
 * The content renders the destination scaffold with its animated branches:
 * notification detail, local pages, Home, search with Photo Grid,
 * notifications, and profile. It owns no state. Navigation and overlay
 * holders, scroll states, contracts, and callbacks arrive as parameters.
 * Saveable holders and scroll states stay with the shell and pass through
 * unchanged, so restoration keys and scroll positions stay stable.
 */
@Composable
internal fun ShellDestinationContent(
    paneModifier: Modifier,
    navigator: ShellNavigator,
    overlay: ShellOverlayPresenter,
    screenStates: SaveableStateHolder,
    homeListState: LazyListState,
    searchListState: LazyListState,
    photoGridScrollState: LazyStaggeredGridState,
    profileListState: LazyListState,
    largePresentation: Boolean,
    account: Account?,
    displayedProfile: Account?,
    savedTitle: Int,
    notificationAccountIdentity: String,
    availableTimelines: Set<Timeline>,
    availableActions: Set<PostAction>,
    quoteEnabled: Boolean,
    sessionRevision: Long,
    home: HomeContract?,
    photoGrid: PhotoGridContract,
    profile: ProfileContract,
    search: SearchContract,
    bookmarks: BookmarksContract,
    likes: LikesContract,
    notifications: NotificationsContract,
    directMessages: DirectMessagesContract,
    accountSwitcher: AccountSwitcher,
    drafts: List<PostDraft>,
    onLoadDraft: (PostDraft) -> Unit,
    onDeleteDraft: (PostDraft) -> Unit,
    onReact: (OwnedPost) -> Unit,
    onReply: (OwnedPost) -> Unit,
    onReshare: (OwnedPost) -> Unit,
    onBookmark: (OwnedPost) -> Unit,
    onReaction: (OwnedPost, EmojiChoice) -> Unit,
    onQuote: (OwnedPost) -> Unit,
    onOpenPost: (OwnedPost, LargePostOrigin) -> Unit,
    onOpenNotificationTarget: (AppRoute) -> Unit,
    onEditProfile: () -> Unit,
) {
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
                onBack = { overlay.clearPostActionBubble(); if (navigator.page != null) navigator.page = null else navigator.notificationRoute = null },
            )
        }
    ) { padding ->
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
                                onOpenHashtagBubble = overlay::openHashtagBubble,
                                onOpenPost = { post -> onOpenPost(post, LargePostOrigin.Notification) },
                                onOpenTarget = (navigator.notificationRoute as? AppRoute.Profile)?.let { route ->
                                    { onOpenNotificationTarget(route) }
                                },
                                availableActions = availableActions,
                                onReact = onReact,
                                onReply = onReply,
                                onReshare = onReshare,
                                onBookmark = onBookmark,
                                onReaction = onReaction,
                                onQuote = onQuote,
                                quoteEnabled = quoteEnabled,
                                onOpenReactionBubble = { post, bounds -> overlay.openReactionBubble(post, bounds, onReaction) },
                                sessionRevision = sessionRevision,
                                largeLayout = largePresentation,
                            )
                        } else if (navigator.page != null) {
                            AppLocalPageContent(
                                page = navigator.page,
                                savedPostsState = bookmarks.state,
                                likedPostsState = likes.state,
                                drafts = drafts,
                                onLoadDraft = onLoadDraft,
                                onDeleteDraft = onDeleteDraft,
                                onRefreshSavedPosts = bookmarks.actions::refresh,
                                onLoadMoreSavedPosts = bookmarks.actions::loadMore,
                                onUnsaveSavedPost = bookmarks.actions::remove,
                                onRefreshLikedPosts = likes.actions::refresh,
                                onLoadMoreLikedPosts = likes.actions::loadMore,
                                onUnsaveLikedPost = likes.actions::toggle,
                                onUpgradeSavedPermissions = bookmarks.actions::upgradePermissions,
                                onReact = onReact,
                                onReply = onReply,
                                onReshare = onReshare,
                                onBookmark = onBookmark,
                                onSavedPostReaction = bookmarks.actions::react,
                                onLikedPostReaction = likes.actions::react,
                                onOpenSavedReactionBubble = { post, bounds -> overlay.openReactionBubble(post, bounds, bookmarks.actions::react) },
                                onOpenLikedReactionBubble = { post, bounds -> overlay.openReactionBubble(post, bounds, likes.actions::react) },
                                onOpenReactionPicker = overlay::expandReactionPicker,
                                onOpenMedia = overlay::openMedia,
                                onOpenPost = onOpenPost,
                                onOpenProfile = navigator::openProfile,
                                onSearchHashtag = navigator::openHashtagSearch,
                                onOpenHashtagBubble = overlay::openHashtagBubble,
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
                                onReply = onReply,
                                onReshare = onReshare,
                                onBookmark = onBookmark,
                                onReaction = onReaction,
                                onOpenReactionBubble = { ownedPost, bounds -> overlay.openReactionBubble(ownedPost, bounds, onReaction) },
                                onOpenReactionPicker = overlay::expandReactionPicker,
                                onQuote = onQuote,
                                onOpenProfile = navigator::openProfile,
                                onSearchHashtag = navigator::openHashtagSearch,
                                onOpenHashtagBubble = overlay::openHashtagBubble,
                                onOpenMedia = overlay::openMedia,
                                onOpenPost = { post -> onOpenPost(post, LargePostOrigin.Home) },
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
                                        onReply = onReply,
                                        onReshare = onReshare,
                                        onBookmark = onBookmark,
                                        onReaction = onReaction,
                                        onOpenReactionBubble = { ownedPost, bounds ->
                                            overlay.openReactionBubble(ownedPost, bounds, onReaction)
                                        },
                                        onOpenReactionPicker = overlay::expandReactionPicker,
                                        quoteEnabled = quoteEnabled,
                                        onQuote = onQuote,
                                        onSearchHashtag = navigator::openHashtagSearch,
                                        onOpenHashtagBubble = overlay::openHashtagBubble,
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
                                        onOpenMedia = overlay::openMedia,
                                        onOpenPost = { post -> onOpenPost(post, LargePostOrigin.Search) },
                                        onOpenUsername = navigator::openAccountSearch,
                                    )
                                    SearchPanel.PhotoGrid -> PhotoGridScreen(
                                        state = photoGrid.state,
                                        onRefresh = photoGrid.actions::refresh,
                                        onLoadMore = photoGrid.actions::loadMore,
                                        onSelectFeed = photoGrid.actions::selectFeed,
                                        onAddHashtag = photoGrid.actions::addHashtag,
                                        onClearPreferenceError = photoGrid.actions::clearPreferenceError,
                                        onOpenPost = { post -> onOpenPost(post, LargePostOrigin.PhotoGrid) },
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
                                    overlay.clearPostActionBubble()
                                    if (largePresentation) navigator.clearSelectedPost()
                                    navigator.notificationRoute = NotificationRouteResolver.resolve(notification)
                                },
                                onSelectQuery = notifications.actions::selectQuery,
                                onMarkAllRead = notifications.actions::markAllRead,
                                onOpenSettings = {
                                    if (account != null) {
                                        overlay.clearPostActionBubble()
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
                                onOpenProfileImage = { url -> overlay.openProfileImage(url, navigator.viewedProfile?.id ?: account?.id) },
                                onEditProfile = onEditProfile,
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
                                onOpenHashtagBubble = overlay::openHashtagBubble,
                                availableActions = availableActions,
                                onReact = onReact,
                                onReply = onReply,
                                onReshare = onReshare,
                                onBookmark = onBookmark,
                                onReaction = profile.actions::react,
                                onOpenReactionBubble = { ownedPost, bounds ->
                                    overlay.openReactionBubble(ownedPost, bounds, profile.actions::react)
                                },
                                onOpenReactionPicker = overlay::expandReactionPicker,
                                onOpenMedia = overlay::openMedia,
                                onOpenPost = { post -> onOpenPost(post, LargePostOrigin.Profile) },
                                onOpenUsername = navigator::openAccountSearch,
                                quoteEnabled = quoteEnabled,
                                onQuote = onQuote,
                            )
                        }
                    }
                }
            }
        }
    }
}
