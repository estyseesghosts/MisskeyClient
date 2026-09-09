@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package me.foxtails.palustris.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.Attachment
import me.foxtails.palustris.domain.EmojiChoice
import me.foxtails.palustris.domain.MediaKind
import me.foxtails.palustris.domain.OwnedPost
import me.foxtails.palustris.domain.PostAction
import me.foxtails.palustris.ui.emoji.InlineEmojiText
import me.foxtails.palustris.ui.media.MediaOpenRequest
import me.foxtails.palustris.ui.media.MediaPage

@Composable
internal fun SinglePostScreen(
    ownedPost: OwnedPost,
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
    onOpenMedia: (MediaOpenRequest) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val post = ownedPost.post
    val photos = post.attachments.filter { it.kind == MediaKind.Image || it.kind == MediaKind.AnimatedImage }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .testTag("single_post_content"),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().statusBarsPadding().height(64.dp).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ActionIcon(AppIcons.Back, "Close post", onClose)
            Text("Post", modifier = Modifier.padding(start = 8.dp), style = MaterialTheme.typography.titleLarge)
        }

        if (photos.isEmpty()) {
            PostRow(
                ownedPost = ownedPost,
                availableActions = availableActions,
                onReact = onReact,
                onReply = onReply,
                onReshare = onReshare,
                onBookmark = onBookmark,
                onReaction = onReaction,
                onOpenProfile = onOpenProfile,
                onSearchHashtag = onSearchHashtag,
                onOpenHashtagBubble = onOpenHashtagBubble,
                onOpenMedia = onOpenMedia,
                truncateBody = false,
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
            if (post.contentWarning != null) {
                InlineEmojiText(
                    post.contentWarning.ifBlank { "Content warning" },
                    post.emoji,
                    Modifier.padding(horizontal = 16.dp),
                    MaterialTheme.typography.bodyLarge,
                )
                TextButton(
                    onClick = { expanded = !expanded },
                    modifier = Modifier.padding(horizontal = 4.dp),
                ) { Text(if (expanded) "Hide content" else "Show content") }
            }
            if (contentVisible) {
                SelectionContainer {
                    Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp)) {
                        if (presentation.visibleText.isNotBlank()) {
                            InlineEmojiText(
                                presentation.visibleText,
                                post.emoji,
                                style = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                            )
                        }
                        postTimestamp(post)?.let { timestamp ->
                            if (presentation.visibleText.isNotBlank()) androidx.compose.foundation.layout.Spacer(Modifier.height(2.dp))
                            Text(
                                timestamp,
                                Modifier.semantics { contentDescription = "Post time" },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f))
        }
    }
    BackHandler(onBack = onClose)
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
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp),
                color = Color.Black.copy(alpha = .6f),
                contentColor = Color.White,
                shape = MaterialTheme.shapes.small,
            ) {
                Text("${pagerState.settledPage + 1} / ${photos.size}", Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
            }
        }
    }
}
