package me.foxtails.palustris.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.R
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.EmojiChoice
import me.foxtails.palustris.domain.OwnedPost
import me.foxtails.palustris.domain.PostAction
import me.foxtails.palustris.ui.components.CategoryChips
import me.foxtails.palustris.ui.media.MediaOpenRequest
import me.foxtails.palustris.ui.motion.AnimatedStatePane
import me.foxtails.palustris.ui.motion.ExpandableContent
import me.foxtails.palustris.ui.motion.LocalPalustrisMotionScheme
import me.foxtails.palustris.ui.motion.springPress
import me.foxtails.palustris.ui.large.LargeBottomDock
import me.foxtails.palustris.ui.large.LargeBottomDockClearance
import me.foxtails.palustris.ui.large.LargeSearchDockClearance

private val exactHashtagQuery = Regex("#[\\p{L}\\p{N}_](?:[\\p{L}\\p{N}\\p{M}_])*")

@Composable
@OptIn(ExperimentalLayoutApi::class)
fun SearchScreen(
    mode: SearchPanel = SearchPanel.Search,
    accountSearch: AccountSearchState = AccountSearchState(),
    onSearchAccounts: (String) -> Unit = {},
    onAccountClick: (Account) -> Unit = {},
    availableActions: Set<PostAction> = emptySet(),
    onReact: (OwnedPost) -> Unit = {},
    onReply: (OwnedPost) -> Unit = {},
    onReshare: (OwnedPost) -> Unit = {},
    onBookmark: (OwnedPost) -> Unit = {},
    onReaction: (OwnedPost, EmojiChoice) -> Unit = { _, _ -> },
    onOpenReactionBubble: ((OwnedPost, Rect) -> Unit)? = null,
    onOpenReactionPicker: (OwnedPost) -> Unit = {},
    quoteEnabled: Boolean = false,
    onQuote: (OwnedPost) -> Unit = {},
    onSearchHashtag: (String) -> Unit = {},
    onOpenHashtagBubble: ((OwnedPost, List<String>, Rect) -> Unit)? = null,
    onLoadMoreSearch: () -> Unit = {},
    initialQuery: String = "",
    compactLayout: Boolean = true,
    compactNavigationVisible: Boolean = false,
    mediaOwner: AccountId? = null,
    onOpenMedia: (MediaOpenRequest) -> Unit = {},
    onOpenPost: (OwnedPost) -> Unit = {},
    onOpenUrl: ((String) -> Unit)? = null,
    onOpenUsername: ((String) -> Unit)? = null,
    largeLayout: Boolean = false,
    sharedQuery: String? = null,
    sharedTab: Int? = null,
    onSharedQueryChange: (String) -> Unit = {},
    onSharedTabChange: (Int) -> Unit = {},
    listState: LazyListState? = null,
) {
    if (mode == SearchPanel.Alternate) {
        EmptyState(AppIcons.WaffleGrid, stringResource(R.string.search_alternate_title), stringResource(R.string.search_alternate_subtitle))
        return
    }
    var localQuery by rememberSaveable { mutableStateOf("") }
    var localTab by rememberSaveable { mutableIntStateOf(0) }
    var largeDockHeightPx by remember { mutableIntStateOf(0) }
    val largeDockClearance = if (largeDockHeightPx > 0) {
        with(LocalDensity.current) { largeDockHeightPx.toDp() }
    } else {
        LargeSearchDockClearance
    }
    val query = sharedQuery ?: localQuery
    val tab = sharedTab ?: localTab
    fun updateQuery(value: String) {
        if (sharedQuery == null) localQuery = value else onSharedQueryChange(value)
    }
    fun updateTab(value: Int) {
        if (sharedTab == null) localTab = value else onSharedTabChange(value)
    }
    LaunchedEffect(initialQuery, sharedQuery == null) {
        if (sharedQuery == null && initialQuery.isNotBlank()) updateQuery(initialQuery)
    }
    val hashtagSearchRequested = query.trim().matches(exactHashtagQuery)
    val sections = listOf(
        stringResource(R.string.search_category_profiles),
        stringResource(R.string.search_category_hashtags),
        stringResource(R.string.search_category_news),
        stringResource(R.string.search_category_for_you),
    )
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

    if (largeLayout) {
        Box(Modifier.fillMaxSize()) {
            SearchContent(
                modifier = Modifier.fillMaxSize(),
                query = query,
                tab = tab,
                hashtagSearchRequested = hashtagSearchRequested,
                accountSearch = accountSearch,
                onAccountClick = onAccountClick,
                availableActions = availableActions,
                onReact = onReact,
                onReply = onReply,
                onReshare = onReshare,
                onBookmark = onBookmark,
                onReaction = onReaction,
                onOpenReactionBubble = onOpenReactionBubble,
                onOpenReactionPicker = onOpenReactionPicker,
                quoteEnabled = quoteEnabled,
                onQuote = onQuote,
                onLoadMoreSearch = onLoadMoreSearch,
                 endClearance = largeDockClearance,
                mediaOwner = mediaOwner,
                onOpenMedia = onOpenMedia,
                onOpenPost = onOpenPost,
                onOpenUrl = onOpenUrl,
                onOpenUsername = onOpenUsername,
                onSearchHashtag = onSearchHashtag,
                onOpenHashtagBubble = onOpenHashtagBubble,
                listState = listState,
                largeLayout = largeLayout,
            )
            LargeBottomDock(
                content = {
                    Column(Modifier.widthIn(max = 520.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(CompactSearchControlsSpacing)) {
                        CategoryChips(sections, tab, stringResource(R.string.search_categories_description), ::updateTab)
                        SearchField(query, ::submitSearch, ::updateQuery)
                    }
                },
                 modifier = Modifier
                     .align(Alignment.BottomStart)
                     .onSizeChanged { largeDockHeightPx = it.height },
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
                availableActions = availableActions,
                onReact = onReact,
                onReply = onReply,
                onReshare = onReshare,
                onBookmark = onBookmark,
                onReaction = onReaction,
                onOpenReactionBubble = onOpenReactionBubble,
                onOpenReactionPicker = onOpenReactionPicker,
                quoteEnabled = quoteEnabled,
                onQuote = onQuote,
                onLoadMoreSearch = onLoadMoreSearch,
                endClearance = searchEndClearance,
                mediaOwner = mediaOwner,
                onOpenMedia = onOpenMedia,
                onOpenPost = onOpenPost,
                onOpenUrl = onOpenUrl,
                onOpenUsername = onOpenUsername,
                onSearchHashtag = onSearchHashtag,
                 onOpenHashtagBubble = onOpenHashtagBubble,
                 listState = listState,
                 largeLayout = false,
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
                    CategoryChips(sections, tab, stringResource(R.string.search_categories_description), ::updateTab)
                    SearchField(query, ::submitSearch, ::updateQuery)
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
    availableActions: Set<PostAction>,
    onReact: (OwnedPost) -> Unit,
    onReply: (OwnedPost) -> Unit,
    onReshare: (OwnedPost) -> Unit,
    onBookmark: (OwnedPost) -> Unit,
    onReaction: (OwnedPost, EmojiChoice) -> Unit,
    onOpenReactionBubble: ((OwnedPost, Rect) -> Unit)?,
    onOpenReactionPicker: (OwnedPost) -> Unit,
    quoteEnabled: Boolean,
    onQuote: (OwnedPost) -> Unit,
    onLoadMoreSearch: () -> Unit,
    endClearance: Dp,
    mediaOwner: AccountId?,
    onOpenMedia: (MediaOpenRequest) -> Unit,
    onOpenPost: (OwnedPost) -> Unit,
    onOpenUrl: ((String) -> Unit)?,
    onOpenUsername: ((String) -> Unit)?,
    onSearchHashtag: (String) -> Unit,
    onOpenHashtagBubble: ((OwnedPost, List<String>, Rect) -> Unit)?,
    listState: LazyListState?,
    largeLayout: Boolean,
) {
    Box(modifier.testTag("search_content")) {
        val queryKind = when {
            hashtagSearchRequested -> "hashtag"
            query.isBlank() -> "blank"
            else -> "account"
        }
        val resultKind = when {
            accountSearch.loading -> "loading"
            accountSearch.error != null -> "error"
            accountSearch.accounts.isNotEmpty() || accountSearch.posts.isNotEmpty() -> "results"
            else -> "empty"
        }
        AnimatedStatePane(
            stateKey = "$tab:$queryKind:$resultKind",
            modifier = Modifier.fillMaxSize(),
        ) {
            if (tab == 0 || hashtagSearchRequested) {
                if (hashtagSearchRequested) HashtagSearchResults(
                    state = accountSearch,
                    query = query,
                    onLoadMore = onLoadMoreSearch,
                    onAccountClick = onAccountClick,
                    availableActions = availableActions,
                    onReact = onReact,
                    onReply = onReply,
                    onReshare = onReshare,
                    onBookmark = onBookmark,
                    onReaction = onReaction,
                    onOpenReactionBubble = onOpenReactionBubble,
                    onOpenReactionPicker = onOpenReactionPicker,
                    quoteEnabled = quoteEnabled,
                    onQuote = onQuote,
                    endClearance = endClearance,
                    mediaOwner = mediaOwner,
                    onOpenMedia = onOpenMedia,
                    onSearchHashtag = onSearchHashtag,
                     onOpenHashtagBubble = onOpenHashtagBubble,
                     listState = listState,
                     largeLayout = largeLayout,
                       onOpenPost = onOpenPost,
                      onOpenUrl = onOpenUrl,
                      onOpenUsername = onOpenUsername,
                ) else AccountSearchResults(
                    query = query,
                    state = accountSearch,
                    onAccountClick = onAccountClick,
                    endClearance = endClearance,
                    listState = listState,
                )
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
}

@Composable
private fun SearchField(query: String, onSubmit: () -> Unit, onQueryChange: (String) -> Unit) {
    val scheme = LocalPalustrisMotionScheme.current
    val searchFieldDescription = stringResource(R.string.search_field)
    TextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = searchFieldDescription },
        placeholder = { Text(stringResource(R.string.search_placeholder_handle)) },
        leadingIcon = { Icon(AppIcons.Search, null) },
        trailingIcon = {
            AnimatedContent(
                targetState = query.isNotEmpty(),
                transitionSpec = {
                    if (scheme.reducedMotion) EnterTransition.None togetherWith ExitTransition.None
                    else (fadeIn(scheme.fastFadeIn) + scaleIn(initialScale = 0.86f, animationSpec = scheme.expressive)) togetherWith
                        (fadeOut(scheme.fastFadeOut) + scaleOut(targetScale = 0.86f, animationSpec = scheme.expressive))
                },
                label = "searchClearVisibility",
            ) { visible ->
                if (visible) ActionIcon(AppIcons.Close, "Clear search", { onQueryChange("") })
            }
        },
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
    onAccountClick: (Account) -> Unit,
    availableActions: Set<PostAction>,
    onReact: (OwnedPost) -> Unit,
    onReply: (OwnedPost) -> Unit,
    onReshare: (OwnedPost) -> Unit,
    onBookmark: (OwnedPost) -> Unit,
    onReaction: (OwnedPost, EmojiChoice) -> Unit,
    onOpenReactionBubble: ((OwnedPost, Rect) -> Unit)?,
    onOpenReactionPicker: (OwnedPost) -> Unit,
    quoteEnabled: Boolean,
    onQuote: (OwnedPost) -> Unit,
    endClearance: Dp,
    mediaOwner: AccountId?,
    onOpenMedia: (MediaOpenRequest) -> Unit,
    onSearchHashtag: (String) -> Unit,
    onOpenHashtagBubble: ((OwnedPost, List<String>, Rect) -> Unit)?,
    onOpenPost: (OwnedPost) -> Unit,
    onOpenUrl: ((String) -> Unit)?,
    onOpenUsername: ((String) -> Unit)?,
    listState: LazyListState?,
    largeLayout: Boolean,
) {
    val scheme = LocalPalustrisMotionScheme.current
    when {
        state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        state.error != null -> EmptyState(AppIcons.Search, stringResource(R.string.search_hashtag_failed), state.error)
        state.posts.isNotEmpty() && state.query == query.trim() -> LazyColumn(
            state = listState ?: androidx.compose.foundation.lazy.rememberLazyListState(),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp + endClearance),
        ) {
            items(state.posts, key = { "${it.id.connection}/${it.id.value}" }) { post ->
                Column(
                    Modifier.animateItem(
                        fadeInSpec = scheme.fastFadeIn,
                        fadeOutSpec = scheme.fastFadeOut,
                        placementSpec = scheme.gentleOffset,
                    ),
                ) {
                    PostRow(
                        ownedPost = OwnedPost(mediaOwner ?: post.author.id, post),
                        availableActions = availableActions.intersect(ClientReadyPostActions),
                        onReact = onReact,
                        onReply = onReply,
                        onReshare = onReshare,
                        onBookmark = onBookmark,
                        onReaction = onReaction,
                        onOpenProfile = onAccountClick,
                        onOpenReactionBubble = onOpenReactionBubble ?: { _, _ -> },
                        onOpenReactionPicker = onOpenReactionPicker,
                        quoteEnabled = quoteEnabled,
                        onQuote = onQuote,
                        onSearchHashtag = onSearchHashtag,
                        onOpenHashtagBubble = onOpenHashtagBubble,
                         onOpenMedia = if (mediaOwner != null) onOpenMedia else { _: MediaOpenRequest -> },
                         onOpenPost = onOpenPost,
                         onOpenUrl = onOpenUrl,
                         onOpenUsername = onOpenUsername,
                         largeLayout = largeLayout,
                      )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f))
                }
            }
            item {
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    if (state.loadingMore) CircularProgressIndicator(Modifier.size(24.dp))
                    else if (state.nextCursor != null) TextButton(onClick = onLoadMore) { Text(stringResource(R.string.search_load_older)) }
                    else Text(stringResource(R.string.search_up_to_date), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        query.isBlank() -> EmptyState(AppIcons.Tag, stringResource(R.string.search_find_hashtag), stringResource(R.string.search_hashtag_prompt))
        state.query == query.trim() -> EmptyState(AppIcons.Tag, stringResource(R.string.search_no_posts), stringResource(R.string.search_no_posts_for, state.query))
        else -> EmptyState(AppIcons.Tag, stringResource(R.string.search_hashtag_ready), stringResource(R.string.search_hashtag_ready_prompt))
    }
}

@Composable
private fun AccountSearchResults(
    query: String,
    state: AccountSearchState,
    onAccountClick: (Account) -> Unit,
    endClearance: Dp,
    listState: LazyListState?,
) {
    val scheme = LocalPalustrisMotionScheme.current
    when {
        state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        state.error != null -> EmptyState(AppIcons.Search, stringResource(R.string.search_account_failed), state.error)
        state.accounts.isNotEmpty() -> LazyColumn(
            state = listState ?: androidx.compose.foundation.lazy.rememberLazyListState(),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 8.dp, end = 8.dp, bottom = endClearance),
        ) {
            items(state.accounts, key = { "${it.id.connection.origin}/${it.id.localId}" }) { account ->
                val interactionSource = remember(account.id) { MutableInteractionSource() }
                ListItem(
                    modifier = Modifier
                        .springPress(interactionSource)
                        .clickable(
                            interactionSource = interactionSource,
                            indication = LocalIndication.current,
                        ) { onAccountClick(account) }
                        .animateItem(
                            fadeInSpec = scheme.fastFadeIn,
                            fadeOutSpec = scheme.fastFadeOut,
                            placementSpec = scheme.gentleOffset,
                        ),
                    headlineContent = { me.foxtails.palustris.ui.emoji.AccountDisplayName(account, style = MaterialTheme.typography.titleMedium) },
                    supportingContent = { Text(account.handle) },
                    leadingContent = { AccountAvatar(account, Modifier.size(48.dp)) },
                )
            }
        }
        query.isBlank() -> EmptyState(AppIcons.Search, stringResource(R.string.search_find_account), stringResource(R.string.search_account_prompt))
        state.query == query.trim() -> EmptyState(AppIcons.Search, stringResource(R.string.search_no_account), stringResource(R.string.search_no_account_prompt))
        else -> EmptyState(AppIcons.Search, stringResource(R.string.search_ready), stringResource(R.string.search_ready_prompt))
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
    isReply: Boolean = false,
    onRemoveQuote: () -> Unit = {},
    onPublish: () -> Unit = {},
    onRequestEmoji: ((me.foxtails.palustris.ui.emoji.ComposerField) -> Unit)? = null,
    pendingEmojiInsertion: Pair<me.foxtails.palustris.domain.EmojiChoice, me.foxtails.palustris.ui.emoji.ComposerField>? = null,
    onEmojiInsertionApplied: () -> Unit = {},
) {
    val scheme = LocalPalustrisMotionScheme.current
    val emojiPickerDescription = stringResource(me.foxtails.palustris.R.string.emoji_open_picker)
    val postTextDescription = stringResource(R.string.composer_post_text)
    var textSelection by remember { mutableStateOf(androidx.compose.ui.text.TextRange(text.length)) }
    var warningSelection by remember { mutableStateOf(androidx.compose.ui.text.TextRange(warning.length)) }
    LaunchedEffect(text) {
        if (textSelection.min > text.length) textSelection = androidx.compose.ui.text.TextRange(text.length)
    }
    LaunchedEffect(warning) {
        if (warningSelection.min > warning.length) warningSelection = androidx.compose.ui.text.TextRange(warning.length)
    }
    LaunchedEffect(pendingEmojiInsertion) {
        val insertion = pendingEmojiInsertion ?: return@LaunchedEffect
        when (insertion.second) {
            me.foxtails.palustris.ui.emoji.ComposerField.Text -> {
                val selection = textSelection
                val start = selection.min.coerceIn(0, text.length)
                val end = selection.max.coerceIn(start, text.length)
                val inserted = text.replaceRange(start, end, insertion.first.submissionValue)
                onTextChange(inserted)
                textSelection = androidx.compose.ui.text.TextRange(start + insertion.first.submissionValue.length)
            }
            me.foxtails.palustris.ui.emoji.ComposerField.Warning -> {
                val selection = warningSelection
                val start = selection.min.coerceIn(0, warning.length)
                val end = selection.max.coerceIn(start, warning.length)
                val inserted = warning.replaceRange(start, end, insertion.first.submissionValue)
                onWarningChange(inserted)
                warningSelection = androidx.compose.ui.text.TextRange(start + insertion.first.submissionValue.length)
            }
        }
        onEmojiInsertionApplied()
    }
    val textValue = remember(text, textSelection) { androidx.compose.ui.text.input.TextFieldValue(text, textSelection) }
    val warningValue = remember(warning, warningSelection) { androidx.compose.ui.text.input.TextFieldValue(warning, warningSelection) }
    Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        Row(Modifier.padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            if (account != null) AccountAvatar(account, Modifier.size(48.dp)) else Avatar(Modifier.size(48.dp))
            Spacer(Modifier.width(12.dp))
            Column {
                 Text(stringResource(R.string.composer_local_draft), style = MaterialTheme.typography.titleMedium)
                 Text(account?.handle ?: stringResource(R.string.composer_no_account), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        quoteTarget?.let { target ->
            OutlinedCard(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
                Column(Modifier.padding(12.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        me.foxtails.palustris.ui.emoji.InlineEmojiText(
                             text = if (isReply) stringResource(R.string.composer_replying_to, target.post.author.displayName) else stringResource(R.string.composer_quoting, target.post.author.displayName),
                            emoji = target.post.author.emoji,
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.weight(1f),
                        )
                         TextButton(onClick = onRemoveQuote) { Text(stringResource(R.string.composer_remove_quote)) }
                    }
                    if (target.post.text.isBlank()) {
                         Text(stringResource(R.string.composer_no_post_text), maxLines = 4, overflow = TextOverflow.Ellipsis)
                    } else {
                        me.foxtails.palustris.ui.emoji.PostText(
                            target.post,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
        ExpandableContent(visible = warningEnabled, modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = warningValue,
                onValueChange = { changed ->
                    onWarningChange(changed.text)
                    warningSelection = changed.selection
                },
                 label = { Text(stringResource(R.string.composer_content_warning)) }, modifier = Modifier.fillMaxWidth(),
                trailingIcon = {
                    if (onRequestEmoji != null) {
                        TextButton(
                            onClick = { onRequestEmoji(me.foxtails.palustris.ui.emoji.ComposerField.Warning) },
                            modifier = Modifier.semantics { contentDescription = emojiPickerDescription },
                         ) { Text(stringResource(R.string.composer_emoji)) }
                    }
                },
            )
        }
        BasicTextField(
            value = textValue,
            onValueChange = { changed ->
                onTextChange(changed.text)
                textSelection = changed.selection
            },
             modifier = Modifier.fillMaxWidth().heightIn(min = 240.dp).padding(vertical = 24.dp).semantics { contentDescription = postTextDescription },
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
             decorationBox = { field -> Box { if (text.isEmpty()) Text(stringResource(R.string.composer_placeholder), color = MaterialTheme.colorScheme.onSurfaceVariant); field() } },
        )
        HorizontalDivider()
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                 FilterChip(selected = warningEnabled, onClick = { onWarningEnabled(!warningEnabled) }, label = { Text(stringResource(R.string.composer_content_warning)) })
                if (onRequestEmoji != null) {
                    TextButton(
                        onClick = { onRequestEmoji(me.foxtails.palustris.ui.emoji.ComposerField.Text) },
                        modifier = Modifier.semantics { contentDescription = emojiPickerDescription },
                    ) { Text(stringResource(me.foxtails.palustris.R.string.emoji_picker_title)) }
                }
            }
             Text(pluralStringResource(R.plurals.composer_character_count, text.length, text.length), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
         Text(if (canPublish) stringResource(R.string.composer_publish_enabled) else stringResource(R.string.composer_publish_disabled),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        AnimatedStatePane(
            stateKey = error != null,
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        ) {
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
        }
        Spacer(Modifier.height(24.dp))
        Button(onClick = onPublish, enabled = canPublish && !publishing && text.isNotBlank(), modifier = Modifier.align(Alignment.End)) {
            AnimatedContent(
                targetState = publishing,
                transitionSpec = {
                    if (scheme.reducedMotion) EnterTransition.None togetherWith ExitTransition.None
                    else fadeIn(scheme.fastFadeIn) togetherWith fadeOut(scheme.fastFadeOut)
                },
                label = "publishButtonContent",
            ) { busy ->
                if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                 else Text(stringResource(R.string.composer_publish))
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun DraftsScreen(drafts: List<me.foxtails.palustris.domain.PostDraft>, onEdit: (me.foxtails.palustris.domain.PostDraft) -> Unit, onDelete: (me.foxtails.palustris.domain.PostDraft) -> Unit) {
    val scheme = LocalPalustrisMotionScheme.current
    var confirmDelete by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<me.foxtails.palustris.domain.PostDraft?>(null) }
    if (drafts.isEmpty()) EmptyState(AppIcons.Folder, stringResource(R.string.drafts_empty_title), stringResource(R.string.drafts_empty_subtitle))
    else LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(vertical = 16.dp),
    ) {
        items(drafts, key = { it.id }) { draft ->
            val interactionSource = remember(draft.id) { MutableInteractionSource() }
            ElevatedCard(
                onClick = { onEdit(draft) },
                interactionSource = interactionSource,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
                    .springPress(interactionSource, pressedScale = scheme.largePressedScale)
                    .animateItem(
                        fadeInSpec = scheme.fastFadeIn,
                        fadeOutSpec = scheme.fastFadeOut,
                        placementSpec = scheme.gentleOffset,
                    ),
            ) {
                Column(Modifier.padding(20.dp)) {
                     Text(stringResource(R.string.draft_label), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(12.dp))
                    me.foxtails.palustris.ui.emoji.InlineEmojiText(
                        draft.text.ifBlank { draft.contentWarning.orEmpty() },
                        draft.quotePreview?.postEmoji.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 5,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(12.dp))
                     Text(stringResource(R.string.draft_continue), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                     TextButton(onClick = { pendingDelete = draft; confirmDelete = true }, modifier = Modifier.align(Alignment.End)) { Text(stringResource(R.string.draft_delete_action)) }
                }
            }
        }
    }
    if (confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false },
         title = { Text(stringResource(R.string.draft_delete_title)) }, text = { Text(stringResource(R.string.draft_delete_text)) },
         confirmButton = { TextButton(onClick = { confirmDelete = false; pendingDelete?.let(onDelete); pendingDelete = null }) { Text(stringResource(R.string.draft_delete)) } },
         dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.dialog_cancel)) } })
}
