package me.foxtails.palustris.data.emoji

import android.content.Context
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
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

data class EmojiAssetFile(
    val file: File,
    val mimeType: String?,
)

/** Persistent, credential-free, content-addressed storage for custom emoji bytes. */
class EmojiAssetStore(
    private val noBackupDirectory: File,
    private val dao: EmojiCacheDao,
    private val client: OkHttpClient,
    private val clock: Clock = Clock.systemUTC(),
) {
    private val emojiDirectory = File(noBackupDirectory, "emoji")
    private val assetsDirectory = File(emojiDirectory, "assets")
    private val urlLocks = ConcurrentHashMap<String, Any>()

    init {
        assetsDirectory.mkdirs()
        sweepTemporaryFiles()
    }

    suspend fun get(url: String): EmojiAssetFile = withContext(Dispatchers.IO) {
        val canonicalUrl = canonicalUrl(url)
        val lock = urlLocks.computeIfAbsent(canonicalUrl) { Any() }
        synchronized(lock) { load(canonicalUrl) }
    }

    private fun load(canonicalUrl: String): EmojiAssetFile {
        val now = clock.millis()
        val mapping = dao.assetUrl(canonicalUrl)
        val existing = mapping?.let(::existingAsset)
        if (mapping != null && existing != null && now - mapping.lastCheckedEpochMillis < MAPPING_FRESHNESS_MILLIS) {
            return touch(existing, now)
        }

        return try {
            download(canonicalUrl, mapping, existing, now)
        } catch (error: IOException) {
            if (existing != null) touch(existing, now) else throw error
        }
    }

    private fun download(
        canonicalUrl: String,
        mapping: EmojiAssetUrlEntity?,
        existing: StoredAsset?,
        now: Long,
    ): EmojiAssetFile {
        fetch(canonicalUrl, mapping).use { response ->
            when {
                response.code == HTTP_NOT_MODIFIED && existing != null && mapping != null -> {
                    dao.insertAssetUrl(mapping.copy(lastCheckedEpochMillis = now))
                    return touch(existing, now)
                }
                response.isSuccessful -> {
                    val asset = writeResponse(response, now)
                    dao.insertAsset(asset.entity)
                    dao.insertAssetUrl(
                        EmojiAssetUrlEntity(
                            canonicalUrl = canonicalUrl,
                            contentHash = asset.entity.contentHash,
                            etag = response.header("ETag"),
                            lastModified = response.header("Last-Modified"),
                            lastCheckedEpochMillis = now,
                        ),
                    )
                    cleanupUnusedAssets()
                    return EmojiAssetFile(asset.file, asset.entity.mimeType)
                }
                else -> throw AssetDownloadException("Emoji asset request returned HTTP ${response.code}")
            }
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

    private fun writeResponse(response: Response, now: Long): StoredAsset {
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
            val relativePath = "emoji/assets/${contentHash.take(2)}/$contentHash"
            val destination = File(noBackupDirectory, relativePath)
            destination.parentFile?.mkdirs()
            moveAtomically(temporary, destination)
            return StoredAsset(
                entity = EmojiAssetEntity(
                    contentHash = contentHash,
                    relativePath = relativePath,
                    mimeType = response.header("Content-Type")?.substringBefore(';')?.trim(),
                    byteSize = byteSize,
                    lastUsedEpochMillis = now,
                ),
                file = destination,
            )
        } finally {
            temporary.delete()
        }
    }

    private fun existingAsset(mapping: EmojiAssetUrlEntity): StoredAsset? {
        val entity = dao.asset(mapping.contentHash) ?: return null
        val file = File(noBackupDirectory, entity.relativePath)
        return file.takeIf(File::isFile)?.let { StoredAsset(entity, it) }
    }

    private fun touch(asset: StoredAsset, now: Long): EmojiAssetFile {
        val updated = asset.entity.copy(lastUsedEpochMillis = now)
        dao.insertAsset(updated)
        return EmojiAssetFile(asset.file, updated.mimeType)
    }

    private fun cleanupUnusedAssets() {
        var totalBytes = dao.assets().sumOf { it.byteSize }
        if (totalBytes <= MAX_TOTAL_ASSET_BYTES) return
        dao.unreferencedAssets().forEach { asset ->
            if (totalBytes <= MAX_TOTAL_ASSET_BYTES) return
            dao.deleteAsset(asset.contentHash)
            File(noBackupDirectory, asset.relativePath).delete()
            totalBytes -= asset.byteSize
        }
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

    companion object {
        private const val HTTP_NOT_MODIFIED = 304
        private const val MAX_ASSET_BYTES = 10L * 1024L * 1024L
        private const val MAX_TOTAL_ASSET_BYTES = 128L * 1024L * 1024L
        private const val MAPPING_FRESHNESS_MILLIS = 7L * 24L * 60L * 60L * 1000L
        private const val MAX_REDIRECTS = 5
        private const val BUFFER_SIZE = 16 * 1024
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
