@file:OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
)

package me.foxtails.palustris.ui

import android.content.Context
import android.content.Intent
import android.text.format.DateUtils
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import me.foxtails.palustris.ui.components.AccountAvatar
import me.foxtails.palustris.ui.feed.Feed
import me.foxtails.palustris.ui.layout.LegacyFeedBottomClearance
import me.foxtails.palustris.ui.layout.compactHomeScrollEndClearance
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import coil.compose.AsyncImage
import coil.request.ImageRequest
import me.foxtails.palustris.data.media.MediaImageLoader
import kotlinx.coroutines.flow.distinctUntilChanged
import me.foxtails.palustris.R
import me.foxtails.palustris.domain.*
import me.foxtails.palustris.ui.emoji.AccountDisplayName
import me.foxtails.palustris.ui.emoji.CustomEmojiImage
import me.foxtails.palustris.ui.emoji.InlineEmojiText
import me.foxtails.palustris.ui.media.MediaOpenRequest
import me.foxtails.palustris.ui.media.PostMediaCarousel
import me.foxtails.palustris.ui.motion.AnimatedStatePane
import me.foxtails.palustris.ui.motion.ExpandableContent
import me.foxtails.palustris.ui.motion.LocalPalustrisMotionScheme
import me.foxtails.palustris.ui.motion.PopEffect
import me.foxtails.palustris.ui.motion.rememberSelectedColor
import me.foxtails.palustris.ui.motion.springPress
import me.foxtails.palustris.ui.large.LargeBottomDock
import me.foxtails.palustris.ui.posts.reactionPickerGesture
import me.foxtails.palustris.ui.components.PillAction
import me.foxtails.palustris.ui.posts.LocalPostActionOwner
import me.foxtails.palustris.ui.posts.LocalPostRepostConfirmationOwner
import me.foxtails.palustris.ui.posts.PostRepostConfirmationOwner

