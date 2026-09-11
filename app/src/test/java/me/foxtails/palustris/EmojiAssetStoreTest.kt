package me.foxtails.palustris

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import me.foxtails.palustris.data.emoji.EmojiAssetStore
import me.foxtails.palustris.data.emoji.EmojiCacheDatabase
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import kotlinx.coroutines.runBlocking
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
class EmojiAssetStoreTest {
    private lateinit var database: EmojiCacheDatabase
    private lateinit var noBackupDirectory: File

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            EmojiCacheDatabase::class.java,
        ).allowMainThreadQueries().build()
        noBackupDirectory = Files.createTempDirectory("emoji-assets").toFile()
    }

    @After
    fun tearDown() {
        database.close()
        noBackupDirectory.deleteRecursively()
    }

    @Test
    fun identicalBytesAcrossUrlsShareOnePhysicalAsset() = runBlocking {
        val store = store { request -> response(request, 200, "same-bytes".toByteArray()) }

        val first = store.get("https://cdn.example/first.png")
        val second = store.get("https://cdn.example/second.png")

        assertEquals(first.file, second.file)
        assertEquals(1, database.emojiCacheDao().assets().size)
        assertEquals("same-bytes".length.toLong(), database.emojiCacheDao().assets().sumOf { it.byteSize })
    }

    @Test
    fun staleMappingUsesConditionalRequestAnd304KeepsTheAsset() = runBlocking {
        val clock = MutableClock(0L)
        var calls = 0
        val store = store(clock) { request ->
            calls++
            if (calls == 1) {
                response(request, 200, "cached".toByteArray(), "ETag" to "v1")
            } else {
                assertEquals("v1", request.header("If-None-Match"))
                response(request, 304, byteArrayOf())
            }
        }

        val first = store.get("https://cdn.example/blob.png")
        clock.nowMillis = 8L * 24L * 60L * 60L * 1000L
        val second = store.get("https://cdn.example/blob.png")

        assertEquals(first.file, second.file)
        assertEquals(2, calls)
        assertEquals(clock.millis(), database.emojiCacheDao().assetUrl("https://cdn.example/blob.png")?.lastCheckedEpochMillis)
    }

    @Test
    fun failedRevalidationFallsBackToExistingAsset() = runBlocking {
        val clock = MutableClock(0L)
        var calls = 0
        val store = store(clock) { request ->
            calls++
            if (calls == 1) response(request, 200, "cached".toByteArray())
            else throw IOException("offline")
        }

        val first = store.get("https://cdn.example/blob.png")
        clock.nowMillis = 8L * 24L * 60L * 60L * 1000L
        val second = store.get("https://cdn.example/blob.png")

        assertEquals(first.file, second.file)
        assertEquals(2, calls)
        assertTrue(second.file.isFile)
    }

    @Test
    fun nonHttpsUrlsAreRejectedBeforeNetworkAccess() {
        val store = store { request -> response(request, 200, byteArrayOf(1)) }

        var rejected = false
        try {
            runBlocking { store.get("http://cdn.example/blob.png") }
        } catch (_: IllegalArgumentException) {
            rejected = true
        }

        assertTrue(rejected)
    }

    private fun store(
        clock: Clock = Clock.fixed(Instant.ofEpochMilli(0L), ZoneId.of("UTC")),
        responder: (Request) -> Response,
    ): EmojiAssetStore = EmojiAssetStore(
        noBackupDirectory = noBackupDirectory,
        dao = database.emojiCacheDao(),
        client = OkHttpClient.Builder()
            .addInterceptor { chain -> responder(chain.request()) }
            .build(),
        clock = clock,
    )

    private fun response(
        request: Request,
        code: Int,
        bytes: ByteArray,
        header: Pair<String, String>? = null,
    ): Response = Response.Builder()
        .request(request)
        .protocol(Protocol.HTTP_1_1)
        .code(code)
        .message("test")
        .apply { header?.let { addHeader(it.first, it.second) } }
        .body(bytes.toResponseBody("image/png".toMediaType()))
        .build()

    private class MutableClock(var nowMillis: Long) : Clock() {
        override fun getZone(): ZoneId = ZoneId.of("UTC")

        override fun withZone(zone: ZoneId): Clock = this

        override fun instant(): Instant = Instant.ofEpochMilli(nowMillis)
    }
}
