package me.foxtails.palustris

import me.foxtails.palustris.data.mastodon.MastodonMapper
import me.foxtails.palustris.data.misskey.MisskeyMapper
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.NotificationActivity
import me.foxtails.palustris.domain.NotificationReadStatus
import me.foxtails.palustris.domain.NotificationTarget
import me.foxtails.palustris.domain.Protocol
import me.foxtails.palustris.domain.ValidatedUrl
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class NotificationContractTest {
    private val origin = "https://example.org"
    private val receivingAccount = AccountId(Connection(origin, Protocol.MASTODON), "receiver")

    @Test
    fun mastodonUnknownAndActorlessSystemNotificationsRemainRenderable() {
        val unknown = MastodonMapper.notification(
            JSONObject()
                .put("id", "unknown-1")
                .put("type", "future_type")
                .put("created_at", "2026-09-07T10:00:00Z"),
            origin,
            receivingAccount,
        )
        val system = MastodonMapper.notification(
            JSONObject()
                .put("id", "system-1")
                .put("type", "admin.report")
                .put("created_at", "2026-09-07T10:00:00Z"),
            origin,
            receivingAccount,
        )

        assertEquals(receivingAccount, unknown.accountId)
        assertTrue(unknown.actors.isEmpty())
        assertTrue(unknown.activity is NotificationActivity.Unknown)
        assertNull(unknown.target)
        assertTrue(system.activity is NotificationActivity.System.Moderation)
        assertEquals(NotificationReadStatus.Unknown, system.readState.status)
    }

    @Test
    fun misskeyReactionPreservesUnicodeCustomEmojiAndActorlessEvents() {
        val reaction = MisskeyMapper.notification(
            JSONObject()
                .put("id", "reaction-1")
                .put("type", "reaction")
                .put("createdAt", "2026-09-07T10:00:00Z")
                .put("reaction", ":party_parrot:")
                .put("customEmoji", JSONObject().put("url", "https://example.org/party.png")),
            origin,
            AccountId(Connection(origin, Protocol.MISSKEY), "receiver"),
        )
        val event = MisskeyMapper.notification(
            JSONObject()
                .put("id", "event-1")
                .put("type", "future_event")
                .put("createdAt", "2026-09-07T10:00:00Z"),
            origin,
            AccountId(Connection(origin, Protocol.MISSKEY), "receiver"),
        )

        val activity = reaction.activity as NotificationActivity.EmojiReaction
        assertEquals(":party_parrot:", activity.reaction.identity)
        assertEquals("https://example.org/party.png", activity.reaction.imageUrl)
        assertTrue(event.activity is NotificationActivity.Unknown)
        assertTrue(event.actors.isEmpty())
    }

    @Test
    fun postActivityUsesCanonicalPostTargetAndAccountScopedIdentity() {
        val notification = MastodonMapper.notification(
            JSONObject()
                .put("id", "mention-1")
                .put("type", "mention")
                .put("created_at", "2026-09-07T10:00:00Z")
                .put("status", JSONObject()
                    .put("id", "status-1")
                    .put("created_at", "2026-09-07T09:00:00Z")
                    .put("account", JSONObject()
                        .put("id", "author")
                        .put("username", "author")
                        .put("acct", "author"))
                    .put("content", "<p>Hello</p>")
                    .put("visibility", "public")),
            origin,
            receivingAccount,
        )

        assertEquals(receivingAccount, notification.accountId)
        assertEquals(NotificationTarget.Post(notification.post!!.id), notification.target)
        assertEquals("status-1", (notification.destination as me.foxtails.palustris.domain.NotificationDestination.InApp)
            .target.let { (it as NotificationTarget.Post).id.value })
    }

    @Test
    fun validatedDestinationRejectsNonHttpsAndCredentials() {
        assertEquals("https://example.org/path?q=1", ValidatedUrl.https("https://example.org/path?q=1")?.value)
        assertNull(ValidatedUrl.https("http://example.org/path"))
        assertNull(ValidatedUrl.https("https://user:pass@example.org/path"))
        assertNull(ValidatedUrl.https("https://example.org/path#fragment"))
    }
}
