@file:OptIn(
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
)

package me.foxtails.palustris.ui.notifications

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.distinctUntilChanged
import me.foxtails.palustris.R
import me.foxtails.palustris.domain.Notification
import me.foxtails.palustris.domain.NotificationActionState
import me.foxtails.palustris.domain.NotificationActivity
import me.foxtails.palustris.domain.NotificationCategory
import me.foxtails.palustris.domain.NotificationQuery
import me.foxtails.palustris.ui.AppIcons
import me.foxtails.palustris.ui.CategoryChips
import me.foxtails.palustris.ui.CompactOverlayHorizontalPadding
import me.foxtails.palustris.ui.CompactSearchChipRowHeight
import me.foxtails.palustris.ui.EmptyState
import me.foxtails.palustris.ui.compactDockInsets

private enum class NotificationFilter(val labelRes: Int, val query: NotificationQuery) {
    Replies(R.string.notification_filter_replies, NotificationQuery(setOf(NotificationCategory.Replies))),
    Reposts(R.string.notification_filter_reposts, NotificationQuery(setOf(NotificationCategory.Social))),
    Followers(R.string.notification_filter_followers, NotificationQuery(setOf(NotificationCategory.Social))),
    Likes(R.string.notification_filter_likes, NotificationQuery(setOf(NotificationCategory.Social))),
}

