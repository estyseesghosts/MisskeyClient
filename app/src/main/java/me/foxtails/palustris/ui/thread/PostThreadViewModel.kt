package me.foxtails.palustris.ui.thread

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import me.foxtails.palustris.data.preferences.InMemoryPostPreferencesRepository
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.EntityId
import me.foxtails.palustris.domain.CapabilityStatus
import me.foxtails.palustris.domain.EmojiChoice
import me.foxtails.palustris.domain.OwnedPost
import me.foxtails.palustris.domain.Post
import me.foxtails.palustris.domain.PostAction
import me.foxtails.palustris.domain.PostActionResult
import me.foxtails.palustris.domain.PostPreferencesRepository
import me.foxtails.palustris.domain.PostReactionReducer
import me.foxtails.palustris.domain.PrimaryFavouriteMode
import me.foxtails.palustris.domain.ReactionSelectionMode
import me.foxtails.palustris.domain.SocialSource
import me.foxtails.palustris.domain.SourceError
import me.foxtails.palustris.domain.ThreadContext
import me.foxtails.palustris.domain.ThreadLimitation
import me.foxtails.palustris.domain.ThreadRow
import me.foxtails.palustris.domain.ThreadSessionKey
import me.foxtails.palustris.domain.ThreadTreeBuilder
import me.foxtails.palustris.domain.effectiveTargetId
import me.foxtails.palustris.domain.mergeExternalActionFields
import me.foxtails.palustris.domain.DEFAULT_FAVOURITE_EMOJI
import me.foxtails.palustris.domain.normalizeFavouriteEmoji
import me.foxtails.palustris.domain.adjustedBy
import me.foxtails.palustris.ui.posts.PostActionFamily
import me.foxtails.palustris.ui.posts.PostInteractionExecutionAuthority

