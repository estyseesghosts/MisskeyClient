package me.foxtails.palustris.data.misskey

import me.foxtails.palustris.domain.Connection
import okhttp3.OkHttpClient
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

data class HttpLayerConfig(
    val connectTimeoutSeconds: Long = 15,
    val readTimeoutSeconds: Long = 30,
    val callTimeoutSeconds: Long = 40,
)

/** Lazily shares one connection pool per server origin and protocol. */
class HttpClientPool(private val config: HttpLayerConfig = HttpLayerConfig()) {
    private val clients = ConcurrentHashMap<Connection, OkHttpClient>()

    fun clientFor(connection: Connection): OkHttpClient = clients.getOrPut(connection) {
        OkHttpClient.Builder()
            .followRedirects(false)
            .followSslRedirects(false)
            .connectTimeout(config.connectTimeoutSeconds, TimeUnit.SECONDS)
            .readTimeout(config.readTimeoutSeconds, TimeUnit.SECONDS)
            .callTimeout(config.callTimeoutSeconds, TimeUnit.SECONDS)
            .build()
    }
}
