package me.foxtails.palustris

import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import me.foxtails.palustris.data.misskey.MisskeySource
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.Protocol
import me.foxtails.palustris.data.misskey.MisskeyApi
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class WebSocketTransportTest {
    @Test
    fun websocketRequestKeepsHttpsForOkHttpUpgrade() {
        val client = CapturingWebSocketClient()
        MisskeyApi(client).webSocket(
            origin = "https://example.org",
            path = "/streaming",
            headers = mapOf("Authorization" to "Bearer test-token"),
                listener = object : WebSocketListener() {},
        )

        val request = client.request ?: error("WebSocket request was not captured")
        assertNotNull(request)
        assertEquals("https", request.url.scheme)
        assertEquals("/streaming", request.url.encodedPath)
        assertEquals("Bearer test-token", request.header("Authorization"))
    }

    @Test
    fun misskeyStreamAuthenticatesDuringWebSocketUpgrade() = runBlocking {
        val client = CapturingWebSocketClient()
        val origin = "https://example.org"
        val account = AccountId(Connection(origin, Protocol.MISSKEY), "receiver")
        val source = MisskeySource(
            origin = origin,
            token = "test-token",
            api = MisskeyApi(client),
            accountId = account,
        )
        val collector = launch { source.streamEvents().collect() }
        withTimeout(1_000) {
            while (client.request == null) yield()
        }
        collector.cancelAndJoin()

        assertEquals("Bearer test-token", client.request?.header("Authorization"))
    }

    private class CapturingWebSocketClient : OkHttpClient() {
        var request: Request? = null

        override fun newWebSocket(request: Request, listener: WebSocketListener): WebSocket {
            this.request = request
            return NoOpWebSocket(request)
        }
    }

    private class NoOpWebSocket(
        private val requestValue: Request,
    ) : WebSocket {
        override fun request(): Request = requestValue
        override fun queueSize(): Long = 0
        override fun send(text: String): Boolean = true
        override fun send(bytes: ByteString): Boolean = true
        override fun close(code: Int, reason: String?): Boolean = true
        override fun cancel() = Unit
    }
}
