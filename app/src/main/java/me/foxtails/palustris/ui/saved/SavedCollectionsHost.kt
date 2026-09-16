package me.foxtails.palustris.ui.saved

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import me.foxtails.palustris.ui.session.AccountManager
import me.foxtails.palustris.ui.feed.Feed
import me.foxtails.palustris.ui.session.ConnectedEntryStore
import me.foxtails.palustris.ui.shell.BookmarksContract
import me.foxtails.palustris.ui.shell.PostProjectionCoordinator

/**
 * Owns the saved collection presentation for one connected session.
 *
 * The collection model, its state, its actions, and its projection sink stay beside the
 * collection screen. The session host receives only the narrow [SavedCollections] contract.
 * A reaction on a saved row routes through the shared feed-backed interaction owner so every
 * surface keeps the same mutation result.
 */
data class SavedCollections(
    val bookmarks: BookmarksContract,
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
    entryStore: ConnectedEntryStore,
): SavedCollections {
    val savedPostsModel = hiltViewModel<SavedPostsViewModel, SavedPostsViewModel.Factory>(
        key = "saved-posts-$accountId-$sessionGeneration",
        creationCallback = { factory ->
            factory.create(accountId, source, sessionRevision)
        },
    )
    LaunchedEffect(entryStore, sessionGeneration, savedPostsModel) {
        entryStore.register(sessionGeneration, "saved-posts-$accountId-$sessionGeneration") {
            savedPostsModel.stop()
        }
    }
    val savedSink = remember(savedPostsModel) {
        object : PostProjectionCoordinator.Sink {
            override fun applyExternalPost(updated: OwnedPost) { savedPostsModel.applyExternalPost(updated) }
            override fun applyPublishedPost(request: CreatePostRequest) { savedPostsModel.applyPublishedPost(request) }
        }
    }
    DisposableEffect(coordinator, savedSink) {
        coordinator.register(savedSink)
        onDispose { coordinator.unregister(savedSink) }
    }
    val savedPostsState by savedPostsModel.state.collectAsStateWithLifecycle()
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
    return remember(bookmarks) { SavedCollections(bookmarks = bookmarks) }
}
