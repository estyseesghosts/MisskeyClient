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
import android.widget.Toast
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.launch
import me.foxtails.palustris.R
import me.foxtails.palustris.data.auth.toAccount
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.Audience
import me.foxtails.palustris.domain.CapabilityStatus
import me.foxtails.palustris.domain.ServerCapabilities
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.CreatePostRequest
import me.foxtails.palustris.domain.EditableProfilePatch
import me.foxtails.palustris.domain.EmojiChoice
import me.foxtails.palustris.domain.EntityId
import me.foxtails.palustris.domain.Notification
import me.foxtails.palustris.domain.NotificationQuery
import me.foxtails.palustris.domain.OwnedPost
import me.foxtails.palustris.domain.Post
import me.foxtails.palustris.domain.PostAction
import me.foxtails.palustris.domain.PostDraft
import me.foxtails.palustris.domain.PostDraftQuotePreview
import me.foxtails.palustris.domain.effectiveTargetId
import me.foxtails.palustris.domain.SavedPostsKind
import me.foxtails.palustris.domain.Timeline
import java.util.UUID
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
import me.foxtails.palustris.ui.navigation.edgeSwipeDismiss
import me.foxtails.palustris.ui.media.LocalMediaTransitionRegistry
import me.foxtails.palustris.ui.media.MediaTransitionRegistry
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
import me.foxtails.palustris.ui.motion.motionDirection
import me.foxtails.palustris.ui.motion.springPress
import me.foxtails.palustris.ui.large.LargeBottomDockClearance
import me.foxtails.palustris.ui.large.LargeBottomDock
import me.foxtails.palustris.ui.large.LargeNavTarget
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

private const val COMPOSER_OVERLAY_KEY = "Composer"
private const val EDIT_PROFILE_OVERLAY_KEY = "EditProfile"
private const val NOTIFICATION_SETTINGS_OVERLAY_KEY = "NotificationSettings"

