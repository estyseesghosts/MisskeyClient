package me.foxtails.palustris.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.CreatePostRequest
import me.foxtails.palustris.domain.EmojiChoice
import me.foxtails.palustris.domain.OwnedPost
import me.foxtails.palustris.domain.SocialSource
import me.foxtails.palustris.ui.shell.BookmarksContract
import me.foxtails.palustris.ui.shell.LikesContract
import me.foxtails.palustris.ui.shell.PostProjectionCoordinator

/**
 * Owns the saved and liked collection presentation for one connected session.
 *
 * The two collection models, their state, their actions, and their projection sinks stay beside
 * the collection screens. The session host receives only the narrow [SavedCollections] contracts.
 * A reaction on a liked row routes through the shared feed-backed interaction owner so every
 * surface keeps the same mutation result.
 */
data class SavedCollections(
    val bookmarks: BookmarksContract,
    val likes: LikesContract,
)

@Composable
fun SavedCollectionsHost(
    accountId: AccountId,
    sessionGeneration: Long,
    sessionRevision: Long,
    source: SocialSource,
    account: Account,
    accountManager: AccountManager,
    coordinator: PostProjectionCoordinator,
    react: (OwnedPost, EmojiChoice) -> Unit,
): SavedCollections {
    val savedPostsModel = hiltViewModel<SavedPostsViewModel, SavedPostsViewModel.Factory>(
        key = "saved-posts-$accountId-$sessionGeneration",
        creationCallback = { factory ->
            factory.create(accountId, source, SavedPostsCollection.Bookmarks, sessionRevision)
        },
    )
    val likedPostsModel = hiltViewModel<SavedPostsViewModel, SavedPostsViewModel.Factory>(
        key = "liked-posts-$accountId-$sessionGeneration",
        creationCallback = { factory ->
            factory.create(accountId, source, SavedPostsCollection.Likes, sessionRevision)
        },
    )
    DisposableEffect(sessionGeneration, savedPostsModel, likedPostsModel) {
        onDispose {
            savedPostsModel.stop()
            likedPostsModel.stop()
        }
    }
    val savedSink = remember(savedPostsModel) {
        object : PostProjectionCoordinator.Sink {
            override fun applyExternalPost(updated: OwnedPost) { savedPostsModel.applyExternalPost(updated) }
            override fun applyPublishedPost(request: CreatePostRequest) { savedPostsModel.applyPublishedPost(request) }
        }
    }
    val likedSink = remember(likedPostsModel) {
        object : PostProjectionCoordinator.Sink {
            override fun applyExternalPost(updated: OwnedPost) { likedPostsModel.applyExternalPost(updated) }
            override fun applyPublishedPost(request: CreatePostRequest) { likedPostsModel.applyPublishedPost(request) }
        }
    }
    DisposableEffect(coordinator, savedSink, likedSink) {
        listOf(savedSink, likedSink).forEach(coordinator::register)
        onDispose { listOf(savedSink, likedSink).forEach(coordinator::unregister) }
    }
    val savedPostsState by savedPostsModel.state.collectAsStateWithLifecycle()
    val likedPostsState by likedPostsModel.state.collectAsStateWithLifecycle()
    val bookmarksActions = remember(savedPostsModel, accountManager, account) {
        object : BookmarksContract.Actions {
            override fun refresh() { savedPostsModel.refresh() }
            override fun loadMore() { savedPostsModel.loadMore() }
            override fun remove(post: OwnedPost) { savedPostsModel.unsave(post) }
            override fun upgradePermissions() { accountManager.upgradePermissions(account.id) }
            override fun react(post: OwnedPost, choice: EmojiChoice) { savedPostsModel.react(post, choice) }
        }
    }
    val bookmarks = remember(savedPostsState, bookmarksActions) {
        BookmarksContract(state = savedPostsState, actions = bookmarksActions)
    }
    val likesActions = remember(likedPostsModel, react) {
        object : LikesContract.Actions {
            override fun refresh() { likedPostsModel.refresh() }
            override fun loadMore() { likedPostsModel.loadMore() }
            override fun toggle(post: OwnedPost) { likedPostsModel.toggleFavourite(post) }
            override fun react(post: OwnedPost, choice: EmojiChoice) { react(post, choice) }
        }
    }
    val likes = remember(likedPostsState, likesActions) {
        LikesContract(state = likedPostsState, actions = likesActions)
    }
    return remember(bookmarks, likes) { SavedCollections(bookmarks = bookmarks, likes = likes) }
}
