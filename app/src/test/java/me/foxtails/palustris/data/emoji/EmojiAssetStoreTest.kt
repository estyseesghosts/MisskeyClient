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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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

        val first = store.acquire("https://cdn.example/first.png")
        val second = store.acquire("https://cdn.example/second.png")

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

        val first = store.acquire("https://cdn.example/blob.png")
        clock.nowMillis = 8L * 24L * 60L * 60L * 1000L
        val second = store.acquire("https://cdn.example/blob.png")

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

        val first = store.acquire("https://cdn.example/blob.png")
        clock.nowMillis = 8L * 24L * 60L * 60L * 1000L
        val second = store.acquire("https://cdn.example/blob.png")

        assertEquals(first.file, second.file)
        assertEquals(2, calls)
        assertTrue(second.file.isFile)
    }

    @Test
    fun nonHttpsUrlsAreRejectedBeforeNetworkAccess() {
        val store = store { request -> response(request, 200, byteArrayOf(1)) }

        var rejected = false
        try {
            runBlocking { store.acquire("http://cdn.example/blob.png") }
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
                store.acquire("https://cdn.example/shared.png")
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

        val first = async(Dispatchers.IO) { store.acquire(firstUrl) }
        assertTrue(firstStarted.await(10, TimeUnit.SECONDS))
        val second = async(Dispatchers.IO) { store.acquire(secondUrl) }
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

        val first = async(Dispatchers.IO) { store.acquire(firstUrl) }
        assertTrue(firstStarted.await(10, TimeUnit.SECONDS))
        val second = async(Dispatchers.IO) { store.acquire(secondUrl) }

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
            store.acquire("https://cdn.example/retry.png")
        } catch (_: IOException) {
            failed = true
        }
        assertTrue(failed)

        val recovered = store.acquire("https://cdn.example/retry.png")
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
            runCatching { store.acquire("https://cdn.example/cancel.png") }
        }
        assertTrue(started.await(10, TimeUnit.SECONDS))
        pending.cancel()
        release.countDown()
        pending.join()

        val recovered = withTimeout(10_000) { store.acquire("https://cdn.example/cancel.png") }
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

    @Test
    fun mappingCountIsBoundedAndEvictsLeastRecentlyUsed() = runBlocking {
        val clock = MutableClock(0L)
        val store = store(clock, maxInactiveUrlMappings = 2) { request ->
            response(request, 200, request.url.toString().toByteArray())
        }

        store.acquire("https://cdn.example/one.png").close()
        clock.nowMillis = 10
        store.acquire("https://cdn.example/two.png").close()
        clock.nowMillis = 20
        store.acquire("https://cdn.example/three.png").close()

        val dao = database.emojiCacheDao()
        assertEquals(2, dao.assetUrlCount())
        assertNull(dao.assetUrl("https://cdn.example/one.png"))

        clock.nowMillis = 30
        store.acquire("https://cdn.example/two.png").close()
        clock.nowMillis = 40
        store.acquire("https://cdn.example/four.png").close()

        assertEquals(2, dao.assetUrlCount())
        assertNull(dao.assetUrl("https://cdn.example/three.png"))
        assertNotNull(dao.assetUrl("https://cdn.example/two.png"))
        assertNotNull(dao.assetUrl("https://cdn.example/four.png"))
    }

    @Test
    fun byteBudgetEvictsInactiveMappingsAndContent() = runBlocking {
        val clock = MutableClock(0L)
        var counter = 0
        val store = store(clock, maxInactiveUrlMappings = 100, maxTotalAssetBytes = 25) { request ->
            response(request, 200, ByteArray(10) { counter++.toByte() })
        }

        store.acquire("https://cdn.example/a.png").close()
        clock.nowMillis = 10
        store.acquire("https://cdn.example/b.png").close()
        clock.nowMillis = 20
        store.acquire("https://cdn.example/c.png").close()

        val dao = database.emojiCacheDao()
        assertEquals(20, dao.assetBytes())
        assertEquals(2, dao.assetUrlCount())
        assertNull(dao.assetUrl("https://cdn.example/a.png"))
        assertNotNull(dao.assetUrl("https://cdn.example/c.png"))
    }

    @Test
    fun sharedContentSurvivesMappingEviction() = runBlocking {
        val store = store(maxInactiveUrlMappings = 1) { request ->
            response(request, 200, "shared".toByteArray())
        }

        store.acquire("https://cdn.example/a.png").close()
        store.acquire("https://cdn.example/b.png").close()

        val dao = database.emojiCacheDao()
        assertEquals(1, dao.assetUrlCount())
        assertEquals(1, dao.assets().size)
        assertNull(dao.assetUrl("https://cdn.example/a.png"))
        assertNotNull(dao.assetUrl("https://cdn.example/b.png"))
    }

    @Test
    fun openLeaseExemptsContentFromEvictionAndPrunesAfterRelease() = runBlocking {
        val clock = MutableClock(0L)
        var counter = 0
        val store = store(clock, maxInactiveUrlMappings = 100, maxTotalAssetBytes = 15) { request ->
            response(request, 200, ByteArray(10) { counter++.toByte() })
        }

        val first = store.acquire("https://cdn.example/held.png")
        clock.nowMillis = 10
        val second = store.acquire("https://cdn.example/other.png")

        val dao = database.emojiCacheDao()
        assertTrue(first.file.isFile)
        assertEquals(20, dao.assetBytes())

        first.close()
        assertFalse(first.file.exists())
        assertEquals(10, dao.assetBytes())
        second.close()
    }

    @Test
    fun missingFileIsDownloadedAgain() = runBlocking {
        val calls = AtomicInteger(0)
        val store = store { request ->
            calls.incrementAndGet()
            response(request, 200, "content".toByteArray())
        }

        val lease = store.acquire("https://cdn.example/missing.png")
        val path = lease.file
        lease.close()
        assertTrue(path.delete())

        val restored = store.acquire("https://cdn.example/missing.png")
        assertEquals(2, calls.get())
        assertTrue(restored.file.isFile)
        restored.close()
    }

    @Test
    fun failedFileDeletionKeepsTheRowForRetry() = runBlocking {
        val clock = MutableClock(0L)
        var counter = 0
        val store = store(clock, maxInactiveUrlMappings = 100, maxTotalAssetBytes = 15) { request ->
            response(request, 200, ByteArray(10) { counter++.toByte() })
        }

        val dao = database.emojiCacheDao()
        val first = store.acquire("https://cdn.example/first.png")
        val firstHash = dao.assetUrl("https://cdn.example/first.png")!!.contentHash
        val firstFile = first.file
        first.close()

        // A non-empty directory at the asset path makes file deletion fail.
        assertTrue(firstFile.delete())
        assertTrue(firstFile.mkdirs())
        File(firstFile, "child").writeText("x")

        clock.nowMillis = 10
        val second = store.acquire("https://cdn.example/second.png")
        assertTrue(firstFile.exists())
        assertNotNull(dao.asset(firstHash))

        File(firstFile, "child").delete()
        firstFile.delete()
        second.close()
        assertNull(dao.asset(firstHash))
        assertEquals(10, dao.assetBytes())
    }

    @Test
    fun restartSweepsTemporaryFiles() {
        val assets = File(noBackupDirectory, "emoji/assets").apply { mkdirs() }
        val temporary = File(assets, ".tmp-leftover").apply { writeText("partial") }

        store { request -> response(request, 200, byteArrayOf(1)) }

        assertFalse(temporary.exists())
    }

    @Test
    fun leaseCloseIsIdempotent() = runBlocking {
        val store = store { request -> response(request, 200, "content".toByteArray()) }

        val lease = store.acquire("https://cdn.example/idempotent.png")
        lease.close()
        lease.close()

        assertTrue(lease.file.isFile)
        assertEquals(1, database.emojiCacheDao().assetUrlCount())
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
        maxInactiveUrlMappings: Int = EmojiAssetStore.DEFAULT_MAX_INACTIVE_URL_MAPPINGS,
        maxTotalAssetBytes: Long = EmojiAssetStore.DEFAULT_MAX_TOTAL_ASSET_BYTES,
        responder: (Request) -> Response,
    ): EmojiAssetStore = EmojiAssetStore(
        noBackupDirectory = noBackupDirectory,
        dao = database.emojiCacheDao(),
        client = OkHttpClient.Builder()
            .addInterceptor { chain -> responder(chain.request()) }
            .build(),
        clock = clock,
        maxInactiveUrlMappings = maxInactiveUrlMappings,
        maxTotalAssetBytes = maxTotalAssetBytes,
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
