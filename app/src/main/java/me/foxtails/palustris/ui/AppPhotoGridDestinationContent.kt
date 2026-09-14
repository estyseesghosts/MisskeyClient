package me.foxtails.palustris.ui

import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.runtime.Composable
import me.foxtails.palustris.domain.ContentWarningRules
import me.foxtails.palustris.domain.OwnedPost

@Composable
internal fun AppPhotoGridDestinationContent(
    state: PhotoGridFeedState,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onSelectFeed: (PhotoGridFeed) -> Unit,
    onAddHashtag: (String, () -> Unit) -> Unit,
    onClearPreferenceError: () -> Unit,
    onOpenPost: (OwnedPost) -> Unit,
    compactLayout: Boolean,
    compactNavigationVisible: Boolean,
    gridState: LazyStaggeredGridState,
    contentWarningRules: ContentWarningRules = LocalContentWarningRules.current,
) {
    PhotoGridScreen(
        state = state,
        onRefresh = onRefresh,
        onLoadMore = onLoadMore,
        onSelectFeed = onSelectFeed,
        onAddHashtag = onAddHashtag,
        onClearPreferenceError = onClearPreferenceError,
        onOpenPost = onOpenPost,
        compactLayout = compactLayout,
        compactNavigationVisible = compactNavigationVisible,
        gridState = gridState,
        contentWarningRules = contentWarningRules,
    )
}
