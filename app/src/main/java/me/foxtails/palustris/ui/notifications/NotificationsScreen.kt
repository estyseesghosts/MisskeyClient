@file:OptIn(
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
)

package me.foxtails.palustris.ui.notifications

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.distinctUntilChanged
import me.foxtails.palustris.R
import me.foxtails.palustris.domain.Notification
import me.foxtails.palustris.domain.NotificationActionState
import me.foxtails.palustris.domain.NotificationActivity
import me.foxtails.palustris.domain.NotificationCategory
import me.foxtails.palustris.domain.NotificationQuery
import me.foxtails.palustris.ui.AppIcons
import me.foxtails.palustris.ui.components.FilterChipEntry
import me.foxtails.palustris.ui.components.FilterChipRow
import me.foxtails.palustris.ui.CompactOverlayHorizontalPadding
import me.foxtails.palustris.ui.CompactFilterDockHeight
import me.foxtails.palustris.ui.EmptyState
import me.foxtails.palustris.ui.compactContextualControlsPositioningInsets
import me.foxtails.palustris.ui.compactScrollEndClearance
import me.foxtails.palustris.ui.motion.AnimatedStatePane
import me.foxtails.palustris.ui.motion.ExpandableContent

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
    onMarkNotificationSeen: (Notification?) -> Unit = {},
    onDismissNotification: (Notification) -> Unit = {},
    onFollowRequest: (Notification, Boolean) -> Unit = { _, _ -> },
    onOpenNotification: (Notification) -> Unit = {},
    onSelectQuery: (NotificationQuery) -> Unit = {},
    onMarkAllRead: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
) {
    var selectedFilterName by rememberSaveable(accountIdentity) { mutableStateOf<String?>(null) }
    var markAllReadConfirmationPending by rememberSaveable(accountIdentity) { mutableStateOf(false) }
    val selectedFilter = selectedFilterName?.let { name -> NotificationFilter.entries.firstOrNull { it.name == name } }
    val filters = remember { NotificationFilter.entries.toList() }

    fun toggleFilter(filter: NotificationFilter) {
        val nextFilterName = if (selectedFilterName == filter.name) null else filter.name
        selectedFilterName = nextFilterName
        onSelectQuery(if (nextFilterName == null) NotificationQuery() else filter.query)
    }

    val chipEntries = buildList {
        if (connected) {
            add(
                FilterChipEntry(
                    label = stringResource(
                        if (markAllReadConfirmationPending) {
                            R.string.notification_action_mark_all_read_confirmation
                        } else {
                            R.string.notification_action_mark_all_read
                        },
                    ),
                    onClick = {
                        if (markAllReadConfirmationPending) {
                            markAllReadConfirmationPending = false
                            onMarkAllRead()
                        } else {
                            markAllReadConfirmationPending = true
                        }
                    },
                    contentDescription = stringResource(R.string.notification_action_mark_all_read_description),
                    role = Role.Button,
                    testTag = "notification_mark_all_read",
                ),
            )
        }
        filters.forEach { filter ->
            add(
                FilterChipEntry(
                    label = stringResource(filter.labelRes),
                    selected = selectedFilterName == filter.name,
                    onClick = { toggleFilter(filter) },
                ),
            )
        }
        if (connected) {
            add(
                FilterChipEntry(
                    label = stringResource(R.string.notification_action_settings),
                    onClick = onOpenSettings,
                    contentDescription = stringResource(R.string.notification_action_settings_description),
                    role = Role.Button,
                    testTag = "notification_settings",
                ),
            )
        }
    }

    val visibleItems = notificationState.items.filter { selectedFilter?.matches(it) ?: true }
    val controlsPositioningInsets = compactContextualControlsPositioningInsets(navigationVisible = compactLayout)
    val notificationEndClearance = if (compactLayout) {
        compactScrollEndClearance(
            controlStackHeight = CompactFilterDockHeight,
            navigationVisible = true,
        )
    } else {
        0.dp
    }
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
                onMarkSeen = onMarkNotificationSeen,
                onDismiss = onDismissNotification,
                onFollowRequest = onFollowRequest,
                onOpen = onOpenNotification,
                modifier = Modifier.fillMaxSize(),
                endClearance = notificationEndClearance,
                stateKey = "${selectedFilterName ?: "all"}:${when {
                    notificationState.loading && notificationState.items.isEmpty() -> "loading"
                    notificationState.error != null && notificationState.items.isEmpty() -> "error"
                    visibleItems.isEmpty() -> "empty"
                    else -> "content"
                }}",
            )
            Box(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .padding(horizontal = CompactOverlayHorizontalPadding)
                    .windowInsetsPadding(controlsPositioningInsets),
            ) {
                FilterChipRow(chipEntries, stringResource(R.string.notification_filter_description))
            }
        }
    } else {
        Column(Modifier.fillMaxSize()) {
            FilterChipRow(chipEntries, stringResource(R.string.notification_filter_description))
            NotificationContent(
                title = title,
                subtitle = subtitle,
                state = notificationState,
                items = visibleItems,
                onRefresh = onRefreshNotifications,
                onLoadMore = onLoadMoreNotifications,
                onMarkSeen = onMarkNotificationSeen,
                onDismiss = onDismissNotification,
                onFollowRequest = onFollowRequest,
                onOpen = onOpenNotification,
                modifier = Modifier.weight(1f),
                endClearance = 0.dp,
                stateKey = "${selectedFilterName ?: "all"}:${when {
                    notificationState.loading && notificationState.items.isEmpty() -> "loading"
                    notificationState.error != null && notificationState.items.isEmpty() -> "error"
                    visibleItems.isEmpty() -> "empty"
                    else -> "content"
                }}",
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
    onMarkSeen: (Notification?) -> Unit,
    onDismiss: (Notification) -> Unit,
    onFollowRequest: (Notification, Boolean) -> Unit,
    onOpen: (Notification) -> Unit,
    modifier: Modifier,
    endClearance: Dp,
    stateKey: String,
) {
    val list = rememberLazyListState()
    val pullState = rememberPullToRefreshState()
    LaunchedEffect(list, state.loadingMore, state.checkpoint?.oldest) {
        snapshotFlow {
            state.checkpoint?.oldest != null && !state.loadingMore &&
                (list.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1) >= items.size - 3
        }.distinctUntilChanged().collect { shouldLoad -> if (shouldLoad) onLoadMore() }
    }
    AnimatedStatePane(stateKey = stateKey, modifier = modifier) {
        PullToRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = onRefresh,
            state = pullState,
            modifier = Modifier.fillMaxSize().testTag("notification_refresh_surface"),
            indicator = {
                PullToRefreshDefaults.Indicator(
                    state = pullState,
                    isRefreshing = state.refreshing,
                    modifier = Modifier.align(Alignment.TopCenter),
                )
            },
        ) {
        LazyColumn(
            state = list,
            modifier = Modifier.fillMaxSize().testTag("notifications_content"),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp + endClearance),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (items.isEmpty()) {
                item {
                    Box(Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                        when {
                            state.loading && !state.refreshing -> CircularProgressIndicator()
                            state.error != null -> Column(
                                Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                            ) {
                                Text(state.error, color = MaterialTheme.colorScheme.error)
                                TextButton(onClick = onRefresh) { Text(stringResource(R.string.notifications_retry)) }
                            }
                            else -> EmptyState(AppIcons.Notifications, title, subtitle)
                        }
                    }
                }
            } else {
                if (state.syncDelayed) item {
                    ExpandableContent(visible = state.syncDelayed) {
                        Text(stringResource(R.string.notifications_sync_delayed), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                items(items, key = { "${it.id.connection}\u0000${it.id.value}" }) { notification ->
                    NotificationRow(
                        notification = notification,
                        actionState = state.actionStates[notification.id] ?: NotificationActionState.Idle,
                        actionError = state.actionErrors[notification.id],
                        onOpen = { onMarkSeen(notification); onOpen(notification) },
                        onDismiss = { onDismiss(notification) },
                        onFollowRequest = { accept -> onFollowRequest(notification, accept) },
                        modifier = Modifier.animateItem(
                            fadeInSpec = me.foxtails.palustris.ui.motion.LocalPalustrisMotionScheme.current.fastFadeIn,
                            fadeOutSpec = me.foxtails.palustris.ui.motion.LocalPalustrisMotionScheme.current.fastFadeOut,
                            placementSpec = me.foxtails.palustris.ui.motion.LocalPalustrisMotionScheme.current.gentleOffset,
                        ),
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
}

private fun NotificationFilter.matches(notification: Notification): Boolean = when (this) {
    NotificationFilter.Replies -> notification.activity == NotificationActivity.Reply
    NotificationFilter.Reposts -> notification.activity == NotificationActivity.Reshare
    NotificationFilter.Followers -> notification.activity == NotificationActivity.Follow
    NotificationFilter.Likes -> notification.activity == NotificationActivity.Favourite ||
        notification.activity is NotificationActivity.EmojiReaction
}
