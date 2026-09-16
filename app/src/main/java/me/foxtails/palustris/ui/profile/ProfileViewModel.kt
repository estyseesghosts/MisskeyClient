package me.foxtails.palustris.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.CapabilityStatus
import me.foxtails.palustris.domain.EditableProfile
import me.foxtails.palustris.domain.EditableProfileCapabilities
import me.foxtails.palustris.domain.EditableProfilePatch
import me.foxtails.palustris.domain.EmojiChoice
import me.foxtails.palustris.domain.EntityId
import me.foxtails.palustris.domain.OwnedPost
import me.foxtails.palustris.domain.PostAction
import me.foxtails.palustris.domain.PostReactionReducer
import me.foxtails.palustris.domain.ProfileTimelineQuery
import me.foxtails.palustris.domain.ProfileTimelineTab
import me.foxtails.palustris.domain.Protocol
import me.foxtails.palustris.domain.ReactionSelectionMode
import me.foxtails.palustris.domain.ServerCapabilities
import me.foxtails.palustris.domain.SocialSource
import me.foxtails.palustris.domain.SourceError
import me.foxtails.palustris.domain.adjustedBy
import me.foxtails.palustris.domain.effectiveTargetId
import me.foxtails.palustris.domain.mergeExternalActionFields
import me.foxtails.palustris.domain.mergeInto
import me.foxtails.palustris.ui.UiStrings
import me.foxtails.palustris.ui.posts.PostActionFamily
import me.foxtails.palustris.ui.posts.PostInteractionExecutionAuthority
import me.foxtails.palustris.ui.requiresSignIn

