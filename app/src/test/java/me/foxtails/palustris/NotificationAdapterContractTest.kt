package me.foxtails.palustris

import kotlinx.coroutines.runBlocking
import me.foxtails.palustris.data.mastodon.MastodonMapper
import me.foxtails.palustris.data.mastodon.MastodonSource
import me.foxtails.palustris.data.misskey.MisskeyApi
import me.foxtails.palustris.data.misskey.MisskeyMapper
import me.foxtails.palustris.data.misskey.MisskeySource
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.NotificationActivity
import me.foxtails.palustris.domain.NotificationCategory
import me.foxtails.palustris.domain.NotificationQuery
import me.foxtails.palustris.domain.NotificationReadStatus
import me.foxtails.palustris.domain.NotificationTarget
import me.foxtails.palustris.domain.Protocol
import me.foxtails.palustris.domain.SourceError
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class NotificationAdapterContractTest {
    private lateinit var server: MockWebServer

    @Before
    fun startServer() {
        server = MockWebServer().also { it.start() }
    }

    @After
    fun stopServer() {
        server.shutdown()
    }

    @Test
    fun mastodonFiltersUseV1TypesAndOpaqueCursorCannotCrossQueriesOrAccounts() = runBlocking {
        val origin = server.url("/").toString().removeSuffix("/")
        val account = AccountId(Connection(origin, Protocol.MASTODON), "receiver-a")
        val query = NotificationQuery(setOf(NotificationCategory.Mentions), limit = 30)
        server.enqueue(MockResponse()
            .setBody(JSONArray().put(mastodonNotification("n-new", "mention")).toString())
            .addHeader(
                "Link",
                "<$origin/api/v1/notifications?limit=30&types%5B%5D=mention&max_id=n-old>; rel=\"next\", " +
                    "<$origin/api/v1/notifications?limit=30&types%5B%5D=mention&min_id=n-new>; rel=\"prev\"",
            ))
        server.enqueue(MockResponse().setBody(JSONArray().put(mastodonNotification("n-old", "mention")).toString()))

        val source = MastodonSource(origin, "token", MisskeyApi(), account)
        val first = source.notifications(query)
        val second = source.notifications(query, first.olderCursor)

        assertEquals("n-new", first.items.single().id.value)
        assertEquals("n-old", second.items.single().id.value)
        assertTrue(server.takeRequest().path!!.contains("types%5B%5D=mention"))
        assertTrue(server.takeRequest().path!!.contains("max_id=n-old"))

        assertThrows(SourceError.Unsupported::class.java) {
            runBlocking { source.notifications(NotificationQuery(setOf(NotificationCategory.Quotes)), first.olderCursor) }
        }
        val otherAccount = MastodonSource(
            origin,
            "token",
            MisskeyApi(),
            AccountId(Connection(origin, Protocol.MASTODON), "receiver-b"),
        )
        assertThrows(SourceError.Unsupported::class.java) {
            runBlocking { otherAccount.notifications(query, first.olderCursor) }
        }
        assertEquals(2, server.requestCount)
    }

    @Test
    fun mastodonGroupedNotificationsKeepGroupIdentityAndCanonicalStatusTarget() = runBlocking {
        val origin = server.url("/").toString().removeSuffix("/")
        val receiver = AccountId(Connection(origin, Protocol.MASTODON), "receiver")
        val actor = mastodonAccount("actor", "actor")
        val status = mastodonStatus("status-1", actor)
        val payload = JSONObject()
            .put("accounts", JSONArray().put(actor))
            .put("statuses", JSONArray().put(status))
            .put("notification_groups", JSONArray().put(JSONObject()
                .put("group_key", "favourite-status-1")
                .put("notifications_count", 3)
                .put("sample_account_ids", JSONArray().put("actor"))
                .put("status_id", "status-1")
                .put("type", "favourite")
                .put("most_recent_notification_id", "notification-3")))
        server.enqueue(MockResponse().setBody(payload.toString()))

        val result = MastodonSource(origin, "token", MisskeyApi(), receiver)
            .notifications(NotificationQuery(grouped = true))
            .items
            .single()

        assertEquals("notification-3", result.id.value)
        assertEquals("favourite-status-1", result.group?.id?.value)
        assertEquals(3, result.group?.totalCount)
        assertEquals("actor", result.actors.single().id.localId)
        assertEquals(NotificationTarget.Post(result.post!!.id), result.target)
        assertTrue(result.target is NotificationTarget.Post)
        assertEquals("status-1", (result.target as NotificationTarget.Post).id.value)
        assertEquals("/api/v2/notifications?limit=30&grouped_types%5B%5D=favourite&grouped_types%5B%5D=follow&grouped_types%5B%5D=reblog&grouped_types%5B%5D=admin.sign_up", server.takeRequest().path)
    }

    @Test
    fun mastodonRejectsForeignNotificationContinuationBeforeSendingCredentials() = runBlocking {
        MockWebServer().use { foreign ->
            val origin = server.url("/").toString().removeSuffix("/")
            val receiver = AccountId(Connection(origin, Protocol.MASTODON), "receiver")
            server.enqueue(MockResponse()
                .setBody(JSONArray().put(mastodonNotification("n-1", "mention")).toString())
                .addHeader("Link", "<${foreign.url("/api/v1/notifications?max_id=n-1")}>; rel=\"next\""))

            assertThrows(SourceError.Unsupported::class.java) {
                runBlocking {
                    MastodonSource(origin, "token", MisskeyApi(), receiver).notifications()
                }
            }
            assertEquals(0, foreign.requestCount)
            assertEquals(1, server.requestCount)
        }
    }

    @Test
    fun mastodonMalformedOptionalStatusDoesNotEraseActorlessUnknownNotification() = runBlocking {
        val origin = server.url("/").toString().removeSuffix("/")
        val receiver = AccountId(Connection(origin, Protocol.MASTODON), "receiver")
        val malformedOptionalStatus = JSONObject()
            .put("id", "unknown-1")
            .put("type", "future_type")
            .put("created_at", "2026-09-07T10:00:00Z")
            .put("status", JSONObject().put("id", "missing-account"))
        server.enqueue(MockResponse().setBody(JSONArray().put(malformedOptionalStatus).toString()))

        val notification = MastodonSource(origin, "token", MisskeyApi(), receiver)
            .notifications()
            .items
            .single()

        assertTrue(notification.actors.isEmpty())
        assertTrue(notification.activity is NotificationActivity.Unknown)
        assertEquals(NotificationReadStatus.Unknown, notification.readState.status)
        assertEquals(null, notification.target)
    }

    @Test
    fun misskeyFetchesDoNotMarkReadAndUseTransportOrderForOlderAndNewerCursors() = runBlocking {
        val origin = server.url("/").toString().removeSuffix("/")
        val account = AccountId(Connection(origin, Protocol.MISSKEY), "receiver")
        val query = NotificationQuery(setOf(NotificationCategory.Mentions), limit = 2)
        server.enqueue(MockResponse().setBody(JSONArray()
            .put(misskeyNotification("n-new", "mention"))
            .put(misskeyNotification("n-old", "reply"))
            .toString()))
        server.enqueue(MockResponse().setBody(JSONArray().put(misskeyNotification("n-older", "mention")).toString()))
        server.enqueue(MockResponse().setBody(JSONArray().put(misskeyNotification("n-newer", "mention")).toString()))

        val source = MisskeySource(origin, "token", MisskeyApi(), accountId = account)
        val first = source.notifications(query)
        val older = source.notifications(query, first.olderCursor)
        val newer = source.fetchNewerNotifications(query, first.checkpoint!!)

        assertEquals(listOf("n-new", "n-old"), first.items.map { it.id.value })
        assertEquals("n-older", older.items.single().id.value)
        assertEquals("n-newer", newer.items.single().id.value)
        val firstBody = JSONObject(server.takeRequest().body.readUtf8())
        val olderBody = JSONObject(server.takeRequest().body.readUtf8())
        val newerBody = JSONObject(server.takeRequest().body.readUtf8())
        assertFalse(firstBody.getBoolean("markAsRead"))
        assertEquals(JSONArray().put("mention").put("reply").toString(), firstBody.getJSONArray("includeTypes").toString())
        assertEquals("n-old", olderBody.getString("untilId"))
        assertEquals("n-new", newerBody.getString("sinceId"))
    }

    @Test
    fun misskeyGroupedAndSystemRecordsPreserveActorsTargetsAndSafeUnknowns() = runBlocking {
        val origin = server.url("/").toString().removeSuffix("/")
        val receiver = AccountId(Connection(origin, Protocol.MISSKEY), "receiver")
        val actor = misskeyAccount("actor", "actor")
        val groupedReaction = JSONObject()
            .put("id", "reaction-group")
            .put("type", "reaction:grouped")
            .put("createdAt", "2026-09-07T10:00:00Z")
            .put("note", misskeyNote("note-1", actor))
            .put("reactions", JSONArray()
                .put(JSONObject().put("user", actor).put("reaction", "🎉"))
                .put(JSONObject().put("user", actor).put("reaction", ":party_parrot:")))
        val system = JSONObject()
            .put("id", "system-1")
            .put("type", "future_system")
            .put("createdAt", "2026-09-07T10:00:00Z")
        val result = listOf(
            MisskeyMapper.notification(groupedReaction, origin, receiver),
            MisskeyMapper.notification(system, origin, receiver),
        )

        assertEquals(2, result.first().group?.totalCount)
        assertEquals(2, result.first().actors.size)
        assertTrue(result.first().activity is NotificationActivity.EmojiReaction)
        assertEquals("note-1", (result.first().target as NotificationTarget.Post).id.value)
        assertTrue(result.last().actors.isEmpty())
        assertTrue(result.last().activity is NotificationActivity.Unknown)
        assertNotNull(result.first().post)

        server.enqueue(MockResponse().setBody(JSONArray().put(groupedReaction).toString()))
        val page = MisskeySource(origin, "token", MisskeyApi(), accountId = receiver)
            .notifications(NotificationQuery(grouped = true))
        assertEquals("reaction-group", page.items.single().id.value)
        val groupedRequest = server.takeRequest()
        assertEquals("/api/i/notifications-grouped", groupedRequest.path)
        assertFalse(JSONObject(groupedRequest.body.readUtf8()).getBoolean("markAsRead"))
    }

    @Test
    fun mastodonQuotedUpdateUsesQuoteSpecificActivity() {
        val origin = server.url("/").toString().removeSuffix("/")
        val receiver = AccountId(Connection(origin, Protocol.MASTODON), "receiver")
        val notification = MastodonMapper.notification(
            JSONObject()
                .put("id", "quoted-update")
                .put("type", "quoted_update")
                .put("created_at", "2026-09-07T10:00:00Z")
                .put("status", mastodonStatus("my-quote", mastodonAccount("actor", "actor"))),
            origin,
            receiver,
        )

        assertTrue(notification.activity is NotificationActivity.QuotedPostUpdate)
        assertEquals("my-quote", (notification.target as NotificationTarget.Post).id.value)
    }

    @Test
    fun misskeyUnreadStateAndAcknowledgementAreExplicitAccountOperations() = runBlocking {
        val origin = server.url("/").toString().removeSuffix("/")
        val account = AccountId(Connection(origin, Protocol.MISSKEY), "receiver")
        server.enqueue(MockResponse().setBody(JSONObject().put("notificationCount", 4).toString()))
        server.enqueue(MockResponse().setBody("{}"))
        val source = MisskeySource(origin, "token", MisskeyApi(), accountId = account)

        assertEquals(me.foxtails.palustris.domain.NotificationUnreadState.Exact(4), source.notificationUnreadState())
        assertEquals(me.foxtails.palustris.domain.NotificationUnreadState.None, source.acknowledgeNotifications().readState)
        assertEquals("/api/i", server.takeRequest().path)
        assertEquals("/api/notifications/mark-all-as-read", server.takeRequest().path)
    }

    private fun mastodonNotification(id: String, type: String) = JSONObject()
        .put("id", id)
        .put("type", type)
        .put("created_at", "2026-09-07T10:00:00Z")
        .put("account", mastodonAccount("actor", "actor"))
        .put("status", mastodonStatus("status-$id", mastodonAccount("actor", "actor")))

    private fun mastodonStatus(id: String, actor: JSONObject) = JSONObject()
        .put("id", id)
        .put("created_at", "2026-09-07T09:00:00Z")
        .put("account", actor)
        .put("content", "<p>Hello</p>")
        .put("visibility", "public")

    private fun mastodonAccount(id: String, username: String) = JSONObject()
        .put("id", id)
        .put("username", username)
        .put("acct", username)
        .put("display_name", username.replaceFirstChar(Char::uppercase))

    private fun misskeyNotification(id: String, type: String): JSONObject {
        val actor = misskeyAccount("actor", "actor")
        return JSONObject()
            .put("id", id)
            .put("type", type)
            .put("createdAt", "2026-09-07T10:00:00Z")
            .put("user", actor)
            .put("note", misskeyNote("note-$id", actor))
    }

    private fun misskeyNote(id: String, actor: JSONObject) = JSONObject()
        .put("id", id)
        .put("createdAt", "2026-09-07T09:00:00Z")
        .put("user", actor)
        .put("text", "Hello")
        .put("visibility", "public")

    private fun misskeyAccount(id: String, username: String) = JSONObject()
        .put("id", id)
        .put("username", username)
        .put("name", username.replaceFirstChar(Char::uppercase))
        .put("host", JSONObject.NULL)
}
