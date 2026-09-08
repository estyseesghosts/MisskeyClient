package me.foxtails.palustris.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.OwnedPost
import me.foxtails.palustris.ui.components.CategoryChips

private val exactHashtagQuery = Regex("#[\\p{L}\\p{N}_](?:[\\p{L}\\p{N}\\p{M}_])*")

@Composable
@OptIn(ExperimentalLayoutApi::class)
fun SearchScreen(
    mode: SearchPanel = SearchPanel.Search,
    accountSearch: AccountSearchState = AccountSearchState(),
    onSearchAccounts: (String) -> Unit = {},
    onAccountClick: (Account) -> Unit = {},
    onLoadMoreSearch: () -> Unit = {},
    initialQuery: String = "",
    compactLayout: Boolean = true,
    compactNavigationVisible: Boolean = false,
) {
    if (mode == SearchPanel.Alternate) {
        EmptyState(AppIcons.WaffleGrid, "Alternate search", "A second search surface will be available in a future update.")
        return
    }
    var query by rememberSaveable { mutableStateOf("") }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(initialQuery) { if (initialQuery.isNotBlank()) query = initialQuery }
    val hashtagSearchRequested = query.trim().matches(exactHashtagQuery)
    val sections = listOf("Profiles", "Hashtags", "News", "For you")
    fun submitSearch() {
        if (query.isNotBlank()) onSearchAccounts(query)
    }
    // Keep these as live insets: the floating controls move during IME animation,
    // while the result viewport remains full-size and receives only scroll clearance.
    val controlsPositioningInsets = compactContextualControlsPositioningInsets(
        navigationVisible = compactNavigationVisible,
        ime = WindowInsets.ime,
    )
    val searchEndClearance = if (compactLayout) {
        compactScrollEndClearance(
            controlStackHeight = CompactSearchDockHeight,
            navigationVisible = compactNavigationVisible,
            ime = WindowInsets.ime,
        )
    } else {
        0.dp
    }

    if (!compactLayout) {
        Column(Modifier.fillMaxSize()) {
            SearchField(query, ::submitSearch) { query = it }
            CategoryChips(sections, tab, "Search categories; swipe horizontally for more") { tab = it }
            SearchContent(
                modifier = Modifier.weight(1f),
                query = query,
                tab = tab,
                hashtagSearchRequested = hashtagSearchRequested,
                accountSearch = accountSearch,
                onAccountClick = onAccountClick,
                onLoadMoreSearch = onLoadMoreSearch,
                endClearance = 0.dp,
            )
        }
    } else {
        Box(Modifier.fillMaxSize()) {
            SearchContent(
                modifier = Modifier
                    .fillMaxSize(),
                query = query,
                tab = tab,
                hashtagSearchRequested = hashtagSearchRequested,
                accountSearch = accountSearch,
                onAccountClick = onAccountClick,
                onLoadMoreSearch = onLoadMoreSearch,
                endClearance = searchEndClearance,
            )
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(),
            ) {
                Column(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = CompactOverlayHorizontalPadding)
                        .windowInsetsPadding(controlsPositioningInsets),
                    verticalArrangement = Arrangement.spacedBy(CompactSearchControlsSpacing),
                ) {
                    CategoryChips(sections, tab, "Search categories; swipe horizontally for more") { tab = it }
                    SearchField(query, ::submitSearch) { query = it }
                }
            }
        }
    }
}

@Composable
private fun SearchContent(
    modifier: Modifier,
    query: String,
    tab: Int,
    hashtagSearchRequested: Boolean,
    accountSearch: AccountSearchState,
    onAccountClick: (Account) -> Unit,
    onLoadMoreSearch: () -> Unit,
    endClearance: Dp,
) {
    Box(modifier.testTag("search_content")) {
        if (tab == 0 || hashtagSearchRequested) {
            if (hashtagSearchRequested) HashtagSearchResults(accountSearch, query, onLoadMoreSearch, endClearance)
            else AccountSearchResults(query, accountSearch, onAccountClick, endClearance)
        } else {
            EmptyState(
                AppIcons.Tag,
                if (query.isNotBlank()) "Search is ready when you are" else when (tab) {
                    1 -> "Explore hashtags"
                    2 -> "News from your network"
                    else -> "Find your people"
                },
                if (query.isNotBlank()) "Account search is available from the Profiles tab."
                else when (tab) {
                    1 -> "Trending topics will appear here."
                    2 -> "Popular links will appear here."
                    else -> "Suggested accounts will appear here."
                },
            )
        }
    }
}

@Composable
private fun SearchField(query: String, onSubmit: () -> Unit, onQueryChange: (String) -> Unit) {
    TextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = "Search field" },
        placeholder = { Text("Search by @handle@server") },
        leadingIcon = { Icon(AppIcons.Search, null) },
        trailingIcon = { if (query.isNotEmpty()) ActionIcon(AppIcons.Close, "Clear search", { onQueryChange("") }) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
        shape = CircleShape,
        colors = TextFieldDefaults.colors(
            focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
            unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    )
}

