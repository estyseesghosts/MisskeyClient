@file:OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
)

package me.foxtails.palustris.ui

import android.content.Context
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
import androidx.compose.ui.platform.testTag
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
import me.foxtails.palustris.data.auth.AccountRef
import me.foxtails.palustris.data.auth.DraftStore
import me.foxtails.palustris.data.auth.PreferencesDraftStore
import me.foxtails.palustris.data.auth.toAccount
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.Audience
import me.foxtails.palustris.domain.CapabilityStatus
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.CreatePostRequest
import me.foxtails.palustris.domain.EditableProfile
import me.foxtails.palustris.domain.EditableProfileField
import me.foxtails.palustris.domain.EditableProfilePatch
import me.foxtails.palustris.domain.EmojiCapabilities
import me.foxtails.palustris.domain.EmojiChoice
import me.foxtails.palustris.domain.EntityId
import me.foxtails.palustris.domain.Notification
import me.foxtails.palustris.domain.NotificationQuery
import me.foxtails.palustris.domain.OwnedPost
import me.foxtails.palustris.domain.Post
import me.foxtails.palustris.domain.PostAction
import me.foxtails.palustris.domain.PostDraft
import me.foxtails.palustris.domain.PostDraftQuotePreview
import me.foxtails.palustris.domain.SavedPostsKind
import me.foxtails.palustris.domain.Timeline
import java.util.UUID
import me.foxtails.palustris.ui.emoji.ComposerField
import me.foxtails.palustris.ui.emoji.EmojiCatalogState
import me.foxtails.palustris.ui.emoji.EmojiPickerHost
import me.foxtails.palustris.ui.emoji.EmojiPickerTarget
import me.foxtails.palustris.ui.navigation.AppRoute
import me.foxtails.palustris.ui.notifications.NotificationDetailScreen
import me.foxtails.palustris.ui.notifications.NotificationRouteResolver
import me.foxtails.palustris.ui.notifications.NotificationSettingsScreen
import me.foxtails.palustris.ui.notifications.NotificationSettingsUiState
import me.foxtails.palustris.ui.notifications.NotificationsScreen
import me.foxtails.palustris.ui.profile.ProfileCategory
import me.foxtails.palustris.ui.profile.ProfileScreen as RichProfileScreen
import me.foxtails.palustris.ui.profile.ProfileUiState
import me.foxtails.palustris.ui.media.MediaOpenRequest
import me.foxtails.palustris.ui.media.MediaViewerScreen
import me.foxtails.palustris.ui.SinglePostScreen
import me.foxtails.palustris.ui.motion.AnimatedStatePane
import me.foxtails.palustris.ui.motion.LocalPalustrisMotionScheme
import me.foxtails.palustris.ui.motion.SpringAnimatedContent
import me.foxtails.palustris.ui.motion.compactFloatingEnter
import me.foxtails.palustris.ui.motion.compactFloatingExit
import me.foxtails.palustris.ui.motion.rememberSelectedColor
import me.foxtails.palustris.ui.motion.rememberSelectedScale
import me.foxtails.palustris.ui.motion.motionDirection
import me.foxtails.palustris.ui.motion.springPress

private enum class Destination(val label: String, val icon: ImageVector) {
    Home("Home", AppIcons.Home), Search("Search", AppIcons.Search),
    Notifications("Notifications", AppIcons.Notifications), Profile("Profile", AppIcons.Person),
}

private enum class NotificationsPanel { Notifications, DirectMessages }
private enum class LocalPage { SavedPosts, Drafts, About }
enum class SearchPanel { Search, Alternate }
private sealed interface Overlay {
    data object Composer : Overlay
    data object EditProfile : Overlay
    data object NotificationSettings : Overlay
}

private const val COMPOSER_OVERLAY_KEY = "Composer"
private const val EDIT_PROFILE_OVERLAY_KEY = "EditProfile"
private const val NOTIFICATION_SETTINGS_OVERLAY_KEY = "NotificationSettings"

private data class ContextualBottomAction(
    val icon: ImageVector,
    val contentDescription: String,
    val enabled: Boolean,
    val onClick: () -> Unit,
)

private fun savedCollectionTitle(kind: SavedPostsKind?): String = when (kind) {
    SavedPostsKind.Favourites -> "Favourites"
    SavedPostsKind.Bookmarks, null -> "Bookmarks"
}

private fun editableProfilePatch(base: EditableProfile, edited: EditableProfile): EditableProfilePatch = EditableProfilePatch(
    displayName = edited.displayName.takeIf { it != base.displayName },
    biography = edited.biography.takeIf { it != base.biography },
    fields = edited.fields.takeIf { it != base.fields },
    avatarDescription = edited.avatarDescription.takeIf { it != base.avatarDescription },
    headerDescription = edited.headerDescription.takeIf { it != base.headerDescription },
    locked = edited.locked.takeIf { it != base.locked },
    bot = edited.bot.takeIf { it != base.bot },
    hideCollections = edited.hideCollections.takeIf { it != base.hideCollections },
    discoverable = edited.discoverable.takeIf { it != base.discoverable },
    indexable = edited.indexable.takeIf { it != base.indexable },
    showMedia = edited.showMedia.takeIf { it != base.showMedia },
    showMediaReplies = edited.showMediaReplies.takeIf { it != base.showMediaReplies },
    showFeatured = edited.showFeatured.takeIf { it != base.showFeatured },
    attributionDomains = edited.attributionDomains.takeIf { it != base.attributionDomains },
)

internal val CompactNavigationHeight = 60.dp
internal val CompactTimelineSelectorWidth = 168.dp
internal val CompactTimelineSelectorHeight = 60.dp
internal val CompactOverlayControlSpacing = 14.dp
internal val CompactOverlayHorizontalPadding = 16.dp
internal val CompactOverlayVerticalPadding = 12.dp
internal val CompactSearchChipRowHeight = 48.dp
internal val CompactSearchControlsSpacing = 8.dp
internal val CompactSearchFieldHeight = 56.dp
internal val CompactFilterDockHeight = CompactSearchChipRowHeight + CompactSearchControlsSpacing
internal val CompactSearchDockHeight = CompactFilterDockHeight + CompactSearchFieldHeight
internal val CompactContextualControlsPositioningClearance = CompactNavigationHeight +
    CompactOverlayControlSpacing + CompactOverlayVerticalPadding