@HiltViewModel(assistedFactory = PostThreadViewModel.Factory::class)
class PostThreadViewModel @AssistedInject constructor(
    @Assisted val accountId: AccountId,
    @Assisted private val source: SocialSource,
    @Assisted private val sessionRevision: Long,
    private val preferences: PostPreferencesRepository,
    private val executionAuthority: PostInteractionExecutionAuthority = PostInteractionExecutionAuthority(),
) : ViewModel() {
    constructor(accountId: AccountId, source: SocialSource, sessionRevision: Long = 0L) : this(
        accountId,
        source,
        sessionRevision,
        InMemoryPostPreferencesRepository(),
        PostInteractionExecutionAuthority(),
    )

    private val _state = MutableStateFlow(PostThreadUiState())
    val state = _state.asStateFlow()
    private val posts = linkedMapOf<me.foxtails.palustris.domain.EntityId, OwnedPost>()
    private val overlays = mutableMapOf<me.foxtails.palustris.domain.EntityId, MutationOverlay>()
    private val actionJobs = mutableMapOf<ActionKey, Job>()
    private var favouriteEmoji = DEFAULT_FAVOURITE_EMOJI
    private var activeKey: ThreadSessionKey? = null
    private var activeWrapper: OwnedPost? = null
    private var activeLoadGeneration = 0L
    private var loadJob: Job? = null
    private var automaticRefreshJob: Job? = null
    private var automaticRefreshAllowed = true
    private var stopped = false
    private var foreground = true
    private var postUpdateListener: ((OwnedPost) -> Unit)? = null

    init {
        viewModelScope.launch {
            preferences.observe(accountId).collectLatest { value ->
                favouriteEmoji = normalizeFavouriteEmoji(value.favouriteEmoji)
                posts.keys.toList().forEach { id ->
                    posts[id] = posts.getValue(id).copy(post = applyFavouritePreference(posts.getValue(id).post))
                }
                rebuildState()
            }
        }
    }

    fun setPostUpdateListener(listener: ((OwnedPost) -> Unit)?) {
        postUpdateListener = listener
    }

    fun applyExternalPost(updated: OwnedPost) {
        if (stopped || updated.fetchedBy != accountId || updated.sessionRevision != sessionRevision) return
        val target = updated.effectiveTargetId()
        // An externally applied projection never emits. The coordinator already
        // excludes the origin during propagation, but the thread must not rely
        // on that re-entrancy guard. Local mutations keep emitting below.
        updateMatching(target, emit = false) { existing -> existing.mergeExternalActionFields(updated.post) }
    }

    fun activate(ownedPost: OwnedPost?, supportsComments: Boolean) {
        if (stopped) return
        if (ownedPost == null) {
            deactivate()
            return
        }
        // A foreign or old-revision snapshot cannot open or replace a thread. Never make
        // an old callback current by rewriting its durable revision here.
        if (ownedPost.fetchedBy != accountId || ownedPost.sessionRevision != sessionRevision) return
        val key = ThreadSessionKey(accountId, sessionRevision, ownedPost.effectiveTargetId())
        if (key == activeKey && _state.value.focal != null) {
            activeWrapper = ownedPost
            when {
                supportsComments && _state.value.phase == PostThreadPhase.Inactive -> {
                    _state.value = _state.value.copy(phase = PostThreadPhase.InitialLoading, error = null)
                    loadFresh()
                }
                !supportsComments && _state.value.phase != PostThreadPhase.Inactive -> {
                    stopAcquisition()
                    _state.value = _state.value.copy(phase = PostThreadPhase.Inactive, error = null)
                }
            }
            return
        }
        stopAcquisition()
        // A replacement thread owns new action jobs. Cancel the old jobs; the launch-key
        // guard in runAction drops their late results.
        actionJobs.values.forEach { it.cancel() }
        actionJobs.clear()
        activeKey = key
        activeWrapper = ownedPost
        posts.clear()
        overlays.clear()
        posts[ownedPost.post.id] = ownedPost
        _state.value = PostThreadUiState(
            phase = if (supportsComments) PostThreadPhase.InitialLoading else PostThreadPhase.Inactive,
            focal = ownedPost,
        )
        if (supportsComments) loadFresh()
    }

    fun deactivate() {
        stopAcquisition()
        activeKey = null
        activeWrapper = null
        posts.clear()
        overlays.clear()
        actionJobs.values.forEach { it.cancel() }
        actionJobs.clear()
        _state.value = PostThreadUiState()
    }

    fun retry() {
        if (_state.value.phase == PostThreadPhase.AccountUnavailable) return
        if (activeKey != null) loadFresh()
    }

    fun continueAcquisition() {
        val key = activeKey ?: return
        val continuation = _state.value.continuation ?: return
        if (continuation.sessionKey != key || loadJob?.isActive == true || stopped) return
        val generation = activeLoadGeneration
        _state.value = _state.value.copy(phase = PostThreadPhase.Continuing, error = null)
        loadJob = viewModelScope.launch {
            try {
                val context = source.threadContext(key.focalId, continuation)
                if (generation != activeLoadGeneration || activeKey != key) return@launch
                applyContext(context, replacement = false)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (generation == activeLoadGeneration && activeKey == key) {
                    _state.value = _state.value.copy(
                        phase = PostThreadPhase.Partial,
                        error = e as? SourceError ?: SourceError.ServerError(null),
                    )
                }
            }
        }
    }

    fun refresh() {
        if (activeKey != null) loadFresh()
    }

    fun setForeground(value: Boolean) {
        foreground = value
        if (!value) {
            automaticRefreshJob?.cancel()
            loadJob?.cancel()
        }
        else if (_state.value.refreshing) scheduleAutomaticRefresh(_state.value)
    }

    fun acceptPublishedReply(created: OwnedPost) {
        val key = activeKey ?: return
        if (created.fetchedBy != accountId || created.sessionRevision != sessionRevision) return
        val parentId = created.post.replyTo ?: return
        if (parentId.connection != key.focalId.connection || posts.values.none { it.post.id == parentId }) return
        if (posts.containsKey(created.post.id)) return
        posts[created.post.id] = created
        overlays[created.post.id] = MutationOverlay(confirmedReply = true)
        posts[parentId]?.let { parent ->
            posts[parentId] = parent.copy(post = parent.post.copy(
                interactionCounts = parent.post.interactionCounts.copy(
                    replyCount = parent.post.interactionCounts.replyCount.adjustedBy(1),
                ),
            ))
        }
        rebuildState()
        automaticRefreshJob?.cancel()
        automaticRefreshJob = viewModelScope.launch {
            delay(REPLY_REFRESH_DELAY_MILLIS)
            if (!stopped && activeKey == key) loadFresh(allowAutomaticRefresh = false)
        }
    }

    fun acceptPublishedQuote(target: EntityId?) {
        if (stopped || target == null || activeKey?.focalId?.connection != target.connection) return
        updateMatching(target) { post ->
            post.copy(interactionCounts = post.interactionCounts.copy(
                quoteRepostCount = post.interactionCounts.quoteRepostCount.adjustedBy(1),
            ))
        }
    }

    fun favorite(ownedPost: OwnedPost) {
        val selected = !ownedPost.post.favourited
        val target = ownedPost.effectiveTargetId()
        val reactionFavourite = source.capabilities.primaryFavourite.mode == PrimaryFavouriteMode.Reaction
        runAction(ownedPost, PostAction.Favorite, target, { post ->
            if (reactionFavourite) {
                PostReactionReducer.apply(
                    post,
                    EmojiChoice(favouriteEmoji, favouriteEmoji),
                    selected,
                    source.capabilities.emoji.selectionMode,
                    favouriteEmoji,
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
            if (selected && source.capabilities.primaryFavourite.mode == PrimaryFavouriteMode.Reaction) {
                ownedPost.post.myReaction?.takeIf { it != favouriteEmoji }?.let { source.removeReaction(target, it) }
            }
            source.setPrimaryFavourite(target, favouriteEmoji, selected)
        }
    }

    fun reshare(ownedPost: OwnedPost) {
        val selected = !ownedPost.post.reposted
        val target = ownedPost.effectiveTargetId()
        runAction(ownedPost, PostAction.Reshare, target, { post ->
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
        val selected = ownedPost.post.selectedReactions.any { it.submissionValue == choice.submissionValue } ||
            ownedPost.post.myReaction == choice.submissionValue
        val primary = favouriteEmoji.takeIf { source.capabilities.primaryFavourite.mode == PrimaryFavouriteMode.Reaction }
        val target = ownedPost.effectiveTargetId()
        runAction(ownedPost, PostAction.React, target, { post ->
            PostReactionReducer.apply(post, choice, !selected, mode, primary)
        }) {
            val previous = ownedPost.post.selectedReactions.ifEmpty {
                ownedPost.post.myReaction?.let { listOf(EmojiChoice(it, it)) }.orEmpty()
            }
            if (selected) source.removeReaction(target, choice)
            else {
                if (mode != ReactionSelectionMode.Independent) {
                    previous.filterNot { it.submissionValue == choice.submissionValue }
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
        runAction(ownedPost, PostAction.Bookmark, target, { it.copy(saved = selected) }) {
            source.setSaved(target, selected)
        }
    }

    fun isActionPending(ownedPost: OwnedPost, action: PostAction): Boolean =
        actionJobs[ActionKey(ownedPost.effectiveTargetId(), actionFamily(action))]?.isActive == true

    fun stop() {
        if (stopped) return
        stopped = true
        stopAcquisition()
        actionJobs.values.forEach { it.cancel() }
        actionJobs.clear()
    }

    private fun loadFresh(allowAutomaticRefresh: Boolean = true) {
        val key = activeKey ?: return
        stopAcquisition()
        val generation = ++activeLoadGeneration
        val hasContent = _state.value.rows.isNotEmpty() || _state.value.ancestors.isNotEmpty() ||
            _state.value.phase == PostThreadPhase.Content || _state.value.phase == PostThreadPhase.Partial
        _state.value = _state.value.copy(
            phase = if (hasContent) PostThreadPhase.Refreshing else PostThreadPhase.InitialLoading,
            error = null,
        )
        automaticRefreshJob?.cancel()
        automaticRefreshAllowed = allowAutomaticRefresh
        loadJob = viewModelScope.launch {
            try {
                val context = source.threadContext(key.focalId)
                if (generation != activeLoadGeneration || activeKey != key) return@launch
                applyContext(context, replacement = true)
                scheduleAutomaticRefresh(_state.value)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (generation != activeLoadGeneration || activeKey != key) return@launch
                _state.value = _state.value.copy(
                    phase = if (hasContent) PostThreadPhase.Partial else PostThreadPhase.InitialFailure,
                    error = e as? SourceError ?: SourceError.ServerError(null),
                )
            }
        }
    }

    private fun applyContext(context: ThreadContext, replacement: Boolean) {
        val canonicalFocal = owned(context.focal)
        val returned = buildList {
            add(canonicalFocal)
            addAll(context.ancestors.map(::owned))
            addAll(context.descendants.map(::owned))
        }
        if (replacement) {
            val confirmed = posts.values.filter { overlays[it.post.id]?.confirmedReply == true }
            posts.clear()
            returned.forEach { post -> posts[post.post.id] = applyOverlay(post) }
            confirmed.forEach { post -> if (!posts.containsKey(post.post.id)) posts[post.post.id] = post }
            retireAcknowledgedOverlays(returned)
        } else {
            returned.forEach { post -> posts[post.post.id] = applyOverlay(post) }
        }
        val limitations = context.limitations
        val phase = when {
            limitations.isNotEmpty() -> PostThreadPhase.Partial
            else -> PostThreadPhase.Content
        }
        val updated = _state.value.copy(
            phase = phase,
            focal = posts[canonicalFocal.post.id] ?: canonicalFocal,
            ancestors = context.ancestors.map { ancestor -> posts[ancestor.id] ?: owned(ancestor) },
            continuation = context.continuation,
            limitations = limitations,
            refreshHint = context.refreshHint,
            error = null,
        )
        _state.value = updated
        rebuildState()
    }

    /**
     * Drops an overlay once a fresh authoritative page already equals its projection.
     * A confirmed mutation then stops hiding later remote changes from the server.
     */
    private fun retireAcknowledgedOverlays(returned: List<OwnedPost>) {
        overlays.keys.toList().forEach { target ->
            val raw = returned.firstOrNull { it.post.id == target || it.effectiveTargetId() == target }
                ?: return@forEach
            val overlay = overlays[target] ?: return@forEach
            if (overlay.applyTo(raw.post) == raw.post) overlays.remove(target)
        }
    }

    private fun scheduleAutomaticRefresh(state: PostThreadUiState) {
        if (!automaticRefreshAllowed) return
        val delayMillis = state.refreshHint?.minimumDelayMillis ?: return
        if (!foreground || delayMillis > MAX_FOREGROUND_WAIT_MILLIS || stopped) return
        automaticRefreshAllowed = false
        automaticRefreshJob?.cancel()
        automaticRefreshJob = viewModelScope.launch {
            delay(delayMillis)
            if (!stopped && foreground) loadFresh(allowAutomaticRefresh = false)
        }
    }

    private fun rebuildState() {
        val focal = _state.value.focal ?: return
        val all = posts.values.toList()
        val ancestors = all.filter { it.post.id != focal.post.id && it.post.id in _state.value.ancestors.map { ancestor -> ancestor.post.id } }
        val descendants = all.filter { it.post.id != focal.post.id && it.post.id !in ancestors.map { ancestor -> ancestor.post.id } }
        val tree = ThreadTreeBuilder.build(focal, ancestors, descendants)
        _state.value = _state.value.copy(
            focal = posts[focal.post.id] ?: focal,
            ancestors = tree.ancestors.map { it.ownedPost },
            rows = tree.replies,
            disconnectedRows = tree.disconnected,
        )
    }

    private fun runAction(
        ownedPost: OwnedPost,
        action: PostAction,
        target: me.foxtails.palustris.domain.EntityId,
        optimistic: (Post) -> Post,
        operation: suspend () -> PostActionResult,
    ) {
        if (stopped || ownedPost.fetchedBy != accountId || ownedPost.sessionRevision != sessionRevision) return
        val capabilities = source.capabilities
        val actionAllowed = action in capabilities.actions || when (action) {
            PostAction.Favorite -> capabilities.primaryFavourite.status == CapabilityStatus.Supported
            PostAction.Bookmark -> capabilities.savedPosts?.status == CapabilityStatus.Supported
            else -> false
        }
        if (!actionAllowed) return
        if (ownedPost.post.contentVisibility != me.foxtails.palustris.domain.PostContentVisibility.Visible) return
        val family = actionFamily(action)
        val key = ActionKey(target, family)
        if (actionJobs[key]?.isActive == true) return
        val before = posts[ownedPost.post.id] ?: return
        val token = executionAuthority.acquire(accountId, sessionRevision, family, target) ?: return
        val launchKey = activeKey
        val previousOverlay = overlays[target]
        val favoriteOwnsReaction = action == PostAction.Favorite &&
            source.capabilities.primaryFavourite.mode == PrimaryFavouriteMode.Reaction
        val after = before.copy(post = optimistic(before.post))
        overlays[target] = overlays[target].orEmpty().with(action, after.post, favoriteOwnsReaction)
        updateMatching(target) { current -> optimistic(current) }
        val job = viewModelScope.launch {
            _state.value = _state.value.copy(pendingActions = _state.value.pendingActions + key.toString())
            try {
                val result = operation()
                // A replaced thread drops the late result before it can touch rows, overlays,
                // or the popup listener.
                if (launchKey != activeKey) return@launch
                updateMatching(target) { current -> reconcile(action, current, result) }
                val updatedPost = posts.values.firstOrNull { it.post.id == target || it.effectiveTargetId() == target }?.post
                    ?: before.post
                overlays[target] = overlays[target].orEmpty().with(action, updatedPost, favoriteOwnsReaction)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                if (launchKey != activeKey) return@launch
                updateMatching(target) { current -> restore(action, current, before.post, after.post) }
                // Restore the family overlay captured before this attempt. A blind removal
                // would erase a previously confirmed mutation in the same family, and would
                // leave a reaction favorite's optimistic snapshot behind.
                overlays[target] = overlays[target].orEmpty()
                    .restoredFamily(action, favoriteOwnsReaction, previousOverlay)
            } finally {
                // Cleanup releases only the slot of the current operation. A replaced
                // job must not clear a newer pending entry with the same key.
                if (actionJobs[key] === kotlinx.coroutines.currentCoroutineContext()[Job]) {
                    actionJobs.remove(key)
                    _state.value = _state.value.copy(pendingActions = _state.value.pendingActions - key.toString())
                }
                executionAuthority.release(token)
            }
        }
        actionJobs[key] = job
    }

    private fun updateMatching(target: me.foxtails.palustris.domain.EntityId, emit: Boolean = true, transform: (Post) -> Post) {
        posts.entries.toList().forEach { (id, owned) ->
            if (owned.post.id == target || owned.effectiveTargetId() == target) {
                val updated = owned.copy(post = transform(owned.post))
                posts[id] = updated
                if (emit) postUpdateListener?.invoke(updated)
            }
        }
        rebuildState()
    }

    private fun reconcile(action: PostAction, current: Post, result: PostActionResult): Post {
        // Only family-owned fields and counts come from the server response. Every other
        // field stays current so a stale snapshot cannot erase another family's state or
        // newer local fields. A full server snapshot never replaces the current post.
        val serverCounts = result.post?.takeIf { it.id == current.id }?.interactionCounts
        val base = serverCounts?.let {
            current.copy(interactionCounts = current.interactionCounts.merge(it))
        } ?: current
        return when (action) {
            PostAction.Favorite -> base.copy(
                favourited = result.selected ?: base.favourited,
                myReaction = if (source.capabilities.primaryFavourite.mode == PrimaryFavouriteMode.Reaction) {
                    if (result.selected == true) favouriteEmoji else null
                } else base.myReaction,
            )
            PostAction.Reshare -> base.copy(
                reposted = result.selected ?: base.reposted,
                ownRepostId = if (result.selected == false) null else result.createdRepostId ?: base.ownRepostId,
            )
            PostAction.Bookmark -> base.copy(saved = result.selected ?: base.saved)
            PostAction.React, PostAction.Reply -> base
        }
    }

    private fun restore(action: PostAction, current: Post, before: Post, optimistic: Post): Post = when (action) {
        // Restore only family fields this operation still owns. A field that no longer
        // matches the optimistic value was changed by a newer projection and is kept.
        PostAction.Favorite -> if (source.capabilities.primaryFavourite.mode == PrimaryFavouriteMode.Reaction) {
            current.copy(
                favourited = restored(current.favourited, before.favourited, optimistic.favourited),
                myReaction = restored(current.myReaction, before.myReaction, optimistic.myReaction),
                selectedReactions = restored(current.selectedReactions, before.selectedReactions, optimistic.selectedReactions),
                reactions = restored(current.reactions, before.reactions, optimistic.reactions),
                interactionCounts = current.interactionCounts.copy(
                    reactionCount = restored(
                        current.interactionCounts.reactionCount,
                        before.interactionCounts.reactionCount,
                        optimistic.interactionCounts.reactionCount,
                    ),
                ),
            )
        } else {
            current.copy(
                favourited = restored(current.favourited, before.favourited, optimistic.favourited),
                interactionCounts = current.interactionCounts.copy(
                    favouriteCount = restored(
                        current.interactionCounts.favouriteCount,
                        before.interactionCounts.favouriteCount,
                        optimistic.interactionCounts.favouriteCount,
                    ),
                ),
            )
        }
        PostAction.Reshare -> current.copy(
            reposted = restored(current.reposted, before.reposted, optimistic.reposted),
            ownRepostId = restored(current.ownRepostId, before.ownRepostId, optimistic.ownRepostId),
            interactionCounts = current.interactionCounts.copy(
                repostCount = restored(
                    current.interactionCounts.repostCount,
                    before.interactionCounts.repostCount,
                    optimistic.interactionCounts.repostCount,
                ),
            ),
        )
        PostAction.Bookmark -> current.copy(saved = restored(current.saved, before.saved, optimistic.saved))
        PostAction.React -> current.copy(
            reactions = restored(current.reactions, before.reactions, optimistic.reactions),
            myReaction = restored(current.myReaction, before.myReaction, optimistic.myReaction),
            selectedReactions = restored(current.selectedReactions, before.selectedReactions, optimistic.selectedReactions),
            favourited = restored(current.favourited, before.favourited, optimistic.favourited),
            interactionCounts = current.interactionCounts.copy(
                reactionCount = restored(
                    current.interactionCounts.reactionCount,
                    before.interactionCounts.reactionCount,
                    optimistic.interactionCounts.reactionCount,
                ),
            ),
        )
        PostAction.Reply -> current
    }

    private fun <T> restored(current: T, before: T, optimistic: T): T =
        if (current == optimistic) before else current

    private fun applyOverlay(owned: OwnedPost): OwnedPost {
        val overlay = overlays[owned.post.id] ?: overlays[owned.effectiveTargetId()] ?: return owned
        return owned.copy(post = overlay.applyTo(owned.post))
    }

    private fun applyFavouritePreference(post: Post): Post = if (
        source.capabilities.primaryFavourite.mode == PrimaryFavouriteMode.Reaction
    ) post.copy(favourited = post.myReaction == favouriteEmoji) else post

    private fun owned(post: Post) = OwnedPost(accountId, applyFavouritePreference(post), sessionRevision)

    private fun stopAcquisition() {
        loadJob?.cancel()
        loadJob = null
        automaticRefreshJob?.cancel()
        automaticRefreshJob = null
        activeLoadGeneration++
    }

    override fun onCleared() {
        stop()
        super.onCleared()
    }

    private data class ActionKey(val target: me.foxtails.palustris.domain.EntityId, val family: PostActionFamily)

    /**
     * One accepted mutation snapshot per action family. A null family field means that
     * family has no overlay; a present [reactionState] owns the reaction fields
     * outright, including an explicit null selection after a confirmed removal.
     */
    private data class MutationOverlay(
        val favourited: Boolean? = null,
        val favouriteCount: Int? = null,
        val reposted: Boolean? = null,
        val repostCount: Int? = null,
        val ownRepostId: me.foxtails.palustris.domain.EntityId? = null,
        val ownRepostIdOverride: Boolean = false,
        val saved: Boolean? = null,
        val reactionState: ReactionState? = null,
        val confirmedReply: Boolean = false,
    ) {
        fun with(action: PostAction, post: Post, favoriteOwnsReaction: Boolean = false) = when (action) {
            PostAction.Favorite -> if (favoriteOwnsReaction) copy(reactionState = post.toReactionState())
                else copy(
                    favourited = post.favourited,
                    favouriteCount = post.interactionCounts.favouriteCount,
                )
            PostAction.Reshare -> copy(
                reposted = post.reposted,
                repostCount = post.interactionCounts.repostCount,
                ownRepostId = post.ownRepostId,
                ownRepostIdOverride = true,
            )
            PostAction.Bookmark -> copy(saved = post.saved)
            PostAction.React -> copy(reactionState = post.toReactionState())
            PostAction.Reply -> this
        }

        /** Restores one family from the overlay captured before the failed attempt. */
        fun restoredFamily(
            action: PostAction,
            favoriteOwnsReaction: Boolean,
            previous: MutationOverlay?,
        ): MutationOverlay = when (action) {
            PostAction.Favorite -> if (favoriteOwnsReaction) copy(reactionState = previous?.reactionState)
                else copy(favourited = previous?.favourited, favouriteCount = previous?.favouriteCount)
            PostAction.React -> copy(reactionState = previous?.reactionState)
            PostAction.Reshare -> copy(
                reposted = previous?.reposted,
                repostCount = previous?.repostCount,
                ownRepostId = previous?.ownRepostId,
                ownRepostIdOverride = previous?.ownRepostIdOverride ?: false,
            )
            PostAction.Bookmark -> copy(saved = previous?.saved)
            PostAction.Reply -> this
        }

        fun applyTo(post: Post) = post.copy(
            favourited = reactionState?.favourited ?: favourited ?: post.favourited,
            // A present reaction snapshot owns the selection outright. An Elvis fallback
            // to the old post would revive a reaction the user just removed.
            myReaction = if (reactionState != null) reactionState.myReaction else post.myReaction,
            reactions = reactionState?.reactions ?: post.reactions,
            selectedReactions = reactionState?.selectedReactions ?: post.selectedReactions,
            reposted = reposted ?: post.reposted,
            ownRepostId = if (ownRepostIdOverride) ownRepostId else post.ownRepostId,
            saved = saved ?: post.saved,
            interactionCounts = post.interactionCounts.copy(
                favouriteCount = favouriteCount ?: post.interactionCounts.favouriteCount,
                reactionCount = reactionState?.reactionCount ?: post.interactionCounts.reactionCount,
                repostCount = repostCount ?: post.interactionCounts.repostCount,
            ),
        )

        private fun Post.toReactionState() = ReactionState(
            reactions = reactions,
            myReaction = myReaction,
            selectedReactions = selectedReactions,
            favourited = favourited,
            reactionCount = interactionCounts.reactionCount,
        )
    }

    private data class ReactionState(
        val reactions: List<me.foxtails.palustris.domain.Reaction>,
        val myReaction: String?,
        val selectedReactions: List<EmojiChoice>,
        val favourited: Boolean,
        val reactionCount: Int?,
    )

    private fun actionFamily(action: PostAction): PostActionFamily = when (action) {
        PostAction.Favorite -> if (source.capabilities.primaryFavourite.mode == PrimaryFavouriteMode.Reaction) PostActionFamily.FavoriteReaction else PostActionFamily.Favorite
        PostAction.React -> if (source.capabilities.primaryFavourite.mode == PrimaryFavouriteMode.Reaction) PostActionFamily.FavoriteReaction else PostActionFamily.Reaction
        PostAction.Reshare -> PostActionFamily.Reshare
        PostAction.Bookmark -> PostActionFamily.Bookmark
        PostAction.Reply -> PostActionFamily.Reply
    }

    private fun MutationOverlay?.orEmpty(): MutationOverlay = this ?: MutationOverlay()

    @AssistedFactory
    interface Factory {
        fun create(accountId: AccountId, source: SocialSource, sessionRevision: Long): PostThreadViewModel
    }

    private companion object {
        const val MAX_FOREGROUND_WAIT_MILLIS = 10_000L
        const val REPLY_REFRESH_DELAY_MILLIS = 2_000L
    }
}