@Composable
private fun HashtagSearchResults(
    state: AccountSearchState,
    query: String,
    onLoadMore: () -> Unit,
    endClearance: Dp,
) {
    when {
        state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        state.error != null -> EmptyState(AppIcons.Search, "Hashtag search failed", state.error)
        state.posts.isNotEmpty() && state.query == query.trim() -> LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp + endClearance),
        ) {
            items(state.posts, key = { "${it.id.connection}/${it.id.value}" }) { post ->
                PostRow(
                    ownedPost = OwnedPost(post.author.id, post),
                    availableActions = emptySet(),
                    onReact = {}, onReply = {}, onReshare = {}, onBookmark = {}, onReaction = { _, _ -> }, onOpenProfile = {},
                    onSearchHashtag = {},
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f))
            }
            item {
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    if (state.loadingMore) CircularProgressIndicator(Modifier.size(24.dp))
                    else if (state.nextCursor != null) TextButton(onClick = onLoadMore) { Text("Load older posts") }
                    else Text("You're up to date", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        query.isBlank() -> EmptyState(AppIcons.Tag, "Find a hashtag", "Enter an exact tag such as #photography and press enter.")
        state.query == query.trim() -> EmptyState(AppIcons.Tag, "No posts found", "No recent posts use ${state.query}.")
        else -> EmptyState(AppIcons.Tag, "Hashtag search is ready", "Press enter to find recent posts using this tag.")
    }
}

@Composable
private fun AccountSearchResults(
    query: String,
    state: AccountSearchState,
    onAccountClick: (Account) -> Unit,
    endClearance: Dp,
) {
    when {
        state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        state.error != null -> EmptyState(AppIcons.Search, "Account search failed", state.error)
        state.accounts.isNotEmpty() -> LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 8.dp, end = 8.dp, bottom = endClearance),
        ) {
            items(state.accounts, key = { "${it.id.connection.origin}/${it.id.localId}" }) { account ->
                ListItem(
                    modifier = Modifier.clickable { onAccountClick(account) },
                    headlineContent = { Text(account.displayName) },
                    supportingContent = { Text(account.handle) },
                    leadingContent = { AccountAvatar(account, Modifier.size(48.dp)) },
                )
            }
        }
        query.isBlank() -> EmptyState(AppIcons.Search, "Find an account", "Enter a webfinger handle and press enter.")
        state.query == query.trim() -> EmptyState(AppIcons.Search, "No account found", "Try a complete handle such as @user@example.org.")
        else -> EmptyState(AppIcons.Search, "Search is ready", "Press enter to look up this account.")
    }
}

@Composable
fun MessagesScreen() {
    EmptyState(AppIcons.Chat, "Direct messages coming soon", "Private conversations will be available in a future update.")
}

@Composable
fun EditProfileScreen(
    account: Account,
    displayName: String,
    biography: String,
    saving: Boolean,
    error: String?,
    onDisplayNameChange: (String) -> Unit,
    onBiographyChange: (String) -> Unit,
    onSave: () -> Unit,
    onClose: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Edit profile", style = MaterialTheme.typography.headlineSmall)
            TextButton(onClick = onClose) { Text("Close") }
        }
        Text(account.handle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(displayName, onDisplayNameChange, label = { Text("Display name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(account.handle, {}, label = { Text("Handle") }, modifier = Modifier.fillMaxWidth(), enabled = false, singleLine = true)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(biography, onBiographyChange, label = { Text("Biography") }, modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp), minLines = 4)
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 12.dp)) }
        Spacer(Modifier.height(16.dp))
        Button(onClick = onSave, enabled = !saving && displayName.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text(if (saving) "Saving…" else "Save profile") }
        Spacer(Modifier.height(24.dp))
    }
}

private val profileCategories = listOf("Posts", "Media", "Reposts", "Replies", "Show more...")

private val profilePlaceholderCopy = listOf(
    "Posts coming soon" to "Posts from this profile will appear here.",
    "Media coming soon" to "Photos and videos from this profile will appear here.",
    "Reposts coming soon" to "Reposts from this profile will appear here.",
    "Replies coming soon" to "Replies from this profile will appear here.",
    "More profile views coming soon" to "Additional profile views will appear here.",
)

private const val ProfileCategoryDescription = "Profile categories; swipe horizontally for more"

