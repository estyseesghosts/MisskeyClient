package me.foxtails.palustris

import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.runBlocking
import me.foxtails.palustris.data.directmessages.DirectMessageRepository
import me.foxtails.palustris.data.directmessages.InMemoryDirectMessageStore
import me.foxtails.palustris.data.mastodon.MastodonSource
import me.foxtails.palustris.data.misskey.MisskeyApi
import me.foxtails.palustris.data.misskey.MisskeySource
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.ConversationId
import me.foxtails.palustris.domain.DirectMessageRequest
import me.foxtails.palustris.domain.EntityId
import me.foxtails.palustris.domain.Protocol
import me.foxtails.palustris.domain.ServerCapabilities
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DirectMessageSourceTest {
    @Test
    fun misskeySendsSpecifiedRecipientsAndReply() = runBlocking {
        MockWebServer().use { server ->
            val origin = server.url("/").toString().removeSuffix("/")
            server.enqueue(
                MockResponse().setBody(
                    JSONObject().put("createdNote", JSONObject(createdMisskeyNote("sent"))).toString(),
                ),
            )
            val owner = AccountId(Connection(origin, Protocol.MISSKEY), "owner")
            val source = MisskeySource(origin, "token", MisskeyApi(), accountId = owner)

            source.sendDirectMessage(
                DirectMessageRequest(
                    recipients = listOf(AccountId(Connection(origin, Protocol.MISSKEY), "recipient")),
                    text = "Hello",
                    replyTo = EntityId(origin, "parent"),
                ),
            )

            val body = JSONObject(server.takeRequest().body.readUtf8())
            assertEquals("specified", body.getString("visibility"))
            assertEquals(listOf("recipient"), body.getJSONArray("visibleUserIds").let { values ->
                (0 until values.length()).map(values::getString)
            })
            assertEquals("parent", body.getString("replyId"))
            assertFalse(body.has("chatId"))
        }
    }

    @Test
    fun mastodonSendsDirectStatusWithResolvedRecipientMention() = runBlocking {
        MockWebServer().use { server ->
            val origin = server.url("/").toString().removeSuffix("/")
            server.enqueue(MockResponse().setBody(mastodonAccount("remote", "alice", "alice@example.org").toString()))
            server.enqueue(MockResponse().setBody(mastodonStatus("sent", "direct", "Hello").toString()))
            val owner = AccountId(Connection(origin, Protocol.MASTODON), "owner")
            val recipient = AccountId(Connection(origin, Protocol.MASTODON), "remote")
            val source = MastodonSource(
                origin = origin,
                token = "token",
                api = MisskeyApi(),
                accountId = owner,
                initialCapabilities = ServerCapabilities(),
            )

            source.sendDirectMessage(DirectMessageRequest(listOf(recipient), "Hello"))

            assertEquals("/api/v1/accounts/remote", server.takeRequest().path)
            val fields = URLDecoder.decode(server.takeRequest().body.readUtf8(), StandardCharsets.UTF_8.name())
            assertTrue(fields.contains("status=@alice@example.org Hello"))
            assertTrue(fields.contains("visibility=direct"))
        }
    }

    @Test
    fun mastodonConversationUsesLastStatusAndStatusContext() = runBlocking {
        MockWebServer().use { server ->
            val origin = server.url("/").toString().removeSuffix("/")
            val conversation = JSONObject()
                .put("id", "conversation")
                .put("unread", true)
                .put("accounts", org.json.JSONArray().put(mastodonAccount("remote", "alice", "alice@example.org")))
                .put("last_status", mastodonStatus("last", "direct", "Latest"))
            val context = JSONObject()
                .put("ancestors", org.json.JSONArray().put(mastodonStatus("first", "direct", "First")))
                .put("descendants", org.json.JSONArray().put(mastodonStatus("reply", "direct", "Reply")))
            server.enqueue(MockResponse().setBody(org.json.JSONArray().put(conversation).toString()))
            server.enqueue(MockResponse().setBody(context.toString()))
            val source = MastodonSource(
                origin,
                "token",
                MisskeyApi(),
                AccountId(Connection(origin, Protocol.MASTODON), "owner"),
                initialCapabilities = ServerCapabilities(),
            )

            val page = source.conversations()
            val thread = source.conversationThread(ConversationId(origin, "conversation"))

            assertEquals(true, page.items.single().unread)
            assertEquals(listOf("first", "last", "reply"), thread.map { it.id.value })
            assertEquals("/api/v1/conversations?limit=40", server.takeRequest().path)
            assertEquals("/api/v1/statuses/last/context", server.takeRequest().path)
        }
    }

    @Test
    fun misskeySpecifiedNotesBecomeReplyRootedConversations() = runBlocking {
        MockWebServer().use { server ->
            val origin = server.url("/").toString().removeSuffix("/")
            val owner = AccountId(Connection(origin, Protocol.MISSKEY), "owner")
            server.enqueue(MockResponse().setBody("[${createdMisskeyNote("root")}]"))
            server.enqueue(MockResponse().setBody("[${createdMisskeyNote("reply", "root")}]"))
            val source = MisskeySource(origin, "token", MisskeyApi(), accountId = owner)

            val conversation = source.conversations().items.single()

            assertEquals("root", conversation.id.value)
            assertEquals("root", conversation.rootPostId?.value)
            assertEquals("reply", conversation.lastPost.id.value)
            assertEquals("/api/notes/mentions", server.takeRequest().path)
            assertEquals("/api/users/notes", server.takeRequest().path)
        }
    }

    @Test
    fun repositoryKeepsConversationStateSeparateByAccount() = runBlocking {
        val origin = "https://example.org"
        val first = AccountId(Connection(origin, Protocol.MISSKEY), "first")
        val second = AccountId(Connection(origin, Protocol.MISSKEY), "second")
        val store = InMemoryDirectMessageStore()
        val message = Account(first, "First", "@first@example.org")
        val source = object : me.foxtails.palustris.domain.DirectMessageSource {
            override suspend fun conversations(cursor: String?) = me.foxtails.palustris.domain.Page<me.foxtails.palustris.domain.DirectConversation>(emptyList())
            override suspend fun conversationThread(id: ConversationId) = emptyList<me.foxtails.palustris.domain.Post>()
            override suspend fun sendDirectMessage(request: DirectMessageRequest) =
                post(EntityId(origin, "post"), message)
            override suspend fun markConversationRead(id: ConversationId) = Unit
        }
        val repository = DirectMessageRepository(first, source, store)

        repository.send(DirectMessageRequest(listOf(second), "Private"))

        assertEquals(1, store.conversations(first).size)
        assertTrue(store.conversations(second).isEmpty())
    }

    private fun createdMisskeyNote(id: String, replyId: String? = null): String = JSONObject()
        .put("id", id)
        .put("createdAt", if (id == "reply") "2026-09-06T11:00:00Z" else "2026-09-06T10:00:00Z")
        .put("user", JSONObject().put("id", "remote").put("username", "alice").put("host", JSONObject.NULL))
        .put("text", id)
        .put("visibility", "specified")
        .apply { replyId?.let { put("replyId", it) } }
        .toString()

    private fun mastodonAccount(id: String, username: String, acct: String): JSONObject = JSONObject()
        .put("id", id)
        .put("username", username)
        .put("acct", acct)
        .put("display_name", username)
        .put("note", "")

    private fun mastodonStatus(id: String, visibility: String, text: String): JSONObject = JSONObject()
        .put("id", id)
        .put("created_at", "2026-09-06T10:00:00Z")
        .put("account", mastodonAccount("remote", "alice", "alice@example.org"))
        .put("content", "<p>$text</p>")
        .put("visibility", visibility)

    private fun post(id: EntityId, author: Account): me.foxtails.palustris.domain.Post =
        me.foxtails.palustris.domain.Post(id, author, "Private", 0L, me.foxtails.palustris.domain.Audience.Direct)
}
