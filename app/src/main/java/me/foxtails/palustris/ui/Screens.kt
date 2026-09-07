package me.foxtails.palustris.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.foxtails.palustris.domain.Account

@Composable
fun SearchScreen() {
    var query by rememberSaveable { mutableStateOf("") }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val sections = listOf("Posts", "Hashtags", "News", "For you")
    Column(Modifier.fillMaxSize()) {
        TextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            placeholder = { Text("Search the Fediverse") },
            leadingIcon = { Icon(AppIcons.Search, null) },
            trailingIcon = { if (query.isNotEmpty()) ActionIcon(AppIcons.Close, "Clear search", { query = "" }) },
            singleLine = true,
            shape = CircleShape,
            colors = TextFieldDefaults.colors(
                focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
        )
        SectionTabs(sections, tab) { tab = it }
        EmptyState(
            if (tab == 1) AppIcons.Tag else AppIcons.Search,
            if (query.isNotBlank()) "Search is ready when you are" else when (tab) {
                0 -> "Discover conversations"
                1 -> "Explore hashtags"
                2 -> "News from your network"
                else -> "Find your people"
            },
            if (query.isNotBlank()) "Searching the fediverse will be available in a future update."
            else when (tab) {
                0 -> "Trending posts will appear here."
                1 -> "Trending topics will appear here."
                2 -> "Popular links will appear here."
                else -> "Suggested accounts will appear here."
            },
        )
    }
}

@Composable
fun NotificationsScreen(connected: Boolean = false) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        SectionTabs(listOf("All", "Mentions"), tab) { tab = it }
        EmptyState(AppIcons.Notifications, if (connected) "Notifications coming soon" else if (tab == 0) "All caught up" else "No mentions yet",
            if (tab == 0) "Replies, reactions, and new followers will appear here." else "Conversations that mention you will appear here.")
    }
}

@Composable
fun ProfileScreen(account: Account? = null, onAccounts: () -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Box(Modifier.fillMaxWidth().height(220.dp)) {
            Box(Modifier.fillMaxWidth().height(144.dp).background(MaterialTheme.colorScheme.surfaceContainerHighest))
            Surface(Modifier.padding(start = 16.dp).offset(y = 100.dp).size(112.dp),
                shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surface) {
                if (account != null) AccountAvatar(account, Modifier.padding(4.dp)) else Avatar(Modifier.padding(4.dp))
            }
            FilledTonalButton(onClick = onAccounts, modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 16.dp)) {
                Text("View accounts")
            }
        }
        Column(Modifier.padding(horizontal = 16.dp)) {
            Text(account?.displayName ?: "Your profile", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(4.dp))
            Text(account?.handle ?: "No account selected", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(24.dp))
            Text(account?.biography?.ifBlank { "No bio" } ?: "Your bio, links, and profile details will appear here.", style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                if (account == null) {
                    Text("0 followers", style = MaterialTheme.typography.labelLarge)
                    Text("0 following", style = MaterialTheme.typography.labelLarge)
                }
            }
            Spacer(Modifier.height(24.dp))
        }
        SectionTabs(listOf("Posts", "Replies", "Media", "About"), tab) { tab = it }
        Box(Modifier.fillMaxWidth().heightIn(min = 280.dp)) {
            if (account != null) EmptyState(AppIcons.Person, "Profile timeline coming soon", "For now, your conversations are in the Home tab.")
            else EmptyState(if (tab == 2) AppIcons.Image else AppIcons.Person,
                when (tab) { 0 -> "No posts yet"; 1 -> "No replies yet"; 2 -> "No media yet"; else -> "A little about you" },
                when (tab) { 0 -> "Your posts will appear here."; 1 -> "Your replies will appear here."; 2 -> "Photos and videos you share will appear here."; else -> "Profile information will appear here." })
        }
        Spacer(Modifier.height(80.dp))
    }
}

@Composable
fun ComposeScreen(
    text: String, onTextChange: (String) -> Unit,
    warning: String, onWarningChange: (String) -> Unit,
    warningEnabled: Boolean, onWarningEnabled: (Boolean) -> Unit,
    account: Account? = null,
    canPublish: Boolean = false,
    publishing: Boolean = false,
    error: String? = null,
    onPublish: () -> Unit = {},
) {
    Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        Row(Modifier.padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            if (account != null) AccountAvatar(account, Modifier.size(48.dp)) else Avatar(Modifier.size(48.dp))
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Local draft", style = MaterialTheme.typography.titleMedium)
                Text(account?.handle ?: "No account selected", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (warningEnabled) OutlinedTextField(
            value = warning, onValueChange = onWarningChange,
            label = { Text("Content warning") }, modifier = Modifier.fillMaxWidth(),
        )
        BasicTextField(
            value = text, onValueChange = onTextChange,
            modifier = Modifier.fillMaxWidth().heightIn(min = 240.dp).padding(vertical = 24.dp).semantics { contentDescription = "Post text" },
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            decorationBox = { field -> Box { if (text.isEmpty()) Text("What's on your mind?", color = MaterialTheme.colorScheme.onSurfaceVariant); field() } },
        )
        HorizontalDivider()
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            FilterChip(selected = warningEnabled, onClick = { onWarningEnabled(!warningEnabled) }, label = { Text("Content warning") })
            Text("${text.length} characters", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(if (canPublish) "Posts will be published to your connected account." else "Publishing is disabled for this account.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (error != null) {
            Spacer(Modifier.height(12.dp))
            Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(24.dp))
        Button(onClick = onPublish, enabled = canPublish && !publishing && text.isNotBlank(), modifier = Modifier.align(Alignment.End)) {
            Text(if (publishing) "Publishing…" else "Publish")
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun DraftsScreen(draft: String, onEdit: () -> Unit, onDelete: () -> Unit) {
    var confirmDelete by remember { mutableStateOf(false) }
    if (draft.isBlank()) EmptyState(AppIcons.Folder, "No drafts yet", "Save a post while composing to finish it later.")
    else Column(Modifier.fillMaxSize().padding(16.dp)) {
        ElevatedCard(onClick = onEdit, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp)) {
                Text("Local draft", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(12.dp))
                Text(draft, maxLines = 5, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(12.dp))
                Text("Tap to continue editing", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        TextButton(onClick = { confirmDelete = true }, modifier = Modifier.align(Alignment.End)) { Text("Delete draft") }
    }
    if (confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false },
        title = { Text("Delete draft?") }, text = { Text("This draft will be removed from this device.") },
        confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("Delete") } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } })
}
