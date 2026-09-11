package me.foxtails.palustris.ui.emoji

import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import me.foxtails.palustris.MainActivity
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.Audience
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.CustomEmoji
import me.foxtails.palustris.domain.EmojiChoice
import me.foxtails.palustris.domain.EmojiPickerGroupIds
import me.foxtails.palustris.domain.EmojiPickerPreferences
import me.foxtails.palustris.domain.EntityId
import me.foxtails.palustris.domain.OwnedPost
import me.foxtails.palustris.domain.Post
import me.foxtails.palustris.domain.Protocol
import me.foxtails.palustris.domain.Reaction
import me.foxtails.palustris.domain.ReactionSelectionMode
import me.foxtails.palustris.domain.ValidatedUrl
import me.foxtails.palustris.ui.PalustrisTheme
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
class EmojiPickerTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private val account = Account(
        AccountId(Connection("https://example.org", Protocol.MASTODON), "person"),
        "Person",
        "@person@example.org",
    )
    private val post = Post(
        id = EntityId("https://example.org", "post"),
        author = account,
        text = "Post",
        publishedAtEpochMillis = 0,
        audience = Audience.Public,
        reactions = listOf(
            Reaction(":blob:", 2, selected = true),
            Reaction("👍", 1, selected = false),
        ),
    )
    private val catalogEmoji = listOf(
        CustomEmoji(
            shortcode = "blob",
            animatedUrl = ValidatedUrl.https("https://cdn.example/blob.gif"),
            staticUrl = ValidatedUrl.https("https://cdn.example/blob.png"),
            category = "blobs",
            submissionValue = ":blob:",
        ),
        CustomEmoji(
            shortcode = "wave",
            animatedUrl = ValidatedUrl.https("https://cdn.example/wave.gif"),
            staticUrl = ValidatedUrl.https("https://cdn.example/wave.png"),
            category = null,
            submissionValue = ":wave:",
        ),
    )

    private fun show(
        target: EmojiPickerTarget?,
        catalog: EmojiCatalogState = EmojiCatalogState(items = catalogEmoji),
        mutationSupported: Boolean = true,
        selectionMode: ReactionSelectionMode = ReactionSelectionMode.Single,
        onEmojiSelected: (EmojiChoice) -> Unit = {},
        onLoadCatalog: () -> Unit = {},
    ) {
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                EmojiPickerHost(
                    target = target,
                    catalog = catalog,
                    selectionMode = selectionMode,
                    mutationSupported = mutationSupported,
                    onLoadCatalog = onLoadCatalog,
                    onRetryCatalog = {},
                    onDismiss = {},
                    onEmojiSelected = onEmojiSelected,
                )
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun nullTargetRendersNothing() {
        show(
            target = null,
        )
        compose.onNodeWithTag("emoji_picker_sheet").assertDoesNotExist()
    }

    @Test
    fun reactionTargetShowsPickerVisibleServerEntriesAndUnicodeDefaults() {
        show(target = EmojiPickerTarget.Reaction(OwnedPost(account.id, post)))

        compose.onNodeWithTag("emoji_picker_sheet").assertIsDisplayed()
        compose.onNodeWithTag("emoji_picker_cell_:blob:", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("emoji_picker_cell_:wave:", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun selectingACellReportsTheStructuredChoice() {
        var selected: EmojiChoice? = null
        show(
            target = EmojiPickerTarget.Reaction(OwnedPost(account.id, post)),
            onEmojiSelected = { selected = it },
        )

        compose.onNodeWithTag("emoji_picker_cell_:blob:", useUnmergedTree = true).performClick()

        assertEquals(":blob:", selected?.submissionValue)
        assertEquals("blob", selected?.emoji?.shortcode)
    }

    @Test
    fun searchFiltersChoices() {
        show(target = EmojiPickerTarget.Composer(ComposerField.Text))

        compose.onNodeWithTag("emoji_picker_search").performTextInput("wave")
        compose.onNodeWithTag("emoji_picker_cell_:wave:", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("emoji_picker_cell_:blob:", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun readOnlyReactionListAppearsWhenMutationIsUnsupported() {
        show(
            target = EmojiPickerTarget.Reaction(OwnedPost(account.id, post)),
            mutationSupported = false,
        )

        compose.onNodeWithTag("emoji_picker_sheet").assertIsDisplayed()
        compose.onNodeWithTag("emoji_picker_grid").assertDoesNotExist()
        compose.onNodeWithText(":blob:", substring = true).assertIsDisplayed()
        compose.onNodeWithText("👍", substring = true).assertIsDisplayed()
    }

    @Test
    fun loadingAndEmptyStatesRemainReadable() {
        show(target = EmojiPickerTarget.Composer(ComposerField.Warning), catalog = EmojiCatalogState(initialLoading = true))
        compose.onNodeWithTag("emoji_picker_sheet").assertIsDisplayed()
        show(target = EmojiPickerTarget.Composer(ComposerField.Warning), catalog = EmojiCatalogState(empty = true))
        compose.onNodeWithTag("emoji_picker_sheet").assertIsDisplayed()
    }

    @Test
    fun categorySectionsAppearInTheGrid() {
        show(target = EmojiPickerTarget.Composer(ComposerField.Text))

        compose.onNodeWithText("blobs").assertIsDisplayed()
        assertTrue(compose.onAllNodesWithTag("emoji_picker_cell_:blob:").fetchSemanticsNodes().isNotEmpty())
    }

    @Test
    fun extractedGridSupportsCompactCustomChoicesAndExpandedSelection() {
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                PalustrisTheme {
                    EmojiChoiceGrid(
                        catalogItems = catalogEmoji,
                        selectedIdentities = setOf(":blob:"),
                        compact = true,
                        testTag = "reaction_bubble_grid",
                        onEmojiSelected = {},
                    )
                }
            }
        }
        compose.waitForIdle()

        compose.onNodeWithTag("reaction_bubble_grid").assertIsDisplayed()
        compose.onNodeWithTag("emoji_picker_cell_:blob:", useUnmergedTree = true).assertIsSelected()
        compose.onNodeWithTag("emoji_picker_cell_👍", useUnmergedTree = true).assertIsDisplayed()

        compose.activity.runOnUiThread {
            compose.activity.setContent {
                PalustrisTheme {
                    EmojiChoiceGrid(
                        catalogItems = catalogEmoji,
                        selectedIdentities = setOf(":blob:"),
                        onEmojiSelected = {},
                    )
                }
            }
        }
        compose.waitForIdle()
        compose.onNodeWithTag("emoji_picker_cell_:blob:", useUnmergedTree = true).assertIsSelected()
        compose.onNodeWithTag("emoji_picker_cell_:wave:", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun fullGroupsFollowPinnedRecentPostSpecificServerAndUnicodeOrder() {
        val groups = buildEmojiPickerGroups(
            catalogItems = catalogEmoji + catalogEmoji.first().copy(shortcode = "other", submissionValue = ":other:", category = "other"),
            additionalChoices = listOf(
                EmojiChoice(":missing:", ":missing:"),
                EmojiChoice("🎈", "🎈"),
            ),
            recentIdentities = listOf(":blob:", "🎈"),
            selectedIdentities = setOf(":blob:"),
            searchQuery = "",
            preferences = EmojiPickerPreferences(pinnedGroups = listOf("server:blobs")),
        )

        assertEquals(
            listOf(
                EmojiPickerGroupIds.Favorite,
                "server:blobs",
                EmojiPickerGroupIds.Recent,
                EmojiPickerGroupIds.PostSpecific,
                "server:",
                "server:other",
                EmojiPickerGroupIds.Unicode,
            ),
            groups.map { it.id },
        )
        assertEquals("blob", groups[2].choices.first().emoji?.shortcode)
        assertEquals(":missing:", groups[3].choices.first().submissionValue)
        assertEquals(":blob:", groups[1].choices.first().submissionValue)
    }

    @Test
    fun collapsedGroupsStayCollapsedWhenSearching() {
        val groups = buildEmojiPickerGroups(
            catalogItems = catalogEmoji,
            additionalChoices = emptyList(),
            recentIdentities = emptyList(),
            selectedIdentities = emptySet(),
            searchQuery = "blob",
            preferences = EmojiPickerPreferences(collapsedGroups = setOf("server:blobs")),
        )

        val blobs = groups.first { it.id == "server:blobs" }
        assertTrue(blobs.collapsed)
        assertTrue(blobs.choices.isEmpty())
    }

    @Test
    fun groupHeadersExposeIndependentCollapseAndPinControls() {
        var collapsed: String? = null
        var pinned: String? = null
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                PalustrisTheme {
                    EmojiChoiceGrid(
                        catalogItems = catalogEmoji,
                        onToggleGroupCollapsed = { collapsed = it },
                        onToggleGroupPinned = { pinned = it },
                        onEmojiSelected = {},
                    )
                }
            }
        }
        compose.waitForIdle()

        compose.onNodeWithContentDescription("Collapse Favorite Emoji", useUnmergedTree = true).performClick()
        compose.onNodeWithContentDescription("Pin blobs", useUnmergedTree = true).performClick()

        assertEquals(EmojiPickerGroupIds.Favorite, collapsed)
        assertEquals(EmojiPickerGroupIds.server("blobs"), pinned)
    }
}
