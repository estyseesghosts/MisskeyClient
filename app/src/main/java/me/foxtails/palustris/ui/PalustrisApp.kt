@file:OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
)

package me.foxtails.palustris.ui

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
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
import me.foxtails.palustris.domain.CreatePostRequest
import me.foxtails.palustris.domain.Notification
import me.foxtails.palustris.domain.NotificationQuery
import me.foxtails.palustris.domain.OwnedPost
import me.foxtails.palustris.domain.PostDraft
import me.foxtails.palustris.domain.Timeline
import me.foxtails.palustris.domain.UpdateProfileRequest
import java.util.UUID
import me.foxtails.palustris.ui.navigation.AppRoute
import me.foxtails.palustris.ui.notifications.NotificationDetailScreen
import me.foxtails.palustris.ui.notifications.NotificationRouteResolver
import me.foxtails.palustris.ui.notifications.NotificationSettingsScreen
import me.foxtails.palustris.ui.notifications.NotificationSettingsUiState
import me.foxtails.palustris.ui.notifications.NotificationsScreen

private enum class Destination(val label: String, val icon: ImageVector) {
    Home("Home", AppIcons.Home), Search("Search", AppIcons.Search),
    Notifications("Notifications", AppIcons.Notifications), Profile("Profile", AppIcons.Person),
}

private enum class NotificationsPanel { Notifications, DirectMessages }
enum class SearchPanel { Search, Alternate }
private sealed interface Overlay {
    data object Composer : Overlay
    data object EditProfile : Overlay
}

private data class ContextualBottomAction(
    val icon: ImageVector,
    val contentDescription: String,
    val enabled: Boolean,
    val onClick: () -> Unit,
)

internal val CompactNavigationHeight = 60.dp
internal val CompactTimelineSelectorWidth = 168.dp
internal val CompactTimelineSelectorHeight = 60.dp
internal val CompactOverlayControlSpacing = 14.dp
internal val CompactOverlayHorizontalPadding = 16.dp
internal val CompactOverlayVerticalPadding = 12.dp
internal val CompactOverlayFeedBottomClearance = CompactTimelineSelectorHeight +
    CompactOverlayControlSpacing + CompactNavigationHeight + (CompactOverlayVerticalPadding * 2f)
internal val CompactSearchChipRowHeight = 48.dp
internal val CompactSearchDockHeight = CompactSearchChipRowHeight + 8.dp + 56.dp
internal val CompactSearchNavigationClearance = CompactNavigationHeight +
    CompactOverlayControlSpacing + CompactOverlayVerticalPadding
internal val LegacyFeedBottomClearance = 96.dp

private fun Modifier.roundPressLayer(pressed: Boolean, color: Color): Modifier = clip(CircleShape).drawWithContent {
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
    editProfileEnabled: Boolean,
    onCompose: () -> Unit,
    onSearchToggle: () -> Unit,
    onNotificationsToggle: () -> Unit,
    onEditProfile: () -> Unit,
): ContextualBottomAction = when (destination) {
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
    Destination.Profile -> ContextualBottomAction(AppIcons.PersonEdit, "Edit profile", editProfileEnabled, onEditProfile)
}

