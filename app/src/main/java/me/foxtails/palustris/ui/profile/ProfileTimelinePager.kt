package me.foxtails.palustris.ui.profile

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.CapabilityStatus
import me.foxtails.palustris.domain.OwnedPost
import me.foxtails.palustris.domain.ProfileTimelineQuery
import me.foxtails.palustris.domain.ProfileTimelineTab
import me.foxtails.palustris.domain.SocialSource
import me.foxtails.palustris.domain.SourceError
import me.foxtails.palustris.ui.requiresSignIn
import me.foxtails.palustris.ui.sourceErrorMessage

internal class ProfileTimelinePager(
    private val accountId: AccountId,
    private val source: SocialSource,
    private val scope: CoroutineScope,
    private val onPagesChanged: (Map<ProfileTimelineTab, ProfilePageState>) -> Unit,
) {
    private val pageJobs = mutableMapOf<ProfileTimelineTab, Job>()
    private val requestedCursors = mutableMapOf<ProfileTimelineTab, MutableSet<String>>()
    private var pages = emptyMap<ProfileTimelineTab, ProfilePageState>()
    private var target: AccountId? = null
    private var generation = 0L
    private var stopped = false

    fun setTarget(target: AccountId, generation: Long) {
        cancel()
        this.target = target
        this.generation = generation
        requestedCursors.clear()
        pages = emptyMap()
        publish()
    }

    fun refresh(target: AccountId, generation: Long, tab: ProfileTimelineTab) {
        if (!isCurrent(target, generation)) return
        pageJobs[tab]?.cancel()
        requestedCursors[tab] = mutableSetOf()
        val previous = pages[tab] ?: ProfilePageState()
        publish(pages + (tab to previous.copy(
            initialLoading = previous.posts.isEmpty(),
            refreshing = previous.posts.isNotEmpty(),
            loadingMore = false,
            nextCursor = previous.nextCursor,
            error = null,
            needsSignIn = false,
            terminal = false,
            consecutiveEmptyPages = 0,
        )))
        loadPage(target, tab, cursor = null, generation, refreshing = true)
    }

    fun loadMore(target: AccountId, generation: Long, tab: ProfileTimelineTab) {
        if (!isCurrent(target, generation)) return
        val page = pages[tab] ?: return
        val cursor = page.nextCursor ?: return
        if (page.initialLoading || page.refreshing || page.loadingMore || page.terminal || page.needsSignIn) return
        if (pageJobs[tab]?.isActive == true) return
        publish(pages + (tab to page.copy(loadingMore = true, error = null)))
        loadPage(target, tab, cursor, generation, refreshing = false)
    }

    fun updatePosts(transform: (OwnedPost) -> OwnedPost) {
        publish(pages.mapValues { (_, page) -> page.copy(posts = page.posts.map(transform)) })
    }

    fun cancel() {
        pageJobs.values.forEach(Job::cancel)
        pageJobs.clear()
    }

    fun stop() {
        stopped = true
        cancel()
        generation++
    }

    private fun loadPage(
        target: AccountId,
        tab: ProfileTimelineTab,
        cursor: String?,
        requestGeneration: Long,
        refreshing: Boolean,
    ) {
        val cursorSet = requestedCursors.getOrPut(tab) { mutableSetOf() }
        if (cursor != null && !cursorSet.add(cursor)) {
            val page = pages[tab] ?: return
            if (isCurrent(target, requestGeneration)) {
                publish(pages + (tab to page.copy(loadingMore = false, terminal = true, nextCursor = null)))
            }
            return
        }
        if (source.capabilities.profile.timelines == CapabilityStatus.Unsupported) {
            publishPageFailure(target, tab, requestGeneration, SourceError.Unsupported("profile.timeline"))
            return
        }
        pageJobs[tab]?.cancel()
        val job = scope.launch {
            try {
                val page = source.profileTimeline(ProfileTimelineQuery(target, tab), cursor)
                if (!isCurrent(target, requestGeneration)) return@launch
                val current = pages[tab] ?: ProfilePageState()
                val owned = page.items.map { OwnedPost(accountId, it) }
                val merged = (if (refreshing) owned else current.posts + owned).distinctBy { it.post.id }
                val repeatedCursor = page.nextCursor != null && page.nextCursor in cursorSet
                val nextCursor = page.nextCursor?.takeUnless { repeatedCursor }
                publish(pages + (tab to current.copy(
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
                    } else 0,
                )))
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (cursor != null) cursorSet.remove(cursor)
                publishPageFailure(target, tab, requestGeneration, error)
            } finally {
                if (pageJobs[tab] === kotlinx.coroutines.currentCoroutineContext()[Job]) pageJobs.remove(tab)
            }
        }
        pageJobs[tab] = job
    }

    private fun publishPageFailure(
        target: AccountId,
        tab: ProfileTimelineTab,
        requestGeneration: Long,
        error: Exception,
    ) {
        if (!isCurrent(target, requestGeneration)) return
        val current = pages[tab] ?: ProfilePageState()
        publish(pages + (tab to current.copy(
            initialLoading = false,
            refreshing = false,
            loadingMore = false,
            error = sourceErrorMessage(error),
            needsSignIn = requiresSignIn(error),
            nextCursor = current.nextCursor,
        )))
    }

    private fun isCurrent(target: AccountId, requestGeneration: Long): Boolean =
        !stopped && this.target == target && generation == requestGeneration

    private fun publish(next: Map<ProfileTimelineTab, ProfilePageState> = pages) {
        pages = next
        onPagesChanged(next)
    }
}
