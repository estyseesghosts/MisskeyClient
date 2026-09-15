package me.foxtails.palustris.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.OwnedPost
import me.foxtails.palustris.domain.Timeline
import me.foxtails.palustris.ui.Destination
import me.foxtails.palustris.ui.LargePostOrigin
import me.foxtails.palustris.ui.LocalPage
import me.foxtails.palustris.ui.NotificationsPanel
import me.foxtails.palustris.ui.Overlay
import me.foxtails.palustris.ui.SearchPanel

/**
 * Navigation state holder for the application shell.
 *
 * The holder owns shell navigation and selection state behind one boundary. Feature and
 * session coordination stays with the shell: the shell passes side-effect callbacks in and
 * keeps guarded closes, contract reads, and session-bound validation. Saved fields survive
 * process recreation through [Saver]. Transient selection clears on account change through
 * [resetForAccount].
 */
internal class ShellNavigator internal constructor() {
    var destination by mutableStateOf(Destination.Home)
    var destinationTransitionDirection by mutableStateOf(0)
    var timeline by mutableStateOf(Timeline.Home)
    var page by mutableStateOf<LocalPage?>(null)
    var sheet by mutableStateOf<String?>(null)
    var searchPanelName by mutableStateOf(SearchPanel.Search.name)
    var searchQuery by mutableStateOf("")
    var searchCategory by mutableStateOf(0)
    var searchPrefill by mutableStateOf("")
    var notificationsPanelName by mutableStateOf(NotificationsPanel.Notifications.name)
    var navigationVisible by mutableStateOf(true)
    var viewedProfile by mutableStateOf<Account?>(null)
    var singlePost by mutableStateOf<OwnedPost?>(null)
    var singlePostOrigin by mutableStateOf(LargePostOrigin.Other)
    var notificationRoute by mutableStateOf<AppRoute?>(null)

    internal var overlayKey by mutableStateOf<String?>(null)

    val overlay: Overlay?
        get() = when (overlayKey) {
            COMPOSER_OVERLAY_KEY -> Overlay.Composer
            EDIT_PROFILE_OVERLAY_KEY -> Overlay.EditProfile
            NOTIFICATION_SETTINGS_OVERLAY_KEY -> Overlay.NotificationSettings
            else -> null
        }
    val searchPanel: SearchPanel
        get() = SearchPanel.valueOf(searchPanelName)
    val notificationsPanel: NotificationsPanel
        get() = NotificationsPanel.valueOf(notificationsPanelName)

    fun openOverlayKey(key: String) {
        overlayKey = key
    }

    fun closeOverlay() {
        overlayKey = null
    }

    fun openComposerOverlay() {
        overlayKey = COMPOSER_OVERLAY_KEY
    }

    fun openEditProfileOverlay() {
        overlayKey = EDIT_PROFILE_OVERLAY_KEY
    }

    fun openNotificationSettingsOverlay() {
        overlayKey = NOTIFICATION_SETTINGS_OVERLAY_KEY
    }

    fun clearSelectedPost() {
        singlePost = null
        singlePostOrigin = LargePostOrigin.Other
    }

    fun clampTimeline(available: Set<Timeline>) {
        if (timeline !in available) timeline = Timeline.Home
    }

    fun syncHomeTimeline(selected: Timeline?) {
        selected?.let { timeline = it }
    }

    fun applyInitialRoute(route: AppRoute?) {
        notificationRoute = route
        if (route != null) {
            destination = Destination.Notifications
            if (route is AppRoute.NotificationSettings) {
                notificationRoute = null
                overlayKey = NOTIFICATION_SETTINGS_OVERLAY_KEY
            }
        }
    }

    /**
     * Clears account-scoped navigation selection when the active account changes.
     *
     * Destination, timeline, panels, overlays, sheets, and the route stay. Only the
     * viewed profile, the local page, the shared search fields, and the selected post
     * reset. Popup, media, and thread state reset with the shell.
     */
    fun resetForAccount() {
        viewedProfile = null
        page = null
        searchQuery = ""
        searchCategory = 0
        clearSelectedPost()
    }

    companion object {
        private const val COMPOSER_OVERLAY_KEY = "Composer"
        private const val EDIT_PROFILE_OVERLAY_KEY = "EditProfile"
        private const val NOTIFICATION_SETTINGS_OVERLAY_KEY = "NotificationSettings"
        private const val NULL_SENTINEL = ""

        val Saver: Saver<ShellNavigator, *> = listSaver(
            save = {
                listOf(
                    it.destination.name,
                    it.destinationTransitionDirection,
                    it.timeline.name,
                    it.page?.name ?: NULL_SENTINEL,
                    it.sheet ?: NULL_SENTINEL,
                    it.overlayKey ?: NULL_SENTINEL,
                    it.searchPanelName,
                    it.searchQuery,
                    it.searchCategory,
                    it.searchPrefill,
                    it.notificationsPanelName,
                    it.navigationVisible,
                )
            },
            restore = { saved ->
                ShellNavigator().apply {
                    destination = Destination.valueOf(saved[0] as String)
                    destinationTransitionDirection = saved[1] as Int
                    timeline = Timeline.valueOf(saved[2] as String)
                    (saved[3] as String).takeIf { it.isNotEmpty() }?.let { page = LocalPage.valueOf(it) }
                    (saved[4] as String).takeIf { it.isNotEmpty() }?.let { sheet = it }
                    (saved[5] as String).takeIf { it.isNotEmpty() }?.let { overlayKey = it }
                    searchPanelName = saved[6] as String
                    searchQuery = saved[7] as String
                    searchCategory = saved[8] as Int
                    searchPrefill = saved[9] as String
                    notificationsPanelName = saved[10] as String
                    navigationVisible = saved[11] as Boolean
                }
            },
        )
    }
}

/**
 * Creates and binds the shell navigator for the connected session.
 *
 * The navigator survives recomposition and process recreation. It clamps the timeline to
 * the available set, follows the Home selection, reasserts navigation visibility on
 * navigation change, applies the launch route once, and clears account-scoped selection
 * when the account changes.
 */
@Composable
internal fun rememberShellNavigator(
    accountId: AccountId?,
    initialRoute: AppRoute?,
    availableTimelines: Set<Timeline>,
    selectedHomeTimeline: Timeline?,
): ShellNavigator {
    val navigator = rememberSaveable(saver = ShellNavigator.Saver, init = ::ShellNavigator)
    LaunchedEffect(accountId) { navigator.resetForAccount() }
    LaunchedEffect(availableTimelines) { navigator.clampTimeline(availableTimelines) }
    LaunchedEffect(selectedHomeTimeline, accountId) { navigator.syncHomeTimeline(selectedHomeTimeline) }
    LaunchedEffect(navigator.destination, navigator.page, navigator.overlayKey) { navigator.navigationVisible = true }
    LaunchedEffect(initialRoute) { navigator.applyInitialRoute(initialRoute) }
    return navigator
}
