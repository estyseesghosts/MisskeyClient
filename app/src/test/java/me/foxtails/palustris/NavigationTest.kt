package me.foxtails.palustris

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.ViewGroup
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.activity.compose.setContent
import org.junit.Before
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import me.foxtails.palustris.data.auth.AccountRef
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.Audience
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.EntityId
import me.foxtails.palustris.domain.Notification
import me.foxtails.palustris.domain.NotificationActivity
import me.foxtails.palustris.domain.Protocol
import me.foxtails.palustris.domain.Post
import me.foxtails.palustris.domain.OwnedPost
import me.foxtails.palustris.domain.PollOption
import me.foxtails.palustris.domain.ProfileTimelineTab
import me.foxtails.palustris.ui.FeedState
import me.foxtails.palustris.ui.AccountSearchState
import me.foxtails.palustris.ui.NotificationsUiState
import me.foxtails.palustris.ui.PalustrisApp
import me.foxtails.palustris.ui.profile.ProfileCategory
import me.foxtails.palustris.ui.profile.ProfilePageState
import me.foxtails.palustris.ui.profile.ProfileUiState
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-420dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NavigationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Before fun previewShell() { compose.activity.runOnUiThread { compose.activity.setContent { PalustrisApp() } } }

    @After fun clearDraft() {
        compose.activity.getSharedPreferences("local_draft", Context.MODE_PRIVATE).edit().clear().commit()
    }

    private fun screenshot(name: String) {
        compose.waitForIdle()
        val file = File("build/ui-screenshots/$name.png")
        file.parentFile?.mkdirs()
        compose.runOnIdle {
            val view = compose.activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    private fun screenBitmap(): Bitmap {
        lateinit var bitmap: Bitmap
        compose.runOnIdle {
            val view = compose.activity.window.decorView
            bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
        }
        return bitmap
    }

    private fun cornerPixels(contentDescription: String, bitmap: Bitmap): List<Int> {
        val bounds = compose.onNodeWithContentDescription(contentDescription).fetchSemanticsNode().boundsInRoot
        val left = bounds.left.toInt()
        val top = bounds.top.toInt()
        val right = bounds.right.toInt() - 1
        val bottom = bounds.bottom.toInt() - 1
        val inset = 4
        return listOf(
            bitmap.getPixel(left + inset, top + inset),
            bitmap.getPixel(right - inset, top + inset),
            bitmap.getPixel(left + inset, bottom - inset),
            bitmap.getPixel(right - inset, bottom - inset),
        )
    }

    private fun pressSample(contentDescription: String, bitmap: Bitmap): Int {
        val bounds = compose.onNodeWithContentDescription(contentDescription).fetchSemanticsNode().boundsInRoot
        val x = bounds.center.x.toInt()
        val y = bounds.top.toInt() + 8
        return bitmap.getPixel(x, y)
    }

    private fun bounds(contentDescription: String) =
        compose.onNodeWithContentDescription(contentDescription).fetchSemanticsNode().boundsInRoot

    private fun assertPressKeepsCornersUnchanged(contentDescription: String) {
        val before = screenBitmap()
        val beforeCorners = cornerPixels(contentDescription, before)
        compose.onNodeWithContentDescription(contentDescription).performTouchInput { down(center) }
        val pressed = screenBitmap()
        compose.onNodeWithContentDescription(contentDescription).performTouchInput { cancel() }
        assertNotEquals("$contentDescription press did not render an indication", pressSample(contentDescription, before), pressSample(contentDescription, pressed))
        assertEquals("$contentDescription press changed a rounded corner", beforeCorners, cornerPixels(contentDescription, pressed))
    }

    private val fixtureConnection = Connection("https://fixture.example", Protocol.MASTODON)

    private fun fixtureAccount(
        localId: String = "fixture",
        biography: String = "Fixture biography",
    ) = Account(
        id = AccountId(fixtureConnection, localId),
        displayName = "Fixture $localId",
        handle = "@$localId@fixture.example",
        biography = biography,
    )

    private fun fixturePost(id: String, author: Account, text: String) = Post(
        id = EntityId(fixtureConnection.origin, id),
        author = author,
        text = text,
        publishedAtEpochMillis = 0,
        audience = Audience.Public,
        pollOptions = listOf(PollOption("Known fixture stripe", 1)),
    )

    private fun fixtureNotification(id: String, account: Account, actor: Account, text: String) = Notification(
        id = EntityId(fixtureConnection.origin, id),
        accountId = account.id,
        createdAtEpochMillis = 0,
        activity = NotificationActivity.Favourite,
        actors = listOf(actor),
        post = fixturePost("post-$id", actor, text),
        rawType = "favourite",
    )

    private fun longFixtureText(label: String, lines: Int = 20) = (1..lines).joinToString("\n") { "$label fixture line $it" }

    private fun assertUnderlaps(contentTag: String, controlDescription: String) {
        val content = compose.onNodeWithTag(contentTag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val control = bounds(controlDescription)
        assertTrue(
            "$contentTag should continue through $controlDescription: content=$content control=$control",
            content.top < control.bottom && content.bottom > control.top,
        )
    }

    private fun assertFixtureVisibleThroughGap(
        contentTag: String,
        leftControlDescription: String,
        rightControlDescription: String,
    ) {
        val content = compose.onNodeWithTag(contentTag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val leftControl = bounds(leftControlDescription)
        val rightControl = bounds(rightControlDescription)
        val gapLeft = ceil(leftControl.right).toInt() + 4
        val gapRight = floor(rightControl.left).toInt() - 4
        val overlapTop = maxOf(content.top, leftControl.top, rightControl.top).toInt() + 4
        val overlapBottom = minOf(content.bottom, leftControl.bottom, rightControl.bottom).toInt() - 4
        assertTrue(
            "${contentTag} should overlap the transparent gap between $leftControlDescription and $rightControlDescription",
            gapLeft < gapRight && overlapTop < overlapBottom,
        )

        val bitmap = screenBitmap()
        var contrastingPixels = 0
        for (y in overlapTop until overlapBottom step 3) {
            val pageBackground = bitmap.getPixel(0, y)
            for (x in gapLeft until gapRight step 3) {
                if (pixelDistance(bitmap.getPixel(x, y), pageBackground) >= 18) contrastingPixels++
            }
        }
        assertTrue(
            "${contentTag} should paint fixture content through the transparent control gap, but found $contrastingPixels contrasting pixels",
            contrastingPixels >= 4,
        )
    }

    private fun assertFixtureVisibleBesideAction(contentTag: String, actionDescription: String) {
        val content = compose.onNodeWithTag(contentTag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val action = bounds(actionDescription)
        val spacing = 8f * compose.activity.resources.displayMetrics.density
        val gapLeft = floor(action.left - spacing).toInt() + 4
        val gapRight = ceil(action.left).toInt() - 4
        val overlapTop = maxOf(content.top, action.top).toInt() + 4
        val overlapBottom = minOf(content.bottom, action.bottom).toInt() - 4
        assertTrue(
            "${contentTag} should overlap the transparent gap beside $actionDescription",
            gapLeft < gapRight && overlapTop < overlapBottom,
        )

        val bitmap = screenBitmap()
        var contrastingPixels = 0
        for (y in overlapTop until overlapBottom step 3) {
            val pageBackground = bitmap.getPixel(0, y)
            for (x in gapLeft until gapRight step 3) {
                if (pixelDistance(bitmap.getPixel(x, y), pageBackground) >= 18) contrastingPixels++
            }
        }
        assertTrue(
            "${contentTag} should paint fixture content beside $actionDescription, but found $contrastingPixels contrasting pixels",
            contrastingPixels >= 4,
        )
    }

    private fun pixelDistance(first: Int, second: Int): Int =
        abs(Color.red(first) - Color.red(second)) +
            abs(Color.green(first) - Color.green(second)) +
            abs(Color.blue(first) - Color.blue(second))

    private fun scrollToEnd(tag: String, swipes: Int = 12) {
        repeat(swipes) {
            compose.onNodeWithTag(tag, useUnmergedTree = true).performTouchInput { swipeUp() }
        }
        compose.waitForIdle()
    }

    @Test fun navigationRetainsSearchAndSelectedTimeline() {
        compose.onAllNodesWithContentDescription("Choose timeline").onFirst().performClick()
        compose.onNodeWithText("Local").performClick()
        compose.onNodeWithText("Local posts will appear here when an account is connected.").assertIsDisplayed()
        compose.onNodeWithContentDescription("Search").performClick()
        compose.onNodeWithText("Hashtags").performClick()
        compose.onNode(hasSetTextAction()).performTextInput("photography")
        compose.onNodeWithContentDescription("Notifications").performClick()
        compose.onNodeWithText("All caught up").assertIsDisplayed()
        compose.onNodeWithContentDescription("Search").performClick()
        compose.onNodeWithText("photography").assertIsDisplayed()
        compose.onNodeWithText("Hashtags").assertIsSelected()
        compose.onNodeWithContentDescription("Profile").performClick()
        compose.onAllNodesWithText("Your profile").onLast().assertIsDisplayed()
    }

    @Test fun compactNotificationsDockSitsAboveNavigation() {
        compose.onNodeWithContentDescription("Notifications").performClick()
        compose.waitForIdle()

        val chips = bounds("Notification filters; swipe horizontally for more")
        val action = bounds("Direct messages")
        val navigation = bounds("Home")
        val density = compose.activity.resources.displayMetrics.density

        assertTrue("notification chips should be above the contextual action", chips.bottom < action.top)
        assertTrue("notification chips should be above the navigation pill", chips.bottom < navigation.top)
        assertTrue("notification chips should keep compact side margins", chips.left / density >= 16f)
        assertTrue("notification chips should keep compact side margins", chips.right / density <= 411f - 16f)
        compose.onNodeWithContentDescription("Notification filters; swipe horizontally for more").assert(hasScrollAction())
    }

    @Test fun compactHomeSelectorTrailsNavigationAndLeavesNoDestinationSlot() {
        compose.waitForIdle()
        val selector = bounds("Choose timeline")
        val action = bounds("Compose post")
        val homeDestinationBefore = bounds("Home")
        val density = compose.activity.resources.displayMetrics.density

        assertEquals(168f, selector.width / density, 1f)
        assertEquals(60f, selector.height / density, 1f)
        assertTrue("selector should be below the content top", selector.top > 96f * density)
        assertTrue("selector should be above the navigation action", selector.bottom < action.top)
        assertEquals("selector should trail the navigation assembly", action.right, selector.right, density)

        compose.onNodeWithContentDescription("Search").performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Choose timeline").assertDoesNotExist()
        assertEquals(homeDestinationBefore.left, bounds("Home").left, density)
        assertEquals(homeDestinationBefore.top, bounds("Home").top, density)
        assertEquals(homeDestinationBefore.right, bounds("Home").right, density)
        assertEquals(homeDestinationBefore.bottom, bounds("Home").bottom, density)
    }

    @Test fun compactSearchDockSitsAboveNavigation() {
        compose.onNodeWithContentDescription("Search").performClick()
        compose.waitForIdle()

        val field = bounds("Search field")
        val chips = bounds("Search categories; swipe horizontally for more")
        val action = bounds("Alternate search")
        assertTrue("search field should be above navigation", field.bottom < action.top)
        assertTrue("search chips should be above the search field", chips.bottom <= field.top)
        val density = compose.activity.resources.displayMetrics.density
        assertTrue("search field should keep the compact navigation side margins", field.left / density >= 16f)
        assertTrue("search field should keep the compact navigation side margins", field.right / density <= 411f - 16f)
    }

    @Test fun compactHomeFeedUnderlapsTimelineAndFinalPostCanScrollClear() {
        val account = fixtureAccount()
        val final = fixturePost("home-final", account, "Home final fixture")
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                PalustrisApp(
                    account = account,
                    feedState = FeedState(
                        posts = listOf(
                            fixturePost("home-underlap", account, longFixtureText("Home", lines = 26)),
                            final,
                        ),
                    ),
                )
            }
        }
        compose.waitForIdle()

        screenshot("home")
        assertUnderlaps("post_row_home-underlap", "Choose timeline")
        assertFixtureVisibleBesideAction("post_row_home-underlap", "Compose post")
        scrollToEnd("home_feed_content")
        compose.onNodeWithText("Home final fixture").assertIsDisplayed()
    }

    @Test fun compactSearchResultsUnderlapDockAndFinalPostCanScrollClear() {
        val account = fixtureAccount()
        val final = fixturePost("search-final", account, "Search final fixture")
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                PalustrisApp(
                    account = account,
                    feedState = FeedState(
                        accountSearch = AccountSearchState(
                            query = "#fixture",
                            tagQuery = "fixture",
                            posts = listOf(
                                fixturePost("search-underlap", account, longFixtureText("Search", lines = 28)),
                                final,
                            ),
                        ),
                    ),
                )
            }
        }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Search").performClick()
        compose.onNode(hasSetTextAction()).performTextInput("#fixture")
        compose.waitForIdle()

        screenshot("search")
        assertUnderlaps("post_row_search-underlap", "Search categories; swipe horizontally for more")
        assertFixtureVisibleThroughGap("post_row_search-underlap", "Profiles", "Hashtags")
        scrollToEnd("search_content")
        val finalBounds = compose.onNodeWithTag("post_row_search-final").fetchSemanticsNode().boundsInRoot
        val chips = bounds("Search categories; swipe horizontally for more")
        assertTrue("final Search result should clear the floating controls", finalBounds.bottom <= chips.top)
    }

    @Test fun compactNotificationsUnderlapFiltersAndFinalNotificationCanScrollClear() {
        val account = fixtureAccount("receiver")
        val actor = fixtureAccount("actor")
        val notifications = (0..8).map { index ->
            fixtureNotification(
                id = "notification-$index",
                account = account,
                actor = actor.copy(displayName = "Actor $index"),
                text = if (index == 0) longFixtureText("Notification") else "Notification $index",
            )
        }
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                PalustrisApp(
                    account = account,
                    notificationState = NotificationsUiState(items = notifications),
                )
            }
        }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Notifications").performClick()
        compose.waitForIdle()

        screenshot("notifications")
        assertUnderlaps("notification_row_notification-5", "Notification filters; swipe horizontally for more")
        assertFixtureVisibleThroughGap("notification_row_notification-5", "Replies", "Reposts")
        scrollToEnd("notifications_content")
        val finalBounds = compose.onNodeWithTag("notification_row_notification-8").fetchSemanticsNode().boundsInRoot
        val filters = bounds("Notification filters; swipe horizontally for more")
        assertTrue("final notification should clear the floating filters", finalBounds.bottom <= filters.top)
    }

    @Test fun compactProfileUnderlapsCategoriesAndFinalSectionCanScrollClear() {
        val biography = longFixtureText("Profile")
        val account = fixtureAccount("profile", biography)
        val posts = (0..8).map { index ->
            fixturePost("profile-$index", account, "Profile post $index")
        }
        val profileState = ProfileUiState(
            targetId = account.id,
            seedAccount = account,
            account = account,
            pages = mapOf(
                ProfileTimelineTab.Posts to ProfilePageState(
                    posts = posts.map { OwnedPost(account.id, it) },
                ),
            ),
        )
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                PalustrisApp(account = account, profileState = profileState)
            }
        }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Profile").performClick()
        compose.waitForIdle()

        screenshot("profile")
        assertUnderlaps("profile_biography", "Profile categories; swipe horizontally for more")
        assertFixtureVisibleThroughGap("profile_biography", "Posts", "Media")
        scrollToEnd("profile_content")
        val finalBounds = compose.onNodeWithTag("post_row_profile-8", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val categories = bounds("Profile categories; swipe horizontally for more")
        assertTrue("final Profile section should clear the floating categories", finalBounds.bottom <= categories.top)
    }

    @Test fun compactProfileDockSitsAboveNavigationAndResetsForAnotherProfile() {
        val connection = Connection("https://example.org", Protocol.MASTODON)
        val alice = Account(AccountId(connection, "alice"), "Alice Profile", "@alice@example.org")
        val bob = Account(AccountId(connection, "bob"), "Bob Profile", "@bob@example.org", biography = "Bob's biography")
        val post = Post(EntityId("https://example.org", "bob-post"), bob, "Bob's post", 0, Audience.Public)
        val profileState = mutableStateOf(
            ProfileUiState(
                targetId = alice.id,
                seedAccount = alice,
                account = alice,
                pages = mapOf(
                    ProfileTimelineTab.Posts to ProfilePageState(
                        posts = listOf(
                            OwnedPost(
                                alice.id,
                                Post(
                                    EntityId("https://example.org", "alice-post"),
                                    alice,
                                    "Alice's post",
                                    0,
                                    Audience.Public,
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                PalustrisApp(
                    account = alice,
                    feedState = FeedState(posts = listOf(post)),
                    profileState = profileState.value,
                    onProfileShown = { seed ->
                        val sameTarget = profileState.value.targetId == seed.id
                        profileState.value = profileState.value.copy(
                            targetId = seed.id,
                            seedAccount = seed,
                            account = seed,
                            selectedTab = if (sameTarget) {
                                profileState.value.selectedTab
                            } else {
                                ProfileCategory.Posts
                            },
                            pages = if (sameTarget) profileState.value.pages else emptyMap(),
                        )
                    },
                    onProfileCategorySelected = { category ->
                        profileState.value = profileState.value.copy(selectedTab = category)
                    },
                )
            }
        }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Profile").performClick()
        compose.waitForIdle()

        val categories = bounds("Profile categories; swipe horizontally for more")
        val action = bounds("Edit profile")
        val density = compose.activity.resources.displayMetrics.density
        assertTrue("profile categories should be above navigation", categories.bottom < action.top)
        assertTrue("profile categories should keep compact side margins", categories.left / density >= 16f)
        assertTrue("profile categories should keep compact side margins", categories.right / density <= 411f - 16f)
        compose.onNodeWithContentDescription("Profile categories; swipe horizontally for more").assert(hasScrollAction())
        compose.onNodeWithText("Posts").assertIsSelected()
        compose.onNodeWithText("Media").performClick()
        compose.onNodeWithText("Media").assertIsSelected()

        compose.onNodeWithContentDescription("Home").performClick()
        compose.onNodeWithContentDescription("Profile").performClick()
        compose.onNodeWithText("Media").assertIsSelected()

        compose.onNodeWithContentDescription("Home").performClick()
        compose.onNodeWithText("Bob Profile").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Bob's biography").assertIsDisplayed()
        compose.onNodeWithText("Posts").assertIsSelected()
        compose.onNodeWithText("Media").assertIsNotSelected()
    }

    @Test fun selfProfileActionsOpenExistingLocalPagesWithoutProfileTopBarActions() {
        val account = fixtureAccount("profile-actions")
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                PalustrisApp(
                    account = account,
                    profileState = ProfileUiState(
                        targetId = account.id,
                        seedAccount = account,
                        account = account,
                    ),
                )
            }
        }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Profile").performClick()
        val categories = compose.onNodeWithContentDescription("Profile categories; swipe horizontally for more")
        categories.performScrollToNode(hasText("Drafts"))
        compose.onNodeWithTag("profile_drafts_chip").performClick()
        compose.onNodeWithText("Drafts").assertIsDisplayed()
        compose.onNodeWithContentDescription("Accounts").assertDoesNotExist()
        compose.onNodeWithContentDescription("Back").performClick()
        categories.performScrollToNode(hasText("Bookmarks"))
        compose.onNodeWithTag("profile_bookmarks_chip").performClick()
        compose.onNodeWithText("Bookmarks").assertIsDisplayed()
    }

    @Test fun notificationSettingsUsesPullUpSheetAndBackClosesIt() {
        val account = fixtureAccount("notification-settings")
        compose.activity.runOnUiThread {
            compose.activity.setContent { PalustrisApp(account = account) }
        }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Notifications").performClick()
        val row = compose.onNodeWithContentDescription("Notification filters; swipe horizontally for more")
        row.performScrollToNode(hasText("Notification settings"))
        compose.onNodeWithTag("notification_settings").performClick()
        compose.onNodeWithTag("notification_settings_sheet").assertIsDisplayed()
        compose.onNodeWithTag("notification_settings_form").assertIsDisplayed()
        compose.onAllNodesWithText("Notification settings").onLast().assertIsDisplayed()

        compose.onNodeWithContentDescription("Close notification settings").performClick()
        compose.onNodeWithTag("notification_settings_sheet").assertDoesNotExist()
        row.performScrollToNode(hasText("Notification settings"))
        compose.onNodeWithTag("notification_settings").performClick()
        // ModalBottomSheet owns the native dialog back callback; Robolectric's
        // activity dispatcher cannot target that dialog window directly.
        compose.onNodeWithContentDescription("Close notification settings").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("notification_settings_sheet").assertDoesNotExist()
        compose.onNodeWithContentDescription("Notification filters; swipe horizontally for more").assertIsDisplayed()
    }

    @Test fun searchDockNeverCrossesNavigationDuringKeyboardDismissal() {
        compose.onNodeWithContentDescription("Search").performClick()
        val density = compose.activity.resources.displayMetrics.density
        val systemBottom = (24 * density).toInt()
        var previousBottom = 0f
        var firstImeFieldBottom = Float.NaN
        var initialViewportTop = Float.NaN
        var initialViewportBottom = Float.NaN
        for (keyboardDp in listOf(360, 300, 200, 120, 100, 80, 40, 0)) {
            compose.runOnIdle {
                val content = compose.activity.findViewById<ViewGroup>(android.R.id.content)
                val insets = WindowInsetsCompat.Builder()
                    .setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.of(0, 0, 0, systemBottom))
                    .setInsetsIgnoringVisibility(WindowInsetsCompat.Type.navigationBars(), Insets.of(0, 0, 0, systemBottom))
                    .setInsets(WindowInsetsCompat.Type.ime(), Insets.of(0, 0, 0, (keyboardDp * density).toInt()))
                    .setVisible(WindowInsetsCompat.Type.ime(), keyboardDp > 0)
                    .build()
                ViewCompat.dispatchApplyWindowInsets(content.getChildAt(0), insets)
            }
            compose.waitForIdle()
            val field = bounds("Search field")
            val viewport = compose.onNodeWithTag("search_content", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            if (initialViewportTop.isNaN()) {
                initialViewportTop = viewport.top
                initialViewportBottom = viewport.bottom
            } else {
                assertEquals("IME motion must not resize the Search content viewport", initialViewportTop, viewport.top, 0.5f)
                assertEquals("IME motion must not resize the Search content viewport", initialViewportBottom, viewport.bottom, 0.5f)
            }
            val navigation = bounds("Alternate search")
            assertTrue("field crossed navigation at IME height $keyboardDp", field.bottom < navigation.top)
            assertTrue("field bounced upward at IME height $keyboardDp", field.bottom >= previousBottom)
            if (keyboardDp == 360) {
                firstImeFieldBottom = field.bottom
                assertTrue("test must actually move the field above the keyboard", navigation.top - field.bottom > 150 * density)
            }
            if (keyboardDp == 0) {
                assertTrue("IME dismissal should move the floating Search controls", field.bottom > firstImeFieldBottom)
            }
            previousBottom = field.bottom
        }
    }

    @Test fun scrollingHidesAndRestoresCompactControlsAndLeavesFinalPostReachable() {
        val account = Account(
            AccountId(Connection("https://example.org", Protocol.MASTODON), "scrolling"),
            "Scrolling account",
            "@scrolling@example.org",
        )
        val posts = (0..14).map { index ->
            Post(
                EntityId("https://example.org", "scroll-$index"),
                account,
                if (index == 14) "Final post" else "Post $index with enough content to make the feed scroll.",
                0,
                Audience.Public,
            )
        }
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                PalustrisApp(account = account, feedState = FeedState(posts = posts))
            }
        }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Choose timeline").assertIsDisplayed()
        compose.onNodeWithContentDescription("Compose post").assertIsDisplayed()

        compose.onNode(hasScrollAction()).performTouchInput { swipeUp() }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Choose timeline").assertDoesNotExist()
        compose.onNodeWithContentDescription("Compose post").assertDoesNotExist()

        compose.onNode(hasScrollAction()).performTouchInput { swipeDown() }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Choose timeline").assertIsDisplayed()
        compose.onNodeWithContentDescription("Compose post").assertIsDisplayed()

        repeat(12) {
            compose.onNode(hasScrollAction()).performTouchInput { swipeUp() }
        }
        compose.waitForIdle()
        compose.onNodeWithText("Final post").assertIsDisplayed()
    }

    @Test fun homeAndSelectedSearchIndicationsStayRounded() {
        assertPressKeepsCornersUnchanged("Choose timeline")

        compose.onNodeWithContentDescription("Search").performClick()
        compose.onNodeWithContentDescription("Search").assertIsSelected()
        assertPressKeepsCornersUnchanged("Search")
    }

    @Test fun draftsSurviveActivityRecreationAndCanBeDeleted() {
        val account = fixtureAccount("draft-owner")
        compose.activity.runOnUiThread { compose.activity.setContent { PalustrisApp(account = account) } }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Compose post").performClick()
        compose.onNodeWithContentDescription("Post text").performTextInput("A draft stored only on this device.")
        screenshot("compose")
        compose.onNodeWithText("Save draft").performClick()
        compose.activityRule.scenario.recreate()
        compose.activity.runOnUiThread { compose.activity.setContent { PalustrisApp(account = account) } }
        compose.onNodeWithContentDescription("Profile").performClick()
        compose.onNodeWithTag("profile_drafts_chip").performClick()
        compose.onNodeWithText("A draft stored only on this device.").assertIsDisplayed()
        compose.onNodeWithText("Delete draft").performClick()
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithText("A draft stored only on this device.").assertIsDisplayed()
        compose.onNodeWithText("Delete draft").performClick()
        compose.onNodeWithText("Delete", substring = false).performClick()
        compose.onNodeWithText("No drafts yet").assertIsDisplayed()
    }

    @Test fun closingComposerAutosavesUnsavedText() {
        val account = fixtureAccount("autosave-owner")
        compose.activity.runOnUiThread { compose.activity.setContent { PalustrisApp(account = account) } }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Compose post").performClick()
        compose.onNodeWithContentDescription("Post text").performTextInput("Unsaved")
        compose.onNodeWithContentDescription("Close composer").performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Profile").performClick()
        compose.onNodeWithTag("profile_drafts_chip").performClick()
        compose.onNodeWithText("Unsaved").assertIsDisplayed()
    }

    @Test fun contextualActionsFollowSelectedDestination() {
        compose.onNodeWithContentDescription("Compose post").assertIsEnabled()
        compose.onNodeWithContentDescription("Edit profile").assertDoesNotExist()

        compose.onNodeWithContentDescription("Search").performClick()
        compose.onNodeWithContentDescription("Alternate search").assertIsEnabled().performClick()
        compose.onNodeWithText("Alternate search").assertIsDisplayed()

        compose.onNodeWithContentDescription("Notifications").performClick()
        compose.onNodeWithContentDescription("Direct messages").assertIsEnabled().performClick()
        compose.onNodeWithText("Direct messages coming soon").assertIsDisplayed()
        compose.onAllNodesWithContentDescription("Notifications").onLast().assertIsEnabled().performClick()
        compose.onNodeWithText("All caught up").assertIsDisplayed()

        compose.onNodeWithContentDescription("Profile").performClick()
        compose.onNodeWithContentDescription("Edit profile").assertDoesNotExist()
        compose.onAllNodesWithText("Your profile").onLast().assertIsDisplayed()
        compose.onNodeWithContentDescription("Profile").assertIsSelected()
    }

    @Test fun profileAvatarLongPressOpensExistingAccountSwitcher() {
        val current = Account(
            AccountId(Connection("https://example.org", Protocol.MASTODON), "current"),
            "Current account",
            "@current@example.org",
            "https://example.org/current.png",
        )
        val other = Account(
            AccountId(Connection("https://other.example", Protocol.MISSKEY), "other"),
            "Other account",
            "@other@other.example",
        )
        var switchedTo: AccountId? = null
        compose.activity.runOnUiThread { compose.activity.setContent {
            PalustrisApp(
                account = current,
                accounts = listOf(
                    AccountRef(current.id, current.handle, current.avatarUrl, current.displayName),
                    AccountRef(other.id, other.handle, other.avatarUrl, other.displayName),
                ),
                onSwitchAccount = { switchedTo = it },
            )
        } }

        compose.onNodeWithContentDescription("Profile").performClick()
        compose.onNodeWithContentDescription("Profile").assertIsSelected()
        compose.onNodeWithText("Other account").assertDoesNotExist()

        compose.onNodeWithContentDescription("Profile").performTouchInput { longClick() }
        compose.onNodeWithText("Other account").assertIsDisplayed()
        compose.onNodeWithText("@other@other.example").assertIsDisplayed()
        compose.onNodeWithText("Other account").performClick()
        assertEquals(other.id, switchedTo)
        compose.onNodeWithContentDescription("Profile").assertIsSelected()
    }
}
