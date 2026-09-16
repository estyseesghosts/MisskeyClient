package me.foxtails.palustris

import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
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
import me.foxtails.palustris.domain.Timeline
import me.foxtails.palustris.ui.PalustrisApp
import me.foxtails.palustris.ui.shell.AccountSwitcher
import me.foxtails.palustris.ui.shell.BookmarksContract
import me.foxtails.palustris.ui.shell.ComposerContract
import me.foxtails.palustris.ui.shell.DirectMessagesContract
import me.foxtails.palustris.ui.shell.DraftsContract
import me.foxtails.palustris.ui.shell.EmojiPresentation
import me.foxtails.palustris.ui.shell.HomeContract
import me.foxtails.palustris.ui.shell.HomeFeedUiState
import me.foxtails.palustris.ui.shell.LikesContract
import me.foxtails.palustris.ui.shell.NotificationSettingsContract
import me.foxtails.palustris.ui.shell.NotificationsContract
import me.foxtails.palustris.ui.shell.PhotoGridContract
import me.foxtails.palustris.ui.shell.PostInteractions
import me.foxtails.palustris.ui.shell.ProfileContract
import me.foxtails.palustris.ui.shell.SearchContract
import me.foxtails.palustris.ui.shell.ThreadContract
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Api29StartupInstrumentedTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun activityStartsAndGuardedMediaPathRendersOnApi29() {
        val account = Account(
            id = AccountId(Connection("https://fixture.example", Protocol.MASTODON), "fixture"),
            displayName = "Fixture account",
            handle = "@fixture@fixture.example",
        )
        val post = Post(
            id = EntityId(account.id.connection.origin, "fixture-post"),
            author = account,
            text = "API 29 media fixture",
            publishedAtEpochMillis = 0,
            audience = Audience.Public,
            attachments = listOf(
                Attachment(
                    id = "fixture-image",
                    url = "https://fixture.example/image.jpg",
                    mimeType = "image/jpeg",
                    kind = MediaKind.Image,
                ),
            ),
        )
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                PalustrisApp(
                    account = account,
                    sessionGeneration = 0L,
                    sessionRevision = 0L,
                    home = HomeContract(
                        state = HomeFeedUiState(
                            ownedPosts = listOf(OwnedPost(account.id, post)),
                            posts = listOf(post),
                        ),
                        actions = object : HomeContract.Actions {
                            override fun refresh(timeline: Timeline) = Unit
                            override fun loadMore(timeline: Timeline) = Unit
                        },
                    ),
                    photoGrid = PhotoGridContract.Empty,
                    profile = ProfileContract.Empty,
                    accountSwitcher = AccountSwitcher.Empty,
                    composer = ComposerContract.Empty,
                    search = SearchContract.Empty,
                    postInteractions = PostInteractions.Empty,
                    thread = ThreadContract.Empty,
                    draftsContract = DraftsContract.Empty,
                    emojiPresentation = EmojiPresentation.Empty,
                    bookmarks = BookmarksContract.Empty,
                    likes = LikesContract.Empty,
                    notifications = NotificationsContract.Empty,
                    directMessages = DirectMessagesContract.Empty,
                    initialNotificationRoute = null,
                    notificationSettings = NotificationSettingsContract.Empty,
                )
            }
        }

        compose.waitForIdle()
        compose.onNodeWithContentDescription("Open media 1 of 1").performClick()
        compose.onNodeWithContentDescription("Close media viewer").assertIsDisplayed()
    }
}
