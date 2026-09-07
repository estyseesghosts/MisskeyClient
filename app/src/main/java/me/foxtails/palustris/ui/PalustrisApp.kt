@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package me.foxtails.palustris.ui

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
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
import me.foxtails.palustris.domain.Timeline
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.CreatePostRequest
import me.foxtails.palustris.domain.OwnedPost
import me.foxtails.palustris.data.auth.AccountRef
import me.foxtails.palustris.data.auth.toAccount

private enum class Destination(val label: String, val icon: ImageVector) {
    Home("Home", AppIcons.Home), Search("Search", AppIcons.Search),
    Notifications("Notifications", AppIcons.Notifications), Profile("Profile", AppIcons.Person),
}

private data class ContextualBottomAction(
    val icon: ImageVector,
    val contentDescription: String,
    val enabled: Boolean,
    val onClick: () -> Unit,
)

private fun contextualActionFor(
    destination: Destination,
    onCompose: () -> Unit,
    onMessages: () -> Unit,
    onEditProfile: () -> Unit,
): ContextualBottomAction = when (destination) {
    Destination.Home -> ContextualBottomAction(AppIcons.Compose, "Compose post", true, onCompose)
    Destination.Search -> ContextualBottomAction(AppIcons.Unavailable, "Search action unavailable", false, {})
    Destination.Notifications -> ContextualBottomAction(AppIcons.Chat, "Direct messages", true, onMessages)
    Destination.Profile -> ContextualBottomAction(AppIcons.PersonEdit, "Edit profile", true, onEditProfile)
}

