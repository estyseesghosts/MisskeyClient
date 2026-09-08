package me.foxtails.palustris

import androidx.test.core.app.ApplicationProvider
import me.foxtails.palustris.data.notifications.NotificationChannelKind
import me.foxtails.palustris.data.notifications.NotificationPresentationFactory
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.Audience
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.EntityId
import me.foxtails.palustris.domain.Notification
import me.foxtails.palustris.domain.NotificationActivity
import me.foxtails.palustris.domain.Post
import me.foxtails.palustris.domain.Protocol
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class NotificationPresentationTest {
    @Test
    fun contentWarningTakesPrecedenceOverPublicPostText() {
        val account = AccountId(Connection("https://example.org", Protocol.MASTODON), "receiver")
        val actor = Account(account.copy(localId = "actor"), "Actor", "@actor@example.org")
        val notification = Notification(
            id = EntityId(account.connection.origin, "event"),
            accountId = account,
            createdAtEpochMillis = 1L,
            activity = NotificationActivity.Mention,
            actors = listOf(actor),
            post = Post(
                id = EntityId(account.connection.origin, "post"),
                author = actor,
                text = "private spoiler text",
                publishedAtEpochMillis = 1L,
                audience = Audience.Public,
                contentWarning = "spoilers",
            ),
            rawType = "mention",
        )

        val presentation = NotificationPresentationFactory(
            ApplicationProvider.getApplicationContext(),
        ).prepare(notification, showPreview = true, channel = NotificationChannelKind.RepliesAndMentions)

        assertTrue(presentation.body.contains("spoilers"))
        assertFalse(presentation.body.contains("private spoiler text"))
    }
}
