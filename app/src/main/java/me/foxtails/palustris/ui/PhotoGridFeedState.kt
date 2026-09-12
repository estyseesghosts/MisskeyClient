package me.foxtails.palustris.ui

import me.foxtails.palustris.domain.OwnedPost
import me.foxtails.palustris.domain.Timeline

sealed interface PhotoGridFeed {
    data class TimelineFeed(val timeline: Timeline) : PhotoGridFeed
    data class Hashtag(val tag: String) : PhotoGridFeed
}

data class PhotoGridFeedState(
    val selectedFeed: PhotoGridFeed = PhotoGridFeed.TimelineFeed(Timeline.Home),
    val availableTimelines: List<Timeline> = listOf(Timeline.Home),
    val savedHashtags: List<String> = emptyList(),
    val posts: List<OwnedPost> = emptyList(),
    val initialLoadComplete: Boolean = false,
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val nextCursor: String? = null,
    val error: String? = null,
    val needsSignIn: Boolean = false,
    val preferenceLoading: Boolean = true,
    val preferenceSaving: Boolean = false,
    val preferenceError: String? = null,
)
