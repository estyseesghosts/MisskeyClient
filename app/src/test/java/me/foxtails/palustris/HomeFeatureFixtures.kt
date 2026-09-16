package me.foxtails.palustris

import androidx.compose.runtime.Composable
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.CapabilityStatus
import me.foxtails.palustris.domain.EmojiChoice
import me.foxtails.palustris.domain.OwnedPost
import me.foxtails.palustris.ui.feed.FeedState
import me.foxtails.palustris.ui.feed.HomeFeed

/**
 * Feature-level Home feed harness. It composes the Home feed presenter directly,
 * without shell assembly, navigation, or popups. Row rendering, filtering, counts,
 * and truncation resolve through the presenter. Tests that need floating assembly,
 * shell navigation, the thread detail, or the shell popup hosts stay on
 * `AppShellFixtures.app`.
 */
internal object HomeFeatureFixtures {
    @Composable
    fun feed(
        feedState: FeedState,
        onReaction: (OwnedPost, EmojiChoice) -> Unit = { _, _ -> },
        onSearchHashtag: (String) -> Unit = {},
        onOpenProfile: (Account) -> Unit = {},
        onOpenPost: ((OwnedPost) -> Unit)? = null,
    ) {
        HomeFeed(
            state = AppShellFixtures.homeFeed(feedState),
            onRefresh = {},
            onLoadMore = {},
            onSignIn = {},
            availableActions = feedState.actions,
            quoteEnabled = feedState.quoteStatus == CapabilityStatus.Supported,
            onReaction = onReaction,
            onSearchHashtag = onSearchHashtag,
            onOpenProfile = onOpenProfile,
            onOpenPost = onOpenPost,
        )
    }
}
