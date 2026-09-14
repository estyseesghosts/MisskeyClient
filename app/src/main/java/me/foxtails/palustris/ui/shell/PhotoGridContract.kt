package me.foxtails.palustris.ui.shell

import me.foxtails.palustris.ui.PhotoGridFeed
import me.foxtails.palustris.ui.PhotoGridFeedState

/**
 * Photo Grid presentation.
 *
 * Photo Grid has independent feed state and timeline selection. It must not reuse Home feed
 * state or Home scroll state. [Empty] is an inert preview value.
 */
data class PhotoGridContract(
    val state: PhotoGridFeedState,
    val actions: Actions,
) {
    interface Actions {
        fun ensureLoaded()
        fun selectFeed(feed: PhotoGridFeed)
        fun refresh()
        fun loadMore()
        fun addHashtag(value: String, onSuccess: () -> Unit)
        fun clearPreferenceError()
    }

    companion object {
        val Empty = PhotoGridContract(PhotoGridFeedState(), PhotoGridEmptyActions)
    }
}

private object PhotoGridEmptyActions : PhotoGridContract.Actions {
    override fun ensureLoaded() = Unit
    override fun selectFeed(feed: PhotoGridFeed) = Unit
    override fun refresh() = Unit
    override fun loadMore() = Unit
    override fun addHashtag(value: String, onSuccess: () -> Unit) = Unit
    override fun clearPreferenceError() = Unit
}
