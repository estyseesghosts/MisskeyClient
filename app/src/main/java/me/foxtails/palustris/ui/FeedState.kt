package me.foxtails.palustris.ui

import me.foxtails.palustris.domain.OwnedPost
import me.foxtails.palustris.domain.Post
import me.foxtails.palustris.domain.Timeline

data class FeedState(
    val posts: List<Post> = emptyList(),
    val ownedPosts: List<OwnedPost> = emptyList(),
    val timelines: Set<Timeline> = setOf(Timeline.Home),
    val canPublish: Boolean = false,
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val publishing: Boolean = false,
    val nextCursor: String? = null,
    val error: String? = null,
    val needsSignIn: Boolean = false,
)
