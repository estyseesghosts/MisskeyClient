package me.foxtails.palustris.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.Modifier
import me.foxtails.palustris.domain.ContentWarningRules
import me.foxtails.palustris.domain.Notification
import me.foxtails.palustris.domain.OwnedPost
import me.foxtails.palustris.ui.notifications.NotificationDetailScreen
import me.foxtails.palustris.ui.navigation.AppRoute

@Composable
internal fun AppNotificationDetailContent(
    route: AppRoute,
    items: List<Notification>,
    onSearchHashtag: (String) -> Unit,
    onOpenHashtagBubble: (OwnedPost, List<String>, Rect) -> Unit,
    onOpenPost: (OwnedPost) -> Unit,
    onOpenTarget: (() -> Unit)?,
    largeLayout: Boolean,
    contentWarningRules: ContentWarningRules,
) {
    NotificationDetailScreen(
        route = route,
        items = items,
        onSearchHashtag = onSearchHashtag,
        onOpenHashtagBubble = onOpenHashtagBubble,
        onOpenPost = onOpenPost,
        onOpenTarget = onOpenTarget,
        largeLayout = largeLayout,
        modifier = Modifier.fillMaxSize(),
        contentWarningRules = contentWarningRules,
    )
}
