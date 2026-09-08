package me.foxtails.palustris

import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.Audience
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.EntityId
import me.foxtails.palustris.domain.OwnedPost
import me.foxtails.palustris.domain.Post
import me.foxtails.palustris.domain.ProfileField
import me.foxtails.palustris.domain.ProfileRelationship
import me.foxtails.palustris.domain.ProfileTimelineTab
import me.foxtails.palustris.domain.Protocol
import me.foxtails.palustris.ui.PalustrisTheme
import me.foxtails.palustris.ui.profile.ProfilePageState
import me.foxtails.palustris.ui.profile.ProfileScreen
import me.foxtails.palustris.ui.profile.ProfileUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-420dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ProfileScreenTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private val connection = Connection("https://example.org", Protocol.MASTODON)
    private val self = account("self", "Self")

    @Test
    fun rendersRichHeaderAndInlineDetailsInTheTypedCategoryOrder() {
        val profile = account("profile", "Profile Name").copy(
            handle = "@profile@example.org",
            biography = "A rich profile biography",
            profileFields = listOf(
                ProfileField("Website", "https://example.org"),
                ProfileField("Pronouns", "they/them"),
            ),
            bannerUrl = "https://example.org/banner.jpg",
            followersCount = 12_345,
            followingCount = 6_789,
            postsCount = 42,
            locked = true,
            bot = true,
        )
        val state = mutableStateOf(profileState(profile, listOf(post("profile-post", profile))))

        show {
            ProfileScreen(
                account = profile,
                profileState = state.value,
                compactLayout = false,
                authenticatedAccountId = self.id,
                onCategorySelected = { category ->
                    state.value = state.value.copy(selectedTab = category)
                },
            )
        }

        compose.onNodeWithTag("profile_banner").assertIsDisplayed()
        compose.onAllNodesWithText("Profile Name", substring = false).get(0).assertIsDisplayed()
        compose.onNodeWithText("@profile@example.org").assertIsDisplayed()
        compose.onNodeWithText("A rich profile biography").assertIsDisplayed()
        compose.onNodeWithText("42 posts").assertIsDisplayed()
        compose.onNodeWithText("12.3K followers").assertIsDisplayed()
        compose.onNodeWithText("6.8K following").assertIsDisplayed()
        compose.onNodeWithText("Locked").assertIsDisplayed()
        compose.onNodeWithText("Bot").assertIsDisplayed()
        compose.onNodeWithText("Posts").assertIsSelected()
        listOf("Posts", "Media", "Reposts", "Replies", "Show more...").forEach { label ->
            compose.onNodeWithText(label).assertExists()
        }

        val categories = compose.onNodeWithContentDescription("Profile categories; swipe horizontally for more")
        categories.performScrollToNode(hasText("Show more..."))
        compose.onNodeWithText("Show more...").performClick()
        compose.waitForIdle()

        compose.onNodeWithText("Show more...").assertIsSelected()
        compose.onNodeWithTag("profile_details").assertExists()
        compose.onNodeWithText("Profile details").assertIsDisplayed()
        compose.onNodeWithText("Website").assertIsDisplayed()
        compose.onNodeWithText("https://example.org").assertIsDisplayed()
        compose.onNodeWithText("they/them").assertIsDisplayed()
        compose.onNodeWithText("More profile views coming soon").assertDoesNotExist()
    }

    @Test
    fun remoteRelationshipActionReflectsAuthoritativeFollowRequestAndUnfollowStates() {
        val profile = account("remote", "Remote")
        val state = mutableStateOf(
            profileState(profile, emptyList()).copy(
                relationshipSupported = true,
                relationship = ProfileRelationship(profile.id),
            ),
        )
        var follows = 0
        var unfollows = 0

        show {
            ProfileScreen(
                account = profile,
                profileState = state.value,
                compactLayout = false,
                authenticatedAccountId = self.id,
                onFollow = { follows++ },
                onUnfollow = { unfollows++ },
            )
        }

        compose.onNodeWithTag("profile_follow_action").assertIsDisplayed()
        compose.onNodeWithText("Follow").assertIsDisplayed().performClick()
        assertEquals(1, follows)
        compose.onNodeWithText("Edit profile").assertDoesNotExist()

        compose.runOnIdle {
            state.value = state.value.copy(
                relationship = ProfileRelationship(profile.id, requested = true),
            )
        }
        compose.onNodeWithText("Requested").assertIsDisplayed()

        compose.runOnIdle {
            state.value = state.value.copy(
                relationship = ProfileRelationship(profile.id, following = true),
            )
        }
        compose.onNodeWithText("Following").assertIsDisplayed().performClick()
        assertEquals(1, unfollows)
    }

    @Test
    fun selfGetsEditActionAndUnsupportedRemoteRelationshipGetsNoDeadAction() {
        val selfState = mutableStateOf(profileState(self, emptyList()))
        var edits = 0
        show {
            ProfileScreen(
                account = self,
                profileState = selfState.value,
                compactLayout = false,
                authenticatedAccountId = self.id,
                onEditProfile = { edits++ },
            )
        }

        compose.onNodeWithText("Edit profile").assertIsDisplayed().performClick()
        assertEquals(1, edits)
        compose.onNodeWithTag("profile_follow_action").assertDoesNotExist()

        val remote = account("unsupported", "Unsupported")
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                PalustrisTheme {
                    ProfileScreen(
                        account = remote,
                        profileState = profileState(remote, emptyList()).copy(relationshipSupported = false),
                        compactLayout = false,
                        authenticatedAccountId = self.id,
                    )
                }
            }
        }
        compose.waitForIdle()
        compose.onNodeWithTag("profile_follow_action").assertDoesNotExist()
        compose.onNodeWithText("Edit profile").assertDoesNotExist()
    }

    @Test
    fun staleDetailsAndPagingErrorsKeepContentAndOfferRetry() {
        val profile = account("stale", "Stale")
        val visible = post("visible", profile)
        val state = profileState(profile, listOf(visible)).copy(
            detailError = "Profile refresh failed",
            staleDetails = true,
            pages = mapOf(
                ProfileTimelineTab.Posts to ProfilePageState(
                    posts = listOf(OwnedPost(self.id, visible)),
                    nextCursor = "cursor-a",
                    error = "Timeline refresh failed",
                ),
            ),
        )
        var retries = 0

        show {
            ProfileScreen(
                account = profile,
                profileState = state,
                compactLayout = false,
                authenticatedAccountId = self.id,
                onRefresh = { retries++ },
            )
        }

        compose.onNodeWithText("Profile refresh failed", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Timeline refresh failed", substring = true).assertIsDisplayed()
        compose.onNodeWithTag("post_row_visible").assertExists()
        compose.onAllNodesWithText("Retry").get(0).performClick()
        assertEquals(1, retries)
    }

    @Test
    fun emptyFilteredPageOffersManualContinuationAndProfileRowsReuseActions() {
        val profile = account("paged", "Paged")
        val state = mutableStateOf(
            profileState(profile, emptyList()).copy(
                pages = mapOf(
                    ProfileTimelineTab.Posts to ProfilePageState(
                        nextCursor = "cursor-a",
                        consecutiveEmptyPages = 3,
                    ),
                ),
            ),
        )
        var loads = 0
        show {
            ProfileScreen(
                account = profile,
                profileState = state.value,
                compactLayout = false,
                onLoadMore = { loads++ },
            )
        }

        compose.onNodeWithText("No posts in this view").assertIsDisplayed()
        compose.onNodeWithText("Continue browsing").performClick()
        assertEquals(1, loads)

        val visible = post("action-row", profile)
        compose.runOnIdle {
            state.value = state.value.copy(
                pages = mapOf(
                    ProfileTimelineTab.Posts to ProfilePageState(
                        posts = listOf(OwnedPost(self.id, visible)),
                    ),
                ),
            )
        }
        compose.waitForIdle()
        compose.onNodeWithTag("post_row_action-row").assertIsDisplayed()
        compose.onNodeWithContentDescription("Reply").assertIsDisplayed()
        compose.onNodeWithContentDescription("Bookmark").assertIsDisplayed()
    }

    @Test
    fun compactProfileContentExtendsBehindDockAndFinalRowCanClearIt() {
        val profile = account("compact", "Compact").copy(biography = (1..18).joinToString("\n") { "Bio line $it" })
        val posts = (0..8).map { index -> post("compact-$index", profile, "Profile post $index") }

        show {
            ProfileScreen(
                account = profile,
                profileState = profileState(profile, posts),
                compactLayout = true,
                compactNavigationVisible = true,
            )
        }

        val categories = compose.onNodeWithContentDescription("Profile categories; swipe horizontally for more")
        val content = compose.onNodeWithTag("profile_content", useUnmergedTree = true)
        val contentBounds = content.fetchSemanticsNode().boundsInRoot
        val categoryBounds = categories.fetchSemanticsNode().boundsInRoot
        assertTrue("profile content should extend behind the category dock", contentBounds.top < categoryBounds.bottom)

        repeat(14) {
            compose.onNodeWithTag("profile_timeline_list", useUnmergedTree = true).performTouchInput { swipeUp() }
        }
        compose.waitForIdle()
        val finalBounds = compose.onNodeWithTag("post_row_compact-8", useUnmergedTree = true)
            .fetchSemanticsNode()
            .boundsInRoot
        assertTrue("final profile row should clear the floating category dock", finalBounds.bottom <= categoryBounds.top)
        compose.onNodeWithText("Profile post 8").assertIsDisplayed()
    }

    private fun show(content: @Composable () -> Unit) {
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                PalustrisTheme(content)
            }
        }
        compose.waitForIdle()
    }

    private fun profileState(account: Account, posts: List<Post>): ProfileUiState = ProfileUiState(
        targetId = account.id,
        seedAccount = account,
        account = account,
        pages = mapOf(
            ProfileTimelineTab.Posts to ProfilePageState(
                posts = posts.map { OwnedPost(self.id, it) },
            ),
        ),
    )

    private fun account(id: String, name: String): Account = Account(
        id = AccountId(connection, id),
        displayName = name,
        handle = "@$id@example.org",
    )

    private fun post(id: String, author: Account, text: String = id): Post = Post(
        id = EntityId(connection.origin, id),
        author = author,
        text = text,
        publishedAtEpochMillis = 0,
        audience = Audience.Public,
    )
}
