package me.foxtails.palustris.ui.emoji

import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import me.foxtails.palustris.MainActivity
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.Audience
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.CustomEmoji
import me.foxtails.palustris.domain.EntityId
import me.foxtails.palustris.domain.Post
import me.foxtails.palustris.domain.Protocol
import me.foxtails.palustris.domain.ValidatedUrl
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-420dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class InlineEmojiTextTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private val blob = CustomEmoji(
        shortcode = "blob_cat",
        animatedUrl = ValidatedUrl.https("https://cdn.example/blob_cat.gif"),
        staticUrl = ValidatedUrl.https("https://cdn.example/blob_cat.png"),
        submissionValue = ":blob_cat:",
    )

    private fun show(content: @androidx.compose.runtime.Composable () -> Unit) {
        compose.activity.runOnUiThread { compose.activity.setContent { content() } }
        compose.waitForIdle()
    }

    @Test
    fun knownTokenRendersAndShortcodeStaysReadableInTheText() {
        show {
            InlineEmojiText("hello :blob_cat: world", mapOf("blob_cat" to blob))
        }

        compose.onNodeWithText("hello :blob_cat: world", substring = true).assertIsDisplayed()
    }

    @Test
    fun unknownTokenRemainsPlainText() {
        show {
            InlineEmojiText("no :missing: here", mapOf("blob_cat" to blob))
        }

        compose.onNodeWithText("no :missing: here", substring = true).assertIsDisplayed()
    }

    @Test
    fun accountDisplayNameUsesTheAccountsOwnEmojiMap() {
        val account = Account(
            AccountId(Connection("https://example.org", Protocol.MASTODON), "person"),
            "Named :blob_cat: Person",
            "@person@example.org",
            emoji = mapOf("blob_cat" to blob),
        )
        show { AccountDisplayName(account) }

        compose.onNodeWithText("Named :blob_cat: Person", substring = true).assertIsDisplayed()
    }

    @Test
    fun postTextUsesThePostsOwnEmojiMap() {
        val post = Post(
            id = EntityId("https://example.org", "post"),
            author = Account(
                AccountId(Connection("https://example.org", Protocol.MASTODON), "person"),
                "Person",
                "@person@example.org",
            ),
            text = "post :blob_cat: body",
            publishedAtEpochMillis = 0,
            audience = Audience.Public,
            emoji = mapOf("blob_cat" to blob),
        )
        show { PostText(post) }

        compose.onNodeWithText("post :blob_cat: body", substring = true).assertIsDisplayed()
    }

    @Test
    fun linksSurviveInsideEmojiAwareText() {
        show {
            InlineEmojiText(
                "read [the guide](https://example.org/guide) :blob_cat:",
                mapOf("blob_cat" to blob),
            )
        }

        compose.onNodeWithText("read the guide", substring = true)
            .assertIsDisplayed()
    }

    @Test
    fun customEmojiInsideLinkStillRendersAsInlineContent() {
        show {
            InlineEmojiText(
                "[the :blob_cat: guide](https://example.org/guide)",
                mapOf("blob_cat" to blob),
            )
        }

        compose.onNodeWithText("the :blob_cat: guide", substring = true)
            .assertIsDisplayed()
    }

    @Test
    fun postEntityBubblesExposeLabelsAndRouteClicksToTheirEntityCallbacks() {
        var openedUrl = ""
        var openedUsername = ""
        var searchedHashtag = ""
        show {
            InlineEmojiText(
                "read https://example.org/guide from @handle@mastodon.social about #Zurich",
                emptyMap(),
                enableInlineEntities = true,
                onOpenUrl = { openedUrl = it },
                onOpenUsername = { openedUsername = it },
                onSearchHashtag = { searchedHashtag = it },
            )
        }

        compose.onNodeWithContentDescription("Link example.org").assertIsDisplayed().performClick()
        compose.onNodeWithContentDescription("Username @handle").assertIsDisplayed().performClick()
        compose.onNodeWithContentDescription("Hashtag #Zurich").assertIsDisplayed().performClick()

        assertEquals("https://example.org/guide", openedUrl)
        assertEquals("@handle@mastodon.social", openedUsername)
        assertEquals("#Zurich", searchedHashtag)
    }
}
