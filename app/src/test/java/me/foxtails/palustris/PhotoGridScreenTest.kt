package me.foxtails.palustris

import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.Attachment
import me.foxtails.palustris.domain.Audience
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.EntityId
import me.foxtails.palustris.domain.MediaKind
import me.foxtails.palustris.domain.OwnedPost
import me.foxtails.palustris.domain.Post
import me.foxtails.palustris.domain.Protocol
import me.foxtails.palustris.ui.FeedState
import me.foxtails.palustris.ui.PalustrisTheme
import me.foxtails.palustris.ui.PhotoGridScreen
import me.foxtails.palustris.ui.Destination
import me.foxtails.palustris.ui.SearchPanel
import me.foxtails.palustris.ui.large.LargeNavTarget
import me.foxtails.palustris.ui.photoGridAspectRatio
import me.foxtails.palustris.ui.photoGridItems
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
class PhotoGridScreenTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun mediaFilterKeepsOneFirstDisplayableAttachmentPerPost() {
        val textOnly = post("text-only")
        val videoThenImage = post(
            "video-then-image",
            listOf(
                Attachment(
                    id = "video",
                    url = "https://cdn.example/video.mp4",
                    mimeType = "video/mp4",
                    kind = MediaKind.Video,
                ),
                image("second"),
            ),
        )
        val twoImages = post("two-images", listOf(image("first"), image("second")))

        val items = photoGridItems(
            listOf(
                OwnedPost(account.id, textOnly),
                OwnedPost(account.id, videoThenImage),
                OwnedPost(account.id, twoImages),
            ),
        )

        assertEquals(listOf("video-then-image", "two-images"), items.map { it.ownedPost.post.id.value })
        assertEquals("second", items.first().attachment.id)
        assertEquals("first", items.last().attachment.id)
    }

    @Test
    fun aspectRatioUsesOriginalAttachmentDimensionsWithFallback() {
        assertEquals(
            1.5f,
            photoGridAspectRatio(image("ratio").copy(width = 300, height = 200)),
            0.001f,
        )
        assertEquals(4f / 3f, photoGridAspectRatio(image("fallback").copy(width = null, height = null)), 0.001f)
    }

    @Test
    fun searchPanelsMapToSeparateLargeNavigationTargets() {
        assertEquals(
            LargeNavTarget.Search,
            me.foxtails.palustris.ui.largeTargetFor(
                Destination.Search,
                SearchPanel.Search,
                me.foxtails.palustris.ui.NotificationsPanel.Notifications,
            ),
        )
        assertEquals(
            LargeNavTarget.PhotoGrid,
            me.foxtails.palustris.ui.largeTargetFor(
                Destination.Search,
                SearchPanel.PhotoGrid,
                me.foxtails.palustris.ui.NotificationsPanel.Notifications,
            ),
        )
    }

    @Test
    fun tileTapOpensTheCompletePost() {
        var opened: OwnedPost? = null
        show(
            state = FeedState(posts = listOf(post("tap", listOf(image("tap-image"))))),
            onOpenPost = { opened = it },
        )

        compose.onNodeWithContentDescription("Open post").performClick()

        assertEquals("tap", opened?.post?.id?.value)
    }

    @Test
    fun sensitiveTileRequiresRevealBeforeOpening() {
        var opened: OwnedPost? = null
        show(
            state = FeedState(posts = listOf(post("sensitive", listOf(image("sensitive").copy(sensitive = true))))),
            onOpenPost = { opened = it },
        )

        compose.onNodeWithText("Show sensitive media").assertIsDisplayed().performClick()
        compose.onNodeWithContentDescription("Open post").performClick()

        assertEquals("sensitive", opened?.post?.id?.value)
    }

    @Test
    fun pagingErrorKeepsAlreadyLoadedTiles() {
        val loaded = post("loaded", listOf(image("loaded-image")))
        show(
            state = FeedState(
                posts = listOf(loaded),
                error = "Connection failed",
                nextCursor = "older",
            ),
        )

        compose.onNodeWithContentDescription("Open post").assertIsDisplayed()
        compose.onNodeWithText("Couldn\'t load more photos").assertIsDisplayed()
    }

    @Test
    fun textOnlyPageContinuesToItsNextCursor() {
        val textOnly = post("text-only")
        val loadedMedia = post("loaded-media", listOf(image("loaded-image")))
        var state by mutableStateOf(
            FeedState(
                posts = listOf(textOnly),
                ownedPosts = listOf(OwnedPost(account.id, textOnly)),
                nextCursor = "first-page",
            ),
        )
        var loadCount = 0
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                PalustrisTheme {
                    PhotoGridScreen(
                        state = state,
                        onLoadMore = {
                            loadCount++
                            state = FeedState(
                                posts = listOf(loadedMedia),
                                ownedPosts = listOf(OwnedPost(account.id, loadedMedia)),
                            )
                        },
                    )
                }
            }
        }
        compose.waitForIdle()

        assertEquals(1, loadCount)
        compose.onNodeWithContentDescription("Open post").assertIsDisplayed()
    }

    private fun show(state: FeedState, onOpenPost: (OwnedPost) -> Unit = {}) {
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                PalustrisTheme {
                    PhotoGridScreen(state = state, onOpenPost = onOpenPost)
                }
            }
        }
        compose.waitForIdle()
    }

    private fun post(id: String, attachments: List<Attachment> = emptyList()) = Post(
        id = EntityId(account.id.connection.origin, id),
        author = account,
        text = id,
        publishedAtEpochMillis = 0,
        audience = Audience.Public,
        attachments = attachments,
    )

    private fun image(id: String) = Attachment(
        id = id,
        url = "https://cdn.example/$id.jpg",
        previewUrl = "https://cdn.example/$id-preview.jpg",
        mimeType = "image/jpeg",
        kind = MediaKind.Image,
        width = 640,
        height = 480,
    )

    private val account = Account(
        id = AccountId(Connection("https://example.org", Protocol.MASTODON), "photo-grid"),
        displayName = "Photo Grid",
        handle = "@photo-grid@example.org",
    )
}
