@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package me.foxtails.palustris.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.unit.dp
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.Attachment
import me.foxtails.palustris.R
import me.foxtails.palustris.domain.EmojiChoice
import me.foxtails.palustris.domain.MediaKind
import me.foxtails.palustris.domain.OwnedPost
import me.foxtails.palustris.domain.PostAction
import me.foxtails.palustris.ui.emoji.InlineEmojiText
import me.foxtails.palustris.ui.emoji.AccountDisplayName
import me.foxtails.palustris.ui.media.MediaOpenRequest
import me.foxtails.palustris.ui.media.MediaPage
import me.foxtails.palustris.ui.media.PostMediaCarousel
import me.foxtails.palustris.ui.thread.PostThreadPhase
import me.foxtails.palustris.ui.thread.PostThreadUiState
import me.foxtails.palustris.ui.thread.ThreadedReplyRow

internal enum class SinglePostPresentation { Standard, PhotoGrid }

@Composable
internal fun SinglePostScreen(
    ownedPost: OwnedPost,
    presentation: SinglePostPresentation = SinglePostPresentation.Standard,
    onClose: () -> Unit,
    availableActions: Set<PostAction> = emptySet(),
    onReact: (OwnedPost) -> Unit = {},
    onReply: (OwnedPost) -> Unit = {},
    onReshare: (OwnedPost) -> Unit = {},
    onBookmark: (OwnedPost) -> Unit = {},
    onReaction: (OwnedPost, EmojiChoice) -> Unit = { _, _ -> },
    onOpenProfile: (Account) -> Unit = {},
    onSearchHashtag: (String) -> Unit = {},
    onOpenHashtagBubble: ((OwnedPost, List<String>, androidx.compose.ui.geometry.Rect) -> Unit)? = null,
    onOpenReactionBubble: ((OwnedPost, androidx.compose.ui.geometry.Rect) -> Unit)? = null,
    onOpenMedia: (MediaOpenRequest) -> Unit = {},
    onOpenUrl: ((String) -> Unit)? = null,
    onOpenUsername: ((String) -> Unit)? = null,
    embedded: Boolean = false,
    threadState: PostThreadUiState? = null,
    onThreadRefresh: () -> Unit = {},
    onThreadContinue: () -> Unit = {},
    quoteEnabled: Boolean = false,
    onQuote: (OwnedPost) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val post = ownedPost.post
    val context = LocalContext.current
    val photos = post.attachments.filter { it.kind == MediaKind.Image || it.kind == MediaKind.AnimatedImage }

    key(ownedPost.fetchedBy, post.id.connection, post.id.value, presentation) {
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
            val listState = rememberLazyListState()
            LazyColumn(
                state = listState,
                modifier = modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .testTag("single_post_content"),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 96.dp),
            ) {
                item("single-post-header", contentType = "header") {
                    Row(
                        modifier = Modifier.fillMaxWidth().then(if (embedded) Modifier else Modifier.statusBarsPadding())
                            .height(64.dp).padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ActionIcon(AppIcons.Back, stringResource(R.string.single_post_close), onClose)
                        Text(stringResource(R.string.single_post_title), modifier = Modifier.padding(start = 8.dp), style = MaterialTheme.typography.titleLarge)
                    }
                }
                threadState?.ancestors?.let { ancestors ->
                    items(ancestors, key = { "ancestor:${it.sessionRevision}:${it.post.id.connection}:${it.post.id.value}" }, contentType = { "ancestor" }) { ancestor ->
                        PostRow(
                            ownedPost = ancestor,
                            availableActions = actionsForPost(availableActions, ancestor.post),
                            onReact = onReact,
                            onReply = onReply,
                            onReshare = onReshare,
                            onBookmark = onBookmark,
                            onReaction = onReaction,
                            onOpenProfile = onOpenProfile,
                            onSearchHashtag = onSearchHashtag,
                            onOpenHashtagBubble = onOpenHashtagBubble,
                            onOpenReactionBubble = onOpenReactionBubble ?: { _, _ -> },
                            onOpenMedia = onOpenMedia,
                            truncateBody = false,
                            quoteEnabled = quoteEnabled,
                            onQuote = onQuote,
                            onOpenUrl = onOpenUrl,
                            onOpenUsername = onOpenUsername,
                        )
                    }
                }
                item("single-post-focal", contentType = "focal") {
                if (presentation == SinglePostPresentation.Standard || photos.isEmpty()) {
                    PostRow(
                        ownedPost = ownedPost,
                        availableActions = actionsForPost(availableActions, post),
                        onReact = onReact,
                        onReply = onReply,
                        onReshare = onReshare,
                        onBookmark = onBookmark,
                        onReaction = onReaction,
                        onOpenProfile = onOpenProfile,
                        onSearchHashtag = onSearchHashtag,
                        onOpenHashtagBubble = onOpenHashtagBubble,
                        onOpenReactionBubble = onOpenReactionBubble ?: { _, _ -> },
                        onOpenMedia = onOpenMedia,
                        truncateBody = false,
                        quoteEnabled = quoteEnabled,
                        onQuote = onQuote,
                        onOpenUrl = onOpenUrl,
                        onOpenUsername = onOpenUsername,
                        interactionPresentation = PostInteractionPresentation.Detailed,
                    )
                } else {
            val presentation = remember(post.text, post.emoji) { parseHashtagBlocks(post.text, post.emoji) }
            var expanded by rememberSaveable(post.id.connection, post.id.value) { mutableStateOf(false) }
            val contentVisible = post.contentWarning == null || expanded

            PostMetadataRow(
                post = post,
                filteredHashtags = presentation.filteredHashtags.takeIf { contentVisible }.orEmpty(),
                onOpenProfile = { onOpenProfile(post.author) },
                onSearchHashtag = onSearchHashtag,
                onOpenHashtagBubble = onOpenHashtagBubble,
                postOwned = ownedPost,
            )
             PhotoPager(ownedPost, photos)
             val remainingAttachmentIndices = post.attachments.indices.filter { index ->
                 post.attachments[index].kind != MediaKind.Image && post.attachments[index].kind != MediaKind.AnimatedImage
             }
              if (remainingAttachmentIndices.isNotEmpty()) {
                  PostMediaCarousel(
                     ownedPost = ownedPost,
                     onOpenMedia = onOpenMedia,
                     attachmentIndices = remainingAttachmentIndices,
                      modifier = Modifier.padding(top = 12.dp),
                  )
              }
             HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f))
             InteractionRow(
                 ownedPost = ownedPost,
                  availableActions = actionsForPost(availableActions, post),
                 onReply = onReply,
                 onReact = onReact,
                 onReshare = onReshare,
                 onBookmark = onBookmark,
                 onReaction = onReaction,
                 quoteEnabled = quoteEnabled,
                 onQuote = onQuote,
                  onOpenReactionBubble = onOpenReactionBubble ?: { _, _ -> },
                  onShare = { sharePost(context, post) },
              )
              if (post.reactions.any { it.count > 0 }) {
                  ReactionRow(
                      reactions = post.reactions,
                      ownedPost = ownedPost,
                      enabled = PostAction.React in actionsForPost(availableActions, post),
                      onReaction = onReaction,
                      showReactionNumbers = true,
                  )
              }
             if (post.contentWarning != null) {
                InlineEmojiText(
                    post.contentWarning.ifBlank { stringResource(R.string.content_warning) },
                    post.emoji,
                    Modifier.padding(horizontal = 16.dp),
                    MaterialTheme.typography.bodyLarge,
                )
                TextButton(
                    onClick = { expanded = !expanded },
                    modifier = Modifier.padding(horizontal = 4.dp),
                ) { Text(stringResource(if (expanded) R.string.content_warning_hide else R.string.content_warning_show)) }
            }
            if (contentVisible) {
                SelectionContainer {
                    Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp)) {
                        if (presentation.visibleText.isNotBlank()) {
                            PostBodyText(
                                text = presentation.visibleText,
                                emoji = post.emoji,
                                onOpenUrl = onOpenUrl,
                                onOpenUsername = onOpenUsername,
                                onSearchHashtag = onSearchHashtag,
                                style = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                            )
                        }
                        postTimestamp(post)?.let { timestamp ->
                            if (presentation.visibleText.isNotBlank()) androidx.compose.foundation.layout.Spacer(Modifier.height(2.dp))
                            val timeDescription = stringResource(R.string.post_time)
                            Text(
                                timestamp,
                                Modifier.semantics { contentDescription = timeDescription },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
             InteractionSummaryRow(post.interactionCounts)
             post.pollOptions.forEach { option ->
                Surface(
                    Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainer,
                ) {
                    Row(Modifier.padding(12.dp)) {
                        InlineEmojiText(option.text, post.emoji, Modifier.weight(1f), MaterialTheme.typography.bodyMedium)
                        Text(pluralStringResource(R.plurals.post_poll_votes, option.votes, option.votes), style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
            post.quote?.let { quote ->
                OutlinedCard(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    onClick = { openExternal(context, quote.url) },
                ) {
                    Column(Modifier.padding(16.dp)) {
                        AccountDisplayName(quote.author, style = MaterialTheme.typography.titleSmall)
                        InlineEmojiText(
                            quote.contentWarning?.ifBlank { stringResource(R.string.content_warning) } ?: quote.text,
                            quote.emoji,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 5,
                        )
                        Text(stringResource(R.string.single_post_view_quote), Modifier.padding(top = 12.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
                    }
                }
                threadState?.let { state ->
                    item("thread-heading", contentType = "thread-heading") {
                        Text(
                            stringResource(R.string.thread_replies),
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                    items(state.rows, key = { it.stableKey }, contentType = { "reply" }) { row ->
                        ThreadedReplyRow(
                            row = row,
                            availableActions = availableActions,
                            onReact = onReact,
                            onReply = onReply,
                            onReshare = onReshare,
                            onBookmark = onBookmark,
                            onReaction = onReaction,
                            onOpenProfile = onOpenProfile ?: {},
                            onSearchHashtag = onSearchHashtag ?: {},
                            onOpenHashtagBubble = onOpenHashtagBubble,
                            onOpenReactionBubble = onOpenReactionBubble,
                            onOpenMedia = onOpenMedia,
                            onOpenUrl = onOpenUrl,
                            onOpenUsername = onOpenUsername,
                            quoteEnabled = quoteEnabled,
                            onQuote = onQuote,
                        )
                    }
                    items(state.disconnectedRows, key = { "disconnected:${it.stableKey}" }, contentType = { "disconnected" }) { row ->
                        ThreadedReplyRow(
                            row = row,
                            availableActions = availableActions,
                            onReact = onReact,
                            onReply = onReply,
                            onReshare = onReshare,
                            onBookmark = onBookmark,
                            onReaction = onReaction,
                            onOpenProfile = onOpenProfile ?: {},
                            onSearchHashtag = onSearchHashtag ?: {},
                            onOpenHashtagBubble = onOpenHashtagBubble,
                            onOpenReactionBubble = onOpenReactionBubble,
                            onOpenMedia = onOpenMedia,
                            onOpenUrl = onOpenUrl,
                            onOpenUsername = onOpenUsername,
                            quoteEnabled = quoteEnabled,
                            onQuote = onQuote,
                        )
                    }
                    item("thread-status", contentType = "thread-status") {
                        ThreadStatus(state, onThreadRefresh, onThreadContinue)
                    }
                }
            }
        }
    }
    if (!embedded) BackHandler(onBack = onClose)
}

@Composable
private fun ThreadStatus(
    state: PostThreadUiState,
    onRefresh: () -> Unit,
    onContinue: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        when (state.phase) {
            PostThreadPhase.InitialLoading, PostThreadPhase.Continuing ->
                Text(stringResource(R.string.thread_loading_replies), color = MaterialTheme.colorScheme.onSurfaceVariant)
            PostThreadPhase.InitialFailure -> {
                Text(stringResource(R.string.thread_replies_could_not_load), color = MaterialTheme.colorScheme.error)
                TextButton(onClick = onRefresh) { Text(stringResource(R.string.notifications_retry)) }
            }
            PostThreadPhase.Partial -> {
                if (state.error != null) Text(stringResource(R.string.thread_replies_could_not_load), color = MaterialTheme.colorScheme.error)
                if (state.continuation == null) Text(stringResource(R.string.thread_loading_limit_reached), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            PostThreadPhase.Content -> if (state.rows.isEmpty() && state.disconnectedRows.isEmpty()) {
                Text(stringResource(R.string.thread_no_replies), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            PostThreadPhase.Refreshing -> Text(stringResource(R.string.thread_refreshing), color = MaterialTheme.colorScheme.onSurfaceVariant)
            PostThreadPhase.Inactive, PostThreadPhase.AccountUnavailable -> Unit
        }
        if (state.continuation != null) TextButton(onClick = onContinue) { Text(stringResource(R.string.thread_load_more)) }
    }
}

@Composable
private fun PhotoPager(ownedPost: OwnedPost, photos: List<Attachment>) {
    val pagerState = rememberPagerState { photos.size }
    val revealedPages = remember { mutableStateMapOf<Int, Boolean>() }
    BoxWithConstraints(Modifier.fillMaxWidth().testTag("single_post_photo_pager")) {
        val photoHeight = (maxWidth * .75f).coerceAtLeast(240.dp)
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth().height(photoHeight),
            beyondViewportPageCount = 1,
        ) { page ->
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                MediaPage(
                    attachment = photos[page],
                    index = page,
                    selected = page == pagerState.settledPage,
                    revealed = !photos[page].sensitive || revealedPages[page] == true,
                    accountIdentity = ownedPost.fetchedBy.toString(),
                    postIdentity = "${ownedPost.post.id.connection}/${ownedPost.post.id.value}",
                    onReveal = { revealedPages[page] = true },
                    edgeToEdge = true,
                )
            }
        }
        if (photos.size > 1) {
            Surface(
                modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp).size(32.dp),
                color = Color.Black.copy(alpha = .6f),
                contentColor = Color.White,
                shape = CircleShape,
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(R.string.media_page_count, pagerState.settledPage + 1, photos.size),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
    }
}
