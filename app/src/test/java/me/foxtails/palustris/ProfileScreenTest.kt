package me.foxtails.palustris

import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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
import me.foxtails.palustris.ui.profile.ProfileCategory
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
        val blurBounds = compose.onNodeWithTag("profile_banner_status_bar_blur").fetchSemanticsNode().boundsInRoot
        assertEquals(0f, blurBounds.top, 0.5f)
        assertTrue("profile status-bar banner region should have height", blurBounds.height > 0f)
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
    fun selfProfileAddsDraftsAndBookmarksBeforeShowMoreWithoutChangingCategory() {
        var selected = ProfileCategory.Posts
        var drafts = 0
        var bookmarks = 0

        show {
            ProfileScreen(
                account = self,
                profileState = profileState(self, emptyList()),
                compactLayout = true,
                authenticatedAccountId = self.id,
                onCategorySelected = { selected = it },
                onOpenDrafts = { drafts++ },
                onOpenBookmarks = { bookmarks++ },
            )
        }

        val categories = compose.onNodeWithContentDescription("Profile categories; swipe horizontally for more")
        listOf("Posts", "Media", "Reposts", "Replies", "Drafts", "Bookmarks", "Show more...").forEach { label ->
            categories.performScrollToNode(hasText(label))
            compose.onNodeWithText(label).assertExists()
        }
        categories.performScrollToNode(hasText("Replies"))
        val repliesRight = compose.onNodeWithText("Replies").fetchSemanticsNode().boundsInRoot.right
        categories.performScrollToNode(hasText("Drafts"))
        val draftsLeft = compose.onNodeWithText("Drafts").fetchSemanticsNode().boundsInRoot.left
        categories.performScrollToNode(hasText("Bookmarks"))
        val bookmarksLeft = compose.onNodeWithText("Bookmarks").fetchSemanticsNode().boundsInRoot.left
        categories.performScrollToNode(hasText("Show more..."))
        val showMoreLeft = compose.onNodeWithText("Show more...").fetchSemanticsNode().boundsInRoot.left
        assertTrue(repliesRight <= draftsLeft)
        assertTrue(draftsLeft <= bookmarksLeft)
        assertTrue(bookmarksLeft <= showMoreLeft)

        categories.performScrollToNode(hasText("Drafts"))
        compose.onNodeWithTag("profile_drafts_chip").performClick()
        categories.performScrollToNode(hasText("Bookmarks"))
        compose.onNodeWithTag("profile_bookmarks_chip").performClick()
        assertEquals(1, drafts)
        assertEquals(1, bookmarks)
        assertEquals(ProfileCategory.Posts, selected)
    }

    @Test
    fun remoteProfileOmitsDraftsAndBookmarks() {
        show {
            ProfileScreen(
                account = account("remote-actions", "Remote"),
                profileState = profileState(account("remote-actions", "Remote"), emptyList()),
                compactLayout = false,
                authenticatedAccountId = self.id,
            )
        }

        compose.onNodeWithText("Drafts").assertDoesNotExist()
        compose.onNodeWithText("Bookmarks").assertDoesNotExist()
        compose.onNodeWithText("Show more...").assertIsDisplayed()
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
    fun redirectedProfileShowsDestinationBeforeHeaderAndUsesInAppCallback() {
        val destination = account("new-profile", "New profile").copy(
            handle = "@new-profile@example.org",
            avatarUrl = "https://example.org/new-profile.png",
        )
        val profile = account("moved-profile", "Moved profile").copy(movedTo = destination)
        var opened: Account? = null

        show {
            ProfileScreen(
                account = profile,
                profileState = profileState(profile, emptyList()),
                compactLayout = false,
                authenticatedAccountId = self.id,
                onOpenProfile = { opened = it },
            )
        }

        compose.onNodeWithTag("profile_redirect").assertIsDisplayed()
        compose.onNodeWithText("Moved profile has indicated that their new account is now:").assertIsDisplayed()
        compose.onNodeWithText("New profile").assertIsDisplayed()
        compose.onNodeWithText("@new-profile@example.org").assertIsDisplayed()
        compose.onNodeWithTag("profile_redirect_go_to_profile").assertIsDisplayed().performClick()
        assertEquals(destination.id, opened?.id)
        val redirectBounds = compose.onNodeWithTag("profile_redirect").fetchSemanticsNode().boundsInRoot
        val bannerBounds = compose.onNodeWithTag("profile_banner").fetchSemanticsNode().boundsInRoot
        assertTrue("redirect notice should precede the profile banner", redirectBounds.bottom <= bannerBounds.top)
        compose.onNodeWithTag("profile_follow_action").assertDoesNotExist()
    }

    @Test
    fun redirectedProfileKeepsLongDestinationIdentityUsableInCompactLayout() {
        val destination = account("long-destination", "A destination account name that is deliberately very long").copy(
            handle = "@a-very-long-destination-handle@example.org",
        )
        val profile = account("compact-moved", "Compact moved profile").copy(movedTo = destination)

        show {
            ProfileScreen(
                account = profile,
                profileState = profileState(profile, emptyList()),
                compactLayout = true,
                authenticatedAccountId = self.id,
            )
        }

        compose.onNodeWithTag("profile_redirect").assertIsDisplayed()
        compose.onNodeWithTag("profile_redirect_destination").assertIsDisplayed()
        compose.onNodeWithTag("profile_redirect_go_to_profile").assertIsDisplayed()
    }

    @Test
    fun selfDoesNotRenderDuplicateEditActionAndUnsupportedRemoteRelationshipGetsNoDeadAction() {
        val selfState = mutableStateOf(profileState(self, emptyList()))
        show {
            ProfileScreen(
                account = self,
                profileState = selfState.value,
                compactLayout = false,
                authenticatedAccountId = self.id,
            )
        }

        compose.onNodeWithText("Edit profile").assertDoesNotExist()
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

    @Test
    fun editorShowsBasicControlsForEveryAdapterAndOmitsUnsupportedAdvancedControls() {
        show {
            PalustrisTheme {
                me.foxtails.palustris.ui.profile.EditProfileScreen(
                    editor = me.foxtails.palustris.domain.EditableProfile(
                        id = "self",
                        displayName = "Self",
                        biography = "Bio",
                    ),
                    capabilities = me.foxtails.palustris.domain.EditableProfileCapabilities(
                        read = me.foxtails.palustris.domain.CapabilityStatus.Supported,
                        update = me.foxtails.palustris.domain.CapabilityStatus.Supported,
                    ),
                    emoji = emptyMap(),
                    handle = "@self@example.org",
                    loading = false,
                    saving = false,
                    error = null,
                    onEditorChange = {},
                    onSave = {},
                    onClose = {},
                )
            }
        }

        compose.onNodeWithText("Edit profile").assertIsDisplayed()
        compose.onNodeWithText("Display name").assertIsDisplayed()
        compose.onNodeWithText("Biography").assertIsDisplayed()
        compose.onNodeWithText("@self@example.org").assertIsDisplayed()
        compose.onNodeWithText("Profile fields").assertDoesNotExist()
        compose.onNodeWithText("Attribution domains").assertDoesNotExist()
        compose.onNodeWithText("This server does not support advanced profile settings.").assertIsDisplayed()
    }

    @Test
    fun editorShowsApiEightControlsOnlyFromCapabilities() {
        show {
            PalustrisTheme {
                me.foxtails.palustris.ui.profile.EditProfileScreen(
                    editor = me.foxtails.palustris.domain.EditableProfile(
                        id = "self",
                        displayName = "Self",
                        biography = "Bio",
                        fields = listOf(me.foxtails.palustris.domain.EditableProfileField("Site", "https://example.org")),
                        attributionDomains = listOf("example.org"),
                    ),
                    capabilities = me.foxtails.palustris.domain.EditableProfileCapabilities(
                        read = me.foxtails.palustris.domain.CapabilityStatus.Supported,
                        update = me.foxtails.palustris.domain.CapabilityStatus.Supported,
                        advancedSettings = me.foxtails.palustris.domain.CapabilityStatus.Supported,
                    ),
                    emoji = emptyMap(),
                    handle = "@self@example.org",
                    loading = false,
                    saving = false,
                    error = null,
                    onEditorChange = {},
                    onSave = {},
                    onClose = {},
                )
            }
        }

        compose.onNodeWithText("Profile fields").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Add field").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Visibility and profile tabs").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Allow discovery").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Attribution domains").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Image descriptions").assertDoesNotExist()
    }

    @Test
    fun editorShowsImageDescriptionControlsOnlyAtApiNine() {
        show {
            PalustrisTheme {
                me.foxtails.palustris.ui.profile.EditProfileScreen(
                    editor = me.foxtails.palustris.domain.EditableProfile(
                        id = "self",
                        displayName = "Self",
                        biography = "Bio",
                    ),
                    capabilities = me.foxtails.palustris.domain.EditableProfileCapabilities(
                        read = me.foxtails.palustris.domain.CapabilityStatus.Supported,
                        update = me.foxtails.palustris.domain.CapabilityStatus.Supported,
                        advancedSettings = me.foxtails.palustris.domain.CapabilityStatus.Supported,
                        imageDescriptions = me.foxtails.palustris.domain.CapabilityStatus.Supported,
                    ),
                    emoji = emptyMap(),
                    handle = "@self@example.org",
                    loading = false,
                    saving = false,
                    error = null,
                    onEditorChange = {},
                    onSave = {},
                    onClose = {},
                )
            }
        }

        compose.onNodeWithText("Image descriptions").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Profile picture description").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Profile banner description").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun editorSaveDisablesDuringActiveUpdateAndShowsSavingLabel() {
        show {
            PalustrisTheme {
                me.foxtails.palustris.ui.profile.EditProfileScreen(
                    editor = me.foxtails.palustris.domain.EditableProfile(
                        id = "self",
                        displayName = "Self",
                        biography = "Bio",
                    ),
                    capabilities = me.foxtails.palustris.domain.EditableProfileCapabilities(
                        read = me.foxtails.palustris.domain.CapabilityStatus.Supported,
                        update = me.foxtails.palustris.domain.CapabilityStatus.Supported,
                    ),
                    emoji = emptyMap(),
                    handle = "@self@example.org",
                    loading = false,
                    saving = true,
                    error = null,
                    onEditorChange = {},
                    onSave = {},
                    onClose = {},
                )
            }
        }

        compose.onNodeWithText("Saving").assertIsDisplayed()
        compose.onNodeWithText("Saving").assertIsNotEnabled()
    }

    @Test
    fun editorShowsLoadErrorWhenNoEditorValueArrives() {
        show {
            PalustrisTheme {
                me.foxtails.palustris.ui.profile.EditProfileScreen(
                    editor = null,
                    capabilities = me.foxtails.palustris.domain.EditableProfileCapabilities(),
                    emoji = emptyMap(),
                    handle = "@self@example.org",
                    loading = false,
                    saving = false,
                    error = null,
                    onEditorChange = {},
                    onSave = {},
                    onClose = {},
                )
            }
        }

        compose.onNodeWithText("Profile could not load. Try again.").assertIsDisplayed()
    }

    @Test
    fun profileRowReactionsRemainVisibleAndEmojiAware() {
        val profile = account("reactions", "Reactions")
        val reacted = post("reacted", profile).copy(
            reactions = listOf(me.foxtails.palustris.domain.Reaction("🎉", 3, false)),
        )
        val state = mutableStateOf(
            profileState(profile, emptyList()).copy(
                pages = mapOf(
                    ProfileTimelineTab.Posts to ProfilePageState(
                        posts = listOf(OwnedPost(self.id, reacted)),
                    ),
                ),
            ),
        )
        show {
            ProfileScreen(
                account = profile,
                profileState = state.value,
                compactLayout = false,
                authenticatedAccountId = self.id,
            )
        }

        compose.onNodeWithTag("post_row_reacted").assertIsDisplayed()
    }

    @Test
    fun unsupportedEditingDoesNotRenderAnInlineEditAction() {
        val state = profileState(self, emptyList()).copy(editableSupported = false)
        show {
            ProfileScreen(
                account = self,
                profileState = state,
                compactLayout = false,
                authenticatedAccountId = self.id,
            )
        }

        compose.onNodeWithText("Edit profile").assertDoesNotExist()
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