@Composable
@OptIn(ExperimentalLayoutApi::class)
fun ProfileScreen(account: Account? = null, compactLayout: Boolean = true) {
    val profileIdentity = account?.id?.let { "${it.connection.origin}\u0000${it.localId}" } ?: "preview"
    key(profileIdentity) {
        var selectedCategory by rememberSaveable(profileIdentity) { mutableIntStateOf(0) }
        val controlsPositioningInsets = compactContextualControlsPositioningInsets(navigationVisible = compactLayout)
        val profileEndClearance = if (compactLayout) {
            compactScrollEndClearance(
                controlStackHeight = CompactFilterDockHeight,
                navigationVisible = true,
            )
        } else {
            0.dp
        }

        if (compactLayout) {
            Box(Modifier.fillMaxSize()) {
                ProfileContent(
                    account = account,
                    selectedCategory = selectedCategory,
                    showCategoryChips = false,
                    onCategorySelected = { selectedCategory = it },
                    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                    endContentClearance = profileEndClearance,
                )
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = CompactOverlayHorizontalPadding)
                        .windowInsetsPadding(controlsPositioningInsets),
                ) {
                    CategoryChips(profileCategories, selectedCategory, ProfileCategoryDescription) { selectedCategory = it }
                }
            }
        } else {
            ProfileContent(
                account = account,
                selectedCategory = selectedCategory,
                showCategoryChips = true,
                onCategorySelected = { selectedCategory = it },
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                endContentClearance = 0.dp,
            )
        }
    }
}

@Composable
private fun ProfileContent(
    account: Account?,
    selectedCategory: Int,
    showCategoryChips: Boolean,
    onCategorySelected: (Int) -> Unit,
    modifier: Modifier,
    endContentClearance: Dp,
) {
    Column(modifier.testTag("profile_content")) {
        Box(Modifier.fillMaxWidth().height(220.dp)) {
            Box(Modifier.fillMaxWidth().height(144.dp).background(MaterialTheme.colorScheme.surfaceContainerHighest))
            Surface(Modifier.padding(start = 16.dp).offset(y = 100.dp).size(112.dp),
                shape = CircleShape, color = MaterialTheme.colorScheme.surface) {
                if (account != null) AccountAvatar(account, Modifier.padding(4.dp)) else Avatar(Modifier.padding(4.dp))
            }
        }
        Column(Modifier.padding(horizontal = 16.dp)) {
            Text(account?.displayName ?: "Your profile", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(4.dp))
            Text(account?.handle ?: "No account selected", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(24.dp))
            Text(
                account?.biography?.ifBlank { "No bio" } ?: "Your bio, links, and profile details will appear here.",
                modifier = Modifier.testTag("profile_biography"),
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                if (account == null) {
                    Text("0 followers", style = MaterialTheme.typography.labelLarge)
                    Text("0 following", style = MaterialTheme.typography.labelLarge)
                }
            }
            Spacer(Modifier.height(24.dp))
        }
        if (showCategoryChips) {
            CategoryChips(profileCategories, selectedCategory, ProfileCategoryDescription, onSelect = onCategorySelected)
        }
        Box(Modifier.fillMaxWidth().heightIn(min = 280.dp)) {
            val copy = profilePlaceholderCopy[selectedCategory]
            EmptyState(
                if (selectedCategory == 1) AppIcons.Image else AppIcons.Person,
                copy.first,
                copy.second,
            )
        }
        // This spacer is inside verticalScroll, so the viewport still extends
        // behind the compact category/navigation overlay at rest.
        Spacer(Modifier.height(endContentClearance))
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
    quoteTarget: OwnedPost? = null,
    onRemoveQuote: () -> Unit = {},
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
        quoteTarget?.let { target ->
            OutlinedCard(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
                Column(Modifier.padding(12.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Quoting ${target.post.author.displayName}", style = MaterialTheme.typography.titleSmall)
                        TextButton(onClick = onRemoveQuote) { Text("Remove") }
                    }
                    Text(target.post.text.ifBlank { "This post has no text." }, maxLines = 4, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.height(12.dp))
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
fun DraftsScreen(drafts: List<me.foxtails.palustris.domain.PostDraft>, onEdit: (me.foxtails.palustris.domain.PostDraft) -> Unit, onDelete: (me.foxtails.palustris.domain.PostDraft) -> Unit) {
    var confirmDelete by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<me.foxtails.palustris.domain.PostDraft?>(null) }
    if (drafts.isEmpty()) EmptyState(AppIcons.Folder, "No drafts yet", "Save a post while composing to finish it later.")
    else Column(Modifier.fillMaxSize().padding(16.dp)) {
        drafts.forEach { draft ->
            ElevatedCard(onClick = { onEdit(draft) }, modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                Column(Modifier.padding(20.dp)) {
                    Text("Draft", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(12.dp))
                    Text(draft.text.ifBlank { draft.contentWarning.orEmpty() }, maxLines = 5, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(12.dp))
                    Text("Tap to continue editing", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = { pendingDelete = draft; confirmDelete = true }, modifier = Modifier.align(Alignment.End)) { Text("Delete draft") }
                }
            }
        }
    }
    if (confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false },
        title = { Text("Delete draft?") }, text = { Text("This draft will be removed from this device.") },
        confirmButton = { TextButton(onClick = { confirmDelete = false; pendingDelete?.let(onDelete); pendingDelete = null }) { Text("Delete") } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } })
}
