package me.foxtails.palustris.ui.shell

import me.foxtails.palustris.domain.OwnedPost
import me.foxtails.palustris.domain.Post
import me.foxtails.palustris.domain.Timeline

/**
 * Home timeline rows and paging state.
 *
 * Search results and Photo Grid state are deliberately not part of this contract. [Empty] is an
 * inert preview value.
 */
data class HomeFeedUiState(
    val ownedPosts: List<OwnedPost> = emptyList(),
    val posts: List<Post> = emptyList(),
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val nextCursor: String? = null,
    val error: String? = null,
    val needsSignIn: Boolean = false,
    val selectedTimeline: Timeline = Timeline.Home,
    val availableTimelines: Set<Timeline> = setOf(Timeline.Home),
)

/** Home timeline presentation. The Home owner drives timeline selection and paging. */
data class HomeContract(
    val state: HomeFeedUiState,
    val actions: Actions,
) {
    interface Actions {
        fun refresh(timeline: Timeline)
        fun loadMore(timeline: Timeline)
    }

    companion object {
        val Empty = HomeContract(HomeFeedUiState(), HomeEmptyActions)
    }
}

private object HomeEmptyActions : HomeContract.Actions {
    override fun refresh(timeline: Timeline) = Unit
    override fun loadMore(timeline: Timeline) = Unit
}
