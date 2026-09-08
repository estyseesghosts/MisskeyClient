package me.foxtails.palustris.ui

import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.CapabilityStatus
import me.foxtails.palustris.domain.OwnedPost
import me.foxtails.palustris.domain.Post
import me.foxtails.palustris.domain.PostAction
import me.foxtails.palustris.domain.SavedPostsCapability
import me.foxtails.palustris.domain.Timeline

data class FeedState(
    val posts: List<Post> = emptyList(),
    val ownedPosts: List<OwnedPost> = emptyList(),
    val accountSearch: AccountSearchState = AccountSearchState(),
    val timeline: Timeline = Timeline.Home,
    val timelines: Set<Timeline> = setOf(Timeline.Home),
    val canPublish: Boolean = false,
    val actions: Set<PostAction> = emptySet(),
    val quoteStatus: CapabilityStatus = CapabilityStatus.Unknown,
    val savedPosts: SavedPostsCapability? = null,
    val favouriteEmoji: String = me.foxtails.palustris.domain.DEFAULT_FAVOURITE_EMOJI,
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val publishing: Boolean = false,
    val nextCursor: String? = null,
    val error: String? = null,
    val needsSignIn: Boolean = false,
)

data class AccountSearchState(
    val query: String = "",
    val accounts: List<Account> = emptyList(),
    val posts: List<Post> = emptyList(),
    val tagQuery: String? = null,
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val nextCursor: String? = null,
    val error: String? = null,
)

/** Actions with protocol-neutral callbacks in the feed UI. */
internal val ClientReadyPostActions = setOf(
    PostAction.Reply,
    PostAction.Reshare,
    PostAction.Favorite,
    PostAction.React,
    PostAction.Bookmark,
)
