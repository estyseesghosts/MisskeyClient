package me.foxtails.palustris.ui.search

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import me.foxtails.palustris.R
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.EmojiChoice
import me.foxtails.palustris.domain.OwnedPost
import me.foxtails.palustris.domain.PostAction
import me.foxtails.palustris.domain.isExactHashtag
import me.foxtails.palustris.ui.ActionIcon
import me.foxtails.palustris.ui.AppIcons
import me.foxtails.palustris.ui.EmptyState
import me.foxtails.palustris.ui.PostInteractionPresentation
import me.foxtails.palustris.ui.PostRow
import me.foxtails.palustris.ui.components.AccountAvatar
import me.foxtails.palustris.ui.components.CategoryChips
import me.foxtails.palustris.ui.feed.AccountSearchState
import me.foxtails.palustris.ui.feed.ClientReadyPostActions
import me.foxtails.palustris.ui.large.LargeBottomDock
import me.foxtails.palustris.ui.large.LargeSearchDockClearance
import me.foxtails.palustris.ui.layout.CompactOverlayHorizontalPadding
import me.foxtails.palustris.ui.layout.CompactSearchControlsSpacing
import me.foxtails.palustris.ui.layout.CompactSearchDockHeight
import me.foxtails.palustris.ui.layout.compactContextualControlsPositioningInsets
import me.foxtails.palustris.ui.layout.compactScrollEndClearance
import me.foxtails.palustris.ui.motion.AnimatedStatePane
import me.foxtails.palustris.ui.motion.LocalPalustrisMotionScheme
import me.foxtails.palustris.ui.motion.springPress

@Composable
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
fun SearchScreen(
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
    sessionRevision: Long = 0L,
    onOpenMedia: (me.foxtails.palustris.ui.media.MediaOpenRequest) -> Unit = {},
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
    val hashtagSearchRequested = isExactHashtag(query)
    val sections = listOf(
        stringResource(R.string.search_category_profiles),
        stringResource(R.string.search_category_hashtags),
        stringResource(R.string.search_category_news),
        stringResource(R.string.search_category_for_you),
    )
    fun submitSearch() {
        if (query.isNotBlank()) onSearchAccounts(query)
    }
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
            endClearance = if (largeLayout) largeDockClearance else searchEndClearance,
            mediaOwner = mediaOwner,
            sessionRevision = sessionRevision,
            onOpenMedia = onOpenMedia,
            onOpenPost = onOpenPost,
            onOpenUrl = onOpenUrl,
            onOpenUsername = onOpenUsername,
            onSearchHashtag = onSearchHashtag,
            onOpenHashtagBubble = onOpenHashtagBubble,
            listState = listState,
            largeLayout = largeLayout,
        )
        if (largeLayout) {
            LargeBottomDock(
                content = {
                    Column(
                        Modifier.widthIn(max = 520.dp).fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(CompactSearchControlsSpacing),
                    ) {
                        CategoryChips(sections, tab, stringResource(R.string.search_categories_description), ::updateTab)
                        SearchField(query, ::submitSearch, ::updateQuery)
                    }
                },
                modifier = Modifier.align(Alignment.BottomStart).onSizeChanged { largeDockHeightPx = it.height },
            )
        } else {
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
    sessionRevision: Long,
    onOpenMedia: (me.foxtails.palustris.ui.media.MediaOpenRequest) -> Unit,
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
        AnimatedStatePane(stateKey = "$tab:$queryKind:$resultKind", modifier = Modifier.fillMaxSize()) {
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
                    sessionRevision = sessionRevision,
                    onOpenMedia = onOpenMedia,
                    onSearchHashtag = onSearchHashtag,
                    onOpenHashtagBubble = onOpenHashtagBubble,
                    listState = listState,
                    largeLayout = largeLayout,
                    onOpenPost = onOpenPost,
                    onOpenUrl = onOpenUrl,
                    onOpenUsername = onOpenUsername,
                ) else AccountSearchResults(query, accountSearch, onAccountClick, endClearance, listState)
            } else {
                EmptyState(
                    AppIcons.Tag,
                    if (query.isNotBlank()) stringResource(R.string.search_ready_when_you_are) else when (tab) {
                        1 -> stringResource(R.string.search_explore_hashtags)
                        2 -> stringResource(R.string.search_news_from_network)
                        else -> stringResource(R.string.search_find_your_people)
                    },
                    if (query.isNotBlank()) stringResource(R.string.search_accounts_available_profiles)
                    else when (tab) {
                        1 -> stringResource(R.string.search_trending_topics)
                        2 -> stringResource(R.string.search_popular_links)
                        else -> stringResource(R.string.search_suggested_accounts)
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
        modifier = Modifier.fillMaxWidth().semantics { contentDescription = searchFieldDescription },
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
            ) { visible -> if (visible) ActionIcon(AppIcons.Close, stringResource(R.string.search_clear), { onQueryChange("") }) }
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
    sessionRevision: Long,
    onOpenMedia: (me.foxtails.palustris.ui.media.MediaOpenRequest) -> Unit,
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
            state = listState ?: rememberLazyListState(),
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
                        ownedPost = OwnedPost(mediaOwner ?: post.author.id, post, sessionRevision),
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
                        onOpenMedia = if (mediaOwner != null) onOpenMedia else { _: me.foxtails.palustris.ui.media.MediaOpenRequest -> },
                        onOpenPost = onOpenPost,
                        onOpenUrl = onOpenUrl,
                        onOpenUsername = onOpenUsername,
                        largeLayout = largeLayout,
                        interactionPresentation = PostInteractionPresentation.Detailed,
                    )
                    androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f))
                }
            }
            item {
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    if (state.loadingMore) CircularProgressIndicator(Modifier.size(24.dp))
                    else if (state.nextCursor != null) androidx.compose.material3.TextButton(onClick = onLoadMore) { Text(stringResource(R.string.search_load_older)) }
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
            state = listState ?: rememberLazyListState(),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 8.dp, end = 8.dp, bottom = endClearance),
        ) {
            items(state.accounts, key = { "${it.id.connection.origin}/${it.id.localId}" }) { account ->
                val interactionSource = remember(account.id) { MutableInteractionSource() }
                androidx.compose.material3.ListItem(
                    modifier = Modifier
                        .springPress(interactionSource)
                        .clickable(interactionSource = interactionSource, indication = LocalIndication.current) { onAccountClick(account) }
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
