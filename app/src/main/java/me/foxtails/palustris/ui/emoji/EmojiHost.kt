package me.foxtails.palustris.ui.emoji

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.SocialSource
import me.foxtails.palustris.ui.session.ConnectedEntryStore
import me.foxtails.palustris.ui.shell.EmojiPresentation

/**
 * Owns the emoji catalog presentation for one connected session.
 *
 * The catalog model and its teardown stay beside the emoji feature. The shell receives only the
 * narrow [EmojiPresentation] contract.
 */
@Composable
fun EmojiHost(
    accountId: AccountId,
    sessionGeneration: Long,
    source: SocialSource,
    entryStore: ConnectedEntryStore,
): EmojiPresentation {
    val model = hiltViewModel<EmojiCatalogViewModel, EmojiCatalogViewModel.Factory>(
        key = "emoji-catalog-$accountId-$sessionGeneration",
        creationCallback = { factory -> factory.create(accountId, source) },
    )
    val state by model.state.collectAsStateWithLifecycle()
    LaunchedEffect(entryStore, sessionGeneration, model) {
        entryStore.register(sessionGeneration, "emoji-catalog-$accountId-$sessionGeneration") { model.stop() }
    }
    val actions = remember(model) {
        object : EmojiPresentation.Actions {
            override fun loadCatalog() { model.loadIfNeeded() }
            override fun retryCatalog() { model.retry() }
            override fun toggleGroupCollapsed(groupId: String) { model.toggleGroupCollapsed(groupId) }
            override fun toggleGroupPinned(groupId: String) { model.toggleGroupPinned(groupId) }
            override fun togglePinnedEmoji(identity: String) { model.togglePinnedEmoji(identity) }
        }
    }
    return remember(state, source.capabilities.emoji, actions) {
        EmojiPresentation(
            catalog = state,
            capabilities = source.capabilities.emoji,
            actions = actions,
        )
    }
}
