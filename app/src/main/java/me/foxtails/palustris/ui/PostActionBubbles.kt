@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package me.foxtails.palustris.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt
import me.foxtails.palustris.R
import me.foxtails.palustris.domain.EmojiCapabilities
import me.foxtails.palustris.domain.EmojiChoice
import me.foxtails.palustris.domain.EntityId
import me.foxtails.palustris.domain.OwnedPost
import me.foxtails.palustris.domain.ReactionSelectionMode
import me.foxtails.palustris.ui.emoji.EmojiCatalogState
import me.foxtails.palustris.ui.emoji.EmojiChoiceGrid

private const val BubbleDismissDurationMillis = 150L
private const val ReactionFlickThreshold = 36f

enum class ReactionBubbleMode { Compact, Expanded }

sealed interface PostActionBubbleTarget {
    val postId: EntityId
    val anchorBounds: Rect

    data class HashtagList(
        override val postId: EntityId,
        val hashtags: List<String>,
        override val anchorBounds: Rect,
    ) : PostActionBubbleTarget

    data class Reaction(
        val ownedPost: OwnedPost,
        override val anchorBounds: Rect,
        val mode: ReactionBubbleMode = ReactionBubbleMode.Compact,
    ) : PostActionBubbleTarget {
        override val postId: EntityId get() = ownedPost.post.id
    }
}

/** A popup host for post actions. It never participates in page measurement. */
@Composable
fun PostActionBubbleHost(
    target: PostActionBubbleTarget?,
    emojiCatalog: EmojiCatalogState,
    emojiCapabilities: EmojiCapabilities,
    onLoadEmojiCatalog: () -> Unit = {},
    onRetryEmojiCatalog: () -> Unit = {},
    onDismiss: () -> Unit,
    onHashtagSelected: (String) -> Unit,
    onReactionSelected: (OwnedPost, EmojiChoice) -> Unit,
    onReactionModeChanged: (PostActionBubbleTarget.Reaction) -> Unit = {},
) {
    var renderedTarget by remember { mutableStateOf<PostActionBubbleTarget?>(null) }
    var visible by remember { mutableStateOf(false) }
    var localReactionMode by remember { mutableStateOf(ReactionBubbleMode.Compact) }
    val dismiss by rememberUpdatedState(onDismiss)

    LaunchedEffect(target) {
        if (target != null) {
            renderedTarget = target
            localReactionMode = (target as? PostActionBubbleTarget.Reaction)?.mode ?: ReactionBubbleMode.Compact
            visible = true
        } else if (renderedTarget != null) {
            visible = false
            delay(BubbleDismissDurationMillis)
            renderedTarget = null
        }
    }

    val reactionTarget = renderedTarget as? PostActionBubbleTarget.Reaction
    if (reactionTarget != null && emojiCapabilities.reactionMutation != me.foxtails.palustris.domain.CapabilityStatus.Supported) {
        LaunchedEffect(reactionTarget) { dismiss() }
        return
    }
    if (renderedTarget == null) return

    LaunchedEffect(renderedTarget?.postId) {
        if (renderedTarget is PostActionBubbleTarget.Reaction) onLoadEmojiCatalog()
    }

    val placement = if (renderedTarget is PostActionBubbleTarget.Reaction) BubblePlacement.Above else BubblePlacement.Below
    Popup(
        popupPositionProvider = WindowAnchorPositionProvider(renderedTarget!!.anchorBounds, placement),
        onDismissRequest = dismiss,
        properties = PopupProperties(
            focusable = true,
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            clippingEnabled = false,
        ),
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            when (val current = renderedTarget) {
                is PostActionBubbleTarget.HashtagList -> HashtagBubble(
                    hashtags = current.hashtags,
                    onSelected = { hashtag ->
                        onHashtagSelected(hashtag)
                        dismiss()
                    },
                )
                is PostActionBubbleTarget.Reaction -> ReactionBubble(
                    target = current.copy(mode = localReactionMode),
                    catalog = emojiCatalog,
                    selectionMode = emojiCapabilities.selectionMode,
                    onRetryCatalog = onRetryEmojiCatalog,
                    onSelected = { choice ->
                        onReactionSelected(current.ownedPost, choice)
                        dismiss()
                    },
                    onExpanded = {
                        val expanded = current.copy(mode = ReactionBubbleMode.Expanded)
                        localReactionMode = ReactionBubbleMode.Expanded
                        onReactionModeChanged(expanded)
                    },
                )
                null -> Unit
            }
        }
    }
}