internal val LegacyFeedBottomClearance = 96.dp

/** Positions the shared navigation pill above the system navigation bar. */
@Composable
internal fun compactGlobalNavigationPositioningInsets(): WindowInsets =
    WindowInsets.navigationBarsIgnoringVisibility.only(WindowInsetsSides.Bottom)

/** Positions a Search/Profile/Notifications control stack above the shared navigation pill. */
@Composable
internal fun compactContextualControlsPositioningInsets(
    navigationVisible: Boolean,
    ime: WindowInsets = WindowInsets(bottom = 0.dp),
): WindowInsets = compactGlobalNavigationPositioningInsets()
    .add(
        WindowInsets(
            bottom = if (navigationVisible) CompactContextualControlsPositioningClearance else 0.dp,
        ),
    )
    .union(ime)
    .only(WindowInsetsSides.Bottom)

/**
 * Adds obstruction clearance to a scroll range without changing the page viewport.
 * The IME can replace the navigation assembly's positioning inset while it is taller.
 */
@Composable
internal fun compactScrollEndClearance(
    controlStackHeight: Dp,
    navigationVisible: Boolean,
    ime: WindowInsets = WindowInsets(bottom = 0.dp),
): Dp {
    val systemNavigationBottom = WindowInsets.navigationBarsIgnoringVisibility
        .asPaddingValues()
        .calculateBottomPadding()
    val imeBottom = ime.asPaddingValues().calculateBottomPadding()
    val contextualPositioning = if (navigationVisible) CompactContextualControlsPositioningClearance else 0.dp
    return maxOf(systemNavigationBottom + contextualPositioning, imeBottom) + controlStackHeight
}

/** Home has one additional timeline surface above the shared navigation pill. */
@Composable
internal fun compactHomeScrollEndClearance(): Dp {
    val systemNavigationBottom = WindowInsets.navigationBarsIgnoringVisibility
        .asPaddingValues()
        .calculateBottomPadding()
    return systemNavigationBottom + CompactNavigationHeight +
        CompactOverlayControlSpacing + CompactTimelineSelectorHeight +
        (CompactOverlayVerticalPadding * 2f)
}

private fun Modifier.roundPressLayer(pressed: Boolean, color: androidx.compose.ui.graphics.Color): Modifier = clip(CircleShape).drawWithContent {
    drawContent()
    if (pressed) {
        drawRoundRect(
            color = color,
            cornerRadius = CornerRadius(minOf(size.width, size.height) / 2f),
        )
    }
}

private fun contextualActionFor(
    destination: Destination,
    searchPanel: SearchPanel,
    notificationsPanel: NotificationsPanel,
    profileTarget: Account?,
    authenticatedAccountId: AccountId?,
    profileState: ProfileUiState,
    onCompose: () -> Unit,
    onSearchToggle: () -> Unit,
    onNotificationsToggle: () -> Unit,
    onEditProfile: () -> Unit,
    onFollowProfile: () -> Unit,
    onUnfollowProfile: () -> Unit,
): ContextualBottomAction? = when (destination) {
    Destination.Home -> ContextualBottomAction(AppIcons.Compose, "Compose post", true, onCompose)
    Destination.Search -> if (searchPanel == SearchPanel.Search) {
        ContextualBottomAction(AppIcons.WaffleGrid, "Alternate search", true, onSearchToggle)
    } else {
        ContextualBottomAction(AppIcons.Search, "Search", true, onSearchToggle)
    }
    Destination.Notifications -> if (notificationsPanel == NotificationsPanel.Notifications) {
        ContextualBottomAction(AppIcons.Chat, "Direct messages", true, onNotificationsToggle)
    } else {
        ContextualBottomAction(AppIcons.Notifications, "Notifications", true, onNotificationsToggle)
    }
    Destination.Profile -> when {
        profileTarget?.movedTo != null -> null
        profileTarget?.id == authenticatedAccountId && authenticatedAccountId != null ->
            ContextualBottomAction(AppIcons.PersonEdit, "Edit profile", profileState.editableSupported, onEditProfile)
        profileState.relationshipSupported == true && profileState.relationship != null -> {
            val relationship = profileState.relationship
            val following = relationship.following || relationship.requested
            ContextualBottomAction(
                icon = AppIcons.Person,
                contentDescription = when {
                    relationship.following -> "Unfollow profile"
                    relationship.requested -> "Cancel follow request"
                    else -> "Follow profile"
                },
                enabled = !profileState.relationshipMutation,
                onClick = if (following) onUnfollowProfile else onFollowProfile,
            )
        }
        else -> null
    }
}

@Composable
private fun TimelineSelector(
    timeline: Timeline,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val scheme = LocalPalustrisMotionScheme.current
    val pressed by interactionSource.collectIsPressedAsState()
    val pressColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    Surface(
        modifier = Modifier
            .size(CompactTimelineSelectorWidth, CompactTimelineSelectorHeight)
            .springPress(interactionSource, pressedScale = scheme.pressedScale)
            .roundPressLayer(pressed, pressColor)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .semantics { contentDescription = "Choose timeline" },
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.85f),
        shadowElevation = 6.dp,
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                timeline.name,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp, fontWeight = FontWeight.Bold),
            )
        }
    }
}

