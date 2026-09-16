package me.foxtails.palustris.data.emoji

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import me.foxtails.palustris.data.emoji.EmojiAssetStore
import me.foxtails.palustris.data.emoji.EmojiCacheDatabase
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
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

    @Test
    fun concurrentRequestsForSameUrlDownloadOnce() = runBlocking {
        val calls = AtomicInteger(0)
        val downloadStarted = CountDownLatch(1)
        val releaseDownload = CountDownLatch(1)
        val store = store { request ->
            if (calls.incrementAndGet() == 1) {
                downloadStarted.countDown()
                assertTrue(releaseDownload.await(10, TimeUnit.SECONDS))
            }
            response(request, 200, "shared-bytes".toByteArray())
        }

        val barrier = CyclicBarrier(4)
        val requests = (1..4).map {
            async(Dispatchers.IO) {
                barrier.await(10, TimeUnit.SECONDS)
                store.get("https://cdn.example/shared.png")
            }
        }

        assertTrue(downloadStarted.await(10, TimeUnit.SECONDS))
        releaseDownload.countDown()
        val files = requests.awaitAll().map { it.file }.toSet()

        assertEquals(1, files.size)
        assertEquals(1, calls.get())
        assertEquals(1, database.emojiCacheDao().assets().size)
    }

    @Test
    fun collidingUrlsKeepSeparateCacheIdentity() = runBlocking {
        val (firstUrl, secondUrl) = collidingUrls()
        val firstStarted = CountDownLatch(1)
        val releaseFirst = CountDownLatch(1)
        val store = store { request ->
            if (request.url.toString() == firstUrl) {
                firstStarted.countDown()
                assertTrue(releaseFirst.await(10, TimeUnit.SECONDS))
            }
            response(request, 200, request.url.toString().toByteArray())
        }

        val first = async(Dispatchers.IO) { store.get(firstUrl) }
        assertTrue(firstStarted.await(10, TimeUnit.SECONDS))
        val second = async(Dispatchers.IO) { store.get(secondUrl) }
        releaseFirst.countDown()

        assertNotEquals(first.await().file, second.await().file)
        assertEquals(2, database.emojiCacheDao().assets().size)
    }

    @Test
    fun requestsOnDifferentStripesProceedIndependently() = runBlocking {
        val (firstUrl, secondUrl) = differentlyStripedUrls()
        val firstStarted = CountDownLatch(1)
        val releaseFirst = CountDownLatch(1)
        val store = store { request ->
            if (request.url.toString() == firstUrl) {
                firstStarted.countDown()
                assertTrue(releaseFirst.await(10, TimeUnit.SECONDS))
            }
            response(request, 200, request.url.toString().toByteArray())
        }

        val first = async(Dispatchers.IO) { store.get(firstUrl) }
        assertTrue(firstStarted.await(10, TimeUnit.SECONDS))
        val second = async(Dispatchers.IO) { store.get(secondUrl) }

        val secondFile = withTimeout(10_000) { second.await() }
        releaseFirst.countDown()
        first.await()

        assertTrue(secondFile.file.isFile)
    }

    @Test
    fun downloadFailureAllowsLaterRetry() = runBlocking {
        val calls = AtomicInteger(0)
        val store = store { request ->
            if (calls.incrementAndGet() == 1) throw IOException("offline")
            response(request, 200, "recovered".toByteArray())
        }

        var failed = false
        try {
            store.get("https://cdn.example/retry.png")
        } catch (_: IOException) {
            failed = true
        }
        assertTrue(failed)

        val recovered = store.get("https://cdn.example/retry.png")
        assertTrue(recovered.file.isFile)
        assertEquals(2, calls.get())
    }

    @Test
    fun cancelledRequestReleasesItsStripe() = runBlocking {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val store = store { request ->
            started.countDown()
            assertTrue(release.await(10, TimeUnit.SECONDS))
            response(request, 200, "cancelled".toByteArray())
        }

        val pending = launch(Dispatchers.IO) {
            runCatching { store.get("https://cdn.example/cancel.png") }
        }
        assertTrue(started.await(10, TimeUnit.SECONDS))
        pending.cancel()
        release.countDown()
        pending.join()

        val recovered = withTimeout(10_000) { store.get("https://cdn.example/cancel.png") }
        assertTrue(recovered.file.isFile)
    }

    @Test
    fun thousandsOfUniqueUrlsKeepTheCoordinationStructureConstant() {
        val stripes = EmojiAssetStore.stripeCount()
        val used = IntArray(stripes)
        for (index in 0 until 10_000) {
            val stripe = EmojiAssetStore.stripeIndex("https://cdn.example/unique/$index.png")
            assertTrue(stripe in 0 until stripes)
            used[stripe]++
        }

        assertEquals(64, stripes)
        assertEquals(10_000, used.sum())
    }

    private fun collidingUrls(): Pair<String, String> {
        val seen = mutableMapOf<Int, String>()
        var index = 0
        while (true) {
            val url = "https://cdn.example/collide-$index.png"
            val previous = seen.putIfAbsent(EmojiAssetStore.stripeIndex(url), url)
            if (previous != null) return previous to url
            index++
        }
    }

    private fun differentlyStripedUrls(): Pair<String, String> {
        val first = "https://cdn.example/first.png"
        val firstStripe = EmojiAssetStore.stripeIndex(first)
        var index = 0
        while (true) {
            val candidate = "https://cdn.example/second-$index.png"
            if (EmojiAssetStore.stripeIndex(candidate) != firstStripe) return first to candidate
            index++
        }
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
