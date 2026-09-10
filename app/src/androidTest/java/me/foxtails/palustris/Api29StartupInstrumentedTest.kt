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
import me.foxtails.palustris.domain.Post
import me.foxtails.palustris.domain.Protocol
import me.foxtails.palustris.ui.FeedState
import me.foxtails.palustris.ui.PalustrisApp
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
                PalustrisApp(account = account, feedState = FeedState(posts = listOf(post)))
            }
        }

        compose.waitForIdle()
        compose.onNodeWithContentDescription("Open media 1 of 1").performClick()
        compose.onNodeWithContentDescription("Close media viewer").assertIsDisplayed()
    }
}
