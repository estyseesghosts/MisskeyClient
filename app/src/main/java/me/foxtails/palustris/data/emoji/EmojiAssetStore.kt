package me.foxtails.palustris.data.emoji

import android.content.Context
import androidx.annotation.VisibleForTesting
import java.io.File
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.security.MessageDigest
import java.time.Clock
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

/**
 * Persistent, credential-free, content-addressed storage for custom emoji bytes.
 *
 * Retention has two independent limits. [maxInactiveUrlMappings] bounds the
 * mapping rows, and [maxTotalAssetBytes] bounds the stored bytes. A caller holds
 * an [EmojiAssetLease] while it reads a file. Open leases and in-progress writes
 * are exempt from eviction, so the byte limit can be exceeded temporarily until
 * the last reader releases.
 */
class EmojiAssetStore(
    private val noBackupDirectory: File,
    private val dao: EmojiCacheDao,
    private val client: OkHttpClient,
    private val clock: Clock = Clock.systemUTC(),
    private val maxInactiveUrlMappings: Int = DEFAULT_MAX_INACTIVE_URL_MAPPINGS,
    private val maxTotalAssetBytes: Long = DEFAULT_MAX_TOTAL_ASSET_BYTES,
) {
    private val emojiDirectory = File(noBackupDirectory, "emoji")
    private val assetsDirectory = File(emojiDirectory, "assets")

    // Fixed lock stripes for URL coordination. Different canonical URLs can
    // share a stripe, so they serialize without becoming the same cache
    // identity. The array never grows, so no URL key survives a finished or
    // failed request. Only colliding URLs lose network concurrency.
    private val urlLocks = Array(URL_LOCK_COUNT) { Any() }

    // Guards every metadata write, file move, file deletion, and lease change.
    // The lock order is urlLocks stripe, then retentionLock. Network fetches and
    // byte streaming run outside retentionLock, so no database work spans a
    // network call. Cleanup never takes a stripe lock.
    private val retentionLock = Any()
    private val activeUrls = mutableMapOf<String, Int>()
    private val activeContent = mutableMapOf<String, Int>()

    init {
        assetsDirectory.mkdirs()
        sweepTemporaryFiles()
    }

    /** Resolve [url] and hold its file for the caller. Close the lease when done. */
    suspend fun acquire(url: String): EmojiAssetLease = withContext(Dispatchers.IO) {
        val canonicalUrl = canonicalUrl(url)
        val lock = urlLocks[stripeIndex(canonicalUrl)]
        synchronized(lock) { load(canonicalUrl) }
    }

    private fun load(canonicalUrl: String): EmojiAssetLease {
        val now = clock.millis()
        val cached = synchronized(retentionLock) { cachedLease(canonicalUrl, now) }
        if (cached != null) return cached
        return download(canonicalUrl, now)
    }

    /** Return a lease for a fresh cached file, or null when a download is needed. */
    private fun cachedLease(canonicalUrl: String, now: Long): EmojiAssetLease? {
        val mapping = dao.assetUrl(canonicalUrl) ?: return null
        val existing = existingAsset(mapping) ?: return null
        if (now - mapping.lastCheckedEpochMillis >= MAPPING_FRESHNESS_MILLIS) return null
        return registerLease(canonicalUrl, touch(existing, now))
    }

    private fun download(canonicalUrl: String, now: Long): EmojiAssetLease {
        val state = synchronized(retentionLock) {
            val mapping = dao.assetUrl(canonicalUrl)
            DownloadState(mapping, mapping?.let(::existingAsset))
        }
        val outcome = try {
            fetchAsset(canonicalUrl, state.mapping, state.existing)
        } catch (error: IOException) {
            val existing = state.existing ?: throw error
            return synchronized(retentionLock) { registerLease(canonicalUrl, touch(existing, now)) }
        }
        return synchronized(retentionLock) { publish(canonicalUrl, state, outcome, now) }
    }

    private fun publish(
        canonicalUrl: String,
        state: DownloadState,
        outcome: FetchOutcome,
        now: Long,
    ): EmojiAssetLease = when (outcome) {
        FetchOutcome.NotModified -> {
            val mapping = requireNotNull(state.mapping) { "A 304 needs a stored mapping." }
            val existing = requireNotNull(state.existing) { "A 304 needs a stored asset." }
            dao.insertAssetUrl(mapping.copy(lastCheckedEpochMillis = now))
            registerLease(canonicalUrl, touch(existing, now))
        }
        is FetchOutcome.FreshBytes -> {
            val destination = File(noBackupDirectory, outcome.relativePath)
            destination.parentFile?.mkdirs()
            try {
                moveAtomically(outcome.temporary, destination)
            } finally {
                outcome.temporary.delete()
            }
            val entity = EmojiAssetEntity(
                contentHash = outcome.contentHash,
                relativePath = outcome.relativePath,
                mimeType = outcome.mimeType,
                byteSize = outcome.byteSize,
                lastUsedEpochMillis = now,
            )
            dao.insertAsset(entity)
            dao.insertAssetUrl(
                EmojiAssetUrlEntity(
                    canonicalUrl = canonicalUrl,
                    contentHash = outcome.contentHash,
                    etag = outcome.etag,
                    lastModified = outcome.lastModified,
                    lastCheckedEpochMillis = now,
                ),
            )
            // Register the lease before retention runs so the new content and
            // its mapping are exempt from the eviction pass that follows.
            val lease = registerLease(canonicalUrl, StoredAsset(entity, destination))
            enforceRetention()
            lease
        }
    }

    private fun fetchAsset(
        canonicalUrl: String,
        mapping: EmojiAssetUrlEntity?,
        existing: StoredAsset?,
    ): FetchOutcome {
        fetch(canonicalUrl, mapping).use { response ->
            if (response.code == HTTP_NOT_MODIFIED && mapping != null && existing != null) {
                return FetchOutcome.NotModified
            }
            if (!response.isSuccessful) {
                throw AssetDownloadException("Emoji asset request returned HTTP ${response.code}")
            }
            return writeFreshBytes(response)
        }
    }

    private fun writeFreshBytes(response: Response): FetchOutcome.FreshBytes {
        val body = response.body ?: throw AssetDownloadException("Emoji asset response had no body")
        if (body.contentLength() > MAX_ASSET_BYTES) {
            throw AssetDownloadException("Emoji asset exceeds the size limit")
        }
        val temporary = File(assetsDirectory, ".tmp-${UUID.randomUUID()}")
        var byteSize = 0L
        val digest = MessageDigest.getInstance("SHA-256")
        try {
            body.byteStream().use { input ->
                temporary.outputStream().use { output ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        byteSize += count
                        if (byteSize > MAX_ASSET_BYTES) {
                            throw AssetDownloadException("Emoji asset exceeds the size limit")
                        }
                        digest.update(buffer, 0, count)
                        output.write(buffer, 0, count)
                    }
                }
            }
            if (byteSize == 0L) throw AssetDownloadException("Emoji asset response was empty")
            val contentHash = digest.digest().hex()
            return FetchOutcome.FreshBytes(
                temporary = temporary,
                contentHash = contentHash,
                relativePath = "emoji/assets/${contentHash.take(2)}/$contentHash",
                mimeType = response.header("Content-Type")?.substringBefore(';')?.trim(),
                byteSize = byteSize,
                etag = response.header("ETag"),
                lastModified = response.header("Last-Modified"),
            )
        } catch (error: Throwable) {
            temporary.delete()
            throw error
        }
    }

    private fun fetch(canonicalUrl: String, mapping: EmojiAssetUrlEntity?): Response {
        var current = canonicalUrl.toHttpUrl()
        repeat(MAX_REDIRECTS + 1) { redirectIndex ->
            val request = Request.Builder()
                .url(current)
                .apply {
                    if (redirectIndex == 0) {
                        mapping?.etag?.let { header("If-None-Match", it) }
                        mapping?.lastModified?.let { header("If-Modified-Since", it) }
                    }
                }
                .build()
            val response = client.newCall(request).execute()
            if (response.code !in REDIRECT_CODES || response.code == HTTP_NOT_MODIFIED) return response
            val location = response.header("Location")
            response.close()
            val destination = location?.let(current::resolve)
                ?: throw AssetDownloadException("Emoji asset redirect had no destination")
            try {
                validateHttps(destination)
            } catch (error: IllegalArgumentException) {
                throw AssetDownloadException(error.message ?: "Emoji asset redirect was invalid")
            }
            current = destination
            if (redirectIndex == MAX_REDIRECTS) {
                throw AssetDownloadException("Emoji asset redirect limit exceeded")
            }
        }
        error("unreachable")
    }

    private fun existingAsset(mapping: EmojiAssetUrlEntity): StoredAsset? {
        val entity = dao.asset(mapping.contentHash) ?: return null
        val file = File(noBackupDirectory, entity.relativePath)
        return file.takeIf(File::isFile)?.let { StoredAsset(entity, it) }
    }

    private fun touch(asset: StoredAsset, now: Long): StoredAsset {
        val updated = asset.entity.copy(lastUsedEpochMillis = now)
        dao.insertAsset(updated)
        return StoredAsset(updated, asset.file)
    }

    private fun registerLease(canonicalUrl: String, asset: StoredAsset): EmojiAssetLease {
        val contentHash = asset.entity.contentHash
        activeUrls[canonicalUrl] = (activeUrls[canonicalUrl] ?: 0) + 1
        activeContent[contentHash] = (activeContent[contentHash] ?: 0) + 1
        return EmojiAssetLease(file = asset.file, mimeType = asset.entity.mimeType) {
            releaseLease(canonicalUrl, contentHash)
        }
    }

    private fun releaseLease(canonicalUrl: String, contentHash: String) {
        synchronized(retentionLock) {
            decrement(activeUrls, canonicalUrl)
            decrement(activeContent, contentHash)
            enforceRetention()
        }
    }

    private fun decrement(counts: MutableMap<String, Int>, key: String) {
        val remaining = (counts[key] ?: 1) - 1
        if (remaining <= 0) counts.remove(key) else counts[key] = remaining
    }

    private fun enforceRetention() {
        pruneInactiveMappings()
        pruneInactiveContent()
    }

    /** Remove the least recently used inactive mappings above the count limit. */
    private fun pruneInactiveMappings() {
        var overflow = dao.assetUrlCount() - maxInactiveUrlMappings
        if (overflow <= 0) return
        for (mapping in dao.assetUrlsByLastUsed()) {
            if (overflow <= 0) return
            if (activeUrls.containsKey(mapping.canonicalUrl)) continue
            dao.deleteAssetUrl(mapping.canonicalUrl)
            overflow--
        }
    }

    /** Remove inactive content toward the byte budget, evicting mappings if needed. */
    private fun pruneInactiveContent() {
        var total = dao.assetBytes()
        if (total <= maxTotalAssetBytes) return
        total = deleteUnreferenced(total)
        if (total <= maxTotalAssetBytes) return
        // All remaining content is referenced. Evict the least recently used
        // inactive mapping, then drop its content once it has no other mapping.
        for (mapping in dao.assetUrlsByLastUsed()) {
            if (total <= maxTotalAssetBytes) return
            if (activeUrls.containsKey(mapping.canonicalUrl)) continue
            dao.deleteAssetUrl(mapping.canonicalUrl)
            if (dao.assetUrlCountForHash(mapping.contentHash) > 0) continue
            val asset = dao.asset(mapping.contentHash) ?: continue
            if (activeContent.containsKey(asset.contentHash)) continue
            if (deleteStoredAsset(asset)) total -= asset.byteSize
        }
    }

    private fun deleteUnreferenced(total: Long): Long {
        var remaining = total
        for (asset in dao.unreferencedAssets()) {
            if (remaining <= maxTotalAssetBytes) return remaining
            if (activeContent.containsKey(asset.contentHash)) continue
            if (deleteStoredAsset(asset)) remaining -= asset.byteSize
        }
        return remaining
    }

    /**
     * Delete one unused asset. A missing file still removes the row. A failed
     * file deletion keeps the row so a later cleanup retries it.
     */
    private fun deleteStoredAsset(asset: EmojiAssetEntity): Boolean {
        val file = File(noBackupDirectory, asset.relativePath)
        if (file.exists() && !file.delete()) return false
        dao.deleteAsset(asset.contentHash)
        return true
    }

    private fun sweepTemporaryFiles() {
        assetsDirectory.listFiles()
            .orEmpty()
            .filter { it.isFile && it.name.startsWith(".tmp-") }
            .forEach(File::delete)
    }

    private fun moveAtomically(temporary: File, destination: File) {
        try {
            Files.move(temporary.toPath(), destination.toPath(), ATOMIC_MOVE)
        } catch (_: FileAlreadyExistsException) {
            temporary.delete()
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(temporary.toPath(), destination.toPath(), REPLACE_EXISTING)
        }
    }

    private sealed interface FetchOutcome {
        data object NotModified : FetchOutcome

        class FreshBytes(
            val temporary: File,
            val contentHash: String,
            val relativePath: String,
            val mimeType: String?,
            val byteSize: Long,
            val etag: String?,
            val lastModified: String?,
        ) : FetchOutcome
    }

    private data class DownloadState(
        val mapping: EmojiAssetUrlEntity?,
        val existing: StoredAsset?,
    )

    companion object {
        private const val HTTP_NOT_MODIFIED = 304
        private const val MAX_ASSET_BYTES = 10L * 1024L * 1024L
        private const val MAPPING_FRESHNESS_MILLIS = 7L * 24L * 60L * 60L * 1000L
        private const val MAX_REDIRECTS = 5
        private const val BUFFER_SIZE = 16 * 1024
        private const val URL_LOCK_COUNT = 64
        private const val URL_LOCK_MASK = URL_LOCK_COUNT - 1
        internal const val DEFAULT_MAX_INACTIVE_URL_MAPPINGS = 4_096
        internal const val DEFAULT_MAX_TOTAL_ASSET_BYTES = 128L * 1024L * 1024L
        private val REDIRECT_CODES = 300..399
        @Volatile private var instance: EmojiAssetStore? = null

        fun get(context: Context): EmojiAssetStore = instance ?: synchronized(this) {
            instance ?: EmojiAssetStore(
                noBackupDirectory = context.applicationContext.noBackupFilesDir,
                dao = EmojiCacheDatabase.get(context).emojiCacheDao(),
                client = OkHttpClient.Builder()
                    .followRedirects(false)
                    .followSslRedirects(false)
                    .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
                    .writeTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
                    .callTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                    .build(),
            ).also { instance = it }
        }

        // The mask keeps the index nonnegative for every hash, including
        // Int.MIN_VALUE, where abs() would stay negative.
        internal fun stripeIndex(canonicalUrl: String): Int = canonicalUrl.hashCode() and URL_LOCK_MASK

        @VisibleForTesting
        internal fun stripeCount(): Int = URL_LOCK_COUNT

        fun canonicalUrl(url: String): String {
            val parsed = url.toHttpUrl()
            validateHttps(parsed)
            return parsed.newBuilder()
                .username("")
                .password("")
                .fragment(null)
                .build()
                .toString()
        }

        private fun validateHttps(url: HttpUrl) {
            require(url.scheme == "https") { "Emoji asset URL must use HTTPS" }
            require(url.username.isEmpty() && url.password.isEmpty()) {
                "Emoji asset URL must not contain credentials"
            }
        }
    }

    private data class StoredAsset(
        val entity: EmojiAssetEntity,
        val file: File,
    )

    private class AssetDownloadException(message: String) : IOException(message)
}

private fun ByteArray.hex(): String {
    val result = StringBuilder(size * 2)
    forEach { byte -> result.append("%02x".format(byte)) }
    return result.toString()
}
