package me.foxtails.palustris.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.OwnedPost
import me.foxtails.palustris.domain.ProfileTimelineQuery
import me.foxtails.palustris.domain.ProfileTimelineTab
import me.foxtails.palustris.domain.ServerCapabilities
import me.foxtails.palustris.domain.SocialSource
import me.foxtails.palustris.domain.SourceError
import me.foxtails.palustris.domain.UpdateProfileRequest
import me.foxtails.palustris.ui.requiresSignIn
import me.foxtails.palustris.ui.sourceErrorMessage

@HiltViewModel(assistedFactory = ProfileViewModel.Factory::class)
class ProfileViewModel @AssistedInject constructor(
    @Assisted val accountId: AccountId,
    @Assisted private val source: SocialSource,
) : ViewModel() {
    private val _state = MutableStateFlow(ProfileUiState())
    val state = _state.asStateFlow()

    private var generation = 0L
    private var stopped = false
    private var detailJob: Job? = null
    private var relationshipJob: Job? = null
    private var pinnedJob: Job? = null
    private var editJob: Job? = null
    private val pageJobs = mutableMapOf<ProfileTimelineTab, Job>()
    private val requestedCursors = mutableMapOf<ProfileTimelineTab, MutableSet<String>>()

    fun open(seed: Account) {
        if (stopped) return
        val current = _state.value
        if (current.targetId == seed.id) {
            _state.value = current.copy(
                seedAccount = seed,
                account = current.account ?: seed,
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
        )
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
        if (stopped) return
        val target = _state.value.targetId ?: return
        val tab = _state.value.selectedTab.timelineTab ?: return
        pageJobs[tab]?.cancel()
        requestedCursors[tab] = mutableSetOf()
        val previous = _state.value.pages[tab] ?: ProfilePageState()
        _state.value = _state.value.copy(
            pages = _state.value.pages + (tab to previous.copy(
                initialLoading = previous.posts.isEmpty(),
                refreshing = previous.posts.isNotEmpty(),
                loadingMore = false,
                nextCursor = previous.nextCursor,
                error = null,
                needsSignIn = false,
                terminal = false,
                consecutiveEmptyPages = 0,
            )),
        )
        loadPage(target, tab, cursor = null, targetGeneration = generation, refreshing = true)
    }

    fun loadMoreSelected() {
        if (stopped) return
        val target = _state.value.targetId ?: return
        val tab = _state.value.selectedTab.timelineTab ?: return
        val page = _state.value.pages[tab] ?: return
        val cursor = page.nextCursor ?: return
        if (page.initialLoading || page.refreshing || page.loadingMore || page.terminal || page.needsSignIn) return
        if (pageJobs[tab]?.isActive == true) return
        _state.value = _state.value.copy(
            pages = _state.value.pages + (tab to page.copy(loadingMore = true, error = null)),
        )
        loadPage(target, tab, cursor, generation, refreshing = false)
    }

    fun updateSelf(request: UpdateProfileRequest, onSuccess: (Account) -> Unit = {}) {
        if (stopped || _state.value.savingProfile || _state.value.targetId != accountId) return
        val targetGeneration = generation
        _state.value = _state.value.copy(savingProfile = true, editError = null)
        editJob?.cancel()
        editJob = viewModelScope.launch {
            try {
                val updated = source.updateProfile(request)
                if (isCurrent(targetGeneration, accountId)) {
                    _state.value = _state.value.copy(
                        account = updated,
                        seedAccount = updated,
                        savingProfile = false,
                        editError = null,
                    )
                    onSuccess(updated)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (isCurrent(targetGeneration, accountId)) {
                    _state.value = _state.value.copy(
                        savingProfile = false,
                        editError = sourceErrorMessage(error),
                    )
                }
            }
        }
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
        generation += 1
        cancelProfileRequests()
    }

    private fun loadDetails(target: AccountId, targetGeneration: Long) {
        detailJob?.cancel()
        if (!profileDetailsAvailable(source.capabilities)) {
            if (isCurrent(targetGeneration, target)) {
                _state.value = _state.value.copy(
                    detailLoading = false,
                    detailError = sourceErrorMessage(SourceError.Unsupported("profile.details")),
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
                        detailError = sourceErrorMessage(error),
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
                        relationshipError = sourceErrorMessage(error),
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
                    .map { OwnedPost(accountId, it) }
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
                        pinnedError = sourceErrorMessage(error),
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
                val owned = page.items.map { OwnedPost(accountId, it) }
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
                error = sourceErrorMessage(error),
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
                        relationshipError = sourceErrorMessage(error),
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
    }

    private fun isCurrent(targetGeneration: Long, target: AccountId): Boolean =
        !stopped && generation == targetGeneration && _state.value.targetId == target

    override fun onCleared() {
        stop()
        super.onCleared()
    }

    @AssistedFactory
    interface Factory {
        fun create(accountId: AccountId, source: SocialSource): ProfileViewModel
    }
}

private fun profileDetailsAvailable(capabilities: ServerCapabilities): Boolean =
    capabilities.profile.details != me.foxtails.palustris.domain.CapabilityStatus.Unsupported
