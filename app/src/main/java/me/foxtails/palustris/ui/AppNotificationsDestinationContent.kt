package me.foxtails.palustris.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import me.foxtails.palustris.R
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.ContentWarningRules
import me.foxtails.palustris.domain.DirectConversation
import me.foxtails.palustris.domain.Notification
import me.foxtails.palustris.domain.NotificationQuery
import me.foxtails.palustris.ui.directmessages.DirectMessageConversationScreen
import me.foxtails.palustris.ui.directmessages.DirectMessageInboxScreen
import me.foxtails.palustris.ui.directmessages.DirectMessageUiState
import me.foxtails.palustris.ui.motion.AnimatedStatePane
import me.foxtails.palustris.ui.notifications.NotificationsScreen

@Composable
internal fun AppNotificationsDestinationContent(
    panel: NotificationsPanel,
    account: Account?,
    compactLayout: Boolean,
    compactNavigationVisible: Boolean,
    notificationAccountIdentity: String,
    notificationState: NotificationsUiState,
    onRefreshNotifications: () -> Unit,
    onLoadMoreNotifications: () -> Unit,
    onMarkNotificationSeen: (Notification?) -> Unit,
    onDismissNotification: (Notification) -> Unit,
    onFollowRequest: (Notification, Boolean) -> Unit,
    onOpenNotification: (Notification) -> Unit,
    onSelectQuery: (NotificationQuery) -> Unit,
    onMarkAllRead: () -> Unit,
    onOpenSettings: () -> Unit,
    directMessageState: DirectMessageUiState,
    onRefreshDirectMessages: () -> Unit,
    onLoadMoreDirectMessages: () -> Unit,
    onOpenDirectConversation: (DirectConversation) -> Unit,
    onBackDirectConversation: () -> Unit,
    onSendDirectMessage: (String) -> Unit,
    contentWarningRules: ContentWarningRules = LocalContentWarningRules.current,
) {
    AnimatedStatePane(
        stateKey = panel,
        modifier = Modifier.fillMaxSize(),
    ) { selectedPanel ->
        if (selectedPanel == NotificationsPanel.Notifications) {
            NotificationsScreen(
                connected = account != null,
                compactLayout = compactLayout,
                accountIdentity = notificationAccountIdentity,
                notificationState = notificationState,
                onRefreshNotifications = onRefreshNotifications,
                onLoadMoreNotifications = onLoadMoreNotifications,
                onMarkNotificationSeen = onMarkNotificationSeen,
                onDismissNotification = onDismissNotification,
                onFollowRequest = onFollowRequest,
                onOpenNotification = onOpenNotification,
                onSelectQuery = onSelectQuery,
                onMarkAllRead = onMarkAllRead,
                onOpenSettings = onOpenSettings,
                contentWarningRules = contentWarningRules,
            )
        } else if (account == null) {
            EmptyState(
                AppIcons.Chat,
                androidx.compose.ui.res.stringResource(R.string.direct_messages_connect_title),
                androidx.compose.ui.res.stringResource(R.string.direct_messages_connect_subtitle),
            )
        } else if (directMessageState.selectedConversationId != null || directMessageState.recipient != null) {
            DirectMessageConversationScreen(
                accountId = account.id,
                state = directMessageState,
                compactLayout = compactLayout,
                compactNavigationVisible = compactNavigationVisible,
                onBack = onBackDirectConversation,
                onSend = onSendDirectMessage,
            )
        } else {
            DirectMessageInboxScreen(
                accountId = account.id,
                state = directMessageState,
                compactLayout = compactLayout,
                compactNavigationVisible = compactNavigationVisible,
                onRefresh = onRefreshDirectMessages,
                onLoadMore = onLoadMoreDirectMessages,
                onOpenConversation = onOpenDirectConversation,
            )
        }
    }
}
