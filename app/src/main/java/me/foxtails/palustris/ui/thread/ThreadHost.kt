package me.foxtails.palustris.ui.thread

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.EmojiChoice
import me.foxtails.palustris.domain.EntityId
import me.foxtails.palustris.domain.OwnedPost
import me.foxtails.palustris.domain.SocialSource
import me.foxtails.palustris.ui.shell.PostProjectionCoordinator
import me.foxtails.palustris.ui.shell.ThreadContract

/**
 * Owns the selected-thread presentation for one connected session.
 *
 * The thread model, its foreground observation, and its projection registration stay beside the
 * thread feature. The shell receives only the narrow [ThreadContract].
 */
@Composable
fun ThreadHost(
    accountId: AccountId,
    sessionGeneration: Long,
    sessionRevision: Long,
    source: SocialSource,
    coordinator: PostProjectionCoordinator,
    lifecycleOwner: LifecycleOwner,
): ThreadContract {
    val model = hiltViewModel<PostThreadViewModel, PostThreadViewModel.Factory>(
        key = "thread-$accountId-$sessionGeneration",
        creationCallback = { factory -> factory.create(accountId, source, sessionRevision) },
    )
    DisposableEffect(sessionGeneration, model) {
        onDispose { model.stop() }
    }
    val sink = remember(model) {
        object : PostProjectionCoordinator.Sink {
            override fun applyExternalPost(updated: OwnedPost) { model.applyExternalPost(updated) }
            override fun acceptPublishedReply(created: OwnedPost) { model.acceptPublishedReply(created) }
            override fun acceptPublishedQuote(target: EntityId?) { model.acceptPublishedQuote(target) }
        }
    }
    DisposableEffect(coordinator, sink, model) {
        coordinator.register(sink)
        val forward: (OwnedPost) -> Unit = { updated -> coordinator.forwardExternalPost(sink, updated) }
        model.setPostUpdateListener(forward)
        onDispose {
            model.setPostUpdateListener(null)
            coordinator.unregister(sink)
        }
    }
    DisposableEffect(model, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> model.setForeground(true)
                Lifecycle.Event.ON_STOP -> model.setForeground(false)
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        model.setForeground(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            model.setForeground(false)
        }
    }
    val state by model.state.collectAsStateWithLifecycle()
    val actions = remember(model) {
        object : ThreadContract.Actions {
            override fun activate(post: OwnedPost?, enabled: Boolean) { model.activate(post, enabled) }
            override fun deactivate() { model.deactivate() }
            override fun refresh() { model.refresh() }
            override fun continueAcquisition() { model.continueAcquisition() }
            override fun favorite(post: OwnedPost) { model.favorite(post) }
            override fun repost(post: OwnedPost) { model.reshare(post) }
            override fun bookmark(post: OwnedPost) { model.bookmark(post) }
            override fun react(post: OwnedPost, choice: EmojiChoice) { model.react(post, choice) }
        }
    }
    return remember(state, actions) { ThreadContract(state = state, actions = actions) }
}
