package me.foxtails.palustris.ui.posts

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.EmojiChoice
import me.foxtails.palustris.domain.OwnedPost
import me.foxtails.palustris.domain.Post
import me.foxtails.palustris.domain.PostAction
import me.foxtails.palustris.domain.PostActionResult
import me.foxtails.palustris.domain.PostReactionReducer
import me.foxtails.palustris.domain.PrimaryFavouriteMode
import me.foxtails.palustris.domain.ReactionSelectionMode
import me.foxtails.palustris.domain.SocialSource
import me.foxtails.palustris.domain.effectiveTargetId

/** Owns account-bound post mutations while collections remain presentation owners. */
class PostInteractionMutationOwner(
    private val accountId: AccountId,
    private val source: SocialSource,
    private val sessionRevision: Long,
    private val scope: CoroutineScope,
    private val isActionAvailable: (PostAction) -> Boolean,
    private val favouriteEmoji: () -> String,
    private val updatePost: (ownedPost: OwnedPost, target: me.foxtails.palustris.domain.EntityId, transform: (Post) -> Post) -> Unit,
    private val onFailure: (Exception) -> Unit,
) {
    private val jobs = mutableMapOf<ActionKey, Job>()
    private var stopped = false

    fun favorite(ownedPost: OwnedPost) {
        val selected = !ownedPost.post.favourited
        val target = ownedPost.effectiveTargetId()
        val reactionFavourite = source.capabilities.primaryFavourite.mode == PrimaryFavouriteMode.Reaction
        runAction(ownedPost, PostAction.Favorite, { post ->
            if (reactionFavourite) {
                PostReactionReducer.apply(
                    post,
                    EmojiChoice(favouriteEmoji(), favouriteEmoji()),
                    selected,
                    source.capabilities.emoji.selectionMode,
                    favouriteEmoji(),
                )
            } else {
                post.copy(
                    favourited = selected,
                    interactionCounts = post.interactionCounts.copy(
                        favouriteCount = post.interactionCounts.favouriteCount.adjustedBy(if (selected) 1 else -1),
                    ),
                )
            }
        }) {
            if (selected && reactionFavourite) {
                ownedPost.post.myReaction?.takeIf { it != favouriteEmoji() }?.let { source.removeReaction(target, it) }
            }
            source.setPrimaryFavourite(target, favouriteEmoji(), selected)
        }
    }

    fun reshare(ownedPost: OwnedPost) {
        val selected = !ownedPost.post.reposted
        val target = ownedPost.effectiveTargetId()
        runAction(ownedPost, PostAction.Reshare, { post ->
            post.copy(
                reposted = selected,
                interactionCounts = post.interactionCounts.copy(
                    repostCount = post.interactionCounts.repostCount.adjustedBy(if (selected) 1 else -1),
                ),
            )
        }) { source.setReshared(target, selected, ownedPost.post.ownRepostId) }
    }

    fun react(ownedPost: OwnedPost, choice: EmojiChoice) {
        val mode = source.capabilities.emoji.selectionMode
        val identity = choice.submissionValue
        val selected = ownedPost.post.selectedReactions.any { it.submissionValue == identity } ||
            ownedPost.post.myReaction == identity
        val primary = favouriteEmoji().takeIf {
            source.capabilities.primaryFavourite.mode == PrimaryFavouriteMode.Reaction
        }
        val target = ownedPost.effectiveTargetId()
        runAction(ownedPost, PostAction.React, { post ->
            PostReactionReducer.apply(post, choice, !selected, mode, primary)
        }) {
            val previous = ownedPost.post.selectedReactions.ifEmpty {
                ownedPost.post.myReaction?.let { mine ->
                    listOf(EmojiChoice(
                        mine,
                        mine,
                        ownedPost.post.reactions.firstOrNull { it.emoji == mine }?.emojiMetadata,
                    ))
                }.orEmpty()
            }
            if (selected) {
                source.removeReaction(target, choice)
            } else {
                if (mode != ReactionSelectionMode.Independent) {
                    previous.filterNot { it.submissionValue == identity }
                        .forEach { source.removeReaction(target, it) }
                }
                source.react(target, choice)
            }
            PostActionResult(selected = !selected)
        }
    }

    fun bookmark(ownedPost: OwnedPost) {
        val selected = !ownedPost.post.saved
        val target = ownedPost.effectiveTargetId()
        runAction(ownedPost, PostAction.Bookmark, { post -> post.copy(saved = selected) }) {
            source.setSaved(target, selected)
        }
    }

    fun stop() {
        stopped = true
        jobs.values.forEach(Job::cancel)
        jobs.clear()
    }

    private fun runAction(
        ownedPost: OwnedPost,
        action: PostAction,
        optimistic: (Post) -> Post,
        operation: suspend () -> PostActionResult,
    ) {
        if (stopped || ownedPost.fetchedBy != accountId || ownedPost.sessionRevision != sessionRevision) return
        if (!isActionAvailable(action)) return
        val target = ownedPost.effectiveTargetId()
        val key = ActionKey(actionFamily(action), target)
        if (jobs[key]?.isActive == true) return
        val before = ownedPost.post
        updatePost(ownedPost, target) { optimistic(it) }
        val job = scope.launch {
            try {
                if (stopped || ownedPost.fetchedBy != accountId || ownedPost.sessionRevision != sessionRevision) return@launch
                val result = operation()
                updatePost(ownedPost, target) { current -> reconcile(action, current, result) }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                updatePost(ownedPost, target) { current -> rollback(action, current, before) }
                onFailure(error)
            } finally {
                jobs.remove(key)
            }
        }
        jobs[key] = job
    }

    private fun reconcile(action: PostAction, current: Post, result: PostActionResult): Post {
        val server = result.post?.takeIf { it.id == current.id }
        val base = server?.copy(interactionCounts = current.interactionCounts.merge(server.interactionCounts)) ?: current
        return when (action) {
            PostAction.Favorite -> base.copy(
                favourited = result.selected ?: base.favourited,
                myReaction = if (source.capabilities.primaryFavourite.mode == PrimaryFavouriteMode.Reaction) {
                    if (result.selected == true) favouriteEmoji() else null
                } else base.myReaction,
            )
            PostAction.React -> base
            PostAction.Reshare -> base.copy(
                reposted = result.selected ?: base.reposted,
                ownRepostId = when {
                    result.selected == false -> null
                    result.createdRepostId != null -> result.createdRepostId
                    else -> base.ownRepostId
                },
            )
            PostAction.Bookmark -> base.copy(saved = result.selected ?: base.saved)
            PostAction.Reply -> base
        }
    }

    private fun rollback(action: PostAction, current: Post, before: Post): Post = when (action) {
        PostAction.Favorite -> if (source.capabilities.primaryFavourite.mode == PrimaryFavouriteMode.Reaction) {
            current.copy(
                favourited = before.favourited,
                myReaction = before.myReaction,
                selectedReactions = before.selectedReactions,
                reactions = before.reactions,
                interactionCounts = current.interactionCounts.copy(reactionCount = before.interactionCounts.reactionCount),
            )
        } else {
            current.copy(
                favourited = before.favourited,
                interactionCounts = current.interactionCounts.copy(favouriteCount = before.interactionCounts.favouriteCount),
            )
        }
        PostAction.React -> current.copy(
            favourited = before.favourited,
            myReaction = before.myReaction,
            selectedReactions = before.selectedReactions,
            reactions = before.reactions,
            interactionCounts = current.interactionCounts.copy(reactionCount = before.interactionCounts.reactionCount),
        )
        PostAction.Reshare -> current.copy(
            reposted = before.reposted,
            ownRepostId = before.ownRepostId,
            interactionCounts = current.interactionCounts.copy(repostCount = before.interactionCounts.repostCount),
        )
        PostAction.Bookmark -> current.copy(saved = before.saved)
        PostAction.Reply -> current
    }

    private fun actionFamily(action: PostAction): String = when (action) {
        PostAction.Favorite, PostAction.React -> "favorite-reaction"
        else -> action.name
    }

    private data class ActionKey(val family: String, val target: me.foxtails.palustris.domain.EntityId)
}

private fun Int?.adjustedBy(delta: Int): Int? = this?.let {
    (it.toLong() + delta).coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
}
