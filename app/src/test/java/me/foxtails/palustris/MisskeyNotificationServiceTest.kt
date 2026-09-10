package me.foxtails.palustris

import kotlinx.coroutines.runBlocking
import me.foxtails.palustris.data.misskey.MisskeyApi
import me.foxtails.palustris.data.misskey.MisskeyNotificationCursorCodec
import me.foxtails.palustris.data.misskey.MisskeyNotificationCursorDirection
import me.foxtails.palustris.data.misskey.MisskeyNotificationService
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.NotificationCategory
import me.foxtails.palustris.domain.NotificationCheckpoint
import me.foxtails.palustris.domain.NotificationCursor
import me.foxtails.palustris.domain.NotificationQuery
import me.foxtails.palustris.domain.NotificationUnreadState
import me.foxtails.palustris.domain.Protocol
import me.foxtails.palustris.domain.SourceError
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MisskeyNotificationServiceTest {
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
    fun initialAndOlderRequestsUseMisskeyTransportParameters() = runBlocking {
        val service = service()
        server.enqueue(MockResponse().setBody(JSONArray().put(notification("new")).toString()))
        server.enqueue(MockResponse().setBody(JSONArray().put(notification("old")).toString()))
        val query = NotificationQuery(setOf(NotificationCategory.Mentions), limit = 2)

        val initial = service.notifications(query, null)
        val older = service.fetchOlder(query, initial.checkpoint!!)

        assertEquals("new", initial.items.single().id.value)
        assertEquals("old", older.items.single().id.value)
        val first = server.takeRequest()
        val firstBody = JSONObject(first.body.readUtf8())
        assertEquals("/api/i/notifications", first.path)
        assertEquals(2, firstBody.getInt("limit"))
        assertFalse(firstBody.getBoolean("markAsRead"))
        assertEquals(JSONArray().put("mention").put("reply").toString(), firstBody.getJSONArray("includeTypes").toString())
        assertEquals("new", JSONObject(server.takeRequest().body.readUtf8()).getString("untilId"))
    }

    @Test
    fun newerCatchUpKeepsTheTransportCursorStableAcrossPages() = runBlocking {
        val service = service()
        val query = NotificationQuery(limit = 2)
        server.enqueue(MockResponse().setBody(JSONArray().put(notification("base-2")).put(notification("base-1")).toString()))
        server.enqueue(MockResponse().setBody(JSONArray().put(notification("new-2")).put(notification("new-1")).toString()))
        server.enqueue(MockResponse().setBody(JSONArray().toString()))

        val initial = service.notifications(query, null)
        val firstNewer = service.fetchNewer(query, initial.checkpoint!!)
        val secondNewer = service.fetchNewer(query, firstNewer.checkpoint!!)

        assertTrue(firstNewer.newerCursor != null)
        assertTrue(firstNewer.reachedBoundary.not())
        assertTrue(secondNewer.reachedBoundary)
        val initialBody = JSONObject(server.takeRequest().body.readUtf8())
        assertEquals(2, initialBody.getInt("limit"))
        val newerBody = JSONObject(server.takeRequest().body.readUtf8())
        assertEquals("base-2", newerBody.getString("sinceId"))
        val nextBody = JSONObject(server.takeRequest().body.readUtf8())
        assertEquals("new-2", nextBody.getString("sinceId"))
        assertEquals("new-1", nextBody.getString("untilId"))
    }

    @Test
    fun olderEmptyPageIsAClosedBoundaryAndEmptyCategoriesDoNotRequest() = runBlocking {
        val service = service()
        val query = NotificationQuery(limit = 2)
        server.enqueue(MockResponse().setBody(JSONArray().put(notification("base")).toString()))
        server.enqueue(MockResponse().setBody(JSONArray().toString()))

        val initial = service.notifications(query, null)
        val terminal = service.fetchOlder(query, initial.checkpoint!!)
        assertNull(terminal.olderCursor)
        assertTrue(terminal.reachedBoundary)

        val requestCount = server.requestCount
        val empty = service.notifications(NotificationQuery(emptySet()), null)
        assertTrue(empty.items.isEmpty())
        assertEquals(requestCount, server.requestCount)
    }

    @Test
    fun cursorOwnershipIncludesAccountQueryAndDirection() = runBlocking {
        val account = account()
        val query = NotificationQuery(limit = 2)
        val valid = MisskeyNotificationCursorCodec.encode(
            account,
            query,
            MisskeyNotificationCursorDirection.Older,
            "opaque-id",
        )
        val foreign = MisskeyNotificationCursorCodec.encode(
            account.copy(localId = "other"),
            query,
            MisskeyNotificationCursorDirection.Older,
            "opaque-id",
        )
        val wrongDirection = MisskeyNotificationCursorCodec.encode(
            account,
            query,
            MisskeyNotificationCursorDirection.Newer,
            "opaque-id",
        )
        val service = service(account)

        assertThrows(SourceError.Unsupported::class.java) {
            runBlocking { service.fetchOlder(query, NotificationCheckpoint(account, query, oldest = foreign)) }
        }
        assertThrows(SourceError.Unsupported::class.java) {
            runBlocking { service.fetchOlder(query, NotificationCheckpoint(account, query, oldest = wrongDirection)) }
        }
        assertThrows(SourceError.Unsupported::class.java) {
            runBlocking { service.fetchOlder(NotificationQuery(limit = 3), NotificationCheckpoint(account, query, oldest = valid)) }
        }
        Unit
    }

    @Test
    fun unreadMappingUsesExactCountAndBooleanFallback() = runBlocking {
        val service = service()
        server.enqueue(MockResponse().setBody(JSONObject().put("notificationCount", 4).toString()))
        server.enqueue(MockResponse().setBody(JSONObject().put("hasUnreadNotification", true).toString()))

        assertEquals(NotificationUnreadState.Exact(4), service.unreadState())
        assertEquals(NotificationUnreadState.Present, service.unreadState())
        assertEquals("/api/i", server.takeRequest().path)
        assertEquals("/api/i", server.takeRequest().path)
    }

    private fun service(account: AccountId = account()) = MisskeyNotificationService(
        origin = account.connection.origin,
        token = "token",
        api = MisskeyApi(),
        accountId = account,
        clock = { 10L },
    )

    private fun account() = AccountId(Connection(server.url("/").toString().removeSuffix("/"), Protocol.MISSKEY), "receiver")

    private fun notification(id: String) = JSONObject()
        .put("id", id)
        .put("type", "mention")
        .put("createdAt", "2026-09-07T10:00:00Z")
        .put("user", JSONObject().put("id", "actor").put("username", "actor").put("name", "Actor").put("host", JSONObject.NULL))
}
