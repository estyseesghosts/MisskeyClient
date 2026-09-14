package me.foxtails.palustris.ui

import me.foxtails.palustris.ui.shell.HomeFeedUiState

/** One evaluated Home paging demand. */
internal data class HomePagingInput(
    val nextCursor: String?,
    val loading: Boolean,
    val loadingMore: Boolean,
    val errorPresent: Boolean,
    val visiblePostCount: Int,
    val lastVisiblePostIndex: Int,
)

/**
 * Bounds automatic Home paging.
 *
 * A hidden-only or duplicate-only page can leave the visible list unchanged. The demand
 * therefore stops after [noProgressLimit] consecutive accepted pages without a new visible
 * row and waits for an explicit user continuation. New visible rows, a refresh, a timeline
 * change, a filter change, or a manual continuation reset the budget.
 */
internal class HomePagingDemand(
    private val threshold: Int = VISIBLE_THRESHOLD,
    private val noProgressLimit: Int = NO_PROGRESS_LIMIT,
) {
    private var pagesWithoutVisibleProgress = 0
    private var visibleBaseline = 0

    /** Records the current visible post count. New visible rows reset the budget. */
    fun onVisiblePostsChanged(count: Int) {
        if (count != visibleBaseline) {
            visibleBaseline = count
            pagesWithoutVisibleProgress = 0
        }
    }

    /** Resets the budget after a refresh, timeline or filter change, or manual continuation. */
    fun reset() {
        pagesWithoutVisibleProgress = 0
    }

    fun shouldRequestNextPage(input: HomePagingInput): Boolean {
        if (input.nextCursor == null) return false
        if (input.loading || input.loadingMore || input.errorPresent) return false
        if (pagesWithoutVisibleProgress >= noProgressLimit) return false
        // A fully filtered list still has a usable cursor. Keep bounded automatic progress
        // instead of stopping at the first hidden page.
        if (input.visiblePostCount == 0) return true
        return input.lastVisiblePostIndex >= input.visiblePostCount - threshold
    }

    fun onPageRequested() {
        pagesWithoutVisibleProgress += 1
    }

    companion object {
        const val VISIBLE_THRESHOLD = 5
        const val NO_PROGRESS_LIMIT = 3
    }
}

/**
 * Maps lazy-list item indices to the last visible post index.
 *
 * Indices that belong to the error, empty, loading, or footer items are ignored, so those
 * rows cannot satisfy the post threshold. Returns -1 when no post row is visible.
 */
internal fun lastVisiblePostIndex(
    visibleItemIndices: Iterable<Int>,
    leadingItemCount: Int,
    postCount: Int,
): Int {
    if (postCount <= 0) return -1
    val firstPostIndex = leadingItemCount
    val lastPostIndex = leadingItemCount + postCount - 1
    val lastVisiblePost = visibleItemIndices.filter { it in firstPostIndex..lastPostIndex }.maxOrNull()
        ?: return -1
    return lastVisiblePost - leadingItemCount
}

/** Counts the non-post items that precede the post range in the Home list. */
internal fun HomeFeedUiState.leadingItemCount(): Int =
    (if (error != null) 1 else 0) + (if (posts.isEmpty() && !loading && error == null) 1 else 0)