@Composable
fun NotificationsScreen(
    connected: Boolean = false,
    compactLayout: Boolean = true,
    accountIdentity: String = "preview",
    notificationState: me.foxtails.palustris.ui.NotificationsUiState = me.foxtails.palustris.ui.NotificationsUiState(),
    onRefreshNotifications: () -> Unit = {},
    onLoadMoreNotifications: () -> Unit = {},
    onMarkAllNotificationsRead: () -> Unit = {},
    onMarkNotificationSeen: (Notification?) -> Unit = {},
    onDismissNotification: (Notification) -> Unit = {},
    onFollowRequest: (Notification, Boolean) -> Unit = { _, _ -> },
    onOpenNotification: (Notification) -> Unit = {},
    onSelectQuery: (NotificationQuery) -> Unit = {},
) {
    var selectedFilterName by rememberSaveable(accountIdentity) { mutableStateOf<String?>(null) }
    val selectedFilter = selectedFilterName?.let { name -> NotificationFilter.entries.firstOrNull { it.name == name } }
    val selectedIndex = selectedFilter?.ordinal
    val filters = remember { NotificationFilter.entries.toList() }

    fun toggleFilter(index: Int) {
        val filter = filters[index]
        val nextFilterName = if (selectedFilterName == filter.name) null else filter.name
        selectedFilterName = nextFilterName
        onSelectQuery(if (nextFilterName == null) NotificationQuery() else filter.query)
    }

    val visibleItems = notificationState.items.filter { selectedFilter?.matches(it) ?: true }
    val dockInsets = compactDockInsets(
        WindowInsets.navigationBarsIgnoringVisibility,
        WindowInsets(bottom = 0.dp),
        navigationVisible = compactLayout,
    )
    val title = selectedFilter?.let { stringResource(it.labelRes) }
        ?: stringResource(if (connected) R.string.notifications_title else R.string.notifications_empty_title)
    val subtitle = if (selectedFilter == null) {
        stringResource(R.string.notifications_empty_subtitle)
    } else {
        stringResource(R.string.notifications_empty_filter_subtitle)
    }

    if (compactLayout) {
        Box(Modifier.fillMaxSize()) {
            NotificationContent(
                title = title,
                subtitle = subtitle,
                state = notificationState,
                items = visibleItems,
                onRefresh = onRefreshNotifications,
                onLoadMore = onLoadMoreNotifications,
                onMarkAll = onMarkAllNotificationsRead,
                onMarkSeen = onMarkNotificationSeen,
                onDismiss = onDismissNotification,
                onFollowRequest = onFollowRequest,
                onOpen = onOpenNotification,
                modifier = Modifier.fillMaxSize().windowInsetsPadding(dockInsets).padding(bottom = CompactSearchChipRowHeight + 8.dp),
            )
            Box(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .padding(horizontal = CompactOverlayHorizontalPadding)
                    .windowInsetsPadding(dockInsets),
            ) {
                CategoryChips(
                    filters.map { stringResource(it.labelRes) },
                    selectedIndex,
                    stringResource(R.string.notification_filter_description),
                    onSelect = ::toggleFilter,
                )
            }
        }
    } else {
        Column(Modifier.fillMaxSize()) {
            CategoryChips(
                filters.map { stringResource(it.labelRes) },
                selectedIndex,
                stringResource(R.string.notification_filter_description),
                onSelect = ::toggleFilter,
            )
            NotificationContent(
                title = title,
                subtitle = subtitle,
                state = notificationState,
                items = visibleItems,
                onRefresh = onRefreshNotifications,
                onLoadMore = onLoadMoreNotifications,
                onMarkAll = onMarkAllNotificationsRead,
                onMarkSeen = onMarkNotificationSeen,
                onDismiss = onDismissNotification,
                onFollowRequest = onFollowRequest,
                onOpen = onOpenNotification,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun NotificationContent(
    title: String,
    subtitle: String,
    state: me.foxtails.palustris.ui.NotificationsUiState,
    items: List<Notification>,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onMarkAll: () -> Unit,
    onMarkSeen: (Notification?) -> Unit,
    onDismiss: (Notification) -> Unit,
    onFollowRequest: (Notification, Boolean) -> Unit,
    onOpen: (Notification) -> Unit,
    modifier: Modifier,
) {
    val list = rememberLazyListState()
    val pullState = rememberPullToRefreshState()
    LaunchedEffect(list, state.loadingMore, state.checkpoint?.oldest) {
        snapshotFlow {
            state.checkpoint?.oldest != null && !state.loadingMore &&
                (list.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1) >= items.size - 3
        }.distinctUntilChanged().collect { shouldLoad -> if (shouldLoad) onLoadMore() }
    }
    PullToRefreshBox(
        isRefreshing = state.refreshing || (state.loading && items.isNotEmpty()),
        onRefresh = onRefresh,
        state = pullState,
        modifier = modifier,
        indicator = {
            PullToRefreshDefaults.Indicator(
                state = pullState,
                isRefreshing = state.refreshing || state.loading,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        },
    ) {
        when {
            state.loading && items.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            items.isEmpty() && state.error != null -> Column(
                Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(state.error, color = MaterialTheme.colorScheme.error)
                TextButton(onClick = onRefresh) { Text(stringResource(R.string.notifications_retry)) }
            }
            items.isEmpty() -> EmptyState(AppIcons.Notifications, title, subtitle)
            else -> LazyColumn(
                state = list,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = onRefresh) { Text(stringResource(R.string.notifications_refresh)) }
                        TextButton(onClick = onMarkAll) { Text(stringResource(R.string.notifications_mark_all_read)) }
                    }
                }
                if (state.syncDelayed) item {
                    Text(stringResource(R.string.notifications_sync_delayed), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                items(items, key = { "${it.id.connection}\u0000${it.id.value}" }) { notification ->
                    NotificationRow(
                        notification = notification,
                        actionState = state.actionStates[notification.id] ?: NotificationActionState.Idle,
                        actionError = state.actionErrors[notification.id],
                        onOpen = { onMarkSeen(notification); onOpen(notification) },
                        onDismiss = { onDismiss(notification) },
                        onFollowRequest = { accept -> onFollowRequest(notification, accept) },
                    )
                }
                item {
                    when {
                        state.loadingMore -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(Modifier.padding(12.dp))
                        }
                        state.checkpoint?.oldest != null -> TextButton(
                            onClick = onLoadMore,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(stringResource(R.string.notifications_load_older)) }
                    }
                }
                state.error?.let { error -> item { Text(error, color = MaterialTheme.colorScheme.error) } }
            }
        }
    }
}

private fun NotificationFilter.matches(notification: Notification): Boolean = when (this) {
    NotificationFilter.Replies -> notification.activity == NotificationActivity.Reply
    NotificationFilter.Reposts -> notification.activity == NotificationActivity.Reshare
    NotificationFilter.Followers -> notification.activity == NotificationActivity.Follow
    NotificationFilter.Likes -> notification.activity == NotificationActivity.Favourite ||
        notification.activity is NotificationActivity.EmojiReaction
}
