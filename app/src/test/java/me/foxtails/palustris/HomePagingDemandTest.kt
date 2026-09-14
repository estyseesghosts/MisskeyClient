package me.foxtails.palustris

import me.foxtails.palustris.ui.HomePagingDemand
import me.foxtails.palustris.ui.HomePagingInput
import me.foxtails.palustris.ui.lastVisiblePostIndex
import me.foxtails.palustris.ui.leadingItemCount
import me.foxtails.palustris.ui.shell.HomeFeedUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomePagingDemandTest {
    private fun input(
        nextCursor: String? = "c1",
        loading: Boolean = false,
        loadingMore: Boolean = false,
        errorPresent: Boolean = false,
        visiblePostCount: Int = 10,
        lastVisiblePostIndex: Int = 0,
    ) = HomePagingInput(nextCursor, loading, loadingMore, errorPresent, visiblePostCount, lastVisiblePostIndex)

    @Test
    fun unfilteredRowsReachTheThresholdOncePerCursor() {
        val demand = HomePagingDemand()
        demand.onVisiblePostsChanged(10)

        assertFalse(demand.shouldRequestNextPage(input(lastVisiblePostIndex = 4)))
        assertTrue(demand.shouldRequestNextPage(input(lastVisiblePostIndex = 5)))

        demand.onPageRequested()
        // The accepted page adds visible rows, so the budget resets.
        demand.onVisiblePostsChanged(20)
        assertTrue(demand.shouldRequestNextPage(input(visiblePostCount = 20, lastVisiblePostIndex = 15)))
    }

    @Test
    fun finalFetchedRowsAreFiltered() {
        val demand = HomePagingDemand()
        demand.onVisiblePostsChanged(8)

        assertFalse(demand.shouldRequestNextPage(input(visiblePostCount = 8, lastVisiblePostIndex = 2)))
        assertTrue(demand.shouldRequestNextPage(input(visiblePostCount = 8, lastVisiblePostIndex = 3)))
    }

    @Test
    fun everyRowFilteredThenALaterPageHasAVisiblePost() {
        val demand = HomePagingDemand()
        demand.onVisiblePostsChanged(0)

        // A fully filtered list still has a usable cursor.
        assertTrue(demand.shouldRequestNextPage(input(visiblePostCount = 0, lastVisiblePostIndex = -1)))
        demand.onPageRequested()
        // The next page still has no visible row.
        assertTrue(demand.shouldRequestNextPage(input(visiblePostCount = 0, lastVisiblePostIndex = -1)))
        // A later page contains a visible post, which resets the budget.
        demand.onVisiblePostsChanged(1)
        assertTrue(demand.shouldRequestNextPage(input(visiblePostCount = 1, lastVisiblePostIndex = 0)))
    }

    @Test
    fun hiddenOnlyPagesExhaustTheAutomaticBudget() {
        val demand = HomePagingDemand()
        demand.onVisiblePostsChanged(0)

        repeat(HomePagingDemand.NO_PROGRESS_LIMIT) {
            assertTrue(demand.shouldRequestNextPage(input(visiblePostCount = 0, lastVisiblePostIndex = -1)))
            demand.onPageRequested()
        }
        assertFalse(demand.shouldRequestNextPage(input(visiblePostCount = 0, lastVisiblePostIndex = -1)))
    }

    @Test
    fun manualContinuationResetsAnExhaustedBudget() {
        val demand = HomePagingDemand()
        demand.onVisiblePostsChanged(0)
        repeat(HomePagingDemand.NO_PROGRESS_LIMIT) { demand.onPageRequested() }

        assertFalse(demand.shouldRequestNextPage(input(visiblePostCount = 0, lastVisiblePostIndex = -1)))
        demand.reset()
        assertTrue(demand.shouldRequestNextPage(input(visiblePostCount = 0, lastVisiblePostIndex = -1)))
    }

    @Test
    fun cursorChangeWithoutVisibleProgressStillAllowsBoundedProgress() {
        val demand = HomePagingDemand()
        demand.onVisiblePostsChanged(0)

        assertTrue(demand.shouldRequestNextPage(input(nextCursor = "c1", visiblePostCount = 0, lastVisiblePostIndex = -1)))
        demand.onPageRequested()
        // The next cursor arrived but the visible rows did not change.
        assertTrue(demand.shouldRequestNextPage(input(nextCursor = "c2", visiblePostCount = 0, lastVisiblePostIndex = -1)))
    }

    @Test
    fun cursorChangeAloneDoesNotBreakTheThreshold() {
        val demand = HomePagingDemand()
        demand.onVisiblePostsChanged(10)

        assertFalse(demand.shouldRequestNextPage(input(nextCursor = "c2", lastVisiblePostIndex = 0)))
    }

    @Test
    fun loadingErrorAndFooterRowsCannotTriggerPaging() {
        val demand = HomePagingDemand()
        demand.onVisiblePostsChanged(10)

        assertFalse(demand.shouldRequestNextPage(input(loading = true, lastVisiblePostIndex = 9)))
        assertFalse(demand.shouldRequestNextPage(input(loadingMore = true, lastVisiblePostIndex = 9)))
        assertFalse(demand.shouldRequestNextPage(input(errorPresent = true, lastVisiblePostIndex = 9)))
        assertFalse(demand.shouldRequestNextPage(input(nextCursor = null, lastVisiblePostIndex = 9)))
    }

    @Test
    fun lastVisiblePostIndexIgnoresNonPostRows() {
        // Error item at 0, posts 1..3, loading item at 4, footer at 5.
        assertEquals(2, lastVisiblePostIndex(listOf(0, 1, 2, 3, 4, 5), leadingItemCount = 1, postCount = 3))
        // Only the loading and footer rows are visible.
        assertEquals(-1, lastVisiblePostIndex(listOf(4, 5), leadingItemCount = 1, postCount = 3))
        // No post rows loaded.
        assertEquals(-1, lastVisiblePostIndex(listOf(0, 1), leadingItemCount = 0, postCount = 0))
        // Posts start at 0 when no leading item exists.
        assertEquals(2, lastVisiblePostIndex(listOf(0, 1, 2), leadingItemCount = 0, postCount = 3))
    }

    @Test
    fun leadingItemCountTracksNonPostItems() {
        // An empty feed shows the empty-state item.
        assertEquals(1, HomeFeedUiState().leadingItemCount())
        // A loading empty feed shows the loading state, not the empty-state item.
        assertEquals(0, HomeFeedUiState(loading = true).leadingItemCount())
        // An error row precedes the posts.
        assertEquals(1, HomeFeedUiState(error = "down").leadingItemCount())
        // A refresh keeps the error row; no empty-state item is added.
        assertEquals(1, HomeFeedUiState(error = "down", loading = true).leadingItemCount())
    }
}
