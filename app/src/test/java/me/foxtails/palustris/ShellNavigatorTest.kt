package me.foxtails.palustris

import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.SaverScope
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.Audience
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.EntityId
import me.foxtails.palustris.domain.OwnedPost
import me.foxtails.palustris.domain.Post
import me.foxtails.palustris.domain.Protocol
import me.foxtails.palustris.domain.Timeline
import me.foxtails.palustris.ui.Destination
import me.foxtails.palustris.ui.LargePostOrigin
import me.foxtails.palustris.ui.LocalPage
import me.foxtails.palustris.ui.NotificationsPanel
import me.foxtails.palustris.ui.Overlay
import me.foxtails.palustris.ui.SearchPanel
import me.foxtails.palustris.ui.navigation.AppRoute
import me.foxtails.palustris.ui.navigation.ShellNavigator
import me.foxtails.palustris.ui.large.LargeNavTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ShellNavigatorTest {
    private val connection = Connection("https://example.org", Protocol.MASTODON)
    private val accountId = AccountId(connection, "owner")
    private val account = Account(accountId, "Owner", "@owner@example.org")
    private val scope = object : SaverScope {
        override fun canBeSaved(value: Any): Boolean = true
    }

    @Suppress("UNCHECKED_CAST")
    private val saver = ShellNavigator.Saver as Saver<ShellNavigator, Any>

    private fun ownedPost(id: String) = OwnedPost(        fetchedBy = accountId,
        post = Post(
            id = EntityId(connection.origin, id),
            author = account,
            text = id,
            publishedAtEpochMillis = 0L,
            audience = Audience.Public,
        ),
    )

    @Test
    fun overlayKeysMapToOverlays() {
        val navigator = ShellNavigator()
        assertNull(navigator.overlay)
        navigator.openComposerOverlay()
        assertEquals(Overlay.Composer, navigator.overlay)
        navigator.openEditProfileOverlay()
        assertEquals(Overlay.EditProfile, navigator.overlay)
        navigator.openNotificationSettingsOverlay()
        assertEquals(Overlay.NotificationSettings, navigator.overlay)
        navigator.closeOverlay()
        assertNull(navigator.overlay)
    }

    @Test
    fun clearSelectedPostResetsOrigin() {
        val navigator = ShellNavigator()
        navigator.singlePost = ownedPost("p1")
        navigator.singlePostOrigin = LargePostOrigin.PhotoGrid
        navigator.clearSelectedPost()
        assertNull(navigator.singlePost)
        assertEquals(LargePostOrigin.Other, navigator.singlePostOrigin)
    }

    @Test
    fun clampTimelineKeepsAvailableSelection() {
        val navigator = ShellNavigator()
        navigator.timeline = Timeline.Local
        navigator.clampTimeline(setOf(Timeline.Home, Timeline.Local))
        assertEquals(Timeline.Local, navigator.timeline)
        navigator.clampTimeline(setOf(Timeline.Home))
        assertEquals(Timeline.Home, navigator.timeline)
    }

    @Test
    fun syncHomeTimelineIgnoresNull() {
        val navigator = ShellNavigator()
        navigator.timeline = Timeline.Local
        navigator.syncHomeTimeline(null)
        assertEquals(Timeline.Local, navigator.timeline)
        navigator.syncHomeTimeline(Timeline.Social)
        assertEquals(Timeline.Social, navigator.timeline)
    }

    @Test
    fun resetForAccountClearsOnlyAccountScopedSelection() {
        val navigator = ShellNavigator()
        navigator.destination = Destination.Profile
        navigator.timeline = Timeline.Local
        navigator.searchPanelName = SearchPanel.PhotoGrid.name
        navigator.notificationsPanelName = NotificationsPanel.DirectMessages.name
        navigator.navigationVisible = false
        navigator.openNotificationSettingsOverlay()
        navigator.sheet = "Accounts"
        navigator.notificationRoute = AppRoute.Post(accountId, EntityId(connection.origin, "p1"))
        navigator.viewedProfile = account
        navigator.page = LocalPage.Drafts
        navigator.searchQuery = "tag"
        navigator.searchCategory = 2
        navigator.searchPrefill = "pre"
        navigator.singlePost = ownedPost("p1")
        navigator.singlePostOrigin = LargePostOrigin.Home
        navigator.resetForAccount()
        assertNull(navigator.viewedProfile)
        assertNull(navigator.page)
        assertEquals("", navigator.searchQuery)
        assertEquals(0, navigator.searchCategory)
        assertNull(navigator.singlePost)
        assertEquals(LargePostOrigin.Other, navigator.singlePostOrigin)
        assertEquals(Destination.Profile, navigator.destination)
        assertEquals(Timeline.Local, navigator.timeline)
        assertEquals(SearchPanel.PhotoGrid, navigator.searchPanel)
        assertEquals(NotificationsPanel.DirectMessages, navigator.notificationsPanel)
        assertFalse(navigator.navigationVisible)
        assertEquals(Overlay.NotificationSettings, navigator.overlay)
        assertEquals("Accounts", navigator.sheet)
        assertTrue(navigator.notificationRoute is AppRoute.Post)
        assertEquals("pre", navigator.searchPrefill)
    }

    @Test
    fun applyInitialRouteOpensNotificationsDestination() {
        val navigator = ShellNavigator()
        navigator.applyInitialRoute(null)
        assertNull(navigator.notificationRoute)
        assertEquals(Destination.Home, navigator.destination)
        val route = AppRoute.Post(accountId, EntityId(connection.origin, "p1"))
        navigator.applyInitialRoute(route)
        assertEquals(route, navigator.notificationRoute)
        assertEquals(Destination.Notifications, navigator.destination)
    }

    @Test
    fun applyInitialSettingsRouteOpensOverlayWithoutDetail() {
        val navigator = ShellNavigator()
        navigator.applyInitialRoute(AppRoute.NotificationSettings(accountId))
        assertNull(navigator.notificationRoute)
        assertEquals(Destination.Notifications, navigator.destination)
        assertEquals(Overlay.NotificationSettings, navigator.overlay)
    }

    @Test
    fun saverRoundTripPreservesNavigationState() {
        val navigator = ShellNavigator()
        navigator.destination = Destination.Search
        navigator.destinationTransitionDirection = -1
        navigator.timeline = Timeline.Federated
        navigator.page = LocalPage.Likes
        navigator.sheet = "Accounts"
        navigator.openEditProfileOverlay()
        navigator.searchPanelName = SearchPanel.PhotoGrid.name
        navigator.searchQuery = "tag"
        navigator.searchCategory = 3
        navigator.searchPrefill = "pre"
        navigator.notificationsPanelName = NotificationsPanel.DirectMessages.name
        navigator.navigationVisible = false
        val saved = with(scope) { with(saver) { save(navigator) } }!!
        val restored = saver.restore(saved)!!
        assertEquals(Destination.Search, restored.destination)
        assertEquals(-1, restored.destinationTransitionDirection)
        assertEquals(Timeline.Federated, restored.timeline)
        assertEquals(LocalPage.Likes, restored.page)
        assertEquals("Accounts", restored.sheet)
        assertEquals(Overlay.EditProfile, restored.overlay)
        assertEquals(SearchPanel.PhotoGrid, restored.searchPanel)
        assertEquals("tag", restored.searchQuery)
        assertEquals(3, restored.searchCategory)
        assertEquals("pre", restored.searchPrefill)
        assertEquals(NotificationsPanel.DirectMessages, restored.notificationsPanel)
        assertFalse(restored.navigationVisible)
        assertNull(restored.viewedProfile)
        assertNull(restored.singlePost)
        assertNull(restored.notificationRoute)
    }

    private fun recordingNavigator(): Triple<ShellNavigator, MutableList<String>, MutableList<Account>> {
        val cleared = mutableListOf<String>()
        val searched = mutableListOf<String>()
        val conversations = mutableListOf<Account>()
        val navigator = ShellNavigator()
        navigator.onClearTransient = { cleared += "cleared" }
        navigator.onSearch = { searched += it }
        navigator.onStartConversation = { conversations += it }
        return Triple(navigator, searched, conversations)
    }

    @Test
    fun selectDestinationClearsPageAndRoute() {
        val (navigator, searched, _) = recordingNavigator()
        navigator.page = LocalPage.Drafts
        navigator.notificationRoute = AppRoute.Post(accountId, EntityId(connection.origin, "p1"))
        navigator.selectDestination(Destination.Search)
        assertEquals(Destination.Search, navigator.destination)
        assertEquals(1, navigator.destinationTransitionDirection)
        assertNull(navigator.page)
        assertNull(navigator.notificationRoute)
        assertTrue(navigator.navigationVisible)
        assertTrue(searched.isEmpty())
    }

    @Test
    fun selectProfileDestinationClearsViewedProfile() {
        val (navigator, _, _) = recordingNavigator()
        navigator.viewedProfile = account
        navigator.selectDestination(Destination.Profile)
        assertNull(navigator.viewedProfile)
        assertEquals(Destination.Profile, navigator.destination)
    }

    @Test
    fun reducedMotionHasNoDirection() {
        val (navigator, _, _) = recordingNavigator()
        navigator.reducedMotion = true
        navigator.selectDestination(Destination.Profile)
        assertEquals(0, navigator.destinationTransitionDirection)
    }

    @Test
    fun openProfileShowsOwnProfileDestination() {
        val (navigator, _, _) = recordingNavigator()
        navigator.sheet = "Accounts"
        navigator.openProfile(account)
        assertEquals(account, navigator.viewedProfile)
        assertEquals(Destination.Profile, navigator.destination)
        assertEquals(1, navigator.destinationTransitionDirection)
        assertNull(navigator.sheet)
        assertNull(navigator.page)
        assertNull(navigator.notificationRoute)
    }

    @Test
    fun selectLargeTargetBindsPanels() {
        val (navigator, _, _) = recordingNavigator()
        navigator.selectLargeTarget(LargeNavTarget.PhotoGrid)
        assertEquals(Destination.Search, navigator.destination)
        assertEquals(SearchPanel.PhotoGrid, navigator.searchPanel)
        navigator.selectLargeTarget(LargeNavTarget.DirectMessages)
        assertEquals(Destination.Notifications, navigator.destination)
        assertEquals(NotificationsPanel.DirectMessages, navigator.notificationsPanel)
        navigator.selectLargeTarget(LargeNavTarget.Profile)
        assertEquals(Destination.Profile, navigator.destination)
        assertNull(navigator.viewedProfile)
    }

    @Test
    fun openDirectMessageStartsConversation() {
        val (navigator, _, conversations) = recordingNavigator()
        navigator.openDirectMessage(account)
        assertEquals(listOf(account), conversations)
        assertEquals(Destination.Notifications, navigator.destination)
        assertEquals(NotificationsPanel.DirectMessages, navigator.notificationsPanel)
        assertNull(navigator.page)
        assertNull(navigator.notificationRoute)
    }

    @Test
    fun openHashtagSearchExecutesSearch() {
        val (navigator, searched, _) = recordingNavigator()
        navigator.searchPrefill = "stale"
        navigator.openHashtagSearch("#tag")
        assertEquals("#tag", navigator.searchQuery)
        assertEquals("", navigator.searchPrefill)
        assertEquals(SearchPanel.Search, navigator.searchPanel)
        assertEquals(Destination.Search, navigator.destination)
        assertEquals(listOf("#tag"), searched)
    }

    @Test
    fun openAccountSearchExecutesSearch() {
        val (navigator, searched, _) = recordingNavigator()
        navigator.openAccountSearch("owner")
        assertEquals("owner", navigator.searchQuery)
        assertEquals(Destination.Search, navigator.destination)
        assertEquals(listOf("owner"), searched)
    }

    @Test
    fun openSinglePostClearsTransientAndSelects() {
        val (navigator, _, _) = recordingNavigator()
        var cleared = 0
        navigator.onClearTransient = { cleared++ }
        val post = ownedPost("p1")
        navigator.openSinglePost(post, LargePostOrigin.Home)
        assertEquals(1, cleared)
        assertEquals(post, navigator.singlePost)
        assertEquals(LargePostOrigin.Home, navigator.singlePostOrigin)
    }
}