@Composable
fun PalustrisApp(
    account: Account? = null,
    sessionGeneration: Long = 0L,
    sessionRevision: Long = 0L,
     home: HomeContract? = null,
    photoGrid: PhotoGridContract = PhotoGridContract.Empty,
    profile: ProfileContract = ProfileContract.Empty,
    accountSwitcher: AccountSwitcher = AccountSwitcher.Empty,
    composer: ComposerContract = ComposerContract.Empty,
    search: SearchContract = SearchContract.Empty,
    postInteractions: PostInteractions = PostInteractions.Empty,
    thread: ThreadContract = ThreadContract.Empty,
    draftsContract: DraftsContract = DraftsContract.Empty,
    emojiPresentation: EmojiPresentation = EmojiPresentation.Empty,
    bookmarks: BookmarksContract = BookmarksContract.Empty,
    likes: LikesContract = LikesContract.Empty,
    notifications: NotificationsContract = NotificationsContract.Empty,
    directMessages: DirectMessagesContract = DirectMessagesContract.Empty,
    initialNotificationRoute: AppRoute? = null,
    notificationSettings: NotificationSettingsContract = NotificationSettingsContract.Empty,
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
    val replySentMessage = stringResource(R.string.reply_sent)
    val quoteSentMessage = stringResource(R.string.quote_sent)
    val onReact = postInteractions.actions::favorite
    val onReshare = postInteractions.actions::repost
    val onBookmark = postInteractions.actions::bookmark
    val onReaction = postInteractions.actions::react
    val postPreferences = composer.postPreferences
    val availableActions = postInteractions.availableActions
    val quoteEnabled = postInteractions.quoteEnabled
    var destination by rememberSaveable { mutableStateOf(Destination.Home) }
    var destinationTransitionDirection by rememberSaveable { mutableIntStateOf(0) }
    var timeline by rememberSaveable { mutableStateOf(Timeline.Home) }
    var page by rememberSaveable { mutableStateOf<LocalPage?>(null) }
    var sheet by rememberSaveable { mutableStateOf<String?>(null) }
    var overlayKey by rememberSaveable { mutableStateOf<String?>(null) }
    var searchPanelName by rememberSaveable { mutableStateOf(SearchPanel.Search.name) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var searchCategory by rememberSaveable { mutableIntStateOf(0) }
    var searchPrefill by rememberSaveable { mutableStateOf("") }
    var notificationsPanelName by rememberSaveable { mutableStateOf(NotificationsPanel.Notifications.name) }
    val searchPanel = SearchPanel.valueOf(searchPanelName)
    val notificationsPanel = NotificationsPanel.valueOf(notificationsPanelName)
    val screenStates = rememberSaveableStateHolder()
    var drafts by remember { mutableStateOf<List<PostDraft>>(emptyList()) }
    var draftId by rememberSaveable { mutableStateOf<String?>(null) }
    var draft by rememberSaveable { mutableStateOf("") }
    var savedDraft by rememberSaveable { mutableStateOf("") }
    var warning by rememberSaveable { mutableStateOf("") }
    var savedWarning by rememberSaveable { mutableStateOf("") }
    var warningEnabled by rememberSaveable { mutableStateOf(false) }
    var composerAudience by rememberSaveable { mutableStateOf(Audience.Public) }
    var savedAudience by rememberSaveable { mutableStateOf(Audience.Public) }
    var draftError by rememberSaveable { mutableStateOf<String?>(null) }
    var savedQuoteOf by remember { mutableStateOf<String?>(null) }
    var composerReplyTo by remember { mutableStateOf<EntityId?>(null) }
    var savedReplyTo by remember { mutableStateOf<String?>(null) }
    var composerQuoteOf by remember { mutableStateOf<EntityId?>(null) }
    var composerTarget by remember { mutableStateOf<OwnedPost?>(null) }
    var viewedProfile by remember { mutableStateOf<Account?>(null) }
    var profileDialog by rememberSaveable { mutableStateOf(false) }
    var signOutDialog by remember { mutableStateOf(false) }
    var mediaRequest by remember { mutableStateOf<MediaOpenRequest?>(null) }
    var profileImageRequest by remember { mutableStateOf<ImageViewerContent?>(null) }
    var singlePost by remember { mutableStateOf<OwnedPost?>(null) }
    var singlePostOrigin by remember { mutableStateOf(LargePostOrigin.Other) }
    val homeListState = rememberLazyListState()
    val searchListState = rememberLazyListState()
    val photoGridScrollState = rememberLazyStaggeredGridState()
    val profileListState = rememberLazyListState()
    var emojiPickerTarget by remember { mutableStateOf<EmojiPickerTarget?>(null) }
    var postActionBubbleTarget by remember { mutableStateOf<PostActionBubbleTarget?>(null) }
    var pendingExpandedReactionTarget by remember { mutableStateOf<OwnedPost?>(null) }
    var postReactionHandler by remember { mutableStateOf<((OwnedPost, EmojiChoice) -> Unit)?>(null) }
    var pendingEmojiInsertion by remember { mutableStateOf<Pair<EmojiChoice, ComposerField>?>(null) }
    var navigationVisible by rememberSaveable { mutableStateOf(true) }
    var notificationRoute by remember { mutableStateOf<AppRoute?>(initialNotificationRoute) }
    var closing by remember { mutableStateOf(false) }
    val motionScheme = LocalPalustrisMotionScheme.current
    val overlay = when (overlayKey) {
        COMPOSER_OVERLAY_KEY -> Overlay.Composer
        EDIT_PROFILE_OVERLAY_KEY -> Overlay.EditProfile
        NOTIFICATION_SETTINGS_OVERLAY_KEY -> Overlay.NotificationSettings
        else -> null
    }
    val modalOverlayOpen = overlay != null || sheet != null || profileDialog || signOutDialog || mediaRequest != null || profileImageRequest != null || singlePost != null || emojiPickerTarget != null
    val availableTimelines = if (account == null) Timeline.entries.toSet() else home?.state?.availableTimelines ?: setOf(Timeline.Home)
    val profileTargetId = viewedProfile?.id ?: account?.id
    val refreshedProfile = profile.state.account?.takeIf { it.id == profileTargetId }
    val displayedProfile = refreshedProfile ?: viewedProfile ?: account
    val savedKind = bookmarks.state?.kind
    val savedTitle = savedCollectionTitle(savedKind)
    val notificationAccountIdentity = account?.id?.let { "${it.connection.origin}\u0000${it.localId}" } ?: "preview"
    val hasDraftChanges = draft != savedDraft ||
        (if (warningEnabled) warning else "") != savedWarning ||
        composerQuoteOf?.value != savedQuoteOf ||
        composerReplyTo?.value != savedReplyTo ||
        composerAudience != savedAudience

    fun clearPostActionBubble() {
        postActionBubbleTarget = null
        pendingExpandedReactionTarget = null
        postReactionHandler = null
    }

    fun clearSelectedPost() {
        singlePost = null
        singlePostOrigin = LargePostOrigin.Other
    }

    fun reloadDrafts() {
        draftsContract.actions.load(account?.id) { result -> drafts = result }
    }

    LaunchedEffect(account?.id, draftsContract) { reloadDrafts() }
    LaunchedEffect(availableTimelines) { if (timeline !in availableTimelines) timeline = Timeline.Home }
    LaunchedEffect(home?.state?.selectedTimeline, account?.id) { home?.state?.selectedTimeline?.let { timeline = it } }
    LaunchedEffect(destination, page, overlayKey) { navigationVisible = true }
    LaunchedEffect(account?.id) {
        mediaTransitionRegistry.endActive()
        viewedProfile = null
        page = null
        composerTarget = null
        composerQuoteOf = null
        savedQuoteOf = null
        composerReplyTo = null
        savedReplyTo = null
        mediaRequest = null
        clearSelectedPost()
        searchQuery = ""
        searchCategory = 0
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
    LaunchedEffect(destination, searchPanel, account?.id, sessionGeneration) {
        if (destination == Destination.Search && searchPanel == SearchPanel.PhotoGrid) {
            photoGrid.actions.ensureLoaded()
        }
    }
    LaunchedEffect(photoGrid.state.selectedFeed, account?.id, sessionGeneration) {
        photoGridScrollState.scrollToItem(0)
        if (singlePostOrigin == LargePostOrigin.PhotoGrid) clearSelectedPost()
    }
    LaunchedEffect(singlePost?.post?.id, singlePostOrigin, singlePostOrigin.supportsComments()) {
        thread.actions.activate(singlePost, singlePostOrigin.supportsComments())
    }
    LaunchedEffect(destination, page, overlayKey, sheet, profileDialog, signOutDialog, mediaRequest, singlePost, notificationRoute) {
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
    LaunchedEffect(initialNotificationRoute) {
        notificationRoute = initialNotificationRoute
        if (initialNotificationRoute != null) {
            destination = Destination.Notifications
            if (initialNotificationRoute is AppRoute.NotificationSettings) {
                notificationRoute = null
                overlayKey = NOTIFICATION_SETTINGS_OVERLAY_KEY
            }
        }
    }

    fun draftTarget(item: PostDraft): OwnedPost? {
        val owner = account ?: return null
        val targetId = item.quoteOf ?: return null
        val preview = item.quotePreview ?: return null
        if (item.accountId != owner.id || targetId.connection != owner.id.connection.origin) return null
        val author = Account(
            id = AccountId(Connection(targetId.connection, owner.id.connection.protocol), "draft-quote-author"),
            displayName = preview.authorDisplayName.ifBlank { preview.authorHandle.ifBlank { "Quoted post" } },
            handle = preview.authorHandle.ifBlank { "Quoted post" },
        )
        return OwnedPost(
            owner.id,
            Post(
                id = targetId,
                author = author,
                text = preview.text,
                publishedAtEpochMillis = 0L,
                audience = Audience.Public,
                url = preview.url,
            ),
        )
    }

    fun loadDraft(item: PostDraft) {
        clearPostActionBubble()
        draftId = item.id
        draft = item.text
        savedDraft = item.text
        warning = item.contentWarning.orEmpty()
        savedWarning = item.contentWarning.orEmpty()
        warningEnabled = !item.contentWarning.isNullOrBlank()
        composerAudience = item.audience
        savedAudience = item.audience
        composerQuoteOf = item.quoteOf?.takeIf { quote -> quote.connection == account?.id?.connection?.origin }
        composerReplyTo = item.replyTo?.takeIf { reply -> reply.connection == account?.id?.connection?.origin }
        composerTarget = draftTarget(item)
        savedQuoteOf = composerQuoteOf?.value
        savedReplyTo = composerReplyTo?.value
        draftError = null
        overlayKey = COMPOSER_OVERLAY_KEY
    }

    fun openComposer() {
        if (overlay == Overlay.Composer) return
        clearPostActionBubble()
        val first = drafts.firstOrNull()
        if (draft.isBlank() && savedDraft.isBlank() && first != null) {
            loadDraft(first)
        } else {
            composerAudience = runCatching {
                me.foxtails.palustris.domain.PostingVisibilityPolicy.forNewPost(
                    postPreferences,
                    ServerCapabilities(audiences = composer.availableAudiences),
                )
            }.getOrDefault(postPreferences.defaultAudience)
            savedAudience = composerAudience
            overlayKey = COMPOSER_OVERLAY_KEY
        }
    }

    fun openQuote(target: OwnedPost) {
        val owner = account ?: return
        if (target.fetchedBy != owner.id || !quoteEnabled) return
        if (overlay != null || hasDraftChanges) return
        clearPostActionBubble()
        draftId = null
        draft = ""
        savedDraft = ""
        warning = ""
        savedWarning = ""
        warningEnabled = false
        composerAudience = runCatching {
            me.foxtails.palustris.domain.PostingVisibilityPolicy.forReply(
                postPreferences,
                ServerCapabilities(audiences = composer.availableAudiences),
                context = target.post.audience,
            )
        }.getOrDefault(target.post.audience)
        savedAudience = composerAudience
        composerTarget = target
        composerQuoteOf = target.post.id
        composerReplyTo = null
        savedQuoteOf = null
        savedReplyTo = null
        draftError = null
        overlayKey = COMPOSER_OVERLAY_KEY
    }

    fun openReply(target: OwnedPost) {
        val owner = account ?: return
        if (target.fetchedBy != owner.id || PostAction.Reply !in availableActions) return
        if (overlay != null || hasDraftChanges) return
        clearPostActionBubble()
        draftId = null
        draft = ""
        savedDraft = ""
        warning = ""
        savedWarning = ""
        warningEnabled = false
        composerAudience = runCatching {
            me.foxtails.palustris.domain.PostingVisibilityPolicy.forReply(
                postPreferences,
                ServerCapabilities(audiences = composer.availableAudiences),
                context = target.post.audience,
            )
        }.getOrDefault(target.post.audience)
        savedAudience = composerAudience
        composerTarget = target
        composerQuoteOf = null
        composerReplyTo = target.post.actionTargetId ?: target.post.id
        savedQuoteOf = null
        savedReplyTo = null
        draftError = null
        overlayKey = COMPOSER_OVERLAY_KEY
    }

    val handleReply: (OwnedPost) -> Unit = { target -> openReply(target) }

    fun draftValue() = PostDraft(
        id = draftId ?: UUID.randomUUID().toString(),
        accountId = account?.id,
        text = draft,
        audience = composerAudience,
        contentWarning = warning.takeIf { warningEnabled && it.isNotBlank() },
        quoteOf = composerQuoteOf?.takeIf { quote -> quote.connection == account?.id?.connection?.origin },
        replyTo = composerReplyTo?.takeIf { reply -> reply.connection == account?.id?.connection?.origin },
        quotePreview = composerTarget?.let { target ->
            PostDraftQuotePreview(
                authorDisplayName = target.post.author.displayName,
                authorHandle = target.post.author.handle,
                text = target.post.text,
                url = target.post.url,
            )
        },
    )

    fun saveCurrentDraft(onSaved: () -> Unit = {}) {
        if (draft.isBlank() && warning.isBlank() && composerQuoteOf == null && composerReplyTo == null) { onSaved(); return }
        closing = true
        draftsContract.actions.save(
            draftValue(),
            onResult = { item ->
                draftId = item.id
                savedDraft = item.text
                savedWarning = item.contentWarning.orEmpty()
                savedQuoteOf = item.quoteOf?.value
                savedReplyTo = item.replyTo?.value
                savedAudience = item.audience
                draftError = null
                reloadDrafts()
                closing = false
                onSaved()
            },
            onError = {
                draftError = "Draft could not be saved. Keep editing and try again."
                closing = false
            },
        )
    }
    fun closeComposer() { if (composer.publishing || closing) return; if (hasDraftChanges) saveCurrentDraft { overlayKey = null } else overlayKey = null }
    fun discardProfileEditor() {
        overlayKey = null
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
            overlayKey = EDIT_PROFILE_OVERLAY_KEY
        }
    }
    fun closeNotificationSettings() { overlayKey = null }
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
    fun selectDestination(item: Destination) {
        clearPostActionBubble()
        navigationVisible = true
        if (item == Destination.Profile) viewedProfile = null
        destinationTransitionDirection = motionDirection(destination.ordinal, item.ordinal, motionScheme.reducedMotion)
        destination = item
        page = null
        notificationRoute = null
    }
    fun openProfile(profile: Account) {
        clearPostActionBubble()
        clearSelectedPost()
        viewedProfile = profile
        destinationTransitionDirection = motionDirection(destination.ordinal, Destination.Profile.ordinal, motionScheme.reducedMotion)
        destination = Destination.Profile
        page = null
        sheet = null
        notificationRoute = null
    }

    fun selectLargeTarget(target: LargeNavTarget) {
        clearSelectedPost()
        when (target) {
            LargeNavTarget.Home -> selectDestination(Destination.Home)
            LargeNavTarget.Search -> {
                searchPanelName = SearchPanel.Search.name
                selectDestination(Destination.Search)
            }
            LargeNavTarget.PhotoGrid -> {
                searchPanelName = SearchPanel.PhotoGrid.name
                selectDestination(Destination.Search)
            }
            LargeNavTarget.Notifications -> {
                notificationsPanelName = NotificationsPanel.Notifications.name
                selectDestination(Destination.Notifications)
            }
            LargeNavTarget.DirectMessages -> {
                notificationsPanelName = NotificationsPanel.DirectMessages.name
                selectDestination(Destination.Notifications)
            }
            LargeNavTarget.Profile -> {
                viewedProfile = null
                selectDestination(Destination.Profile)
            }
        }
    }

    fun openDirectMessage(profile: Account) {
        clearPostActionBubble()
        clearSelectedPost()
        directMessages.actions.startConversation(profile)
        notificationsPanelName = NotificationsPanel.DirectMessages.name
        destinationTransitionDirection = motionDirection(
            destination.ordinal,
            Destination.Notifications.ordinal,
            motionScheme.reducedMotion,
        )
        destination = Destination.Notifications
        page = null
        notificationRoute = null
    }

    fun openNotificationTarget(route: AppRoute) {
        if (route !is AppRoute.Profile) {
            notificationRoute = null
            return
        }
        val target = notifications.state.items
            .firstOrNull { it.target == me.foxtails.palustris.domain.NotificationTarget.Profile(route.profileId) }
            ?.actors
            ?.firstOrNull { it.id == route.profileId }
        if (target != null) openProfile(target) else notificationRoute = null
    }

    fun openHashtagSearch(hashtag: String) {
        clearPostActionBubble()
        clearSelectedPost()
        searchQuery = hashtag
        searchPrefill = ""
        searchPanelName = SearchPanel.Search.name
        destinationTransitionDirection = motionDirection(destination.ordinal, Destination.Search.ordinal, motionScheme.reducedMotion)
        destination = Destination.Search
        page = null
        search.actions.search(hashtag)
    }

    fun openAccountSearch(username: String) {
        clearPostActionBubble()
        clearSelectedPost()
        searchQuery = username
        searchPrefill = ""
        searchPanelName = SearchPanel.Search.name
        destinationTransitionDirection = motionDirection(destination.ordinal, Destination.Search.ordinal, motionScheme.reducedMotion)
        destination = Destination.Search
        page = null
        search.actions.search(username)
    }

    fun openMedia(request: MediaOpenRequest) {
        if (account?.id != null && request.ownedPost.fetchedBy != account.id) return
        if (request.attachmentIndex !in request.ownedPost.post.attachments.indices) return
        clearPostActionBubble()
        mediaRequest = request
    }

    fun openProfileImage(url: String) {
        if (url.isBlank()) return
        val owner = viewedProfile?.id ?: account?.id
        profileImageRequest = ImageViewerContent(
            url = url,
            identity = "profile:${owner?.connection?.origin}:${owner?.localId}:$url",
        )
        clearPostActionBubble()
    }

    fun openSinglePost(post: OwnedPost, origin: LargePostOrigin = LargePostOrigin.Other) {
        clearPostActionBubble()
        mediaRequest = null
        singlePost = post
        singlePostOrigin = origin
    }

    fun latestSelectedPost(): OwnedPost? {
        val selected = singlePost ?: return null
        val candidates = home?.state?.ownedPosts.orEmpty() +
            photoGrid.state.posts +
            bookmarks.state?.posts.orEmpty() +
            likes.state?.posts.orEmpty() +
            profile.state.pinnedPosts +
            profile.state.pages.values.flatMap { it.posts }
        return candidates.firstOrNull {
            it.fetchedBy == selected.fetchedBy &&
                it.sessionRevision == selected.sessionRevision &&
                it.post.id == selected.post.id
        } ?: selected
    }

    val selectedThreadState = thread.state?.takeIf { state ->
        val selected = singlePost ?: return@takeIf false
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
        fun dismissTopSurface() {
            when {
                profileImageRequest != null -> profileImageRequest = null
                largePresentation && overlay == Overlay.NotificationSettings -> closeNotificationSettings()
                largePresentation && overlay == Overlay.Composer -> closeComposer()
                largePresentation && overlay == Overlay.EditProfile -> closeProfile()
                singlePost != null -> clearSelectedPost()
                overlay == Overlay.NotificationSettings -> closeNotificationSettings()
                overlay == Overlay.Composer -> closeComposer()
                overlay == Overlay.EditProfile -> closeProfile()
                notificationRoute != null -> notificationRoute = null
                page != null -> page = null
                else -> selectDestination(Destination.Home)
            }
        }
        BackHandler(enabled = mediaRequest == null && (profileImageRequest != null || singlePost != null || notificationRoute != null || overlay != null || page != null || destination != Destination.Home), onBack = ::dismissTopSurface)
        Row(
            Modifier.fillMaxSize().edgeSwipeDismiss(
                enabled = navigationMode == NavigationMode.NonGesture && mediaRequest == null &&
                    (profileImageRequest != null || singlePost != null || notificationRoute != null || overlay != null || page != null || destination != Destination.Home),
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
                        page == null && notificationRoute == null && destination == Destination.Profile ->
                            WindowInsets.systemBars.only(WindowInsetsSides.Horizontal)
                        !largePresentation && page == null && notificationRoute == null ->
                            WindowInsets.systemBars.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
                        else -> ScaffoldDefaults.contentWindowInsets
                    },
                    topBar = {
                    AppDestinationTopBar(
                        page = page,
                        notificationRoute = notificationRoute,
                        savedTitle = savedTitle,
                        onBack = { clearPostActionBubble(); if (page != null) page = null else notificationRoute = null },
                    )
                }) { padding ->
                    Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
                        SpringAnimatedContent(
                            stateKey = destination,
                            direction = destinationTransitionDirection,
                            modifier = Modifier.fillMaxSize(),
                        ) { animatedDestination ->
                        screenStates.SaveableStateProvider(animatedDestination.name) {
                        AnimatedStatePane(
                            stateKey = notificationRoute ?: page?.name ?: "${animatedDestination.name}:content",
                            modifier = Modifier.fillMaxSize(),
                        ) {
                        if (notificationRoute != null) {
                            AppNotificationDetailContent(
                                route = notificationRoute!!,
                                items = notifications.state.items,
                                onSearchHashtag = ::openHashtagSearch,
                                onOpenHashtagBubble = ::openHashtagBubble,
                                 onOpenPost = { post -> openSinglePost(post, LargePostOrigin.Notification) },
                                 onOpenTarget = (notificationRoute as? AppRoute.Profile)?.let { route ->
                                      { openNotificationTarget(route) }
                                  },
                                 availableActions = availableActions,
                                 onReact = onReact,
                                 onReply = handleReply,
                                 onReshare = onReshare,
                                 onBookmark = onBookmark,
                                 onReaction = onReaction,
                                 onQuote = ::openQuote,
                                 quoteEnabled = quoteEnabled,
                                  onOpenReactionBubble = { post, bounds -> openReactionBubble(post, bounds, onReaction) },
                                  sessionRevision = sessionRevision,
                                 largeLayout = largePresentation,
                             )
                         } else if (page != null) {
                             AppLocalPageContent(
                                 page = page,
                                 savedPostsState = bookmarks.state,
                                 likedPostsState = likes.state,
                                 drafts = drafts,
                                 onLoadDraft = ::loadDraft,
                                 onDeleteDraft = { item -> draftsContract.actions.delete(account?.id, item.id) { reloadDrafts() } },
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
                                 onOpenProfile = ::openProfile,
                                 onSearchHashtag = ::openHashtagSearch,
                                 onOpenHashtagBubble = ::openHashtagBubble,
                                 onOpenUsername = ::openAccountSearch,
                                 availableActions = availableActions,
                                 largeLayout = largePresentation,
                             )
                         } else when (animatedDestination) {
                                      Destination.Home -> if (home != null) HomeFeed(
                                          state = home.state,
                                          compactLayout = !largePresentation,
                                          onRefresh = { home.actions.refresh(timeline) },
                                          onLoadMore = { home.actions.loadMore(timeline) },
                                          onSignIn = accountSwitcher.actions::signOut,
                                          ownedPosts = home.state.ownedPosts,
                                          availableActions = availableActions,
                                          quoteEnabled = quoteEnabled,
                                          onScrollDirectionChanged = { if (destination == Destination.Home && animatedDestination == Destination.Home) navigationVisible = it },
                                          onReact = onReact,
                                          onReply = handleReply,
                                          onReshare = onReshare,
                                          onBookmark = onBookmark,
                                          onReaction = onReaction,
                                           onOpenReactionBubble = { ownedPost, bounds -> openReactionBubble(ownedPost, bounds, onReaction) },
                                           onOpenReactionPicker = ::expandReactionPicker,
                                          onQuote = ::openQuote,
                                          onOpenProfile = ::openProfile,
                                          onSearchHashtag = ::openHashtagSearch,
                                          onOpenHashtagBubble = ::openHashtagBubble,
                                          onOpenMedia = ::openMedia,
                                          onOpenPost = { post -> openSinglePost(post, LargePostOrigin.Home) },
                                          onOpenUsername = ::openAccountSearch,
                                          listState = homeListState,
                                          topContentPadding = if (largePresentation) 16.dp else null,
                                          bottomContentClearance = if (largePresentation) LargeBottomDockClearance else null,
                                          refreshIndicatorTopPadding = if (largePresentation) 16.dp else null,
                                          bottomDock = if (largePresentation) ({
                                         LargeTimelineDockContent(availableTimelines, timeline) { item ->
                                            val changed = item != timeline
                                            if (changed) clearSelectedPost()
                                            timeline = item
                                            if (changed) home.actions.refresh(item)
                                       }
                                     }) else null,
                                      ) else Box(Modifier.fillMaxSize()) {
                                        EmptyState(AppIcons.Home, stringResource(R.string.feed_timeline_empty_title), stringResource(R.string.feed_timeline_empty_subtitle, stringResource(timelineLabelRes(timeline))))
                                       if (largePresentation) {
                                           LargeBottomDock(modifier = Modifier.align(Alignment.BottomStart), content = {
                                                LargeTimelineDockContent(availableTimelines, timeline) { item ->
                                                    val changed = item != timeline
                                                    if (changed) clearSelectedPost()
                                                    timeline = item
                                                    if (changed && home != null) home.actions.refresh(item)
                                               }
                                           })
                                       }
                                   }
                                  Destination.Search -> AnimatedStatePane(
                                      stateKey = searchPanel,
                                      modifier = Modifier.fillMaxSize(),
                                   ) { panel ->
                                       when (panel) {
                                            SearchPanel.Search -> SearchScreen(
                                               accountSearch = search.state,
                                               onSearchAccounts = search.actions::search,
                                               onAccountClick = ::openProfile,
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
                                               onQuote = ::openQuote,
                                               onSearchHashtag = ::openHashtagSearch,
                                               onOpenHashtagBubble = ::openHashtagBubble,
                                               onLoadMoreSearch = search.actions::loadMore,
                                               initialQuery = searchPrefill,
                                               sharedQuery = searchQuery,
                                               sharedTab = searchCategory,
                                               onSharedQueryChange = { searchQuery = it },
                                               onSharedTabChange = { searchCategory = it },
                                               listState = searchListState.takeIf { largePresentation },
                                               largeLayout = largePresentation,
                                               compactLayout = !largePresentation,
                                               compactNavigationVisible = !largePresentation,
                                                mediaOwner = account?.id,
                                                sessionRevision = sessionRevision,
                                               onOpenMedia = ::openMedia,
                                                onOpenPost = { post -> openSinglePost(post, LargePostOrigin.Search) },
                                               onOpenUsername = ::openAccountSearch,
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
                                      panel = notificationsPanel,
                                      account = account,
                                      compactLayout = !largePresentation,
                                      compactNavigationVisible = navigationVisible,
                                      notificationAccountIdentity = notificationAccountIdentity,
                                      notificationState = notifications.state,
                                      onRefreshNotifications = notifications.actions::refresh,
                                      onLoadMoreNotifications = notifications.actions::loadMore,
                                      onMarkNotificationSeen = notifications.actions::markSeen,
                                      onDismissNotification = notifications.actions::dismiss,
                                      onFollowRequest = notifications.actions::respondToFollowRequest,
                                      onOpenNotification = { notification ->
                                          clearPostActionBubble()
                                          if (largePresentation) clearSelectedPost()
                                          notificationRoute = NotificationRouteResolver.resolve(notification)
                                      },
                                      onSelectQuery = notifications.actions::selectQuery,
                                      onMarkAllRead = notifications.actions::markAllRead,
                                      onOpenSettings = {
                                          if (account != null) {
                                              clearPostActionBubble()
                                              overlayKey = NOTIFICATION_SETTINGS_OVERLAY_KEY
                                          }
                                      },
                                      directMessageState = directMessages.state,
                                      onRefreshDirectMessages = directMessages.actions::refresh,
                                      onLoadMoreDirectMessages = directMessages.actions::loadMore,
                                      onOpenDirectConversation = directMessages.actions::openConversation,
                                      onBackDirectConversation = directMessages.actions::closeConversation,
                                      onSendDirectMessage = directMessages.actions::send,
                                  )
                 Destination.Profile -> ProfileScreen(
                     account = displayedProfile,
                     profileState = profile.state,
                     compactLayout = !largePresentation,
                     largeLayout = largePresentation,
                     largeShowSummary = singlePost == null,
                     listState = profileListState,
                     compactNavigationVisible = navigationVisible,
                     authenticatedAccountId = account?.id,
                     onProfileShown = profile.actions::open,
                     onCategorySelected = { category ->
                         if (largePresentation) clearSelectedPost()
                         profile.actions.selectCategory(category)
                     },
                     onRefresh = profile.actions::refresh,
                     onLoadMore = profile.actions::loadMore,
                     onFollow = profile.actions::follow,
                     onUnfollow = profile.actions::unfollow,
                     onMessage = ::openDirectMessage,
                     onOpenProfileImage = ::openProfileImage,
                     onEditProfile = ::openProfileEditor,
                     onOpenDrafts = {
                         if (largePresentation) clearSelectedPost()
                         if (account != null && displayedProfile?.id == account.id) page = LocalPage.Drafts
                     },
                     onOpenBookmarks = {
                         if (largePresentation) clearSelectedPost()
                         if (account != null && displayedProfile?.id == account.id) page = LocalPage.SavedPosts
                     },
                     onOpenLikes = {
                         if (largePresentation) clearSelectedPost()
                         if (account != null && displayedProfile?.id == account.id) page = LocalPage.Likes
                     },
                     onOpenProfile = ::openProfile,
                     onSearchHashtag = ::openHashtagSearch,
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
                      onOpenUsername = ::openAccountSearch,
                      quoteEnabled = quoteEnabled,
                      onQuote = ::openQuote,
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
                        selectedTarget = largeTargetFor(destination, searchPanel, notificationsPanel),
                        account = account,
                        hasDetail = singlePost != null,
                        twoPane = presentationMode == LargeLayoutMode.Expanded &&
                            (destination == Destination.Home || (destination == Destination.Profile && singlePost != null)),
                        onTargetSelected = ::selectLargeTarget,
                        onOpenAccounts = { clearPostActionBubble(); sheet = "Accounts" },
                        onCompose = ::openComposer,
                        primaryContent = { paneModifier -> destinationScaffold(paneModifier) },
                         detailContent = { paneModifier ->
                             val threadEnabled = selectedThreadState != null && singlePostOrigin.supportsComments()
                             val detail = detailActionsFor(
                                 origin = singlePostOrigin,
                                 threadActive = threadEnabled,
                                 thread = thread,
                                 profile = profile,
                                 bookmarks = bookmarks,
                                 likes = likes,
                                 fallback = DetailActions(onReact, handleReply, onReshare, onBookmark, onReaction),
                             )
                             AppLargeDetailPane(
                                 selected = selectedThreadState?.focal ?: latestSelectedPost(),
                                 origin = singlePostOrigin,
                                 availableActions = availableActions,
                                 threadState = selectedThreadState,
                                 onClose = { singlePost = null },
                                 onReact = detail.favorite,
                                 onReply = detail.reply,
                                 onReshare = detail.reshare,
                                 onBookmark = detail.bookmark,
                                 onReaction = detail.react,
                                 onOpenProfile = ::openProfile,
                                 onSearchHashtag = ::openHashtagSearch,
                                 onOpenHashtagBubble = ::openHashtagBubble,
                                 onOpenReactionBubble = { post, bounds, handler -> openReactionBubble(post, bounds, handler) },
                                 onOpenMedia = ::openMedia,
                                 onOpenUsername = ::openAccountSearch,
                                 onThreadRefresh = thread.actions::refresh,
                                 onThreadContinue = thread.actions::continueAcquisition,
                                 quoteEnabled = quoteEnabled,
                                 onQuote = ::openQuote,
                                 modifier = paneModifier,
                             )
                         },
                    )
                } else {
                    destinationScaffold(Modifier.fillMaxSize())
                }
                 if (!largePresentation && page == null && !modalOverlayOpen) {
                    androidx.compose.animation.AnimatedVisibility(
                        visible = navigationVisible,
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
                                 if (destination == Destination.Home) {
                                      HomeTimelineTabs(
                                          timelines = availableTimelines,
                                          selected = timeline,
                                          modifier = Modifier.height(CompactTimelineTabsHeight),
                                          onSelect = { item ->
                                              clearPostActionBubble()
                                              val changed = item != timeline
                                              timeline = item
                                              if (changed) home?.actions?.refresh(item)
                                          },
                                      )
                                      Spacer(Modifier.height(CompactHomeTimelineSpacing))
                                 }
                                 CompactContextualNavigationBar(
                                    destination = destination,
                                    searchPanel = searchPanel,
                                     action = contextualActionFor(
                                        destination = destination,
                                        searchPanel = searchPanel,
                                        notificationsPanel = notificationsPanel,
                                        profileTarget = displayedProfile,
                                        authenticatedAccountId = account?.id,
                                        profileState = profile.state,
                                        onCompose = ::openComposer,
                                        onSearchToggle = {
                                            searchPanelName = if (searchPanel == SearchPanel.Search) {
                                                SearchPanel.PhotoGrid.name
                                            } else {
                                                SearchPanel.Search.name
                                            }
                                        },
                                        onNotificationsToggle = {
                                            notificationsPanelName = if (notificationsPanel == NotificationsPanel.Notifications) {
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
                                     onOpenAccounts = { clearPostActionBubble(); sheet = "Accounts" },
                                    onDestinationSelected = ::selectDestination,
                                )
                            }
                        }
                    }
                }
            }
        }
         val hashtagBottomClearance = if (largePresentation || page != null || notificationRoute != null) {
            0.dp
        } else {
            when (destination) {
                 Destination.Home -> compactHomeScrollEndClearance()
                 Destination.Search -> compactScrollEndClearance(
                     controlStackHeight = CompactSearchDockHeight,
                    navigationVisible = navigationVisible,
                    ime = WindowInsets.ime,
                )
                 Destination.Notifications, Destination.Profile -> compactScrollEndClearance(
                     controlStackHeight = CompactFilterDockHeight,
                    navigationVisible = navigationVisible,
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
                openHashtagSearch(hashtag)
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
             singlePost?.let { post ->
                 val threadEnabled = selectedThreadState != null && singlePostOrigin.supportsComments()
                 val detail = detailActionsFor(
                     origin = singlePostOrigin,
                     threadActive = threadEnabled,
                     thread = thread,
                     profile = profile,
                     bookmarks = bookmarks,
                     likes = likes,
                     fallback = DetailActions(onReact, handleReply, onReshare, onBookmark, onReaction),
                 )
                  SinglePostScreen(
                      ownedPost = post,
                      presentation = singlePostOrigin.singlePostPresentation(),
                      onClose = { singlePost = null },
                      availableActions = availableActions +
                           if (singlePostOrigin == LargePostOrigin.Liked) setOf(PostAction.Favorite) else emptySet(),
                       onReact = detail.favorite,
                      onReply = detail.reply,
                      onReshare = detail.reshare,
                      onBookmark = detail.bookmark,
                       onReaction = detail.react,
                     onOpenProfile = ::openProfile,
                     onSearchHashtag = ::openHashtagSearch,
                     onOpenHashtagBubble = ::openHashtagBubble,
                     onOpenMedia = ::openMedia,
                      onOpenUsername = ::openAccountSearch,
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

       if (sheet != null) AppSelectionSheet(
           account = account,
           accounts = accountSwitcher.accounts,
           onDismiss = { sheet = null },
           onSwitchAccount = accountSwitcher.actions::switchTo,
           onAddAccount = accountSwitcher.actions::addAccount,
           onOpenSettings = accountSwitcher.actions::openSettings,
           onSignOut = { signOutDialog = true },
       )

    if (overlay == Overlay.Composer) ComposerSheet(
        onDismiss = ::closeComposer,
        onSaveDraft = { saveCurrentDraft { overlayKey = null } },
        saveEnabled = draft.isNotBlank() || composerReplyTo != null,
        closing = closing,
    ) {
        ComposeScreen(
            text = draft,
            onTextChange = { draft = it },
            warning = warning,
            onWarningChange = { warning = it },
            warningEnabled = warningEnabled,
            onWarningEnabled = { warningEnabled = it },
            account = account,
            audience = composerAudience,
            availableAudiences = composer.availableAudiences,
            onAudienceChange = { composerAudience = it },
            canPublish = composer.canPublish && (draftId == null || drafts.firstOrNull { it.id == draftId }?.accountId == account?.id),
            publishing = composer.publishing,
            error = composer.error ?: draftError,
            quoteTarget = composerTarget,
            isReply = composerReplyTo != null,
            onRemoveQuote = { composerTarget = null; composerQuoteOf = null; composerReplyTo = null },
            onRequestEmoji = { field -> emojiPickerTarget = EmojiPickerTarget.Composer(field) },
            pendingEmojiInsertion = pendingEmojiInsertion,
            onEmojiInsertionApplied = { pendingEmojiInsertion = null },
            onCleanTrackingParameters = { draft = me.foxtails.palustris.domain.TrackingParameterCleaner.cleanText(draft) },
            onPublish = {
                val submittedText = draft
                val submittedWarning = warning.takeIf { warningEnabled && it.isNotBlank() }
                val submittedQuote = composerQuoteOf?.takeIf { quote -> quote.connection == account?.id?.connection?.origin }
                val submittedReply = composerReplyTo?.takeIf { reply -> reply.connection == account?.id?.connection?.origin }
                val knownAudiences = composer.availableAudiences
                val submittedAudience = if (knownAudiences.isEmpty()) composerAudience else runCatching {
                    me.foxtails.palustris.domain.PostingVisibilityPolicy.validateExplicit(composerAudience, ServerCapabilities(audiences = knownAudiences))
                }.getOrNull()
                if (submittedAudience == null) {
                    draftError = "This audience is not available on this server."
                } else {
                    val publishingAccountId = account?.id
                    draftsContract.actions.save(
                        draftValue(),
                        onResult = { saved ->
                            draftId = saved.id
                            savedDraft = saved.text
                            savedWarning = saved.contentWarning.orEmpty()
                             composer.actions.publish(CreatePostRequest(submittedText, audience = submittedAudience, contentWarning = submittedWarning, replyTo = submittedReply, quoteOf = submittedQuote)) {
                                 draftsContract.actions.delete(publishingAccountId, saved.id) { reloadDrafts() }
                                 val message = when {
                                     submittedReply != null -> replySentMessage
                                     submittedQuote != null -> quoteSentMessage
                                     else -> null
                                 }
                                 message?.let { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
                                 draft = ""
                                savedDraft = ""
                                warning = ""
                                savedWarning = ""
                                warningEnabled = false
                                draftId = null
                                savedQuoteOf = null
                                savedReplyTo = null
                                composerAudience = Audience.Public
                                savedAudience = Audience.Public
                                composerReplyTo = null
                                composerQuoteOf = null
                                composerTarget = null
                                overlayKey = null
                            }
                        },
                        onError = { draftError = "Draft could not be saved. Keep the composer open and try again." },
                    )
                }
            },
        )
    }

    if (overlay == Overlay.EditProfile && account != null) me.foxtails.palustris.ui.profile.EditProfileSheet(
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
                overlayKey = null
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

    if (overlay == Overlay.NotificationSettings && account != null) NotificationSettingsSheet(
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
    )

    BackHandler(enabled = overlay == Overlay.NotificationSettings) {
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

@Preview(showBackground = true, device = "spec:width=411dp,height=891dp,dpi=420")
@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun AppPreview() { PalustrisApp() }
