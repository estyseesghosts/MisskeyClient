package me.foxtails.palustris

import kotlinx.coroutines.runBlocking
import me.foxtails.palustris.data.mastodon.MastodonSource
import me.foxtails.palustris.data.misskey.MisskeyApi
import me.foxtails.palustris.data.notifications.InMemoryNotificationStore
import me.foxtails.palustris.data.notifications.NotificationRepository
import me.foxtails.palustris.data.notifications.NotificationSynchronizer
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.NotificationSyncToken
import me.foxtails.palustris.domain.Protocol
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MastodonNotificationSyncTest {
    private lateinit var server: MockWebServer
    private val paths = mutableListOf<String>()
    private var unreadCountRequests = 0

    @Before
    fun startServer() {
        unreadCountRequests = 0
        server = MockWebServer().also { it.start() }
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                paths += path
                return when {
                    path == "/api/v1/notifications" -> MockResponse()
                        .setBody(JSONArray().put(notification("baseline")).toString())
                    path == "/api/v1/notifications/unread_count" -> MockResponse()
                        .setBody(JSONObject().put("count", if (unreadCountRequests++ == 0) 0 else 2).toString())
                    path == "/api/v1/notifications?min_id=baseline" -> page(
                        "page-3",
                        "min_id=page-3",
                    )
                    path == "/api/v1/notifications?min_id=page-3" -> page(
                        "page-2",
                        "min_id=page-2",
                    )
                    path == "/api/v1/notifications?min_id=page-2" -> MockResponse()
                        .setBody(JSONArray().put(notification("page-1")).toString())
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
    }

    @After
    fun stopServer() {
        server.shutdown()
    }

    @Test
    fun newerCatchUpFollowsMastodonContinuationAcrossMoreThanTwoPages() = runBlocking {
        val origin = server.url("/").toString().removeSuffix("/")
        val account = AccountId(Connection(origin, Protocol.MASTODON), "receiver")
        val token = NotificationSyncToken(account, 1)
        val repository = NotificationRepository(InMemoryNotificationStore())
        repository.activate(token)
        val source = MastodonSource(origin, "token", MisskeyApi(), account)
        val synchronizer = NotificationSynchronizer(repository)

        synchronizer.establishBaseline(source, token)
        val result = synchronizer.catchUpNewer(source, token, pageBudget = 4)

        assertTrue(result.complete)
        assertEquals(listOf("page-3", "page-2", "page-1", "baseline"), repository.observe(account).value.items.map { it.id.value })
        assertEquals(
            listOf(
                "/api/v1/notifications",
                "/api/v1/notifications/unread_count",
                "/api/v1/notifications?min_id=baseline",
                "/api/v1/notifications?min_id=page-3",
                "/api/v1/notifications?min_id=page-2",
                "/api/v1/notifications/unread_count",
            ),
            paths,
        )
        assertEquals(me.foxtails.palustris.domain.NotificationUnreadState.AtLeast(2), result.unreadState)
        assertEquals(result.unreadState, repository.observe(account).value.unreadState)
    }

    @Test
    fun olderHistoryClearsMastodonTerminalContinuation() = runBlocking {
        val origin = server.url("/").toString().removeSuffix("/")
        paths.clear()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                paths += path
                return when {
                    path == "/api/v1/notifications" -> MockResponse()
                        .setBody(JSONArray().put(notification("baseline")).toString())
                        .addHeader("Link", "<$origin/api/v1/notifications?max_id=baseline>; rel=\"next\"")
                    path == "/api/v1/notifications/unread_count" -> MockResponse()
                        .setBody(JSONObject().put("count", 0).toString())
                    path == "/api/v1/notifications?max_id=baseline" -> pageWithNext(
                        "older-2",
                        "max_id=older-2",
                    )
                    path == "/api/v1/notifications?max_id=older-2" -> MockResponse()
                        .setBody(JSONArray().put(notification("older-1")).toString())
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        val account = AccountId(Connection(origin, Protocol.MASTODON), "receiver")
        val token = NotificationSyncToken(account, 1)
        val repository = NotificationRepository(InMemoryNotificationStore())
        repository.activate(token)
        val source = MastodonSource(origin, "token", MisskeyApi(), account)
        val synchronizer = NotificationSynchronizer(repository)

        synchronizer.establishBaseline(source, token)
        synchronizer.loadOlder(source, token)
        val result = synchronizer.loadOlder(source, token)

        assertTrue(result.complete)
        assertEquals(null, repository.checkpoint(account, me.foxtails.palustris.domain.NotificationQuery())?.oldest)
        assertEquals(
            listOf("older-2", "older-1", "baseline"),
            repository.observe(account).value.items.map { it.id.value },
        )
        assertEquals(
            listOf(
                "/api/v1/notifications",
                "/api/v1/notifications/unread_count",
                "/api/v1/notifications?max_id=baseline",
                "/api/v1/notifications?max_id=older-2",
            ),
            paths,
        )
    }

    private fun page(id: String, continuation: String): MockResponse {
        val origin = server.url("/").toString().removeSuffix("/")
        return MockResponse()
            .setBody(JSONArray().put(notification(id)).toString())
            .addHeader("Link", "<$origin/api/v1/notifications?$continuation>; rel=\"prev\"")
    }

    private fun pageWithNext(id: String, continuation: String): MockResponse {
        val origin = server.url("/").toString().removeSuffix("/")
        return MockResponse()
            .setBody(JSONArray().put(notification(id)).toString())
            .addHeader("Link", "<$origin/api/v1/notifications?$continuation>; rel=\"next\"")
    }

    private fun notification(id: String) = JSONObject()
        .put("id", id)
        .put("type", "mention")
        .put("created_at", "2026-09-07T10:00:00Z")
}