@Composable
private fun HashtagBubble(
    hashtags: List<String>,
    onSelected: (String) -> Unit,
) {
    val listDescription = stringResource(R.string.post_action_hashtags_expanded)
    val expandedDescription = stringResource(R.string.post_action_bubble_expanded)
    LazyColumn(
        modifier = Modifier
            .widthIn(min = 160.dp, max = 280.dp)
            .heightIn(max = 480.dp)
            .testTag("hashtag_bubble_list")
            .semantics {
                contentDescription = listDescription
                stateDescription = expandedDescription
            },
        contentPadding = PaddingValues(4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        itemsIndexed(hashtags, key = { _, hashtag -> hashtag }) { _, hashtag ->
            val hashtagDescription = stringResource(R.string.post_action_hashtag_description, hashtag)
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Button) { onSelected(hashtag) }
                    .semantics {
                        contentDescription = hashtagDescription
                        role = Role.Button
                    }
                    .testTag("hashtag_bubble_$hashtag"),
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ) {
                androidx.compose.material3.Text(
                    hashtag,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

@Composable
private fun ReactionBubble(
    target: PostActionBubbleTarget.Reaction,
    catalog: EmojiCatalogState,
    selectionMode: ReactionSelectionMode,
    onRetryCatalog: () -> Unit,
    onSelected: (EmojiChoice) -> Unit,
    onExpanded: () -> Unit,
) {
    val expanded = target.mode == ReactionBubbleMode.Expanded
    val post = target.ownedPost.post
    val selectedIdentities = remember(post) {
        buildSet {
            post.selectedReactions.forEach { add(it.submissionValue) }
            post.reactions.filter { it.selected }.forEach { add(it.emoji) }
            post.myReaction?.let(::add)
        }
    }
    var verticalDistance by remember(target.postId, target.mode) { mutableStateOf(0f) }
    val bubbleDescription = stringResource(
        if (expanded) R.string.post_action_reactions_expanded else R.string.post_action_reactions_collapsed,
    )
    val bubbleStateDescription = stringResource(
        if (expanded) R.string.post_action_bubble_expanded else R.string.post_action_bubble_collapsed,
    )
    Surface(
        modifier = Modifier
            .widthIn(min = 184.dp, max = 320.dp)
            .semantics {
                contentDescription = bubbleDescription
                stateDescription = bubbleStateDescription
            }
            .pointerInput(target.postId, target.mode) {
                detectVerticalDragGestures(
                    onVerticalDrag = { change, amount ->
                        verticalDistance += amount
                    },
                    onDragEnd = {
                        if (!expanded && abs(verticalDistance) >= ReactionFlickThreshold) onExpanded()
                        verticalDistance = 0f
                    },
                    onDragCancel = { verticalDistance = 0f },
                )
            }
            .testTag(if (expanded) "reaction_bubble_expanded" else "reaction_bubble_compact"),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 8.dp,
    ) {
        if (expanded) {
            EmojiChoiceGrid(
                catalogItems = catalog.items,
                additionalChoices = post.reactions.map { reaction ->
                    EmojiChoice(
                        submissionValue = reaction.emoji,
                        displayText = reaction.emoji,
                        emoji = reaction.emojiMetadata,
                    )
                },
                selectedIdentities = selectedIdentities,
                compact = false,
                modifier = Modifier.heightIn(max = 520.dp).padding(horizontal = 8.dp, vertical = 8.dp),
                testTag = "reaction_bubble_grid",
                onEmojiSelected = onSelected,
            )
        } else {
            EmojiChoiceGrid(
                catalogItems = catalog.items,
                additionalChoices = post.reactions.map { reaction ->
                    EmojiChoice(
                        submissionValue = reaction.emoji,
                        displayText = reaction.emoji,
                        emoji = reaction.emojiMetadata,
                    )
                },
                selectedIdentities = selectedIdentities,
                compact = true,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                testTag = "reaction_bubble_grid",
                onEmojiSelected = onSelected,
            )
        }
    }
}

private enum class BubblePlacement { Above, Below }

private class WindowAnchorPositionProvider(
    private val targetBounds: Rect,
    private val placement: BubblePlacement,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val fallback = Rect(
            anchorBounds.left.toFloat(),
            anchorBounds.top.toFloat(),
            anchorBounds.right.toFloat(),
            anchorBounds.bottom.toFloat(),
        )
        val anchor = targetBounds.takeIf { it.width > 0f && it.height > 0f } ?: fallback
        val margin = 8
        val x = anchor.left.roundToInt().coerceIn(
            margin,
            (windowSize.width - popupContentSize.width - margin).coerceAtLeast(margin),
        )
        val preferredY = when (placement) {
            BubblePlacement.Above -> anchor.top.roundToInt() - popupContentSize.height - margin
            BubblePlacement.Below -> anchor.bottom.roundToInt() + margin
        }
        val alternateY = when (placement) {
            BubblePlacement.Above -> anchor.bottom.roundToInt() + margin
            BubblePlacement.Below -> anchor.top.roundToInt() - popupContentSize.height - margin
        }
        val maxY = (windowSize.height - popupContentSize.height - margin).coerceAtLeast(margin)
        val y = if (preferredY in margin..maxY) preferredY else alternateY.coerceIn(margin, maxY)
        return IntOffset(x, y)
    }
}