@HiltViewModel(assistedFactory = ProfileViewModel.Factory::class)
class ProfileViewModel @AssistedInject constructor(
    @Assisted val accountId: AccountId,
    @Assisted private val source: SocialSource,
    @Assisted private val sessionRevision: Long = 0L,
    private val executionAuthority: PostInteractionExecutionAuthority = PostInteractionExecutionAuthority(),
    private val uiStrings: UiStrings = UiStrings.Default,
) : ViewModel() {
    private val _state = MutableStateFlow(ProfileUiState())
    val state = _state.asStateFlow()
    private val timelinePager = ProfileTimelinePager(
        accountId = accountId,
        source = source,
        scope = viewModelScope,
        sessionRevision = sessionRevision,
        onPagesChanged = { pages -> if (!stopped) _state.value = _state.value.copy(pages = pages) },
        uiStrings = uiStrings,
    )

    private var generation = 0L
    private var editorGeneration = 0L
    private var stopped = false
    private var detailJob: Job? = null
    private var relationshipJob: Job? = null
    private var pinnedJob: Job? = null
    private var editJob: Job? = null
    private val pageJobs = mutableMapOf<ProfileTimelineTab, Job>()
    private val requestedCursors = mutableMapOf<ProfileTimelineTab, MutableSet<String>>()
    private val reactionJobs = mutableMapOf<EntityId, Job>()

    fun open(seed: Account) {
        if (stopped) return
        val current = _state.value
        if (current.targetId == seed.id) {
            _state.value = current.copy(
                seedAccount = seed,
                account = current.account ?: seed,
                likedAvailable = likedAvailable(seed.id),
            )
            cancelProfileRequests()
            loadDetails(seed.id, generation)
            loadRelationshipIfNeeded(seed.id, generation)
            loadPinned(seed.id, generation)
            refreshSelected()
            return
        }

        generation += 1
        cancelProfileRequests()
        requestedCursors.clear()
        val targetGeneration = generation
        _state.value = ProfileUiState(
            targetId = seed.id,
            seedAccount = seed,
            account = seed,
            editableSupported = editableSupported(source.capabilities.profile.editable),
            likedAvailable = likedAvailable(seed.id),
        )
        timelinePager.setTarget(seed.id, targetGeneration)
        loadDetails(seed.id, targetGeneration)
        loadRelationshipIfNeeded(seed.id, targetGeneration)
        loadPinned(seed.id, targetGeneration)
        refreshSelected()
    }

    fun refreshDetails() {
        val target = _state.value.targetId ?: return
        loadDetails(target, generation)
    }

    fun refresh() {
        if (stopped) return
        val target = _state.value.targetId ?: return
        val targetGeneration = generation
        loadDetails(target, targetGeneration)
        loadRelationshipIfNeeded(target, targetGeneration)
        loadPinned(target, targetGeneration)
        refreshSelected()
    }

    fun selectCategory(category: ProfileCategory) {
        if (stopped || _state.value.selectedTab == category) return
        _state.value = _state.value.copy(selectedTab = category)
        if (category.timelineTab != null && _state.value.pages[category.timelineTab] == null) {
            refreshSelected()
        }
    }

    fun refreshSelected() {
        val target = _state.value.targetId ?: return
        val tab = _state.value.selectedTab.timelineTab ?: return
        timelinePager.refresh(target, generation, tab)
    }

    fun loadMoreSelected() {
        val target = _state.value.targetId ?: return
        val tab = _state.value.selectedTab.timelineTab ?: return
        timelinePager.loadMore(target, generation, tab)
    }

    fun openEditor() {
        if (stopped || _state.value.targetId != accountId) return
        editorGeneration += 1
        _state.value = _state.value.copy(
            editorOpen = true,
            editorDraft = _state.value.editorBase,
            editableLoading = true,
            editableError = null,
            editError = null,
            editorCapabilities = source.capabilities.profile.editable,
        )
        loadEditor(editorGeneration)
    }

    fun updateEditor(draft: EditableProfile) {
        if (stopped || !_state.value.editorOpen) return
        _state.value = _state.value.copy(editorDraft = draft)
    }

    fun refreshEditor() {
        if (stopped || !_state.value.editorOpen) return
        editorGeneration += 1
        loadEditor(editorGeneration)
    }

    fun closeEditor() {
        editorGeneration += 1
        editJob?.cancel()
        _state.value = _state.value.copy(
            editorOpen = false,
            editorDraft = null,
            editableLoading = false,
            savingProfile = false,
            editError = null,
            editableError = null,
        )
    }

    fun saveEditor(patch: EditableProfilePatch, onSuccess: (Account) -> Unit = {}) {
        if (stopped || _state.value.savingProfile || _state.value.targetId != accountId) return
        if (patch.isEmpty) {
            _state.value = _state.value.copy(editorOpen = false, editorDraft = null, editError = null)
            _state.value.account?.let(onSuccess)
            return
        }
        val targetEditorGeneration = editorGeneration
        _state.value = _state.value.copy(savingProfile = true, editError = null)
        editJob?.cancel()
        editJob = viewModelScope.launch {
            try {
                val updated = source.updateEditableProfile(patch)
                if (isEditorCurrent(targetEditorGeneration, accountId)) {
                    val current = _state.value.account ?: _state.value.seedAccount
                    val merged = current?.let { updated.mergeInto(it) } ?: current
                    _state.value = _state.value.copy(
                        account = merged,
                        seedAccount = merged,
                        editable = updated,
                        savingProfile = false,
                        editError = null,
                        editorOpen = false,
                        editorDraft = null,
                    )
                    merged?.let(onSuccess)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (isEditorCurrent(targetEditorGeneration, accountId)) {
                    _state.value = _state.value.copy(
                        savingProfile = false,
                        editError = uiStrings.sourceError(error),
                    )
                }
            }
        }
    }

    fun react(ownedPost: OwnedPost, choice: EmojiChoice) {
        if (stopped || ownedPost.fetchedBy != accountId || ownedPost.sessionRevision != sessionRevision) return
        val emojiCapabilities = source.capabilities.emoji
        if (emojiCapabilities.reactionMutation != CapabilityStatus.Supported ||
            PostAction.React !in source.capabilities.actions
        ) {
            return
        }
        val postId = ownedPost.post.id
        val actionTargetId = ownedPost.effectiveTargetId()
        val family = if (source.capabilities.primaryFavourite.mode == me.foxtails.palustris.domain.PrimaryFavouriteMode.Reaction) {
            PostActionFamily.FavoriteReaction
        } else {
            PostActionFamily.Reaction
        }
        if (reactionJobs[actionTargetId]?.isActive == true) return
        val token = executionAuthority.acquire(accountId, sessionRevision, family, actionTargetId) ?: return
        val before = ownedPost.post
        val targetGeneration = generation
        val selected = before.selectedReactions.any { it.submissionValue == choice.submissionValue } ||
            (before.myReaction != null && before.myReaction == choice.submissionValue)
        val optimistic = PostReactionReducer.apply(
            post = before,
            choice = choice,
            selected = !selected,
            selectionMode = emojiCapabilities.selectionMode,
        )
        updateOwnedPost(postId) { optimistic }
        val job = viewModelScope.launch {
            try {
                if (selected) {
                    source.removeReaction(actionTargetId, choice)
                    if (stopped || generation != targetGeneration) return@launch
                } else {
                    if (emojiCapabilities.selectionMode == ReactionSelectionMode.Single) {
                        val previous = before.selectedReactions
                            .firstOrNull { it.submissionValue != choice.submissionValue }
                            ?: before.myReaction?.takeIf { it != choice.submissionValue }
                                ?.let { EmojiChoice(it, it, null) }
                        previous?.let {
                            source.removeReaction(actionTargetId, it)
                            if (stopped || generation != targetGeneration) return@launch
                        }
                    }
                    source.react(actionTargetId, choice)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (!stopped && generation == targetGeneration) updateOwnedPost(postId) { before }
            } finally {
                if (reactionJobs[actionTargetId] === currentCoroutineContext()[Job]) reactionJobs.remove(actionTargetId)
                executionAuthority.release(token)
            }
        }
        reactionJobs[actionTargetId] = job
    }

    fun applyExternalPost(updated: OwnedPost) {
        if (stopped || updated.fetchedBy != accountId || updated.sessionRevision != sessionRevision) return
        val target = updated.effectiveTargetId()
        updateOwnedPost(target) { existing -> existing.mergeExternalActionFields(updated.post) }
    }

    fun applyPublishedPost(request: me.foxtails.palustris.domain.CreatePostRequest) {
        request.replyTo?.let { parent -> incrementKnownReplyCount(parent) }
        request.quoteOf?.let { target -> incrementKnownQuoteCount(target) }
    }

    fun follow() {
        mutateRelationship { source.followProfile(it) }
    }

    fun unfollow() {
        mutateRelationship { source.unfollowProfile(it) }
    }

    fun stop() {
        if (stopped) return
        stopped = true
        timelinePager.stop()
        generation += 1
        cancelProfileRequests()
        reactionJobs.values.forEach(Job::cancel)
        reactionJobs.clear()
    }

    private fun loadEditor(targetEditorGeneration: Long) {
        editJob?.cancel()
        editJob = viewModelScope.launch {
            try {
                val editable = source.loadEditableProfile()
                if (isEditorCurrent(targetEditorGeneration, accountId)) {
                    _state.value = _state.value.copy(
                        editable = editable,
                        editableLoading = false,
                        editableError = null,
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (isEditorCurrent(targetEditorGeneration, accountId)) {
                    _state.value = _state.value.copy(
                        editableLoading = false,
                        editableError = uiStrings.sourceError(error),
                    )
                }
            }
        }
    }

    private fun updateOwnedPost(postId: EntityId, transform: (me.foxtails.palustris.domain.Post) -> me.foxtails.palustris.domain.Post) {
        _state.value = _state.value.copy(
            pinnedPosts = _state.value.pinnedPosts.map { owned ->
                if (owned.post.id == postId || owned.effectiveTargetId() == postId) owned.copy(post = transform(owned.post)) else owned
            },
            pages = _state.value.pages.mapValues { (_, page) ->
                page.copy(posts = page.posts.map { owned ->
                    if (owned.post.id == postId || owned.effectiveTargetId() == postId) owned.copy(post = transform(owned.post)) else owned
                })
            },
        )
        timelinePager.updatePosts { owned ->
            if (owned.post.id == postId || owned.effectiveTargetId() == postId) {
                owned.copy(post = transform(owned.post))
            } else owned
        }
    }

    private fun incrementKnownReplyCount(target: EntityId) = updateOwnedPost(target) { post ->
        post.copy(interactionCounts = post.interactionCounts.copy(
            replyCount = post.interactionCounts.replyCount.adjustedBy(1),
        ))
    }

    private fun incrementKnownQuoteCount(target: EntityId) = updateOwnedPost(target) { post ->
        post.copy(interactionCounts = post.interactionCounts.copy(
            quoteRepostCount = post.interactionCounts.quoteRepostCount.adjustedBy(1),
        ))
    }

    private fun loadDetails(target: AccountId, targetGeneration: Long) {
        detailJob?.cancel()
        if (!profileDetailsAvailable(source.capabilities)) {
            if (isCurrent(targetGeneration, target)) {
                _state.value = _state.value.copy(
                    detailLoading = false,
                    detailError = uiStrings.profileDetailsUnsupported(),
                    staleDetails = true,
                )
            }
            return
        }
        _state.value = _state.value.copy(detailLoading = true, detailError = null)
        detailJob = viewModelScope.launch {
            try {
                val account = source.profile(target)
                if (isCurrent(targetGeneration, target)) {
                    _state.value = _state.value.copy(
                        account = account,
                        detailLoading = false,
                        detailError = null,
                        detailNeedsSignIn = false,
                        staleDetails = false,
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (isCurrent(targetGeneration, target)) {
                    _state.value = _state.value.copy(
                        detailLoading = false,
                        detailError = uiStrings.sourceError(error),
                        detailNeedsSignIn = requiresSignIn(error),
                        staleDetails = true,
                    )
                }
            }
        }
    }

    private fun loadRelationshipIfNeeded(target: AccountId, targetGeneration: Long) {
        relationshipJob?.cancel()
        if (target == accountId) {
            _state.value = _state.value.copy(
                relationship = null,
                relationshipSupported = null,
                relationshipLoading = false,
                relationshipMutation = false,
                relationshipError = null,
            )
            return
        }
        if (source.capabilities.profile.relationships == me.foxtails.palustris.domain.CapabilityStatus.Unsupported) {
            _state.value = _state.value.copy(relationshipSupported = false, relationshipLoading = false)
            return
        }
        _state.value = _state.value.copy(
            relationshipLoading = true,
            relationshipError = null,
            relationshipSupported = null,
        )
        relationshipJob = viewModelScope.launch {
            try {
                val relationship = source.profileRelationship(target)
                if (isCurrent(targetGeneration, target)) {
                    _state.value = _state.value.copy(
                        relationship = relationship,
                        relationshipSupported = true,
                        relationshipLoading = false,
                        relationshipError = null,
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (isCurrent(targetGeneration, target)) {
                    _state.value = _state.value.copy(
                        relationshipLoading = false,
                        relationshipSupported = if (error is SourceError.Unsupported) false else null,
                        relationshipError = uiStrings.sourceError(error),
                    )
                }
            }
        }
    }

    private fun loadPinned(target: AccountId, targetGeneration: Long) {
        pinnedJob?.cancel()
        if (source.capabilities.profile.pinnedPosts == me.foxtails.palustris.domain.CapabilityStatus.Unsupported) {
            _state.value = _state.value.copy(pinnedLoading = false)
            return
        }
        _state.value = _state.value.copy(pinnedLoading = true, pinnedError = null)
        pinnedJob = viewModelScope.launch {
            try {
                val posts = source.pinnedPosts(target)
                    .distinctBy { it.id }
                    .map { OwnedPost(accountId, it, sessionRevision) }
                if (isCurrent(targetGeneration, target)) {
                    _state.value = _state.value.copy(
                        pinnedPosts = posts,
                        pinnedLoading = false,
                        pinnedError = null,
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (isCurrent(targetGeneration, target)) {
                    _state.value = _state.value.copy(
                        pinnedLoading = false,
                        pinnedError = uiStrings.sourceError(error),
                    )
                }
            }
        }
    }

    private fun loadPage(
        target: AccountId,
        tab: ProfileTimelineTab,
        cursor: String?,
        targetGeneration: Long,
        refreshing: Boolean,
    ) {
        val cursorSet = requestedCursors.getOrPut(tab) { mutableSetOf() }
        if (cursor != null && !cursorSet.add(cursor)) {
            val page = _state.value.pages[tab] ?: return
            if (isCurrent(targetGeneration, target)) {
                _state.value = _state.value.copy(
                    pages = _state.value.pages + (tab to page.copy(
                        loadingMore = false,
                        terminal = true,
                        nextCursor = null,
                    )),
                )
            }
            return
        }
        if (source.capabilities.profile.timelines == me.foxtails.palustris.domain.CapabilityStatus.Unsupported) {
            publishPageFailure(target, tab, targetGeneration, refreshing, SourceError.Unsupported("profile.timeline"))
            return
        }
        pageJobs[tab]?.cancel()
        val job = viewModelScope.launch {
            try {
                val page = source.profileTimeline(ProfileTimelineQuery(target, tab), cursor)
                if (!isCurrent(targetGeneration, target)) return@launch
                val current = _state.value.pages[tab] ?: ProfilePageState()
                val owned = page.items.map { OwnedPost(accountId, it, sessionRevision) }
                val merged = (if (refreshing) owned else current.posts + owned).distinctBy { it.post.id }
                val repeatedCursor = page.nextCursor != null && page.nextCursor in cursorSet
                val nextCursor = page.nextCursor?.takeUnless { repeatedCursor }
                _state.value = _state.value.copy(
                    pages = _state.value.pages + (tab to current.copy(
                        posts = merged,
                        initialLoading = false,
                        refreshing = false,
                        loadingMore = false,
                        nextCursor = nextCursor,
                        error = null,
                        needsSignIn = false,
                        terminal = nextCursor == null,
                        consecutiveEmptyPages = if (page.items.isEmpty()) {
                            if (refreshing) 1 else current.consecutiveEmptyPages + 1
                        } else {
                            0
                        },
                    )),
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (cursor != null) cursorSet.remove(cursor)
                publishPageFailure(target, tab, targetGeneration, refreshing, error)
            } finally {
                if (pageJobs[tab] === coroutineContext[Job]) pageJobs.remove(tab)
            }
        }
        pageJobs[tab] = job
    }

    private fun publishPageFailure(
        target: AccountId,
        tab: ProfileTimelineTab,
        targetGeneration: Long,
        refreshing: Boolean,
        error: Exception,
    ) {
        if (!isCurrent(targetGeneration, target)) return
        val current = _state.value.pages[tab] ?: ProfilePageState()
        _state.value = _state.value.copy(
            pages = _state.value.pages + (tab to current.copy(
                initialLoading = false,
                refreshing = false,
                loadingMore = false,
                error = uiStrings.sourceError(error),
                needsSignIn = requiresSignIn(error),
                // Refresh failures keep the existing rows and cursor usable.
                nextCursor = current.nextCursor,
            )),
        )
    }

    private fun mutateRelationship(operation: suspend (AccountId) -> me.foxtails.palustris.domain.ProfileRelationship) {
        if (stopped) return
        val current = _state.value
        val target = current.targetId ?: return
        val relationship = current.relationship ?: return
        if (target == accountId || current.relationshipSupported != true || current.relationshipMutation) return
        val targetGeneration = generation
        _state.value = current.copy(relationshipMutation = true, relationshipError = null)
        relationshipJob?.cancel()
        relationshipJob = viewModelScope.launch {
            try {
                val updated = operation(target)
                if (isCurrent(targetGeneration, target)) {
                    _state.value = _state.value.copy(
                        relationship = updated,
                        relationshipMutation = false,
                        relationshipError = null,
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (isCurrent(targetGeneration, target)) {
                    _state.value = _state.value.copy(
                        relationship = relationship,
                        relationshipMutation = false,
                        relationshipError = uiStrings.sourceError(error),
                        relationshipSupported = if (error is SourceError.Unsupported) false else current.relationshipSupported,
                    )
                }
            }
        }
    }

    private fun cancelProfileRequests() {
        detailJob?.cancel()
        relationshipJob?.cancel()
        pinnedJob?.cancel()
        editJob?.cancel()
        pageJobs.values.forEach(Job::cancel)
        detailJob = null
        relationshipJob = null
        pinnedJob = null
        editJob = null
        pageJobs.clear()
        timelinePager.cancel()
    }

    private fun isCurrent(targetGeneration: Long, target: AccountId): Boolean =
        !stopped && generation == targetGeneration && _state.value.targetId == target

    /**
     * The Liked tab is offered for the signed-in account on either protocol, and for another
     * account only on Misskey, where `users/reactions` accepts the viewed user id. Mastodon
     * exposes favourites only for the signed-in account, so another account has no Liked tab.
     */
    private fun likedAvailable(target: AccountId): Boolean =
        if (target == accountId) {
            source.capabilities.likedPosts != CapabilityStatus.Unsupported
        } else {
            accountId.connection.protocol == Protocol.MISSKEY
        }

    private fun isEditorCurrent(targetEditorGeneration: Long, target: AccountId): Boolean =
        !stopped && editorGeneration == targetEditorGeneration && _state.value.targetId == target

    override fun onCleared() {
        stop()
        super.onCleared()
    }

    @AssistedFactory
    interface Factory {
        fun create(accountId: AccountId, source: SocialSource, sessionRevision: Long): ProfileViewModel
    }
}

private fun editableSupported(capabilities: EditableProfileCapabilities): Boolean =
    capabilities.read != CapabilityStatus.Unsupported || capabilities.update != CapabilityStatus.Unsupported

private fun profileDetailsAvailable(capabilities: ServerCapabilities): Boolean =
    capabilities.profile.details != me.foxtails.palustris.domain.CapabilityStatus.Unsupported
