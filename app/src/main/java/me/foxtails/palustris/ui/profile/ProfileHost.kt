package me.foxtails.palustris.ui.profile

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
import me.foxtails.palustris.domain.EditableProfile
import me.foxtails.palustris.domain.EditableProfilePatch
import me.foxtails.palustris.domain.EmojiChoice
import me.foxtails.palustris.domain.OwnedPost
import me.foxtails.palustris.domain.SocialSource
import me.foxtails.palustris.ui.session.AccountManager
import me.foxtails.palustris.ui.session.ConnectedEntryStore
import me.foxtails.palustris.ui.shell.PostProjectionCoordinator
import me.foxtails.palustris.ui.shell.ProfileContract

/**
 * Owns the profile presentation for one connected session.
 *
 * The profile model, editor requests, and projection sink stay beside the profile feature. The
 * shell receives only the narrow [ProfileContract].
 */
@Composable
fun ProfileHost(
    accountId: AccountId,
    sessionGeneration: Long,
    sessionRevision: Long,
    source: SocialSource,
    accountManager: AccountManager,
    coordinator: PostProjectionCoordinator,
    entryStore: ConnectedEntryStore,
): ProfileContract {
    val model = hiltViewModel<ProfileViewModel, ProfileViewModel.Factory>(
        key = "profile-$accountId-$sessionGeneration",
        creationCallback = { factory -> factory.create(accountId, source, sessionRevision) },
    )
    LaunchedEffect(entryStore, sessionGeneration, model) {
        entryStore.register(sessionGeneration, "profile-$accountId-$sessionGeneration") { model.stop() }
    }
    val sink = remember(model) {
        object : PostProjectionCoordinator.Sink {
            override fun applyExternalPost(updated: OwnedPost) { model.applyExternalPost(updated) }
            override fun applyPublishedPost(request: CreatePostRequest) { model.applyPublishedPost(request) }
        }
    }
    DisposableEffect(coordinator, sink) {
        coordinator.register(sink)
        onDispose { coordinator.unregister(sink) }
    }
    val state by model.state.collectAsStateWithLifecycle()
    val actions = remember(model, accountManager) {
        object : ProfileContract.Actions {
            override fun open(account: Account) { model.open(account) }
            override fun selectCategory(category: ProfileCategory) { model.selectCategory(category) }
            override fun refresh() { model.refresh() }
            override fun loadMore() { model.loadMoreSelected() }
            override fun follow() { model.follow() }
            override fun unfollow() { model.unfollow() }
            override fun react(post: OwnedPost, choice: EmojiChoice) { model.react(post, choice) }
            override fun saveEditor(patch: EditableProfilePatch, onSuccess: () -> Unit) {
                model.saveEditor(patch) { updated ->
                    accountManager.updateAccount(updated)
                    onSuccess()
                }
            }
            override fun openEditor() { model.openEditor() }
            override fun updateEditor(draft: EditableProfile) { model.updateEditor(draft) }
            override fun closeEditor() { model.closeEditor() }
        }
    }
    return remember(state, actions) { ProfileContract(state = state, actions = actions) }
}