@Composable
private fun TimelineSelector(
    timeline: Timeline,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    Surface(
        modifier = Modifier
            .size(CompactTimelineSelectorWidth, CompactTimelineSelectorHeight)
            .roundPressLayer(pressed, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
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
    action: ContextualBottomAction,
    account: Account?,
    onOpenAccounts: () -> Unit,
    onDestinationSelected: (Destination) -> Unit,
) {
    Row(Modifier.fillMaxWidth().height(CompactNavigationHeight), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Surface(
            modifier = Modifier.weight(1f).fillMaxHeight(),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.85f),
            shadowElevation = 6.dp,
        ) {
            Row(Modifier.fillMaxSize().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                Destination.entries.forEach { item ->
                    val selected = destination == item
                    Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                        if (selected) Surface(Modifier.size(40.dp), CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {}
                        val interactionSource = remember(item) { MutableInteractionSource() }
                        val pressed by interactionSource.collectIsPressedAsState()
                        val itemModifier = if (item == Destination.Profile) {
                            Modifier.size(48.dp)
                                .roundPressLayer(pressed, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
                                .combinedClickable(interactionSource = interactionSource, indication = null, onClick = { onDestinationSelected(item) }, onLongClick = onOpenAccounts)
                        } else Modifier.size(48.dp)
                            .roundPressLayer(pressed, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
                            .clickable(interactionSource = interactionSource, indication = null) { onDestinationSelected(item) }
                        Box(itemModifier.semantics { contentDescription = item.label; this.selected = selected; role = Role.Tab }, contentAlignment = Alignment.Center) {
                            if (item == Destination.Profile) {
                                if (account != null) AccountAvatar(account, Modifier.size(30.dp), exposeSemantics = false) else Avatar(Modifier.size(30.dp), description = null)
                            } else Icon(item.icon, null, tint = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
        FilledIconButton(onClick = action.onClick, enabled = action.enabled, modifier = Modifier.size(52.dp).semantics { contentDescription = action.contentDescription }, colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer, disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest, disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant)) { Icon(action.icon, null) }
    }
}

@Composable
fun PalustrisApp(
    account: Account? = null,
    feedState: FeedState? = null,
    profile: Account? = null,
    onRefresh: (Timeline) -> Unit = {},
    onLoadMore: (Timeline) -> Unit = {},
    onSignOut: () -> Unit = {},
    accounts: List<AccountRef> = emptyList(),
    onSwitchAccount: (AccountId) -> Unit = {},
    onAddAccount: () -> Unit = {},
    onPublish: (CreatePostRequest, () -> Unit) -> Unit = { _, onSuccess -> onSuccess() },
    onUpdateProfile: (UpdateProfileRequest, () -> Unit) -> Unit = { _, onSuccess -> onSuccess() },
    onSearchAccounts: (String) -> Unit = {},
    onLoadMoreSearch: () -> Unit = {},
    draftStore: DraftStore? = null,
    ownedPosts: List<OwnedPost>? = null,
    onReact: (OwnedPost) -> Unit = {},
    onReply: (OwnedPost) -> Unit = {},
    onReshare: (OwnedPost) -> Unit = {},
    onBookmark: (OwnedPost) -> Unit = {},
    onReaction: (OwnedPost, String) -> Unit = { _, _ -> },
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
    onNotificationPermissionChanged: () -> Unit = {},
) = PalustrisTheme {
    val context = LocalContext.current
    val store = draftStore ?: remember { PreferencesDraftStore(context.getSharedPreferences("local_draft", Context.MODE_PRIVATE)) }
    val scope = rememberCoroutineScope()
    var destination by rememberSaveable { mutableStateOf(Destination.Home) }
    var timeline by rememberSaveable { mutableStateOf(Timeline.Home) }
    var page by rememberSaveable { mutableStateOf<String?>(null) }
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
    var viewedProfile by remember { mutableStateOf<Account?>(null) }
    var profileName by rememberSaveable { mutableStateOf("") }
    var profileBiography by rememberSaveable { mutableStateOf("") }
    var profileDialog by rememberSaveable { mutableStateOf(false) }
    var signOutDialog by remember { mutableStateOf(false) }
    var navigationVisible by rememberSaveable { mutableStateOf(true) }
    var notificationRoute by remember { mutableStateOf<AppRoute?>(initialNotificationRoute) }
    var closing by remember { mutableStateOf(false) }
    val overlay = when (overlayKey) { "Composer" -> Overlay.Composer; "EditProfile" -> Overlay.EditProfile; else -> null }
    val modalOverlayOpen = overlay != null || sheet != null || profileDialog || signOutDialog
    val availableTimelines = if (account == null) Timeline.entries.toSet() else feedState?.timelines ?: setOf(Timeline.Home)
    val currentProfile = profile ?: account
    val displayedProfile = viewedProfile ?: currentProfile
    val notificationAccountIdentity = account?.id?.let { "${it.connection.origin}\u0000${it.localId}" } ?: "preview"
    val hasDraftChanges = draft != savedDraft || (if (warningEnabled) warning else "") != savedWarning
    val profileDirty = account != null && (profileName != account.displayName || profileBiography != account.biography)

    suspend fun reloadDrafts() {
        drafts = runCatching {
            store.migrateLegacy(account?.id, context.getSharedPreferences("local_draft", Context.MODE_PRIVATE))
            store.list(account?.id)
        }.getOrElse { emptyList() }
    }

    LaunchedEffect(account?.id, store) { reloadDrafts() }
    LaunchedEffect(overlayKey, account?.id) { if (overlay == Overlay.EditProfile && account != null) { profileName = account.displayName; profileBiography = account.biography } }
    LaunchedEffect(availableTimelines) { if (timeline !in availableTimelines) timeline = Timeline.Home }
    LaunchedEffect(feedState?.timeline, account?.id) { feedState?.timeline?.let { timeline = it } }
    LaunchedEffect(destination, page, overlayKey) { navigationVisible = true }
    LaunchedEffect(account?.id) { viewedProfile = null }
    LaunchedEffect(initialNotificationRoute) {
        notificationRoute = initialNotificationRoute
        if (initialNotificationRoute != null) destination = Destination.Notifications
    }

    fun loadDraft(item: PostDraft) {
        draftId = item.id; draft = item.text; savedDraft = item.text; warning = item.contentWarning.orEmpty(); savedWarning = item.contentWarning.orEmpty(); warningEnabled = !item.contentWarning.isNullOrBlank(); draftError = null; overlayKey = Overlay.Composer::class.simpleName
    }
    fun openComposer() {
        if (overlay == Overlay.Composer) return
        val first = drafts.firstOrNull()
        if (draft.isBlank() && savedDraft.isBlank() && first != null) loadDraft(first) else overlayKey = Overlay.Composer::class.simpleName
    }
    fun draftValue() = PostDraft(id = draftId ?: UUID.randomUUID().toString(), accountId = account?.id, text = draft, contentWarning = warning.takeIf { warningEnabled && it.isNotBlank() })
    fun saveCurrentDraft(onSaved: () -> Unit = {}) {
        if (draft.isBlank() && warning.isBlank()) { onSaved(); return }
        scope.launch {
            closing = true
            runCatching { val item = draftValue(); store.save(item); reloadDrafts(); item }
                .onSuccess { item -> draftId = item.id; savedDraft = item.text; savedWarning = item.contentWarning.orEmpty(); draftError = null; onSaved() }
                .onFailure { draftError = "Draft could not be saved. Keep editing and try again." }
            closing = false
        }
    }
    fun closeComposer() { if (feedState?.publishing == true || closing) return; if (hasDraftChanges) saveCurrentDraft { overlayKey = null } else overlayKey = null }
    fun closeProfile() { if (feedState?.publishing == true) return; if (profileDirty) profileDialog = true else overlayKey = null }
    fun selectDestination(item: Destination) {
        if (item == Destination.Profile) viewedProfile = null
        destination = item
        page = null
        notificationRoute = null
    }
    fun openProfile(profile: Account) {
        viewedProfile = profile
        destination = Destination.Profile
        page = null
        sheet = null
        notificationRoute = null
    }

    fun openHashtagSearch(hashtag: String) {
        searchPrefill = hashtag
        searchPanelName = SearchPanel.Search.name
        destination = Destination.Search
        page = null
        onSearchAccounts(hashtag)
    }

    BackHandler(enabled = notificationRoute != null || overlay != null || page != null || destination != Destination.Home) {
        when {
            notificationRoute != null -> notificationRoute = null
            overlay == Overlay.Composer -> closeComposer()
            overlay == Overlay.EditProfile -> closeProfile()
            page != null -> page = null
            else -> destination = Destination.Home
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 600.dp
        Row(Modifier.fillMaxSize()) {
            if (wide) NavigationRail(Modifier.fillMaxHeight(), header = { FloatingActionButton(onClick = ::openComposer, modifier = Modifier.padding(vertical = 16.dp)) { Icon(AppIcons.Edit, "Compose post") } }) {
                Destination.entries.forEach { item -> NavigationRailItem(selected = destination == item, onClick = { selectDestination(item) }, icon = { Icon(item.icon, item.label) }, label = { Text(item.label) }) }
            }
            Box(Modifier.weight(1f).fillMaxHeight()) {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    // Compact Search owns the bottom inset together with its
                    // dock. Both it and the navigation overlay use window-bottom
                    // coordinates, including while system bars animate.
                    contentWindowInsets = if (!wide && destination == Destination.Search && page == null) {
                        WindowInsets.systemBars.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
                    } else {
                        ScaffoldDefaults.contentWindowInsets
                    },
                    topBar = {
                    when {
                        page != null -> TopAppBar(title = { Text(page!!) }, navigationIcon = { ActionIcon(AppIcons.Back, "Back") { page = null } })
                        notificationRoute != null -> TopAppBar(title = { Text("Notification") }, navigationIcon = { ActionIcon(AppIcons.Back, "Back") { notificationRoute = null } })
                        destination == Destination.Notifications -> TopAppBar(
                            title = { Text(if (notificationsPanel == NotificationsPanel.Notifications) "Notifications" else "Direct messages") },
                            actions = {
                                if (notificationsPanel == NotificationsPanel.Notifications && account != null) {
                                    ActionIcon(AppIcons.Check, "Mark all notifications read", onMarkAllNotificationsRead)
                                    ActionIcon(AppIcons.More, "Notification settings") {
                                        notificationRoute = AppRoute.NotificationSettings(account.id)
                                    }
                                }
                            },
                        )
                        destination == Destination.Profile -> TopAppBar(title = { Column { Text(displayedProfile?.displayName ?: "Your profile"); Text(displayedProfile?.handle ?: "0 posts", style = MaterialTheme.typography.bodyMedium, maxLines = 1) } }, actions = { if (displayedProfile?.id == account?.id) { ActionIcon(AppIcons.Bookmark, "Bookmarks") { page = "Bookmarks" }; ActionIcon(AppIcons.Folder, "Drafts") { page = "Drafts" }; ActionIcon(AppIcons.More, "Accounts") { sheet = "Accounts" } } })
                    }
                }) { padding ->
                    Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
                        if (notificationRoute is AppRoute.NotificationSettings) {
                            NotificationSettingsScreen(
                                state = notificationSettingsState,
                                onAlertsEnabled = onNotificationAlertsEnabled,
                                onShowPreviews = onNotificationShowPreviews,
                                onPeriodicFallback = onNotificationPeriodicFallback,
                                onQuietHours = onNotificationQuietHours,
                                onCategoryChanged = onNotificationCategoryChanged,
                                onRunLocalTest = onNotificationLocalTest,
                                onPermissionChanged = onNotificationPermissionChanged,
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else if (notificationRoute != null) {
                            NotificationDetailScreen(notificationRoute!!, notificationState.items, Modifier.fillMaxSize())
                        } else when (page) {
                            "Drafts" -> DraftsScreen(drafts, ::loadDraft, { item -> scope.launch { store.delete(account?.id, item.id); reloadDrafts() } })
                            "Bookmarks" -> EmptyState(AppIcons.Bookmark, if (account != null) "Bookmarks coming soon" else "No bookmarks yet", "Posts you save will appear here.")
                            "About" -> EmptyState(AppIcons.Globe, "A place for your fediverse", "Misskey and Sharkey home timelines. Publishing and other timelines are coming later.")
                            else -> screenStates.SaveableStateProvider(destination.name) { when (destination) {
                                Destination.Home -> if (feedState != null) HomeFeed(state = feedState, compactLayout = !wide, onRefresh = { onRefresh(timeline) }, onLoadMore = { onLoadMore(timeline) }, onSignIn = onSignOut, ownedPosts = ownedPosts ?: feedState.ownedPosts, onScrollDirectionChanged = { navigationVisible = it }, onReact = onReact, onReply = onReply, onReshare = onReshare, onBookmark = onBookmark, onReaction = onReaction, onOpenProfile = ::openProfile, onSearchHashtag = ::openHashtagSearch) else EmptyState(AppIcons.Home, "Your timeline starts here", "${timeline.name} posts will appear here when an account is connected.")
                                Destination.Search -> SearchScreen(searchPanel, feedState?.accountSearch ?: AccountSearchState(), onSearchAccounts, ::openProfile, onLoadMoreSearch, searchPrefill, compactLayout = !wide, compactNavigationVisible = !wide)
                                Destination.Notifications -> if (notificationsPanel == NotificationsPanel.Notifications) NotificationsScreen(
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
                                        notificationRoute = NotificationRouteResolver.resolve(notification)
                                    },
                                    onSelectQuery = onSelectNotificationQuery,
                                ) else MessagesScreen()
                                Destination.Profile -> ProfileScreen(displayedProfile, compactLayout = !wide)
                            } }
                        }
                    }
                }
                if (wide && page == null && !modalOverlayOpen && destination == Destination.Home) {
                    androidx.compose.animation.AnimatedVisibility(visible = navigationVisible, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 12.dp).zIndex(1f)) {
                        TimelineSelector(timeline) { sheet = "Timelines" }
                    }
                }
                if (!wide && page == null && !modalOverlayOpen) {
                    androidx.compose.animation.AnimatedVisibility(
                        visible = navigationVisible,
                        enter = fadeIn(),
                        exit = fadeOut(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .zIndex(1f),
                    ) {
                        Box(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBarsIgnoringVisibility.only(WindowInsetsSides.Bottom)).padding(horizontal = CompactOverlayHorizontalPadding, vertical = CompactOverlayVerticalPadding), contentAlignment = Alignment.Center) {
                            Column(
                                modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth(),
                                horizontalAlignment = Alignment.End,
                            ) {
                                if (destination == Destination.Home) {
                                    TimelineSelector(timeline) { sheet = "Timelines" }
                                    Spacer(Modifier.height(CompactOverlayControlSpacing))
                                }
                                CompactContextualNavigationBar(destination, contextualActionFor(destination, searchPanel, notificationsPanel, account == null || displayedProfile?.id == account.id, ::openComposer, { searchPanelName = if (searchPanel == SearchPanel.Search) SearchPanel.Alternate.name else SearchPanel.Search.name }, { notificationsPanelName = if (notificationsPanel == NotificationsPanel.Notifications) NotificationsPanel.DirectMessages.name else NotificationsPanel.Notifications.name }, { if (account != null && displayedProfile?.id == account.id) overlayKey = Overlay.EditProfile::class.simpleName }), account, { sheet = "Accounts" }) { selectDestination(it) }
                            }
                        }
                    }
                }
            }
        }
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
                TextButton(enabled = draft.isNotBlank() && !closing, onClick = { saveCurrentDraft { overlayKey = null } }) { Text("Save draft") }
            }
            ComposeScreen(text = draft, onTextChange = { draft = it }, warning = warning, onWarningChange = { warning = it }, warningEnabled = warningEnabled, onWarningEnabled = { warningEnabled = it }, account = account, canPublish = feedState?.canPublish == true && (draftId == null || drafts.firstOrNull { it.id == draftId }?.accountId == account?.id), publishing = feedState?.publishing == true, error = feedState?.error ?: draftError, onPublish = {
                val submittedText = draft; val submittedWarning = warning.takeIf { warningEnabled && it.isNotBlank() }
                scope.launch {
                    runCatching { val item = draftValue(); store.save(item); reloadDrafts(); item }.onSuccess { saved ->
                        draftId = saved.id; savedDraft = saved.text; savedWarning = saved.contentWarning.orEmpty()
                        onPublish(CreatePostRequest(submittedText, contentWarning = submittedWarning)) {
                            scope.launch { store.delete(account?.id, saved.id); reloadDrafts() }
                            draft = ""; savedDraft = ""; warning = ""; savedWarning = ""; warningEnabled = false; draftId = null; overlayKey = null
                        }
                    }.onFailure { draftError = "Draft could not be saved. Keep the composer open and try again." }
                }
            })
        }
    }

    if (overlay == Overlay.EditProfile && account != null) ModalBottomSheet(onDismissRequest = ::closeProfile, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        EditProfileScreen(account = account, displayName = profileName, biography = profileBiography, saving = feedState?.publishing == true, error = feedState?.error, onDisplayNameChange = { profileName = it }, onBiographyChange = { profileBiography = it }, onSave = { onUpdateProfile(UpdateProfileRequest(profileName.trim(), profileBiography)) { overlayKey = null } }, onClose = ::closeProfile)
    }

    if (profileDialog) AlertDialog(onDismissRequest = { profileDialog = false }, title = { Text("Discard profile changes?") }, text = { Text("Your changes have not been saved.") }, confirmButton = { TextButton(onClick = { profileDialog = false; overlayKey = null }) { Text("Discard") } }, dismissButton = { TextButton(onClick = { profileDialog = false }) { Text("Keep editing") } })
    if (signOutDialog) AlertDialog(onDismissRequest = { signOutDialog = false }, title = { Text("Sign out?") }, text = { Text("Your sign-in will be removed from this device. Local drafts will remain.") }, confirmButton = { TextButton(onClick = { signOutDialog = false; onSignOut() }) { Text("Sign out") } }, dismissButton = { TextButton(onClick = { signOutDialog = false }) { Text("Cancel") } })
}

@Preview(showBackground = true, device = "spec:width=411dp,height=891dp,dpi=420")
@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun AppPreview() { PalustrisApp() }