@Composable
private fun CompactContextualNavigationBar(
    destination: Destination,
    action: ContextualBottomAction?,
    account: Account?,
    onOpenAccounts: () -> Unit,
    onDestinationSelected: (Destination) -> Unit,
) {
    val scheme = LocalPalustrisMotionScheme.current
    Row(Modifier.fillMaxWidth().height(CompactNavigationHeight), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Surface(
            modifier = Modifier.weight(1f).fillMaxHeight(),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.85f),
            shadowElevation = 6.dp,
        ) {
            Box(Modifier.fillMaxSize().padding(horizontal = 4.dp)) {
                val scheme = LocalPalustrisMotionScheme.current
                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    Destination.entries.forEach { item ->
                        val selected = destination == item
                        val interactionSource = remember(item) { MutableInteractionSource() }
                        val pressed by interactionSource.collectIsPressedAsState()
                        val selectedTint = rememberSelectedColor(selected, MaterialTheme.colorScheme.onSecondaryContainer, MaterialTheme.colorScheme.onSurfaceVariant)
                        val selectedScale = rememberSelectedScale(selected)
                        val itemModifier = if (item == Destination.Profile) {
                                Modifier
                                    .size(48.dp)
                                    .springPress(interactionSource, pressedScale = scheme.compactPressedScale)
                                    .roundPressLayer(pressed, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
                                    .combinedClickable(
                                    interactionSource = interactionSource,
                                    indication = LocalIndication.current,
                                    onClick = { onDestinationSelected(item) },
                                    onLongClick = onOpenAccounts,
                                )
                        } else Modifier
                            .size(48.dp)
                            .springPress(interactionSource, pressedScale = scheme.compactPressedScale)
                            .roundPressLayer(pressed, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
                            .clickable(interactionSource = interactionSource, indication = LocalIndication.current) { onDestinationSelected(item) }
                        Box(itemModifier.semantics { contentDescription = item.label; this.selected = selected; role = Role.Tab }, contentAlignment = Alignment.Center) {
                            androidx.compose.animation.AnimatedVisibility(
                                visible = selected,
                                enter = if (scheme.reducedMotion) EnterTransition.None
                                else scaleIn(initialScale = 0.86f, animationSpec = scheme.expressive) + fadeIn(scheme.fastFadeIn),
                                exit = if (scheme.reducedMotion) ExitTransition.None
                                else scaleOut(targetScale = 0.86f, animationSpec = scheme.expressive) + fadeOut(scheme.fastFadeOut),
                            ) {
                                Surface(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .testTag("selected_navigation_indicator"),
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                ) {}
                            }
                            if (item == Destination.Profile) {
                                val avatarModifier = Modifier.size(30.dp).graphicsLayer {
                                    scaleX = selectedScale
                                    scaleY = selectedScale
                                }
                                if (account != null) AccountAvatar(account, avatarModifier, exposeSemantics = false) else Avatar(avatarModifier, description = null)
                            } else Icon(
                                item.icon,
                                null,
                                Modifier.graphicsLayer {
                                    scaleX = selectedScale
                                    scaleY = selectedScale
                                },
                                tint = selectedTint,
                            )
                        }
                    }
                }
            }
        }
        action?.let { contextualAction ->
            FilledIconButton(
                onClick = contextualAction.onClick,
                enabled = contextualAction.enabled,
                modifier = Modifier.size(52.dp).semantics { contentDescription = contextualAction.contentDescription },
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            ) {
                AnimatedContent(
                    targetState = contextualAction,
                    contentKey = { it.contentDescription },
                    transitionSpec = {
                        if (scheme.reducedMotion) {
                            EnterTransition.None togetherWith ExitTransition.None
                        } else {
                            (fadeIn(scheme.fastFadeIn) + scaleIn(initialScale = 0.86f, animationSpec = scheme.expressive)) togetherWith
                                (fadeOut(scheme.fastFadeOut) + scaleOut(targetScale = 0.86f, animationSpec = scheme.expressive))
                        }
                    },
                    modifier = Modifier.size(24.dp),
                    label = "contextualAction",
                ) { actionState -> Icon(actionState.icon, null) }
            }
        }
    }
}

