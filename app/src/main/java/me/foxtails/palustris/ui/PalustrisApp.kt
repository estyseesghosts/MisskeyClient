@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package me.foxtails.palustris.ui

import android.content.Context
import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import me.foxtails.palustris.domain.Timeline
import me.foxtails.palustris.domain.Account

private enum class Destination(val label: String, val icon: ImageVector) {
    Home("Home", AppIcons.Home), Search("Search", AppIcons.Search),
    Notifications("Notifications", AppIcons.Notifications), Profile("Profile", AppIcons.Person),
}

@Composable
fun PalustrisApp(
    account: Account? = null,
    feedState: FeedState? = null,
    onRefresh: () -> Unit = {},
    onLoadMore: () -> Unit = {},
    onSignOut: () -> Unit = {},
) = PalustrisTheme {
    val context = LocalContext.current
    val preferences = remember { context.getSharedPreferences("local_draft", Context.MODE_PRIVATE) }
    var destination by rememberSaveable { mutableStateOf(Destination.Home) }
    var timeline by rememberSaveable { mutableStateOf(Timeline.Home) }
    var page by rememberSaveable { mutableStateOf<String?>(null) }
    var sheet by rememberSaveable { mutableStateOf<String?>(null) }
    var overflow by remember { mutableStateOf(false) }
    val screenStates = rememberSaveableStateHolder()
    var savedDraft by rememberSaveable { mutableStateOf(preferences.getString("text", "").orEmpty()) }
    var draft by rememberSaveable { mutableStateOf(savedDraft) }
    var savedWarning by rememberSaveable { mutableStateOf(preferences.getString("warning", "").orEmpty()) }
    var warning by rememberSaveable { mutableStateOf(savedWarning) }
    var warningEnabled by rememberSaveable { mutableStateOf(savedWarning.isNotEmpty()) }
    var discardDialog by rememberSaveable { mutableStateOf(false) }
    var signOutDialog by remember { mutableStateOf(false) }
    val hasChanges = draft != savedDraft || (if (warningEnabled) warning else "") != savedWarning
    val closeComposer = { if (hasChanges) discardDialog = true else page = null }

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
            Scaffold(
                modifier = Modifier.weight(1f),
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
                        destination == Destination.Home -> TopAppBar(
                            title = {
                                Row(Modifier.clickable { sheet = "Timelines" }.padding(vertical = 12.dp)) {
                                    Text(timeline.name)
                                    Spacer(Modifier.width(8.dp))
                                    Icon(AppIcons.Expand, "Choose timeline")
                                }
                            },
                            navigationIcon = { ActionIcon(AppIcons.Home, "Choose timeline", { sheet = "Timelines" }) },
                            actions = {
                                Box {
                                    ActionIcon(AppIcons.More, "More options", { overflow = true })
                                    DropdownMenu(expanded = overflow, onDismissRequest = { overflow = false }) {
                                        listOf("Bookmarks", "Drafts", "About").forEach { title ->
                                            DropdownMenuItem(text = { Text(title) }, onClick = { overflow = false; page = title })
                                        }
                                    }
                                }
                            },
                        )
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
                bottomBar = {
                    if (!wide && page != "Compose") Box(
                        Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Surface(
                            modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth(),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            shadowElevation = 6.dp,
                        ) {
                            NavigationBar(
                                modifier = Modifier.height(60.dp),
                                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                                windowInsets = WindowInsets(0, 0, 0, 0),
                            ) {
                                Destination.entries.forEachIndexed { index, item ->
                                    if (index == 2) Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                        FilledIconButton(onClick = { page = "Compose" }, modifier = Modifier.size(48.dp)) {
                                            Icon(AppIcons.Edit, "Compose post")
                                        }
                                    }
                                    NavigationBarItem(
                                        selected = destination == item,
                                        onClick = { destination = item; page = null },
                                        icon = { Icon(item.icon, contentDescription = item.label) },
                                        colors = NavigationBarItemDefaults.colors(
                                            selectedIconColor = MaterialTheme.colorScheme.primary,
                                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                            indicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                                        ),
                                    )
                                }
                            }
                        }
                    }
                },
            ) { padding ->
                Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
                    when (page) {
                        "Compose" -> ComposeScreen(draft, { draft = it }, warning, { warning = it }, warningEnabled, { warningEnabled = it }, account)
                        "Bookmarks" -> EmptyState(AppIcons.Bookmark, if (account != null) "Bookmarks coming soon" else "No bookmarks yet", "Posts you save will appear here.")
                        "Drafts" -> DraftsScreen(savedDraft, {
                            draft = savedDraft; warning = savedWarning; warningEnabled = savedWarning.isNotEmpty(); page = "Compose"
                        }, {
                            savedDraft = ""; draft = ""; savedWarning = ""; warning = ""; warningEnabled = false
                            preferences.edit().clear().apply()
                        })
                        "About" -> EmptyState(AppIcons.Globe, "A place for your fediverse", "Misskey and Sharkey home timelines. Publishing and other timelines are coming later.")
                        else -> screenStates.SaveableStateProvider(destination.name) { when (destination) {
                            Destination.Home -> if (feedState != null) HomeFeed(feedState, onRefresh, onLoadMore, onSignOut)
                                else EmptyState(AppIcons.Home, "Your timeline starts here", "${timeline.name} posts will appear here when an account is connected.")
                            Destination.Search -> SearchScreen()
                            Destination.Notifications -> NotificationsScreen(connected = account != null)
                            Destination.Profile -> ProfileScreen(account = account) { sheet = "Accounts" }
                        } }
                    }
                }
            }
        }
    }
    if (sheet != null) ModalBottomSheet(onDismissRequest = { sheet = null }) {
        Text(sheet!!, Modifier.padding(horizontal = 24.dp, vertical = 12.dp), style = MaterialTheme.typography.headlineSmall)
        if (sheet == "Timelines") {
            (if (account != null) listOf(Timeline.Home) else Timeline.entries).forEach { item ->
                ListItem(
                    modifier = Modifier.clickable { timeline = item; sheet = null },
                    headlineContent = { Text(item.name) },
                    supportingContent = { Text(when (item) {
                        Timeline.Home -> "Posts from people you follow"
                        Timeline.Local -> "Posts from your server"
                        Timeline.Federated -> "Posts from across the fediverse"
                    }) },
                    leadingContent = { Icon(if (item == Timeline.Home) AppIcons.Home else AppIcons.Globe, null) },
                    trailingContent = { if (timeline == item) Icon(AppIcons.Check, "Selected") },
                )
            }
            Text(if (account != null) "More timelines coming later" else "Timeline preview", Modifier.padding(24.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            ListItem(headlineContent = { Text(account?.displayName ?: "No accounts connected") },
                supportingContent = { Text(account?.handle ?: "Account connections are not available in this preview.") },
                leadingContent = { if (account != null) AccountAvatar(account, Modifier.size(48.dp)) else Avatar(Modifier.size(48.dp)) })
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
