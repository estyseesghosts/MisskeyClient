package me.foxtails.palustris.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.Audience
import me.foxtails.palustris.domain.CapabilityStatus
import me.foxtails.palustris.domain.CreatePostRequest
import me.foxtails.palustris.domain.EmojiChoice
import me.foxtails.palustris.domain.OwnedPost
import me.foxtails.palustris.domain.SocialSource
import me.foxtails.palustris.domain.Timeline
import me.foxtails.palustris.ui.session.ConnectedEntryStore
import me.foxtails.palustris.ui.shell.HomeContract
import me.foxtails.palustris.ui.shell.HomeFeedUiState
import me.foxtails.palustris.ui.shell.PhotoGridContract
import me.foxtails.palustris.ui.shell.PostInteractions
import me.foxtails.palustris.ui.shell.PostProjectionCoordinator
import me.foxtails.palustris.ui.shell.SearchContract

/**
 * Owns the feed presentation for one connected session.
 *
 * The feed model, Home, Search, and Photo Grid contracts, the shared post-interaction
 * actions, the composer inputs, and the projection sink stay beside the feed feature.
 * The session host receives only the narrow [Feed] bundle and keeps source resolution,
 * the projection coordinator, and composer assembly.
 */
data class FeedComposerInputs(
    val availableAudiences: Set<Audience>,
    val canPublish: Boolean,
    val publishing: Boolean,
    val error: String?,
)

data class Feed(
    val home: HomeContract,
    val search: SearchContract,
    val photoGrid: PhotoGridContract,
    val postInteractions: PostInteractions,
    val composerInputs: FeedComposerInputs,
    val publish: (CreatePostRequest, (OwnedPost) -> Unit) -> Unit,
    val react: (OwnedPost, EmojiChoice) -> Unit,
)

@Composable
fun FeedHost(
    accountId: AccountId,
    sessionGeneration: Long,
    sessionRevision: Long,
    source: SocialSource,
    coordinator: PostProjectionCoordinator,
    entryStore: ConnectedEntryStore,
): Feed {
    val feedModel = hiltViewModel<FeedViewModel, FeedViewModel.Factory>(
        key = "feed-$accountId-$sessionGeneration",
        creationCallback = { factory ->
            factory.create(accountId, source, sessionRevision)
        },
    )
    val modelKey = "feed-$accountId-$sessionGeneration"
    LaunchedEffect(entryStore, sessionGeneration, feedModel) {
        entryStore.register(sessionGeneration, modelKey) { feedModel.stop() }
    }
    val feed by feedModel.feed.collectAsStateWithLifecycle()
    val homeActions = remember(feedModel) {
        object : HomeContract.Actions {
            override fun refresh(timeline: Timeline) { feedModel.refresh(timeline) }
            override fun loadMore(timeline: Timeline) { feedModel.loadMore(timeline) }
        }
    }
    val home = remember(feed, homeActions) {
        HomeContract(
            state = HomeFeedUiState(
                ownedPosts = feed.ownedPosts,
                posts = feed.posts,
                loading = feed.loading,
                loadingMore = feed.loadingMore,
                nextCursor = feed.nextCursor,
                error = feed.error,
                needsSignIn = feed.needsSignIn,
                selectedTimeline = feed.timeline,
                availableTimelines = feed.timelines,
            ),
            actions = homeActions,
        )
    }
    val searchActions = remember(feedModel) {
        object : SearchContract.Actions {
            override fun search(query: String) { feedModel.search(query) }
            override fun loadMore() { feedModel.loadMoreSearch() }
        }
    }
    val search = remember(feed.accountSearch, searchActions) {
        SearchContract(state = feed.accountSearch, actions = searchActions)
    }
    val postInteractionsActions = remember(feedModel) {
        object : PostInteractions.Actions {
            override fun favorite(post: OwnedPost) { feedModel.favorite(post) }
            override fun repost(post: OwnedPost) { feedModel.reshare(post) }
            override fun bookmark(post: OwnedPost) { feedModel.bookmark(post) }
            override fun react(post: OwnedPost, choice: EmojiChoice) { feedModel.react(post, choice) }
        }
    }
    val postInteractions = remember(feed.actions, feed.quoteStatus, postInteractionsActions) {
        PostInteractions(
            availableActions = feed.actions,
            quoteEnabled = feed.quoteStatus == CapabilityStatus.Supported,
            actions = postInteractionsActions,
        )
    }
    val photoGridFeed by feedModel.photoGridFeed.collectAsStateWithLifecycle()
    val photoGridActions = remember(feedModel) {
        object : PhotoGridContract.Actions {
            override fun ensureLoaded() { feedModel.ensurePhotoGridLoaded() }
            override fun selectFeed(feed: PhotoGridFeed) { feedModel.selectPhotoGridFeed(feed) }
            override fun refresh() { feedModel.refreshPhotoGrid() }
            override fun loadMore() { feedModel.loadMorePhotoGrid() }
            override fun addHashtag(value: String, onSuccess: () -> Unit) { feedModel.addPhotoGridHashtag(value, onSuccess) }
            override fun clearPreferenceError() { feedModel.clearPhotoGridPreferenceError() }
        }
    }
    val photoGrid = remember(photoGridFeed, photoGridActions) {
        PhotoGridContract(state = photoGridFeed, actions = photoGridActions)
    }
    val postReaction = remember(feedModel) {
        { post: OwnedPost, choice: EmojiChoice -> feedModel.react(post, choice) }
    }
    val feedSink = remember(feedModel) {
        object : PostProjectionCoordinator.Sink {
            override fun applyExternalPost(updated: OwnedPost) { feedModel.applyExternalPost(updated) }
            override fun applyPublishedPost(request: CreatePostRequest) { feedModel.applyPublishedPost(request) }
        }
    }
    DisposableEffect(coordinator, feedSink, feedModel) {
        coordinator.register(feedSink)
        val feedProjection: (OwnedPost) -> Unit = { updated ->
            coordinator.forwardExternalPost(feedSink, updated)
        }
        feedModel.addPostProjectionListener(feedProjection)
        onDispose {
            feedModel.removePostProjectionListener(feedProjection)
            coordinator.unregister(feedSink)
        }
    }
    val composerInputs = remember(feed.audiences, feed.canPublish, feed.publishing, feed.error) {
        FeedComposerInputs(
            availableAudiences = feed.audiences,
            canPublish = feed.canPublish,
            publishing = feed.publishing,
            error = feed.error,
        )
    }
    val publish = remember(feedModel, feedSink, coordinator) {
        { request: CreatePostRequest, onAccepted: (OwnedPost) -> Unit ->
            feedModel.create(request) { created ->
                feedModel.applyPublishedPost(request)
                coordinator.forwardPublishedPost(feedSink, request, created)
                onAccepted(created)
            }
        }
    }
    return remember(home, search, photoGrid, postInteractions, composerInputs, publish, postReaction) {
        Feed(
            home = home,
            search = search,
            photoGrid = photoGrid,
            postInteractions = postInteractions,
            composerInputs = composerInputs,
            publish = publish,
            react = postReaction,
        )
    }
}