@Composable
fun PalustrisApp(
    account: Account? = null,
    feedState: FeedState? = null,
    profileState: ProfileUiState = ProfileUiState(),
    onProfileShown: (Account) -> Unit = {},
    onProfileCategorySelected: (ProfileCategory) -> Unit = {},
    onRefreshProfile: () -> Unit = {},
    onLoadMoreProfile: () -> Unit = {},
    onFollowProfile: () -> Unit = {},
    onUnfollowProfile: () -> Unit = {},
    onRefresh: (Timeline) -> Unit = {},
    onLoadMore: (Timeline) -> Unit = {},
    onSignOut: () -> Unit = {},
    accounts: List<AccountRef> = emptyList(),
    onSwitchAccount: (AccountId) -> Unit = {},
    onAddAccount: () -> Unit = {},
    onPublish: (CreatePostRequest, () -> Unit) -> Unit = { _, onSuccess -> onSuccess() },
    onUpdateProfile: (EditableProfilePatch, () -> Unit) -> Unit = { _, onSuccess -> onSuccess() },
    onOpenProfileEditor: () -> Unit = {},
    onCloseEditor: () -> Unit = {},
    onSearchAccounts: (String) -> Unit = {},
    onLoadMoreSearch: () -> Unit = {},
    draftStore: DraftStore? = null,
    ownedPosts: List<OwnedPost>? = null,
    onReact: (OwnedPost) -> Unit = {},
    onReply: (OwnedPost) -> Unit = {},
    onReshare: (OwnedPost) -> Unit = {},
    onBookmark: (OwnedPost) -> Unit = {},
    onReaction: (OwnedPost, EmojiChoice) -> Unit = { _, _ -> },
    onSavedPostReaction: (OwnedPost, EmojiChoice) -> Unit = { _, _ -> },
    onProfilePostReaction: (OwnedPost, EmojiChoice) -> Unit = { _, _ -> },
    emojiCatalogState: EmojiCatalogState = EmojiCatalogState(),
    emojiCapabilities: EmojiCapabilities = EmojiCapabilities(),
    onLoadEmojiCatalog: () -> Unit = {},
    onRetryEmojiCatalog: () -> Unit = {},
    savedPostsState: SavedPostsUiState? = null,
    onRefreshSavedPosts: () -> Unit = {},
    onLoadMoreSavedPosts: () -> Unit = {},
    onUnsaveSavedPost: (OwnedPost) -> Unit = {},
    onUpgradeSavedPermissions: () -> Unit = {},
    notificationState: NotificationsUiState = NotificationsUiState(),
    onRefreshNotifications: () -> Unit = {},
    onLoadMoreNotifications: () -> Unit = {},
    onMarkAllNotificationsRead: () -> Unit = {},
    onMarkNotificationSeen: (Notification?) -> Unit = {},
    onDismissNotification: (Notification) -> Unit = {},
    onFollowRequest: (Notification, Boolean) -> Unit = { _, _ -> },
    onSelectNotificationQuery: (NotificationQuery) -> Unit = {},
    initialNotificationRoute: AppRoute? = null,
    notificationSettingsState: NotificationSettingsUiState = NotificationSettingsUiState(),
    onNotificationAlertsEnabled: (Boolean) -> Unit = {},
    onNotificationShowPreviews: (Boolean) -> Unit = {},
    onNotificationPeriodicFallback: (Boolean) -> Unit = {},
    onNotificationQuietHours: (Boolean) -> Unit = {},
    onNotificationCategoryChanged: (me.foxtails.palustris.domain.NotificationCategory, Boolean) -> Unit = { _, _ -> },
    onNotificationLocalTest: () -> Unit = {},
    onNotificationRetryRegistration: () -> Unit = {},
    onNotificationPermissionChanged: () -> Unit = {},
    onNotificationRefreshDistributors: () -> Unit = {},
    onNotificationSelectDistributor: (String) -> Unit = {},
    onNotificationPushConnectionTest: () -> Unit = {},
) = PalustrisTheme {
    val context = LocalContext.current
    val store = draftStore ?: remember { PreferencesDraftStore(context.getSharedPreferences("local_draft", Context.MODE_PRIVATE)) }
    val scope = rememberCoroutineScope()
    var destination by rememberSaveable { mutableStateOf(Destination.Home) }
    var destinationTransitionDirection by rememberSaveable { mutableIntStateOf(0) }
    var timeline by rememberSaveable { mutableStateOf(Timeline.Home) }
    var page by rememberSaveable { mutableStateOf<LocalPage?>(null) }
    var sheet by rememberSaveable { mutableStateOf<String?>(null) }
    var overlayKey by rememberSaveable { mutableStateOf<String?>(null) }
    var searchPanelName by rememberSaveable { mutableStateOf(SearchPanel.Search.name) }
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
    var draftError by rememberSaveable { mutableStateOf<String?>(null) }
    var savedQuoteOf by remember { mutableStateOf<String?>(null) }
    var composerReplyTo by remember { mutableStateOf<EntityId?>(null) }
    var savedReplyTo by remember { mutableStateOf<String?>(null) }
    var composerQuoteOf by remember { mutableStateOf<EntityId?>(null) }
    var composerTarget by remember { mutableStateOf<OwnedPost?>(null) }
    var viewedProfile by remember { mutableStateOf<Account?>(null) }
    var profileEditor by remember { mutableStateOf<EditableProfile?>(null) }
    var profileDialog by rememberSaveable { mutableStateOf(false) }
    var signOutDialog by remember { mutableStateOf(false) }
    var mediaRequest by remember { mutableStateOf<MediaOpenRequest?>(null) }
    var singlePost by remember { mutableStateOf<OwnedPost?>(null) }
    var emojiPickerTarget by remember { mutableStateOf<EmojiPickerTarget?>(null) }
    var postActionBubbleTarget by remember { mutableStateOf<PostActionBubbleTarget?>(null) }
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
    val modalOverlayOpen = overlay != null || sheet != null || profileDialog || signOutDialog || mediaRequest != null || singlePost != null || emojiPickerTarget != null
    val availableTimelines = if (account == null) Timeline.entries.toSet() else feedState?.timelines ?: setOf(Timeline.Home)
    val profileTargetId = viewedProfile?.id ?: account?.id
    val refreshedProfile = profileState.account?.takeIf { it.id == profileTargetId }
    val displayedProfile = refreshedProfile ?: viewedProfile ?: account
    val savedKind = savedPostsState?.kind ?: feedState?.savedPosts?.kind
    val savedTitle = savedCollectionTitle(savedKind)
    val notificationAccountIdentity = account?.id?.let { "${it.connection.origin}\u0000${it.localId}" } ?: "preview"
    val hasDraftChanges = draft != savedDraft ||
        (if (warningEnabled) warning else "") != savedWarning ||
        composerQuoteOf?.value != savedQuoteOf ||
        composerReplyTo?.value != savedReplyTo
    val editableProfile = profileState.account?.takeIf { it.id == account?.id } ?: account
    val editorBase = profileState.editable
        ?: editableProfile?.let { account ->
            EditableProfile(
                id = account.id.localId,
                displayName = account.displayName,
                biography = account.biography,
                fields = account.profileFields.map { EditableProfileField(it.name, it.value) },
                avatarUrl = account.avatarUrl,
                headerUrl = account.bannerUrl,
                locked = account.locked,
                bot = account.bot,
            )
        }
    val profileDirty = profileEditor != null && editorBase != null && profileEditor != editorBase

    fun clearPostActionBubble() {
        postActionBubbleTarget = null
        postReactionHandler = null
    }

    suspend fun reloadDrafts() {
        drafts = runCatching {
            store.migrateLegacy(account?.id, context.getSharedPreferences("local_draft", Context.MODE_PRIVATE))
            store.list(account?.id)
        }.getOrElse { emptyList() }
    }

    LaunchedEffect(account?.id, store) { reloadDrafts() }
    LaunchedEffect(overlayKey) {
        if (overlay == Overlay.EditProfile && editorBase != null && profileEditor == null) {
            profileEditor = editorBase
        }
    }
    LaunchedEffect(availableTimelines) { if (timeline !in availableTimelines) timeline = Timeline.Home }
    LaunchedEffect(feedState?.timeline, account?.id) { feedState?.timeline?.let { timeline = it } }
    LaunchedEffect(destination, page, overlayKey) { navigationVisible = true }
    LaunchedEffect(account?.id) {
        viewedProfile = null
        page = null
        composerTarget = null
        composerQuoteOf = null
        savedQuoteOf = null
        composerReplyTo = null
        savedReplyTo = null
        mediaRequest = null
        singlePost = null
        profileEditor = null
        emojiPickerTarget = null
        postActionBubbleTarget = null
        postReactionHandler = null
        pendingEmojiInsertion = null
    }
    LaunchedEffect(destination, page, overlayKey, sheet, profileDialog, signOutDialog, mediaRequest, singlePost, notificationRoute) {
        postActionBubbleTarget = null
        postReactionHandler = null
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

    SystemBars(mediaViewerOpen = mediaRequest != null)

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
        if (draft.isBlank() && savedDraft.isBlank() && first != null) loadDraft(first) else overlayKey = COMPOSER_OVERLAY_KEY
    }

    fun openQuote(target: OwnedPost) {
        val owner = account ?: return
        if (target.fetchedBy != owner.id || feedState?.quoteStatus != CapabilityStatus.Supported) return
        if (overlay != null || hasDraftChanges) return
        clearPostActionBubble()
        draftId = null
        draft = ""
        savedDraft = ""
        warning = ""
        savedWarning = ""
        warningEnabled = false
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
        if (target.fetchedBy != owner.id || PostAction.Reply !in (feedState?.actions ?: emptySet())) return
        if (overlay != null || hasDraftChanges) return
        clearPostActionBubble()
        draftId = null
        draft = ""
        savedDraft = ""
        warning = ""
        savedWarning = ""
        warningEnabled = false
        composerTarget = target
        composerQuoteOf = null
        composerReplyTo = target.post.actionTargetId ?: target.post.id
        savedQuoteOf = null
        savedReplyTo = null
        draftError = null
        overlayKey = COMPOSER_OVERLAY_KEY
    }

    val handleReply: (OwnedPost) -> Unit = { target ->
        openReply(target)
        onReply(target)
    }

    fun draftValue() = PostDraft(
        id = draftId ?: UUID.randomUUID().toString(),
        accountId = account?.id,
        text = draft,
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
        scope.launch {
            closing = true
            runCatching { val item = draftValue(); store.save(item); reloadDrafts(); item }
                .onSuccess { item ->
                    draftId = item.id
                    savedDraft = item.text
                    savedWarning = item.contentWarning.orEmpty()
                    savedQuoteOf = item.quoteOf?.value
                    savedReplyTo = item.replyTo?.value
                    draftError = null
                    onSaved()
                }
                .onFailure { draftError = "Draft could not be saved. Keep editing and try again." }
            closing = false
        }
    }
    fun closeComposer() { if (feedState?.publishing == true || closing) return; if (hasDraftChanges) saveCurrentDraft { overlayKey = null } else overlayKey = null }
    fun discardProfileEditor() {
        profileEditor = null
        overlayKey = null
        onCloseEditor()
    }

    fun closeProfile() {
        if (profileState.savingProfile) return
        if (profileDirty) profileDialog = true else discardProfileEditor()
    }

    fun openProfileEditor() {
        if (account != null && displayedProfile?.id == account.id && profileState.editableSupported) {
            clearPostActionBubble()
            onOpenProfileEditor()
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
        if (ownedPost.fetchedBy != owner.id || emojiCapabilities.reactionMutation != CapabilityStatus.Supported) return
        postReactionHandler = handler
        postActionBubbleTarget = PostActionBubbleTarget.Reaction(ownedPost, bounds)
    }
    fun selectDestination(item: Destination) {
        clearPostActionBubble()
        if (item == Destination.Profile) viewedProfile = null
        destinationTransitionDirection = motionDirection(destination.ordinal, item.ordinal, motionScheme.reducedMotion)
        destination = item
        page = null
        notificationRoute = null
    }
    fun openProfile(profile: Account) {
        clearPostActionBubble()
        viewedProfile = profile
        destinationTransitionDirection = motionDirection(destination.ordinal, Destination.Profile.ordinal, motionScheme.reducedMotion)
        destination = Destination.Profile
        page = null
        sheet = null
        notificationRoute = null
    }

    fun openNotificationTarget(route: AppRoute) {
        if (route !is AppRoute.Profile) {
            notificationRoute = null
            return
        }
        val target = notificationState.items
            .firstOrNull { it.target == me.foxtails.palustris.domain.NotificationTarget.Profile(route.profileId) }
            ?.actors
            ?.firstOrNull { it.id == route.profileId }
        if (target != null) openProfile(target) else notificationRoute = null
    }

    fun openHashtagSearch(hashtag: String) {
        clearPostActionBubble()
        searchPrefill = hashtag
        searchPanelName = SearchPanel.Search.name
        destinationTransitionDirection = motionDirection(destination.ordinal, Destination.Search.ordinal, motionScheme.reducedMotion)
        destination = Destination.Search
        page = null
        onSearchAccounts(hashtag)
    }

    fun openAccountSearch(username: String) {
        clearPostActionBubble()
        searchPrefill = username
        searchPanelName = SearchPanel.Search.name
        destinationTransitionDirection = motionDirection(destination.ordinal, Destination.Search.ordinal, motionScheme.reducedMotion)
        destination = Destination.Search
        page = null
        onSearchAccounts(username)
    }

    fun openMedia(request: MediaOpenRequest) {
        if (account?.id != null && request.ownedPost.fetchedBy != account.id) return
        if (request.attachmentIndex !in request.ownedPost.post.attachments.indices) return
        clearPostActionBubble()
        mediaRequest = request
    }

    fun openSinglePost(post: OwnedPost) {
        clearPostActionBubble()
        mediaRequest = null
        singlePost = post
    }

    BackHandler(enabled = singlePost != null || mediaRequest != null || notificationRoute != null || overlay != null || page != null || destination != Destination.Home) {
        when {
            singlePost != null -> singlePost = null
            mediaRequest != null -> mediaRequest = null
            overlay == Overlay.NotificationSettings -> closeNotificationSettings()
            overlay == Overlay.Composer -> closeComposer()
            overlay == Overlay.EditProfile -> closeProfile()
            notificationRoute != null -> notificationRoute = null
            page != null -> page = null
            else -> selectDestination(Destination.Home)
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val wide = maxWidth >= 600.dp
        Row(Modifier.fillMaxSize()) {
            if (wide) NavigationRail(Modifier.fillMaxHeight(), header = { FloatingActionButton(onClick = ::openComposer, modifier = Modifier.padding(vertical = 16.dp)) { Icon(AppIcons.Edit, "Compose post") } }) {
                Destination.entries.forEach { item -> NavigationRailItem(selected = destination == item, onClick = { selectDestination(item) }, icon = { Icon(item.icon, item.label) }, label = { Text(item.label) }) }
            }
            Box(Modifier.weight(1f).fillMaxHeight()) {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    // Compact page bodies receive top/horizontal system insets only.
                    // Content must measure through the floating assembly; scrollables
                    // add end clearance inside their scroll range instead.
                    contentWindowInsets = when {
                        page == null && notificationRoute == null && destination == Destination.Profile ->
                            WindowInsets.systemBars.only(WindowInsetsSides.Horizontal)
                        !wide && page == null && notificationRoute == null ->
                            WindowInsets.systemBars.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
                        else -> ScaffoldDefaults.contentWindowInsets
                    },
                    topBar = {
                    when {
                        page != null -> TopAppBar(
                            title = { Text(if (page == LocalPage.SavedPosts) savedTitle else page!!.name) },
                             navigationIcon = { ActionIcon(AppIcons.Back, "Back") { clearPostActionBubble(); page = null } },
                         )
                         notificationRoute != null -> TopAppBar(title = { Text("Notification") }, navigationIcon = { ActionIcon(AppIcons.Back, "Back") { clearPostActionBubble(); notificationRoute = null } })
                    }
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
                            NotificationDetailScreen(
                                route = notificationRoute!!,
                                items = notificationState.items,
                                onSearchHashtag = ::openHashtagSearch,
                                onOpenHashtagBubble = ::openHashtagBubble,
                                onOpenPost = ::openSinglePost,
                                onOpenTarget = (notificationRoute as? AppRoute.Profile)?.let { route ->
                                    { openNotificationTarget(route) }
                                },
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else when (page) {
                            LocalPage.SavedPosts -> savedPostsState?.let { savedState ->
                                SavedPostsScreen(
                                    state = savedState,
                                    onRefresh = onRefreshSavedPosts,
                                    onLoadMore = onLoadMoreSavedPosts,
                                    onUnsave = onUnsaveSavedPost,
                                    onSignIn = onUpgradeSavedPermissions,
                                    onUpgradePermissions = onUpgradeSavedPermissions,
                                    onReact = onReact,
                                     onReply = handleReply,
                                     onReshare = onReshare,
                                      onReaction = onSavedPostReaction,
                                      onOpenReactionBubble = { ownedPost, bounds ->
                                          openReactionBubble(ownedPost, bounds, onSavedPostReaction)
                                      },
                                      onOpenMedia = ::openMedia,
                                      onOpenPost = ::openSinglePost,
                                      availableActions = (feedState?.actions ?: emptySet()) + PostAction.Bookmark,
                                      onOpenProfile = ::openProfile,
                                      onSearchHashtag = ::openHashtagSearch,
                                      onOpenHashtagBubble = ::openHashtagBubble,
                                      onOpenUsername = ::openAccountSearch,
                                  )
                            } ?: EmptyState(AppIcons.Bookmark, "No saved posts yet", "Posts you save will appear here.")
                            LocalPage.Drafts -> DraftsScreen(drafts, ::loadDraft, { item -> scope.launch { store.delete(account?.id, item.id); reloadDrafts() } })
                            LocalPage.About -> EmptyState(AppIcons.Globe, "A place for your fediverse", "Misskey and Sharkey home timelines. Publishing and other timelines are coming later.")
                              else -> when (animatedDestination) {
                                  Destination.Home -> if (feedState != null) HomeFeed(state = feedState, compactLayout = !wide, onRefresh = { onRefresh(timeline) }, onLoadMore = { onLoadMore(timeline) }, onSignIn = onSignOut, ownedPosts = ownedPosts ?: feedState.ownedPosts, onScrollDirectionChanged = { navigationVisible = it }, onReact = onReact, onReply = handleReply, onReshare = onReshare, onBookmark = onBookmark, onReaction = onReaction, onOpenReactionBubble = { ownedPost, bounds ->
                                      openReactionBubble(ownedPost, bounds, onReaction)
                                    }, onQuote = ::openQuote, onOpenProfile = ::openProfile, onSearchHashtag = ::openHashtagSearch, onOpenHashtagBubble = ::openHashtagBubble, onOpenMedia = ::openMedia, onOpenPost = ::openSinglePost, onOpenUsername = ::openAccountSearch) else EmptyState(AppIcons.Home, "Your timeline starts here", "${timeline.name} posts will appear here when an account is connected.")
                                 Destination.Search -> AnimatedStatePane(
                                     stateKey = searchPanel,
                                     modifier = Modifier.fillMaxSize(),
                                  ) { panel -> SearchScreen(
                                        mode = panel,
                                       accountSearch = feedState?.accountSearch ?: AccountSearchState(),
                                       onSearchAccounts = onSearchAccounts,
                                       onAccountClick = ::openProfile,
                                       availableActions = feedState?.actions ?: emptySet(),
                                       onReact = onReact,
                                       onReply = handleReply,
                                       onReshare = onReshare,
                                       onBookmark = onBookmark,
                                       onReaction = onReaction,
                                       onOpenReactionBubble = { ownedPost, bounds ->
                                           openReactionBubble(ownedPost, bounds, onReaction)
                                       },
                                       quoteEnabled = feedState?.quoteStatus == CapabilityStatus.Supported,
                                       onQuote = ::openQuote,
                                       onSearchHashtag = ::openHashtagSearch,
                                       onOpenHashtagBubble = ::openHashtagBubble,
                                      onLoadMoreSearch = onLoadMoreSearch,
                                      initialQuery = searchPrefill,
                                      compactLayout = !wide,
                                      compactNavigationVisible = !wide,
                                        mediaOwner = account?.id,
                                        onOpenMedia = ::openMedia,
                                        onOpenPost = ::openSinglePost,
                                        onOpenUsername = ::openAccountSearch,
                                     ) }
                                 Destination.Notifications -> AnimatedStatePane(
                                     stateKey = notificationsPanel,
                                     modifier = Modifier.fillMaxSize(),
                                 ) { panel -> if (panel == NotificationsPanel.Notifications) NotificationsScreen(
                                    connected = account != null,
                                    compactLayout = !wide,
                                    accountIdentity = notificationAccountIdentity,
                                    notificationState = notificationState,
                                    onRefreshNotifications = onRefreshNotifications,
                                    onLoadMoreNotifications = onLoadMoreNotifications,
                                    onMarkNotificationSeen = onMarkNotificationSeen,
                                    onDismissNotification = onDismissNotification,
                                    onFollowRequest = onFollowRequest,
                                     onOpenNotification = { notification ->
                                         clearPostActionBubble()
                                         notificationRoute = NotificationRouteResolver.resolve(notification)
                                    },
                                    onSelectQuery = onSelectNotificationQuery,
                                    onMarkAllRead = onMarkAllNotificationsRead,
                                     onOpenSettings = {
                                         if (account != null) {
                                             clearPostActionBubble()
                                              overlayKey = NOTIFICATION_SETTINGS_OVERLAY_KEY
                                         }
                                    },
                                 ) else MessagesScreen() }
                Destination.Profile -> RichProfileScreen(
                    account = displayedProfile,
                    profileState = profileState,
                    compactLayout = !wide,
                    compactNavigationVisible = navigationVisible,
                    authenticatedAccountId = account?.id,
                    onProfileShown = onProfileShown,
                    onCategorySelected = onProfileCategorySelected,
                    onRefresh = onRefreshProfile,
                    onLoadMore = onLoadMoreProfile,
                    onFollow = onFollowProfile,
                    onUnfollow = onUnfollowProfile,
                    onOpenDrafts = {
                        if (account != null && displayedProfile?.id == account.id) page = LocalPage.Drafts
                    },
                    onOpenBookmarks = {
                        if (account != null && displayedProfile?.id == account.id) page = LocalPage.SavedPosts
                    },
                    onOpenProfile = ::openProfile,
                     onSearchHashtag = ::openHashtagSearch,
                     onOpenHashtagBubble = ::openHashtagBubble,
                    availableActions = feedState?.actions ?: emptySet(),
                    onReact = onReact,
                     onReply = handleReply,
                    onReshare = onReshare,
                    onBookmark = onBookmark,
                     onReaction = onProfilePostReaction,
                      onOpenReactionBubble = { ownedPost, bounds ->
                          openReactionBubble(ownedPost, bounds, onProfilePostReaction)
                      },
                       onOpenMedia = ::openMedia,
                       onOpenPost = ::openSinglePost,
                       onOpenUsername = ::openAccountSearch,
                   )
                              }
                        }
                        }
                        }
                        }
                    }
                }
                if (wide && page == null && !modalOverlayOpen && destination == Destination.Home) {
                    androidx.compose.animation.AnimatedVisibility(visible = navigationVisible, enter = motionScheme.compactFloatingEnter(bottom = false), exit = motionScheme.compactFloatingExit(bottom = false), modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 12.dp).zIndex(1f)) {
                         TimelineSelector(timeline) { clearPostActionBubble(); sheet = "Timelines" }
                    }
                }
                if (!wide && page == null && !modalOverlayOpen) {
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
                                     TimelineSelector(timeline) { clearPostActionBubble(); sheet = "Timelines" }
                                    Spacer(Modifier.height(CompactOverlayControlSpacing))
                                }
                                CompactContextualNavigationBar(
                                    destination = destination,
                                    action = contextualActionFor(
                                        destination = destination,
                                        searchPanel = searchPanel,
                                        notificationsPanel = notificationsPanel,
                                        profileTarget = displayedProfile,
                                        authenticatedAccountId = account?.id,
                                        profileState = profileState,
                                        onCompose = ::openComposer,
                                        onSearchToggle = {
                                            searchPanelName = if (searchPanel == SearchPanel.Search) {
                                                SearchPanel.Alternate.name
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
                                        onFollowProfile = onFollowProfile,
                                        onUnfollowProfile = onUnfollowProfile,
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
        val hashtagBottomClearance = if (wide || page != null || notificationRoute != null) {
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
            emojiCatalog = emojiCatalogState,
            emojiCapabilities = emojiCapabilities,
            onLoadEmojiCatalog = onLoadEmojiCatalog,
            onRetryEmojiCatalog = onRetryEmojiCatalog,
            onDismiss = ::clearPostActionBubble,
            onHashtagSelected = { hashtag ->
                clearPostActionBubble()
                openHashtagSearch(hashtag)
            },
            onReactionSelected = { target, choice ->
                val owner = account
                val handler = postReactionHandler
                if (owner != null && target.fetchedBy == owner.id && emojiCapabilities.reactionMutation == CapabilityStatus.Supported) {
                    handler?.invoke(target, choice)
                }
                clearPostActionBubble()
            },
            onReactionModeChanged = { expanded -> postActionBubbleTarget = expanded },
            hashtagBottomClearance = hashtagBottomClearance,
        )
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

    singlePost?.let { post ->
        SinglePostScreen(
            ownedPost = post,
            onClose = { singlePost = null },
            availableActions = feedState?.actions ?: emptySet(),
            onReact = onReact,
            onReply = handleReply,
            onReshare = onReshare,
            onBookmark = onBookmark,
            onReaction = onReaction,
            onOpenProfile = ::openProfile,
            onSearchHashtag = ::openHashtagSearch,
             onOpenHashtagBubble = ::openHashtagBubble,
             onOpenMedia = ::openMedia,
             onOpenUsername = ::openAccountSearch,
         )
    }

    if (sheet != null) ModalBottomSheet(onDismissRequest = { sheet = null }) {
        Text(sheet!!, Modifier.padding(horizontal = 24.dp, vertical = 12.dp), style = MaterialTheme.typography.headlineSmall)
        if (sheet == "Timelines") {
            Timeline.entries.filter { it in availableTimelines }.forEach { item ->
                ListItem(modifier = Modifier.clickable { val changed = item != timeline; timeline = item; sheet = null; if (changed) onRefresh(item) }, headlineContent = { Text(item.name) }, supportingContent = { Text(when (item) { Timeline.Home -> "Posts from people you follow"; Timeline.Local -> "Posts from your server"; Timeline.Social -> "Posts from your server and people it follows"; Timeline.Federated -> "Posts from across the fediverse" }) }, leadingContent = { Icon(if (item == Timeline.Home) AppIcons.Home else AppIcons.Globe, null) }, trailingContent = { if (timeline == item) Icon(AppIcons.Check, "Selected") })
            }
            Text(if (account != null) "Timelines are detected from this server" else "Timeline preview", Modifier.padding(24.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            accounts.forEach { accountRef ->
                val listedAccount = accountRef.toAccount()
                ListItem(modifier = Modifier.clickable { sheet = null; if (accountRef.accountId != account?.id) onSwitchAccount(accountRef.accountId) }, headlineContent = { Text(accountRef.displayName) }, supportingContent = { Text(accountRef.handle) }, leadingContent = { AccountAvatar(listedAccount, Modifier.size(48.dp)) }, trailingContent = { if (accountRef.accountId == account?.id) Icon(AppIcons.Check, "Current account") })
            }
            if (accounts.isEmpty()) ListItem(headlineContent = { Text(account?.displayName ?: "No accounts connected") }, supportingContent = { Text(account?.handle ?: "Account connections are not available in this preview.") }, leadingContent = { if (account != null) AccountAvatar(account, Modifier.size(48.dp)) else Avatar(Modifier.size(48.dp)) })
            if (account != null) TextButton(onClick = { sheet = null; onAddAccount() }, modifier = Modifier.padding(horizontal = 16.dp)) { Text("Add account") }
            if (account != null) TextButton(onClick = { sheet = null; signOutDialog = true }, modifier = Modifier.padding(horizontal = 16.dp)) { Text("Sign out") }
            Spacer(Modifier.height(32.dp))
        }
    }

    if (overlay == Overlay.Composer) ModalBottomSheet(onDismissRequest = ::closeComposer, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Row(verticalAlignment = Alignment.CenterVertically) { ActionIcon(AppIcons.Close, "Close composer", ::closeComposer); Text("New post", style = MaterialTheme.typography.titleLarge) }
                TextButton(enabled = (draft.isNotBlank() || composerReplyTo != null) && !closing, onClick = { saveCurrentDraft { overlayKey = null } }) { Text("Save draft") }
            }
            ComposeScreen(text = draft, onTextChange = { draft = it }, warning = warning, onWarningChange = { warning = it }, warningEnabled = warningEnabled, onWarningEnabled = { warningEnabled = it }, account = account, canPublish = feedState?.canPublish == true && (draftId == null || drafts.firstOrNull { it.id == draftId }?.accountId == account?.id), publishing = feedState?.publishing == true, error = feedState?.error ?: draftError, quoteTarget = composerTarget, isReply = composerReplyTo != null, onRemoveQuote = { composerTarget = null; composerQuoteOf = null; composerReplyTo = null }, onRequestEmoji = { field ->
                emojiPickerTarget = EmojiPickerTarget.Composer(field)
            }, pendingEmojiInsertion = pendingEmojiInsertion, onEmojiInsertionApplied = { pendingEmojiInsertion = null }, onPublish = {
                val submittedText = draft; val submittedWarning = warning.takeIf { warningEnabled && it.isNotBlank() }
                val submittedQuote = composerQuoteOf?.takeIf { quote -> quote.connection == account?.id?.connection?.origin }
                val submittedReply = composerReplyTo?.takeIf { reply -> reply.connection == account?.id?.connection?.origin }
                scope.launch {
                    runCatching { val item = draftValue(); store.save(item); reloadDrafts(); item }.onSuccess { saved ->
                        draftId = saved.id; savedDraft = saved.text; savedWarning = saved.contentWarning.orEmpty()
                        onPublish(CreatePostRequest(submittedText, contentWarning = submittedWarning, replyTo = submittedReply, quoteOf = submittedQuote)) {
                            scope.launch { store.delete(account?.id, saved.id); reloadDrafts() }
                            draft = ""
                            savedDraft = ""
                            warning = ""
                            savedWarning = ""
                            warningEnabled = false
                            draftId = null
                            savedQuoteOf = null
                            savedReplyTo = null
                            composerReplyTo = null
                            composerQuoteOf = null
                            composerTarget = null
                            overlayKey = null
                        }
                    }.onFailure { draftError = "Draft could not be saved. Keep the composer open and try again." }
                }
            })
        }
    }

    if (overlay == Overlay.EditProfile && account != null) ModalBottomSheet(onDismissRequest = ::closeProfile, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        me.foxtails.palustris.ui.profile.EditProfileScreen(
            editor = profileEditor,
            capabilities = profileState.editorCapabilities,
            emoji = profileState.account?.emoji ?: emptyMap(),
            handle = account!!.handle,
            loading = profileState.editableLoading,
            saving = profileState.savingProfile,
            error = profileState.editError ?: profileState.editableError,
            onEditorChange = { profileEditor = it },
            onSave = {
                val base = editorBase ?: return@EditProfileScreen
                val edited = profileEditor ?: return@EditProfileScreen
                onUpdateProfile(editableProfilePatch(base, edited)) {
                    profileEditor = null
                    overlayKey = null
                }
            },
            onClose = ::closeProfile,
        )
    }

    if (emojiPickerTarget != null) {
        EmojiPickerHost(
            target = emojiPickerTarget,
            catalog = emojiCatalogState,
            selectionMode = emojiCapabilities.selectionMode,
            mutationSupported = emojiCapabilities.reactionMutation == CapabilityStatus.Supported,
            onLoadCatalog = onLoadEmojiCatalog,
            onRetryCatalog = onRetryEmojiCatalog,
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

    if (overlay == Overlay.NotificationSettings && account != null) {
        ModalBottomSheet(
            onDismissRequest = ::closeNotificationSettings,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .testTag("notification_settings_sheet"),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ActionIcon(
                        AppIcons.Close,
                        context.getString(me.foxtails.palustris.R.string.notification_settings_sheet_close),
                        ::closeNotificationSettings,
                    )
                    Text(
                        context.getString(me.foxtails.palustris.R.string.notification_settings_sheet_title),
                        style = MaterialTheme.typography.titleLarge,
                    )
                }
                NotificationSettingsScreen(
                    state = notificationSettingsState,
                    onAlertsEnabled = onNotificationAlertsEnabled,
                    onShowPreviews = onNotificationShowPreviews,
                    onPeriodicFallback = onNotificationPeriodicFallback,
                    onQuietHours = onNotificationQuietHours,
                    onCategoryChanged = onNotificationCategoryChanged,
                    onRunLocalTest = onNotificationLocalTest,
                    onRetryRegistration = onNotificationRetryRegistration,
                    onPermissionChanged = onNotificationPermissionChanged,
                    onRefreshDistributors = onNotificationRefreshDistributors,
                    onSelectDistributor = onNotificationSelectDistributor,
                    onRunPushConnectionTest = onNotificationPushConnectionTest,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                )
            }
        }
    }

    BackHandler(enabled = overlay == Overlay.NotificationSettings) {
        closeNotificationSettings()
    }

    if (profileDialog) AlertDialog(onDismissRequest = { profileDialog = false }, title = { Text("Discard profile changes?") }, text = { Text("Your changes have not been saved.") }, confirmButton = { TextButton(onClick = { profileDialog = false; discardProfileEditor() }) { Text("Discard") } }, dismissButton = { TextButton(onClick = { profileDialog = false }) { Text("Keep editing") } })
    if (signOutDialog) AlertDialog(onDismissRequest = { signOutDialog = false }, title = { Text("Sign out?") }, text = { Text("Your sign-in will be removed from this device. Local drafts will remain.") }, confirmButton = { TextButton(onClick = { signOutDialog = false; onSignOut() }) { Text("Sign out") } }, dismissButton = { TextButton(onClick = { signOutDialog = false }) { Text("Cancel") } })
}

@Preview(showBackground = true, device = "spec:width=411dp,height=891dp,dpi=420")
@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun AppPreview() { PalustrisApp() }
