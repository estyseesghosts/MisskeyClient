package me.foxtails.palustris

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.PostAction
import me.foxtails.palustris.ui.HomeFeed
import me.foxtails.palustris.ui.composer.ComposerOverlayHost
import me.foxtails.palustris.ui.composer.ComposerOwnerContext
import me.foxtails.palustris.ui.composer.rememberComposerOwner
import me.foxtails.palustris.ui.shell.ComposerContract
import me.foxtails.palustris.ui.shell.DraftsContract
import me.foxtails.palustris.ui.shell.HomeContract
import me.foxtails.palustris.ui.shell.PostInteractions

/**
 * Feature-level composer harness. It composes the Home feed presenter with the composer
 * feature owner and overlay host, without the full shell. Reply and quote transitions
 * resolve through the owner. A composer presentation change exercises this harness
 * without touching shell assembly or the shared shell fixture.
 */
internal object ComposerFeatureFixtures {
    @Composable
    fun reply(
        account: Account,
        home: HomeContract,
        postInteractions: PostInteractions,
        composer: ComposerContract = ComposerContract.Empty,
    ) {
        var overlayOpen by remember { mutableStateOf(false) }
        val owner = rememberComposerOwner(
            context = ComposerOwnerContext(
                account = account,
                contract = composer,
                canReply = PostAction.Reply in postInteractions.availableActions,
                canQuote = postInteractions.quoteEnabled,
                composerOpen = overlayOpen,
                overlayOpen = false,
            ),
            draftsContract = DraftsContract.Empty,
            sessionGeneration = 0L,
            sessionRevision = 0L,
        )
        LaunchedEffect(owner.navigation) {
            if (owner.navigation != null) {
                overlayOpen = true
                owner.consumeNavigation()
            }
        }
        HomeFeed(
            state = home.state,
            onRefresh = {},
            onLoadMore = {},
            onSignIn = {},
            ownedPosts = home.state.ownedPosts,
            availableActions = postInteractions.availableActions,
            quoteEnabled = postInteractions.quoteEnabled,
            onReply = { owner.requestReply(it) },
            onQuote = { owner.requestQuote(it) },
        )
        if (overlayOpen) {
            ComposerOverlayHost(
                owner = owner,
                contract = composer,
                account = account,
                onDismiss = { overlayOpen = false },
                onClose = { overlayOpen = false },
                onRequestEmoji = {},
                pendingEmojiInsertion = null,
                onEmojiInsertionApplied = {},
            )
        }
    }
}
