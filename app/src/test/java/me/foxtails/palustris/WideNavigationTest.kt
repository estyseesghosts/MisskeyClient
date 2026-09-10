package me.foxtails.palustris

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.compose.setContent
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.Audience
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.EntityId
import me.foxtails.palustris.domain.OwnedPost
import me.foxtails.palustris.domain.Post
import me.foxtails.palustris.domain.ProfileTimelineTab
import me.foxtails.palustris.domain.Protocol
import me.foxtails.palustris.ui.PalustrisApp
import me.foxtails.palustris.ui.PalustrisTheme
import me.foxtails.palustris.ui.profile.ProfileCategory
import me.foxtails.palustris.ui.profile.ProfilePageState
import me.foxtails.palustris.ui.profile.ProfileScreen
import me.foxtails.palustris.ui.profile.ProfileUiState
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w800dp-h1000dp-420dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WideNavigationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Before fun showShell() {
        compose.activity.runOnUiThread { compose.activity.setContent { PalustrisApp() } }
    }

    @After fun clearDraft() {
        compose.activity.getSharedPreferences("local_draft", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test fun wideLightLayoutShowsNavigationRail() {
        assertRailAndComposer()
    }

    @Test
    @Config(qualifiers = "w800dp-h1000dp-night-420dpi")
    fun wideDarkLayoutShowsNavigationRail() {
        assertRailAndComposer()
    }

    @Test fun wideNotificationsUseNormalChipFlow() {
        compose.onNodeWithContentDescription("Notifications").performClick()
        compose.waitForIdle()

        val row = compose.onNodeWithContentDescription("Notification filters; swipe horizontally for more")
        row.assert(hasScrollAction())
        val rowBounds = row.fetchSemanticsNode().boundsInRoot
        val placeholderBounds = compose.onNodeWithText("All caught up").fetchSemanticsNode().boundsInRoot
        assertTrue("wide notification chips should precede the placeholder in page flow", rowBounds.bottom < placeholderBounds.top)
        listOf("Replies", "Reposts", "Followers", "Likes").forEach { label ->
            compose.onNodeWithText(label).assertIsDisplayed().assertIsNotSelected()
        }
        compose.onNodeWithText("All caught up").assertIsDisplayed()
        compose.onNodeWithContentDescription("Mark all notifications read").assertDoesNotExist()
        compose.onNodeWithContentDescription("Notification settings").assertDoesNotExist()
    }

    @Test fun wideProfileUsesNormalChipFlowAndKeepsSelfActionReachable() {
        val profile = wideProfile()
        val post = Post(
            id = EntityId(profile.id.connection.origin, "wide-profile-post"),
            author = profile,
            text = "Wide profile post",
            publishedAtEpochMillis = 0,
            audience = Audience.Public,
        )
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                PalustrisTheme {
                    ProfileScreen(
                        account = profile,
                        profileState = ProfileUiState(
                            targetId = profile.id,
                            account = profile,
                            selectedTab = ProfileCategory.Posts,
                            pages = mapOf(
                                ProfileTimelineTab.Posts to ProfilePageState(
                                    posts = listOf(OwnedPost(profile.id, post)),
                                    terminal = true,
                                ),
                            ),
                        ),
                        compactLayout = false,
                        authenticatedAccountId = profile.id,
                    )
                }
            }
        }
        compose.waitForIdle()

        val categories = compose.onNodeWithContentDescription(
            "Profile categories; swipe horizontally for more",
        )
        categories.assert(hasScrollAction())
        val categoryBounds = categories.fetchSemanticsNode().boundsInRoot
        val postBounds = compose.onNodeWithText("Wide profile post").fetchSemanticsNode().boundsInRoot
        assertTrue("wide profile categories should precede the timeline in page flow", categoryBounds.bottom < postBounds.top)
        compose.onAllNodesWithText("Profile Name").onLast().assertIsDisplayed()
        compose.onNodeWithText("Edit profile").assertDoesNotExist()
        listOf("Posts", "Media", "Reposts", "Replies", "Drafts", "Bookmarks", "Show more...").forEach { label ->
            compose.onNodeWithText(label).assertIsDisplayed()
        }
    }

    private fun assertRailAndComposer() {
        compose.waitForIdle()
        compose.runOnIdle {
            val view = compose.activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            File("build/ui-screenshots/wide-${if (view.context.resources.configuration.isNightModeActive) "dark" else "light"}.png")
                .apply { parentFile?.mkdirs() }
                .outputStream()
                .use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        compose.onNodeWithContentDescription("Home").assertIsDisplayed()
        compose.onNodeWithTag("large_screen_shell").assertIsDisplayed()
        compose.onNodeWithContentDescription("Timeline Home").assertIsDisplayed()
        compose.onNodeWithContentDescription("Search").assertIsDisplayed()
        compose.onNodeWithContentDescription("Notifications").assertIsDisplayed()
        compose.onNodeWithContentDescription("Profile").assertIsDisplayed()
        compose.onNodeWithContentDescription("Compose post").performClick()
        compose.onNodeWithText("New post").assertIsDisplayed()
    }

    private fun wideProfile() = Account(
        id = AccountId(Connection("https://example.org", Protocol.MASTODON), "wide-profile"),
        displayName = "Profile Name",
        handle = "@profile@example.org",
        biography = "A wide profile biography",
    )
}
