package me.foxtails.palustris.ui.posts

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
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

/** Options captured when an action starts. A preference change cannot alter them mid-flight. */
private data class ActionOptions(
    val reactionFavourite: Boolean,
    val selectionMode: ReactionSelectionMode,
    val favouriteEmoji: String,
)

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
    private val executionAuthority: PostInteractionExecutionAuthority = PostInteractionExecutionAuthority(),
) {
    private val jobs = mutableMapOf<ActionKey, Job>()
    private var stopped = false

    fun favorite(ownedPost: OwnedPost, onAccepted: (PostActionResult) -> Unit = {}) {
        val emoji = favouriteEmoji()
        val reactionFavourite = source.capabilities.primaryFavourite.mode == PrimaryFavouriteMode.Reaction
        val selectionMode = source.capabilities.emoji.selectionMode
        val options = ActionOptions(reactionFavourite, selectionMode, emoji)
        val selected = !ownedPost.post.favourited
        val target = ownedPost.effectiveTargetId()
        val staleReaction = if (selected && reactionFavourite) {
            ownedPost.post.myReaction?.takeIf { it != emoji }
        } else null
        runAction(ownedPost, PostAction.Favorite, options, { post ->
            if (reactionFavourite) {
                PostReactionReducer.apply(
                    post,
                    EmojiChoice(emoji, emoji),
                    selected,
                    selectionMode,
                    emoji,
                )
            } else {
                post.copy(
                    favourited = selected,
                    interactionCounts = post.interactionCounts.copy(
                        favouriteCount = post.interactionCounts.favouriteCount.adjustedBy(if (selected) 1 else -1),
                    ),
                )
            }
        }, {
            staleReaction?.let { source.removeReaction(target, it) }
            source.setPrimaryFavourite(target, emoji, selected)
        }, partialRisk = staleReaction != null, onAccepted)
    }

    fun reshare(ownedPost: OwnedPost, onAccepted: (PostActionResult) -> Unit = {}) {
        val options = ActionOptions(
            reactionFavourite = false,
            selectionMode = source.capabilities.emoji.selectionMode,
            favouriteEmoji = favouriteEmoji(),
        )
        val selected = !ownedPost.post.reposted
        val target = ownedPost.effectiveTargetId()
        runAction(ownedPost, PostAction.Reshare, options, { post ->
            post.copy(
                reposted = selected,
                interactionCounts = post.interactionCounts.copy(
                    repostCount = post.interactionCounts.repostCount.adjustedBy(if (selected) 1 else -1),
                ),
            )
        }, {
            source.setReshared(target, selected, ownedPost.post.ownRepostId)
        }, partialRisk = false, onAccepted)
    }

    fun react(ownedPost: OwnedPost, choice: EmojiChoice, onAccepted: (PostActionResult) -> Unit = {}) {
        val mode = source.capabilities.emoji.selectionMode
        val identity = choice.submissionValue
        val selected = ownedPost.post.selectedReactions.any { it.submissionValue == identity } ||
            ownedPost.post.myReaction == identity
        val primary = favouriteEmoji().takeIf {
            source.capabilities.primaryFavourite.mode == PrimaryFavouriteMode.Reaction
        }
        val options = ActionOptions(primary != null, mode, favouriteEmoji())
        val target = ownedPost.effectiveTargetId()
        val previous = ownedPost.post.selectedReactions.ifEmpty {
            ownedPost.post.myReaction?.let { mine ->
                listOf(EmojiChoice(
                    mine,
                    mine,
                    ownedPost.post.reactions.firstOrNull { it.emoji == mine }?.emojiMetadata,
                ))
            }.orEmpty()
        }
        val removals = if (selected) {
            listOf(choice)
        } else if (mode != ReactionSelectionMode.Independent) {
            previous.filterNot { it.submissionValue == identity }
        } else {
            emptyList()
        }
        runAction(ownedPost, PostAction.React, options, { post ->
            PostReactionReducer.apply(post, choice, !selected, mode, primary)
        }, {
            if (selected) {
                source.removeReaction(target, choice)
            } else {
                removals.forEach { source.removeReaction(target, it) }
                source.react(target, choice)
            }
            PostActionResult(selected = !selected)
        }, partialRisk = !selected && removals.isNotEmpty(), onAccepted)
    }

    fun bookmark(ownedPost: OwnedPost, onAccepted: (PostActionResult) -> Unit = {}) {
        val options = ActionOptions(
            reactionFavourite = false,
            selectionMode = source.capabilities.emoji.selectionMode,
            favouriteEmoji = favouriteEmoji(),
        )
        val selected = !ownedPost.post.saved
        val target = ownedPost.effectiveTargetId()
        runAction(ownedPost, PostAction.Bookmark, options, { post -> post.copy(saved = selected) }, {
            source.setSaved(target, selected)
        }, partialRisk = false, onAccepted)
    }

    fun stop() {
        stopped = true
        jobs.values.forEach(Job::cancel)
        jobs.clear()
    }

    private fun runAction(
        ownedPost: OwnedPost,
        action: PostAction,
        options: ActionOptions,
        optimistic: (Post) -> Post,
        operation: suspend () -> PostActionResult,
        partialRisk: Boolean,
        onAccepted: (PostActionResult) -> Unit,
    ) {
        if (stopped || ownedPost.fetchedBy != accountId || ownedPost.sessionRevision != sessionRevision) return
        if (!isActionAvailable(action)) return
        val target = ownedPost.effectiveTargetId()
        val family = actionFamily(action)
        val key = ActionKey(family, target)
        if (jobs[key]?.isActive == true) return
        if (!executionAuthority.acquire(accountId, sessionRevision, family, target)) return
        val before = ownedPost.post
        val optimisticPost = optimistic(before)
        updatePost(ownedPost, target) { optimistic(it) }
        val job = scope.launch {
            try {
                if (stopped || ownedPost.fetchedBy != accountId || ownedPost.sessionRevision != sessionRevision) return@launch
                val result = operation()
                if (stopped || ownedPost.fetchedBy != accountId || ownedPost.sessionRevision != sessionRevision) return@launch
                updatePost(ownedPost, target) { current -> reconcile(action, current, result, options) }
                onAccepted(result)
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                if (stopped || ownedPost.fetchedBy != accountId || ownedPost.sessionRevision != sessionRevision) return@launch
                handleFailure(action, ownedPost, target, before, optimisticPost, options, partialRisk, error)
            } finally {
                if (jobs[key] === currentCoroutineContext()[Job]) jobs.remove(key)
                executionAuthority.release(accountId, sessionRevision, family, target)
            }
        }
        jobs[key] = job
    }

    private suspend fun handleFailure(
        action: PostAction,
        ownedPost: OwnedPost,
        target: me.foxtails.palustris.domain.EntityId,
        before: Post,
        optimisticPost: Post,
        options: ActionOptions,
        partialRisk: Boolean,
        error: Exception,
    ) {
        // A replaced reaction may have removed the old reaction before the add failed.
        // Local rollback is then not proof of server state, so reconcile one bounded
        // refresh instead. Any refresh failure falls back to a guarded rollback.
        if (partialRisk) {
            val fresh = runCatching { source.post(target) }.getOrNull()?.takeIf { it.id == target }
            if (fresh != null) {
                updatePost(ownedPost, target) { current -> reconcileFromServer(action, current, fresh, options) }
                onFailure(error)
                return
            }
        }
        updatePost(ownedPost, target) { current -> rollback(action, current, before, optimisticPost) }
        onFailure(error)
    }

    private fun reconcile(
        action: PostAction,
        current: Post,
        result: PostActionResult,
        options: ActionOptions,
    ): Post {
        // Only family-owned fields and counts come from the server response. Every other
        // field stays current so a stale snapshot cannot erase another family's state.
        val serverCounts = result.post?.takeIf { it.id == current.id }?.interactionCounts
        val counts = mergedCounts(action, current, serverCounts, options)
        return when (action) {
            PostAction.Favorite -> if (options.reactionFavourite) {
                current.copy(
                    favourited = result.selected ?: current.favourited,
                    myReaction = if (result.selected == true) options.favouriteEmoji else null,
                    interactionCounts = counts,
                )
            } else {
                current.copy(
                    favourited = result.selected ?: current.favourited,
                    interactionCounts = counts,
                )
            }
            PostAction.React -> current.copy(interactionCounts = counts)
            PostAction.Reshare -> current.copy(
                reposted = result.selected ?: current.reposted,
                ownRepostId = when {
                    result.selected == false -> null
                    result.createdRepostId != null -> result.createdRepostId
                    else -> current.ownRepostId
                },
                interactionCounts = counts,
            )
            PostAction.Bookmark -> current.copy(
                saved = result.selected ?: current.saved,
                interactionCounts = counts,
            )
            PostAction.Reply -> current.copy(interactionCounts = counts)
        }
    }

    private fun reconcileFromServer(action: PostAction, current: Post, fresh: Post, options: ActionOptions): Post {
        val counts = mergedCounts(action, current, fresh.interactionCounts, options)
        return when (action) {
            PostAction.Favorite -> current.copy(
                favourited = fresh.favourited,
                myReaction = fresh.myReaction,
                selectedReactions = fresh.selectedReactions,
                reactions = fresh.reactions,
                interactionCounts = counts,
            )
            PostAction.React -> current.copy(
                favourited = fresh.favourited,
                myReaction = fresh.myReaction,
                selectedReactions = fresh.selectedReactions,
                reactions = fresh.reactions,
                interactionCounts = counts,
            )
            PostAction.Reshare -> current.copy(
                reposted = fresh.reposted,
                ownRepostId = fresh.ownRepostId,
                interactionCounts = counts,
            )
            PostAction.Bookmark -> current.copy(saved = fresh.saved, interactionCounts = counts)
            PostAction.Reply -> current.copy(interactionCounts = counts)
        }
    }

    private fun mergedCounts(
        action: PostAction,
        current: Post,
        server: me.foxtails.palustris.domain.PostInteractionCounts?,
        options: ActionOptions,
    ): me.foxtails.palustris.domain.PostInteractionCounts {
        if (server == null) return current.interactionCounts
        // Known server counts win, except the acted family's count never regresses below
        // the optimistic local value. Unknown counts stay unknown on both sides.
        val merged = current.interactionCounts.merge(server)
        return when (action) {
            PostAction.Favorite -> if (options.reactionFavourite) {
                merged.copy(reactionCount = maxKnown(current.interactionCounts.reactionCount, merged.reactionCount))
            } else {
                merged.copy(favouriteCount = maxKnown(current.interactionCounts.favouriteCount, merged.favouriteCount))
            }
            PostAction.React -> merged.copy(reactionCount = maxKnown(current.interactionCounts.reactionCount, merged.reactionCount))
            PostAction.Reshare -> merged.copy(repostCount = maxKnown(current.interactionCounts.repostCount, merged.repostCount))
            PostAction.Bookmark, PostAction.Reply -> merged
        }
    }

    private fun maxKnown(local: Int?, remote: Int?): Int? =
        if (local != null && remote != null) maxOf(local, remote) else remote ?: local

    private fun rollback(
        action: PostAction,
        current: Post,
        before: Post,
        optimisticPost: Post,
    ): Post {
        // Restore only family fields this operation still owns. A field that no longer
        // matches the optimistic value was changed by a newer projection and is kept.
        return when (action) {
            PostAction.Favorite, PostAction.React -> current.copy(
                favourited = restored(current.favourited, before.favourited, optimisticPost.favourited),
                myReaction = restored(current.myReaction, before.myReaction, optimisticPost.myReaction),
                selectedReactions = restored(current.selectedReactions, before.selectedReactions, optimisticPost.selectedReactions),
                reactions = restored(current.reactions, before.reactions, optimisticPost.reactions),
                interactionCounts = current.interactionCounts.copy(
                    favouriteCount = restored(
                        current.interactionCounts.favouriteCount,
                        before.interactionCounts.favouriteCount,
                        optimisticPost.interactionCounts.favouriteCount,
                    ),
                    reactionCount = restored(
                        current.interactionCounts.reactionCount,
                        before.interactionCounts.reactionCount,
                        optimisticPost.interactionCounts.reactionCount,
                    ),
                ),
            )
            PostAction.Reshare -> current.copy(
                reposted = restored(current.reposted, before.reposted, optimisticPost.reposted),
                ownRepostId = restored(current.ownRepostId, before.ownRepostId, optimisticPost.ownRepostId),
                interactionCounts = current.interactionCounts.copy(
                    repostCount = restored(
                        current.interactionCounts.repostCount,
                        before.interactionCounts.repostCount,
                        optimisticPost.interactionCounts.repostCount,
                    ),
                ),
            )
            PostAction.Bookmark -> current.copy(
                saved = restored(current.saved, before.saved, optimisticPost.saved),
            )
            PostAction.Reply -> current
        }
    }

    private fun <T> restored(current: T, before: T, optimistic: T): T =
        if (current == optimistic) before else current

    private fun actionFamily(action: PostAction): String = when (action) {
        PostAction.Favorite, PostAction.React -> "favorite-reaction"
        else -> action.name
    }

    private data class ActionKey(val family: String, val target: me.foxtails.palustris.domain.EntityId)
}

private fun Int?.adjustedBy(delta: Int): Int? = this?.let {
    (it.toLong() + delta).coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
}
