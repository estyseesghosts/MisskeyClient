package me.foxtails.palustris.ui

import me.foxtails.palustris.domain.OwnedPost
import me.foxtails.palustris.domain.SavedPostsKind

enum class SavedPostsCollection { Bookmarks, Likes }

data class SavedPostsUiState(
    val collection: SavedPostsCollection = SavedPostsCollection.Bookmarks,
    val kind: SavedPostsKind = SavedPostsKind.Bookmarks,
    val posts: List<OwnedPost> = emptyList(),
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val nextCursor: String? = null,
    val error: String? = null,
    val needsSignIn: Boolean = false,
    val permissionRequired: Boolean = false,
)