internal fun openExternal(context: Context, url: String?) {
    val uri = url?.let { me.foxtails.palustris.ui.links.ExternalLinkHandler.prepare(it) }?.toUri() ?: return
    if (uri.scheme !in listOf("https", "http") || uri.host.isNullOrBlank()) return
    try { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
    catch (_: android.content.ActivityNotFoundException) { Toast.makeText(context, context.getString(R.string.error_no_app_open_link), Toast.LENGTH_SHORT).show() }
}

private val PostMetadataVerticalPadding = 2.dp * 1.06f
private val PostChromeHeight = 44.dp + (PostMetadataVerticalPadding * 2f)
private val PostInteractionRowHeight = 48.dp
private val PostInteractionIconSize = 24.dp

internal data class PostInteractionPresentation(
    val showInteractionSummary: Boolean,
    val showReactionNumbers: Boolean,
) {
    companion object {
        val Feed = PostInteractionPresentation(
            showInteractionSummary = false,
            showReactionNumbers = false,
        )
        val Detailed = PostInteractionPresentation(
            showInteractionSummary = true,
            showReactionNumbers = true,
        )
    }
}

@Composable
internal fun PostRow(
    ownedPost: OwnedPost,
    availableActions: Set<PostAction>,
    onReact: (OwnedPost) -> Unit,
    onReply: (OwnedPost) -> Unit,
    onReshare: (OwnedPost) -> Unit,
    onBookmark: (OwnedPost) -> Unit,
    onReaction: (OwnedPost, EmojiChoice) -> Unit,
    onOpenProfile: ((Account) -> Unit)?,
    onSearchHashtag: ((String) -> Unit)?,
    onOpenHashtagBubble: ((OwnedPost, List<String>, Rect) -> Unit)? = null,
    quoteEnabled: Boolean = false,
    onQuote: (OwnedPost) -> Unit = {},
    onOpenReactionBubble: (OwnedPost, Rect) -> Unit = { _, _ -> },
    onOpenReactionPicker: (OwnedPost) -> Unit = {},
    onOpenMedia: (MediaOpenRequest) -> Unit = {},
    modifier: Modifier = Modifier,
    truncateBody: Boolean = true,
    onOpenPost: ((OwnedPost) -> Unit)? = null,
    largeLayout: Boolean = false,
    onOpenUrl: ((String) -> Unit)? = null,
    onOpenUsername: ((String) -> Unit)? = null,
    contentWarningRules: ContentWarningRules = ContentWarningRules(),
    interactionPresentation: PostInteractionPresentation = PostInteractionPresentation.Feed,
) {
    val post = ownedPost.post
    val context = LocalContext.current
    val repostConfirmationOwner = LocalPostRepostConfirmationOwner.current
    var expanded by rememberSaveable(post.id.connection, post.id.value) { mutableStateOf(false) }
    val presentation = remember(post.text, post.emoji) { parseHashtagBlocks(post.text, post.emoji) }
    val hashtags = remember(post.text, post.emoji) { postHashtags(post.text, post.emoji) }
    val warningDecision = remember(post.contentWarning, hashtags, contentWarningRules) {
        ContentWarningPolicy.decide(post.contentWarning, hashtags, contentWarningRules, bodyText = post.text)
    }
    val postActionOwner = LocalPostActionOwner.current
    LaunchedEffect(ownedPost.fetchedBy, post.id, ownedPost.sessionRevision, post.reposted) {
        repostConfirmationOwner.reconcile(ownedPost)
    }
    val contentVisible = warningDecision != ContentWarningDecision.Hidden &&
        (post.contentWarning == null || expanded || warningDecision == ContentWarningDecision.ExpandedByDefault)
    if (warningDecision == ContentWarningDecision.Hidden && LocalHiddenContentPresentation.current == me.foxtails.palustris.domain.HiddenContentPresentation.Remove) return
    val bodyTruncated = truncateBody && postBodyCharacterCount(presentation.visibleText, post.emoji) > PostBodyCharacterLimit
    val bodyText = if (bodyTruncated) truncatedPostBody(presentation.visibleText, post.emoji) else presentation.visibleText
    Column(modifier.fillMaxWidth().testTag("post_row_${post.id.value}")) {
        post.resharedBy?.let {
            Row(Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 2.dp)) {
                AccountDisplayName(
                    it,
                    style = MaterialTheme.typography.labelMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                )
                Text(stringResource(R.string.post_reshared), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        PostMetadataRow(
            post = post,
            filteredHashtags = presentation.filteredHashtags.takeIf { contentVisible }.orEmpty(),
            onOpenProfile = onOpenProfile?.let { callback -> { callback(post.author) } },
            onSearchHashtag = onSearchHashtag,
            onOpenHashtagBubble = onOpenHashtagBubble,
            postOwned = ownedPost,
        )
        if (warningDecision == ContentWarningDecision.Hidden) {
             Text(stringResource(R.string.content_hidden_local_rule), Modifier.padding(horizontal = 16.dp, vertical = 12.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@Column
        }
        if (post.contentVisibility != me.foxtails.palustris.domain.PostContentVisibility.Visible) {
            Text(
                stringResource(
                    if (post.contentVisibility == me.foxtails.palustris.domain.PostContentVisibility.Hidden) {
                        R.string.post_content_unavailable
                    } else {
                        R.string.post_content_filtered
                    },
                ),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (interactionPresentation.showInteractionSummary) {
                InteractionSummaryRow(post.interactionCounts)
            }
            return@Column
        }
        if (post.replyTo != null) Text(stringResource(R.string.post_reply), Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        if (post.contentWarning != null) {
            InlineEmojiText(post.contentWarning.ifBlank { stringResource(R.string.content_warning) }, post.emoji, Modifier.padding(horizontal = 16.dp), MaterialTheme.typography.bodyLarge)
            TextButton(onClick = { expanded = !expanded }, modifier = Modifier.padding(horizontal = 4.dp)) { Text(stringResource(if (expanded) R.string.content_warning_hide else R.string.content_warning_show)) }
        }
        ExpandableContent(visible = contentVisible, modifier = Modifier.fillMaxWidth()) {
            val timestamp = postTimestamp(post)
            if (presentation.visibleText.isNotBlank() || timestamp != null) SelectionContainer {
                Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp)) {
                    if (presentation.visibleText.isNotBlank()) {
                        if (bodyTruncated) {
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                PostBodyText(
                                    text = bodyText,
                                    emoji = post.emoji,
                                    onTextTap = onOpenPost?.let { callback -> { callback(ownedPost) } },
                                    onOpenUrl = onOpenUrl,
                                    onOpenUsername = onOpenUsername,
                                    onSearchHashtag = onSearchHashtag,
                                    style = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                                )
                                onOpenPost?.let { callback -> ViewFullPostBubble { callback(ownedPost) } }
                            }
                        } else {
                            PostBodyText(
                                text = bodyText,
                                emoji = post.emoji,
                                onTextTap = onOpenPost?.let { callback -> { callback(ownedPost) } },
                                onOpenUrl = onOpenUrl,
                                onOpenUsername = onOpenUsername,
                                onSearchHashtag = onSearchHashtag,
                                style = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                            )
                        }
                    }
                    timestamp?.let {
                        if (presentation.visibleText.isNotBlank()) Spacer(Modifier.height(2.dp))
                        val timeDescription = stringResource(R.string.post_time)
                        Text(
                            it,
                            modifier = Modifier.semantics { contentDescription = timeDescription },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            PostMediaCarousel(ownedPost = ownedPost, onOpenMedia = onOpenMedia)
            post.pollOptions.forEach { option ->
                Surface(Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth(), shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainer) {
                    Row(Modifier.padding(12.dp)) {
                        InlineEmojiText(option.text, post.emoji, Modifier.weight(1f), MaterialTheme.typography.bodyMedium)
                        Text(pluralStringResource(R.plurals.post_poll_votes, option.votes, option.votes), style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
            post.quote?.let { quote ->
                val quoteDecision = ContentWarningPolicy.decide(
                    quote.contentWarning,
                     postHashtags(quote.text, quote.emoji),
                    contentWarningRules,
                    bodyText = quote.text,
                )
                OutlinedCard(modifier = Modifier.fillMaxWidth().padding(16.dp), onClick = { openExternal(context, quote.url) }) {
                    Column(Modifier.padding(16.dp)) {
                        AccountDisplayName(quote.author, style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(8.dp))
                        if (quoteDecision != ContentWarningDecision.Hidden) {
                            InlineEmojiText(
                                quote.contentWarning?.ifBlank { stringResource(R.string.content_warning) } ?: quote.text,
                                quote.emoji,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 5,
                                overflow = TextOverflow.Ellipsis,
                            )
                         } else Text(stringResource(R.string.content_hidden_quoted), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(stringResource(R.string.post_view_quoted), Modifier.padding(top = 12.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
        if (largeLayout && !bodyTruncated && onOpenPost != null) {
            TextButton(
                onClick = { onOpenPost(ownedPost) },
                modifier = Modifier.padding(horizontal = 8.dp),
            ) {
                Text(stringResource(R.string.post_open))
            }
        }
        if (interactionPresentation.showInteractionSummary) {
            InteractionSummaryRow(post.interactionCounts)
        }
        if (post.reactions.any { it.count > 0 }) {
            ReactionRow(
                reactions = post.reactions,
                ownedPost = ownedPost,
                enabled = PostAction.React in availableActions,
                onReaction = onReaction,
                showReactionNumbers = interactionPresentation.showReactionNumbers,
            )
        }
        InteractionRow(
            ownedPost = ownedPost,
            availableActions = availableActions,
            onReply = onReply,
            onReact = onReact,
            onReshare = onReshare,
            onBookmark = onBookmark,
            onReaction = onReaction,
            quoteEnabled = quoteEnabled,
            onQuote = onQuote,
             onOpenReactionBubble = { target, bounds ->
                 onOpenReactionBubble(target, bounds)
             },
             onOpenReactionPicker = onOpenReactionPicker,
             onRepostConfirmationRequest = { target, bounds -> repostConfirmationOwner.request(target, bounds) },
             repostConfirmationOwner = repostConfirmationOwner,
              onShare = { target, bounds -> postActionOwner?.open(target, bounds) },
          )
      }
 }

internal fun actionsForPost(availableActions: Set<PostAction>, post: Post): Set<PostAction> =
    if (post.availableActions.isEmpty()) availableActions else availableActions.intersect(post.availableActions)

internal fun Post.hasVisibleInteractionSelection(): Boolean =
    favourited || myReaction != null || selectedReactions.isNotEmpty()

/** Stars show Mastodon favourite state. Hearts show like and reaction state on all other services. */
internal fun favouriteIconFor(ownedPost: OwnedPost): ImageVector {
    val selected = ownedPost.post.hasVisibleInteractionSelection()
    return if (ownedPost.fetchedBy.connection.protocol == Protocol.MASTODON) {
        if (selected) AppIcons.FilledStar else AppIcons.HollowStar
    } else {
        if (selected) AppIcons.FilledHeart else AppIcons.HollowHeart
    }
}

@Composable
internal fun PostBodyText(
    text: String,
    emoji: Map<String, CustomEmoji>,
    modifier: Modifier = Modifier,
    style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodyLarge,
    onTextTap: (() -> Unit)? = null,
    onOpenUrl: ((String) -> Unit)? = null,
    onOpenUsername: ((String) -> Unit)? = null,
    onSearchHashtag: ((String) -> Unit)? = null,
) {
    InlineEmojiText(
        text = text,
        emoji = emoji,
        modifier = modifier,
        style = style,
        onTextTap = onTextTap,
        enableInlineEntities = true,
        onOpenUrl = onOpenUrl,
        onOpenUsername = onOpenUsername,
        onSearchHashtag = onSearchHashtag,
    )
}

@Composable
internal fun PostMetadataRow(
    post: Post,
    filteredHashtags: List<String>,
    onOpenProfile: (() -> Unit)?,
    onSearchHashtag: ((String) -> Unit)?,
    onOpenHashtagBubble: ((OwnedPost, List<String>, Rect) -> Unit)?,
    postOwned: OwnedPost? = null,
) {
    val profileInteractionSource = remember { MutableInteractionSource() }
    val metadataDescription = stringResource(R.string.post_metadata)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = PostMetadataVerticalPadding)
            .height(PostChromeHeight)
            .semantics { contentDescription = metadataDescription },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .then(onOpenProfile?.let { callback ->
                    Modifier
                        .springPress(profileInteractionSource)
                        .clickable(
                            interactionSource = profileInteractionSource,
                            indication = LocalIndication.current,
                            onClick = callback,
                        )
                } ?: Modifier),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AccountAvatar(post.author, Modifier.size(40.dp))
            Spacer(Modifier.width(8.dp))
            AccountDisplayName(
                post.author,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (filteredHashtags.isNotEmpty() && (onSearchHashtag != null || onOpenHashtagBubble != null)) {
            Spacer(Modifier.width(4.dp))
            FilteredHashtagSummary(
                hashtags = filteredHashtags,
                onOpen = { bounds ->
                    if (onOpenHashtagBubble != null && postOwned != null) {
                        onOpenHashtagBubble(postOwned, filteredHashtags, bounds)
                    } else {
                        onSearchHashtag?.invoke(filteredHashtags.first())
                    }
                },
            )
        }
    }
}

@Composable
private fun ViewFullPostBubble(onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val fullPostDescription = stringResource(R.string.post_view_full)
    Surface(
        modifier = Modifier
            .height(32.dp)
            .widthIn(max = 124.dp)
            .springPress(interactionSource, pressedScale = LocalPalustrisMotionScheme.current.compactPressedScale)
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = onClick,
            )
            .semantics {
                 contentDescription = fullPostDescription
                role = Role.Button
            },
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Text(
            stringResource(R.string.post_view_full),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
        )
    }
}

internal fun postTimestamp(post: Post): String? = if (post.publishedAtEpochMillis > 0) {
    DateUtils.getRelativeTimeSpanString(
        post.publishedAtEpochMillis,
        System.currentTimeMillis(),
        DateUtils.MINUTE_IN_MILLIS,
        DateUtils.FORMAT_ABBREV_RELATIVE,
    ).toString()
} else {
    null
}

@Composable
private fun FilteredHashtagSummary(hashtags: List<String>, onOpen: (Rect) -> Unit) {
    val label = if (hashtags.size == 1) hashtags.first() else "${hashtags.first()} +${hashtags.size - 1}"
    val collapsedDescription = stringResource(R.string.post_action_bubble_collapsed)
    var bounds by remember { mutableStateOf(Rect.Zero) }
    val interactionSource = remember { MutableInteractionSource() }
    Box {
        Surface(
            modifier = Modifier
                .widthIn(max = 124.dp)
                .height(32.dp)
                .onGloballyPositioned { bounds = it.boundsInWindow() }
                .springPress(interactionSource, pressedScale = LocalPalustrisMotionScheme.current.compactPressedScale)
                .clickable(interactionSource = interactionSource, indication = LocalIndication.current) { onOpen(bounds) }
                .semantics {
                    contentDescription = hashtagSummaryDescription(hashtags)
                    role = Role.Button
                     stateDescription = collapsedDescription
                },
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ) {
            Text(
                label,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun hashtagSummaryDescription(hashtags: List<String>): String {
    if (hashtags.size == 1) return "1 hashtag: ${hashtags.first()}"
    return "${hashtags.size} hashtags: " + hashtags.dropLast(1).joinToString(", ") + " and ${hashtags.last()}"
}

private val CircleShapeForReaction = RoundedCornerShape(50)
private val ReactionChipHeight = 32.dp
private val ReactionEmojiSlotSize = 20.dp
private val ReactionChipMinWidth = 56.dp

@Composable
internal fun ReactionRow(
    reactions: List<Reaction>,
    ownedPost: OwnedPost,
    enabled: Boolean,
    onReaction: (OwnedPost, EmojiChoice) -> Unit,
    showReactionNumbers: Boolean = false,
) {
    val scheme = LocalPalustrisMotionScheme.current
    val context = LocalContext.current
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        reactions.filter { it.count > 0 }.forEach { reaction ->
            val interactionSource = remember(reaction.emoji) { MutableInteractionSource() }
            val countText = pluralStringResource(R.plurals.reaction_count, reaction.count, reaction.count)
            val reactionDescription = stringResource(R.string.post_reaction_accessibility, reaction.emoji, countText)
            val selectedStateDescription = if (reaction.selected) {
                if (enabled) {
                    stringResource(R.string.emoji_reaction_remove, reaction.emoji)
                } else {
                    stringResource(R.string.emoji_reaction_selected, reaction.emoji)
                }
            } else {
                null
            }
            val chipColor = rememberSelectedColor(
                selected = reaction.selected,
                selectedColor = MaterialTheme.colorScheme.secondaryContainer,
                unselectedColor = MaterialTheme.colorScheme.primaryContainer,
            )
            Surface(
                modifier = Modifier
                    .height(ReactionChipHeight)
                    .widthIn(min = ReactionChipMinWidth)
                    .springPress(interactionSource, pressedScale = scheme.compactPressedScale)
                    .combinedClickable(
                        enabled = enabled,
                        interactionSource = interactionSource,
                        indication = LocalIndication.current,
                        onClick = {
                            onReaction(ownedPost, EmojiChoice(reaction.emoji, reaction.emoji, reaction.emojiMetadata))
                        },
                    )
                    .testTag("reaction_chip_${reaction.emoji}")
                    .semantics {
                        contentDescription = reactionDescription
                        role = Role.Button
                        this.selected = reaction.selected
                        selectedStateDescription?.let { stateDescription = it }
                },
                shape = CircleShapeForReaction,
                color = chipColor,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ) {
                Row(
                    Modifier.fillMaxHeight().padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.size(ReactionEmojiSlotSize).testTag("reaction_emoji_slot_${reaction.emoji}"),
                        contentAlignment = Alignment.Center,
                    ) {
                        CustomEmojiImage(
                            emoji = reaction.emojiMetadata,
                            fallbackText = reaction.emoji,
                            modifier = Modifier.fillMaxSize(),
                            textStyle = MaterialTheme.typography.labelLarge.copy(fontSize = 16.sp),
                        )
                    }
                    if (showReactionNumbers && reaction.count > 1) {
                        Spacer(Modifier.width(4.dp))
                        Box(
                            Modifier.widthIn(min = 16.dp).testTag("reaction_count_${reaction.emoji}"),
                            contentAlignment = Alignment.Center,
                        ) {
                            AnimatedContent(
                                targetState = reaction.count,
                                transitionSpec = {
                                    if (scheme.reducedMotion) {
                                        EnterTransition.None togetherWith ExitTransition.None
                                    } else {
                                        (fadeIn(scheme.fastFadeIn) + scaleIn(initialScale = 0.86f, animationSpec = scheme.expressive)) togetherWith
                                            (fadeOut(scheme.fastFadeOut) + scaleOut(targetScale = 0.86f, animationSpec = scheme.expressive))
                                    }
                                },
                                label = "reactionCount",
                            ) { count -> Text(count.toString(), style = MaterialTheme.typography.labelMedium) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun InteractionSummaryRow(counts: PostInteractionCounts) {
    val metrics = buildList {
        counts.favouriteCount?.let { add("favourites" to (R.plurals.post_favourite_count to it)) }
        counts.reactionCount?.let { add("reactions" to (R.plurals.post_reaction_total to it)) }
        counts.repostCount?.let { add("reposts" to (R.plurals.post_repost_count to it)) }
        counts.quoteRepostCount?.let { add("quote_reposts" to (R.plurals.post_quote_repost_count to it)) }
        counts.replyCount?.let { add("replies" to (R.plurals.post_reply_count to it)) }
    }
    if (metrics.isEmpty()) return
    val summaryDescription = stringResource(R.string.post_interaction_summary)
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .testTag("interaction_summary")
            .semantics { contentDescription = summaryDescription },
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        metrics.forEach { (name, resourceAndCount) ->
            val (resource, count) = resourceAndCount
            Text(
                pluralStringResource(resource, count, count),
                modifier = Modifier.testTag("interaction_metric_$name"),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun InteractionRow(
    ownedPost: OwnedPost,
    availableActions: Set<PostAction>,
    onReply: (OwnedPost) -> Unit,
    onReact: (OwnedPost) -> Unit,
    onReshare: (OwnedPost) -> Unit,
    onBookmark: (OwnedPost) -> Unit,
    onReaction: (OwnedPost, EmojiChoice) -> Unit,
    quoteEnabled: Boolean,
    onQuote: (OwnedPost) -> Unit,
    onOpenReactionBubble: (OwnedPost, Rect) -> Unit,
    onOpenReactionPicker: (OwnedPost) -> Unit = {},
    onRepostConfirmationRequest: (OwnedPost, Rect) -> Unit = { _, _ -> },
    repostConfirmationOwner: PostRepostConfirmationOwner = LocalPostRepostConfirmationOwner.current,
    onShare: (OwnedPost, Rect) -> Unit,
) {
    val context = LocalContext.current
    val actionDescription = stringResource(R.string.post_actions)
    val pendingRepost = repostConfirmationOwner.pending

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(PostInteractionRowHeight)
            .padding(horizontal = 8.dp)
            .semantics { contentDescription = actionDescription },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        InteractionButton(
            Modifier.weight(1f),
            AppIcons.Comment,
            stringResource(R.string.post_action_reply),
            enabled = PostAction.Reply in availableActions,
            onClick = { onReply(ownedPost) },
        )
        InteractionButton(
            Modifier.weight(1f),
            AppIcons.RepostBeeline,
            stringResource(if (ownedPost.post.reposted) R.string.post_action_undo_repost else R.string.post_action_repost),
             enabled = PostAction.Reshare in availableActions,
             isSelected = ownedPost.post.reposted,
             onClick = {},
             onClickWithBounds = { bounds -> onRepostConfirmationRequest(ownedPost, bounds) },
             onLongClick = if (quoteEnabled) ({ onQuote(ownedPost) }) else null,
            customActionLabel = if (quoteEnabled) stringResource(R.string.post_action_quote) else null,
            onCustomAction = if (quoteEnabled) ({ onQuote(ownedPost); true }) else null,
        )
        Box(Modifier.weight(1f)) {
            val favouriteEnabled = PostAction.Favorite in availableActions
            val reactionEnabled = PostAction.React in availableActions
            fun openReactionBubble(bounds: Rect) = onOpenReactionBubble(ownedPost, bounds)
            InteractionButton(
                modifier = Modifier.fillMaxWidth(),
                icon = favouriteIconFor(ownedPost),
                label = stringResource(if (ownedPost.post.favourited) R.string.post_action_unfavorite else R.string.post_action_favorite),
             enabled = favouriteEnabled || reactionEnabled,
             isSelected = ownedPost.post.hasVisibleInteractionSelection(),
                onClick = {
                    when {
                        favouriteEnabled -> onReact(ownedPost)
                    }
                },
                onClickWithBounds = if (reactionEnabled) ({ bounds ->
                    if (!favouriteEnabled) openReactionBubble(bounds)
                }) else null,
                 reactionGestureKey = "${ownedPost.fetchedBy}:${ownedPost.post.id}:${ownedPost.sessionRevision}",
                 reactionLongPressEnabled = true,
                 onReactionCompact = { bounds -> openReactionBubble(bounds) },
                 onReactionExpanded = { bounds ->
                      onOpenReactionBubble(ownedPost, bounds)
                      onOpenReactionPicker(ownedPost)
                  },
                 onLongClick = if (reactionEnabled) ::openReactionBubble else null,
              )
        }
        InteractionButton(
            Modifier.weight(1f),
            if (ownedPost.post.saved) AppIcons.FilledBookmark else AppIcons.HollowBookmark,
            stringResource(if (ownedPost.post.saved) R.string.post_action_remove_bookmark else R.string.post_action_bookmark),
            enabled = PostAction.Bookmark in availableActions,
            isSelected = ownedPost.post.saved,
            onClick = { onBookmark(ownedPost) },
        )
          InteractionButton(
              Modifier.weight(1f),
              AppIcons.ShareBeeline,
              stringResource(R.string.post_action_share),
              onClick = {},
              onClickWithBounds = { bounds -> onShare(ownedPost, bounds) },
          )
    }
    if (pendingRepost?.fetchedBy == ownedPost.fetchedBy && pendingRepost.postId == ownedPost.post.id) {
        Popup(
            popupPositionProvider = WindowAnchorPositionProvider(
                pendingRepost.anchorBounds,
                BubblePlacement.Below,
            ),
            onDismissRequest = repostConfirmationOwner::dismiss,
            properties = PopupProperties(focusable = true, dismissOnBackPress = true, dismissOnClickOutside = true),
        ) {
            PillAction(
                label = stringResource(
                    if (pendingRepost.selected) R.string.post_action_undo_repost_confirmation
                    else R.string.post_action_repost_confirmation,
                ),
                onClick = { repostConfirmationOwner.confirm(ownedPost, onReshare) },
                modifier = Modifier.testTag("repost_confirmation"),
            )
        }
    }
}

@Composable
private fun InteractionButton(
    modifier: Modifier,
    icon: ImageVector,
    label: String,
    enabled: Boolean = true,
    isSelected: Boolean = false,
    selectedIndicator: String? = null,
    onClick: () -> Unit,
    onClickWithBounds: ((Rect) -> Unit)? = null,
    onLongClick: ((Rect) -> Unit)? = null,
    reactionGestureKey: Any? = null,
    reactionLongPressEnabled: Boolean = false,
    onReactionCompact: ((Rect) -> Unit)? = null,
    onReactionExpanded: ((Rect) -> Unit)? = null,
    customActionLabel: String? = null,
    onCustomAction: (() -> Boolean)? = null,
) {
    var bounds by remember { mutableStateOf(Rect.Zero) }
    var popTrigger by remember { mutableIntStateOf(0) }
    val interactionSource = remember { MutableInteractionSource() }
    val scheme = LocalPalustrisMotionScheme.current
    val density = LocalDensity.current
    val selectedDescription = stringResource(if (isSelected) R.string.post_action_selected else R.string.post_action_not_selected)
    val interactionSelectedDescription = stringResource(R.string.post_action_interaction_selected)
    Box(
        modifier = modifier
            .height(PostInteractionRowHeight)
            .onGloballyPositioned { bounds = it.boundsInWindow() }
            .then(
                if (reactionLongPressEnabled && onReactionCompact != null && onReactionExpanded != null) {
                    Modifier.reactionPickerGesture(
                        enabled = enabled,
                        gestureKey = reactionGestureKey ?: Unit,
                        thresholdPx = with(density) { 36.dp.toPx() },
                        onCompact = { onReactionCompact(bounds) },
                        onExpanded = { onReactionExpanded(bounds) },
                    )
                } else {
                    Modifier
                },
            )
            .springPress(interactionSource, enabled, scheme.compactPressedScale)
            .combinedClickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = {
                    popTrigger++
                    onClick()
                    onClickWithBounds?.invoke(bounds)
                },
                onLongClick = onLongClick?.let { callback ->
                    {
                        popTrigger++
                        callback(bounds)
                    }
                },
            )
            .semantics {
                contentDescription = label
                role = Role.Button
                this.selected = isSelected
                stateDescription = selectedDescription
                if (customActionLabel != null && onCustomAction != null) {
                    customActions = listOf(CustomAccessibilityAction(customActionLabel, onCustomAction))
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        PopEffect(popTrigger) {
            Icon(
                icon,
                label,
                Modifier.size(PostInteractionIconSize),
                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else 0.38f),
            )
            selectedIndicator?.let { indicator ->
                Text(
                    text = indicator,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .semantics { contentDescription = interactionSelectedDescription },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

internal fun sharePost(context: Context, post: Post, cleanTrackingParameters: Boolean = false) {
    val text = post.url?.let { me.foxtails.palustris.ui.links.ExternalLinkHandler.prepare(it) } ?: post.text
    try {
        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }, context.getString(R.string.post_action_share_post)))
    } catch (_: android.content.ActivityNotFoundException) {
        Toast.makeText(context, context.getString(R.string.error_no_app_share_post), Toast.LENGTH_SHORT).show()
    }
}