@Composable
private fun CompactContextualNavigationBar(
    destination: Destination,
    action: ContextualBottomAction,
    onDestinationSelected: (Destination) -> Unit,
) {
    Row(
        modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth().height(60.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            modifier = Modifier.weight(1f).fillMaxHeight(),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.85f),
            shadowElevation = 6.dp,
        ) {
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Destination.entries.forEach { item ->
                    val selected = destination == item
                    Box(
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (selected) {
                            Surface(
                                modifier = Modifier.size(40.dp),
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.secondaryContainer,
                            ) {}
                        }
                        IconButton(
                            onClick = { onDestinationSelected(item) },
                            modifier = Modifier.size(48.dp).semantics {
                                contentDescription = item.label
                                this.selected = selected
                                role = Role.Tab
                            },
                        ) {
                            Icon(
                                item.icon,
                                contentDescription = null,
                                tint = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
        FilledIconButton(
            onClick = action.onClick,
            enabled = action.enabled,
            modifier = Modifier.size(52.dp).semantics {
                contentDescription = action.contentDescription
            },
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        ) {
            Icon(action.icon, contentDescription = null)
        }
    }
}

@Composable
fun PalustrisApp(
    account: Account? = null,
    feedState: FeedState? = null,
    onRefresh: (Timeline) -> Unit = {},
    onLoadMore: (Timeline) -> Unit = {},
    onSignOut: () -> Unit = {},
    accounts: List<AccountRef> = emptyList(),
    onSwitchAccount: (AccountId) -> Unit = {},
    onAddAccount: () -> Unit = {},
    onPublish: (CreatePostRequest, () -> Unit) -> Unit = { _, onSuccess -> onSuccess() },
    ownedPosts: List<OwnedPost>? = null,
    onReact: (OwnedPost) -> Unit = {},
    onReply: (OwnedPost) -> Unit = {},
    onReshare: (OwnedPost) -> Unit = {},
    onBookmark: (OwnedPost) -> Unit = {},
    onReaction: (OwnedPost, String) -> Unit = { _, _ -> },
) = PalustrisTheme {
    val context = LocalContext.current
    val preferences = remember { context.getSharedPreferences("local_draft", Context.MODE_PRIVATE) }
    var destination by rememberSaveable { mutableStateOf(Destination.Home) }
    var timeline by rememberSaveable { mutableStateOf(Timeline.Home) }
    var page by rememberSaveable { mutableStateOf<String?>(null) }
    var sheet by rememberSaveable { mutableStateOf<String?>(null) }
    val screenStates = rememberSaveableStateHolder()
    var savedDraft by rememberSaveable { mutableStateOf(preferences.getString("text", "").orEmpty()) }
    var draft by rememberSaveable { mutableStateOf(savedDraft) }
    var savedWarning by rememberSaveable { mutableStateOf(preferences.getString("warning", "").orEmpty()) }
    var warning by rememberSaveable { mutableStateOf(savedWarning) }
    var warningEnabled by rememberSaveable { mutableStateOf(savedWarning.isNotEmpty()) }
    var discardDialog by rememberSaveable { mutableStateOf(false) }
    var signOutDialog by remember { mutableStateOf(false) }
    var navigationVisible by rememberSaveable { mutableStateOf(true) }
    val availableTimelines = if (account == null) Timeline.entries.toSet() else feedState?.timelines ?: setOf(Timeline.Home)
    val hasChanges = draft != savedDraft || (if (warningEnabled) warning else "") != savedWarning
    val closeComposer = { if (feedState?.publishing == true) Unit else if (hasChanges) discardDialog = true else page = null }

    LaunchedEffect(destination, page) { navigationVisible = true }
    LaunchedEffect(availableTimelines) {
        if (timeline !in availableTimelines) {
            timeline = Timeline.Home
        }
    }
    LaunchedEffect(feedState?.timeline, account?.id) {
        feedState?.timeline?.let { timeline = it }
    }

    BackHandler(enabled = page != null || destination != Destination.Home) {
        when {
            page == "Compose" -> closeComposer()
            page != null -> page = null
            else -> destination = Destination.Home
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 600.dp
        Row(Modifier.fillMaxSize()) {
            if (wide && page != "Compose") {
                NavigationRail(Modifier.fillMaxHeight(), header = {
                    FloatingActionButton(onClick = { page = "Compose" }, modifier = Modifier.padding(vertical = 16.dp)) {
                        Icon(AppIcons.Edit, "Compose post")
                    }
                }) {
                    Destination.entries.forEach { item ->
                        NavigationRailItem(selected = destination == item, onClick = { destination = item; page = null },
                            icon = { Icon(item.icon, item.label) }, label = { Text(item.label) })
                    }
                }
            }
            Box(Modifier.weight(1f).fillMaxHeight()) {
                Scaffold(
                modifier = Modifier.fillMaxSize(),
                topBar = {
                    when {
                        page == "Compose" -> TopAppBar(
                            title = { Text("New post") },
                            navigationIcon = { ActionIcon(AppIcons.Close, "Close composer", closeComposer) },
                            actions = {
                                TextButton(enabled = draft.isNotBlank(), onClick = {
                                    savedDraft = draft
                                    savedWarning = if (warningEnabled) warning else ""
                                    preferences.edit().putString("text", savedDraft).putString("warning", savedWarning).apply()
                                    page = "Drafts"
                                }) { Text("Save draft") }
                            },
                        )
                        page != null -> TopAppBar(title = { Text(page!!) }, navigationIcon = {
                            ActionIcon(AppIcons.Back, "Back", { page = null })
                        })
                        destination == Destination.Notifications -> TopAppBar(title = { Text("Notifications") })
                        destination == Destination.Profile -> TopAppBar(
                            title = { Column { Text(account?.displayName ?: "Your profile"); Text(account?.handle ?: "0 posts", style = MaterialTheme.typography.bodyMedium, maxLines = 1) } },
                            actions = {
                                ActionIcon(AppIcons.Bookmark, "Bookmarks", { page = "Bookmarks" })
                                ActionIcon(AppIcons.Folder, "Drafts", { page = "Drafts" })
                                ActionIcon(AppIcons.More, "Accounts", { sheet = "Accounts" })
                            },
                        )
                    }
                },
            ) { padding ->
                Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
                    when (page) {
                        "Compose" -> ComposeScreen(
                            text = draft,
                            onTextChange = { draft = it },
                            warning = warning,
                            onWarningChange = { warning = it },
                            warningEnabled = warningEnabled,
                            onWarningEnabled = { warningEnabled = it },
                            account = account,
                            canPublish = feedState?.canPublish == true,
                            publishing = feedState?.publishing == true,
                            error = feedState?.error,
                            onPublish = {
                                val submittedText = draft
                                val submittedWarning = warning.takeIf { warningEnabled }.orEmpty()
                                savedDraft = submittedText
                                savedWarning = submittedWarning
                                preferences.edit().putString("text", savedDraft).putString("warning", savedWarning).apply()
                                onPublish(CreatePostRequest(
                                    text = submittedText,
                                    contentWarning = submittedWarning.takeIf { it.isNotEmpty() },
                                )) {
                                    savedDraft = ""
                                    draft = ""
                                    savedWarning = ""
                                    warning = ""
                                    warningEnabled = false
                                    preferences.edit().clear().apply()
                                    page = null
                                }
                            },
                        )
                        "Messages" -> MessagesScreen()
                        "Edit profile" -> EditProfileScreen()
                        "Bookmarks" -> EmptyState(AppIcons.Bookmark, if (account != null) "Bookmarks coming soon" else "No bookmarks yet", "Posts you save will appear here.")
                        "Drafts" -> DraftsScreen(savedDraft, {
                            draft = savedDraft; warning = savedWarning; warningEnabled = savedWarning.isNotEmpty(); page = "Compose"
                        }, {
                            savedDraft = ""; draft = ""; savedWarning = ""; warning = ""; warningEnabled = false
                            preferences.edit().clear().apply()
                        })
                        "About" -> EmptyState(AppIcons.Globe, "A place for your fediverse", "Misskey and Sharkey home timelines. Publishing and other timelines are coming later.")
                        else -> screenStates.SaveableStateProvider(destination.name) { when (destination) {
                            Destination.Home -> if (feedState != null) HomeFeed(
                                state = feedState,
                                onRefresh = { onRefresh(timeline) },
                                onLoadMore = { onLoadMore(timeline) },
                                onSignIn = onSignOut,
                                ownedPosts = ownedPosts ?: feedState.ownedPosts,
                                onScrollDirectionChanged = { navigationVisible = it },
                                onReact = onReact,
                                onReply = onReply,
                                onReshare = onReshare,
                                onBookmark = onBookmark,
                                onReaction = onReaction,
                            )
                                else EmptyState(AppIcons.Home, "Your timeline starts here", "${timeline.name} posts will appear here when an account is connected.")
                            Destination.Search -> SearchScreen()
                            Destination.Notifications -> NotificationsScreen(connected = account != null)
                            Destination.Profile -> ProfileScreen(account = account) { sheet = "Accounts" }
                        } }
                    }
                }
            }
                if (page == null && destination == Destination.Home) {
                    androidx.compose.animation.AnimatedVisibility(
                        visible = navigationVisible,
                        enter = fadeIn(),
                        exit = fadeOut(),
                        modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 12.dp)
                            .width(168.dp).height(60.dp).zIndex(1f),
                    ) {
                        Surface(
                            modifier = Modifier.fillMaxSize().clickable { sheet = "Timelines" }
                                .semantics { contentDescription = "Choose timeline" },
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.85f),
                            shadowElevation = 6.dp,
                        ) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(
                                    timeline.name,
                                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp, fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }
                }
                if (!wide && page != "Compose") {
                    androidx.compose.animation.AnimatedVisibility(
                        visible = navigationVisible,
                        enter = fadeIn(),
                        exit = fadeOut(),
                        modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter).zIndex(1f),
                    ) {
                        Box(
                            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CompactContextualNavigationBar(
                                destination = destination,
                                action = contextualActionFor(
                                    destination = destination,
                                    onCompose = { page = "Compose" },
                                    onMessages = { page = "Messages" },
                                    onEditProfile = { page = "Edit profile" },
                                ),
                                onDestinationSelected = { selectedDestination ->
                                    destination = selectedDestination
                                    page = null
                                },
                            )
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
                ListItem(
                    modifier = Modifier.clickable {
                        val changed = item != timeline
                        timeline = item
                        sheet = null
                        if (changed) onRefresh(item)
                    },
                    headlineContent = { Text(item.name) },
                    supportingContent = { Text(when (item) {
                        Timeline.Home -> "Posts from people you follow"
                        Timeline.Local -> "Posts from your server"
                        Timeline.Social -> "Posts from your server and people it follows"
                        Timeline.Federated -> "Posts from across the fediverse"
                    }) },
                    leadingContent = { Icon(if (item == Timeline.Home) AppIcons.Home else AppIcons.Globe, null) },
                    trailingContent = { if (timeline == item) Icon(AppIcons.Check, "Selected") },
                )
            }
            Text(if (account != null) "Timelines are detected from this server" else "Timeline preview", Modifier.padding(24.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            accounts.forEach { accountRef ->
                val listedAccount = accountRef.toAccount()
                ListItem(
                    modifier = Modifier.clickable {
                        sheet = null
                        if (accountRef.accountId != account?.id) onSwitchAccount(accountRef.accountId)
                    },
                    headlineContent = { Text(accountRef.displayName) },
                    supportingContent = { Text(accountRef.handle) },
                    leadingContent = { AccountAvatar(listedAccount, Modifier.size(48.dp)) },
                    trailingContent = {
                        if (accountRef.accountId == account?.id) Icon(AppIcons.Check, "Current account")
                    },
                )
            }
            if (accounts.isEmpty()) ListItem(
                headlineContent = { Text(account?.displayName ?: "No accounts connected") },
                supportingContent = { Text(account?.handle ?: "Account connections are not available in this preview.") },
                leadingContent = { if (account != null) AccountAvatar(account, Modifier.size(48.dp)) else Avatar(Modifier.size(48.dp)) },
            )
            if (account != null) TextButton(
                onClick = { sheet = null; onAddAccount() },
                modifier = Modifier.padding(horizontal = 16.dp),
            ) { Text("Add account") }
            if (account != null) TextButton(onClick = { sheet = null; signOutDialog = true }, modifier = Modifier.padding(horizontal = 16.dp)) { Text("Sign out") }
            Spacer(Modifier.height(32.dp))
        }
    }
    if (signOutDialog) AlertDialog(onDismissRequest = { signOutDialog = false },
        title = { Text("Sign out?") }, text = { Text("Your sign-in will be removed from this device. Local drafts will remain.") },
        confirmButton = { TextButton(onClick = { signOutDialog = false; onSignOut() }) { Text("Sign out") } },
        dismissButton = { TextButton(onClick = { signOutDialog = false }) { Text("Cancel") } })
    if (discardDialog) AlertDialog(
        onDismissRequest = { discardDialog = false },
        title = { Text("Discard changes?") },
        text = { Text("Your unsaved changes will be lost. Any previously saved draft will remain.") },
        confirmButton = { TextButton(onClick = {
            draft = savedDraft; warning = savedWarning; warningEnabled = savedWarning.isNotEmpty()
            discardDialog = false; page = null
        }) { Text("Discard") } },
        dismissButton = { TextButton(onClick = { discardDialog = false }) { Text("Keep editing") } },
    )
}

@Preview(showBackground = true, device = "spec:width=411dp,height=891dp,dpi=420")
@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun AppPreview() { PalustrisApp() }
