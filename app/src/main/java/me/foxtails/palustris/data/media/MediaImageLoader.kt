package me.foxtails.palustris.data.media

import android.content.Context
import coil.ImageLoader
import coil.request.ImageRequest
import coil.size.Size
import me.foxtails.palustris.domain.Attachment
import me.foxtails.palustris.domain.EmojiImageRequest
import me.foxtails.palustris.domain.MediaRequestDecision
import me.foxtails.palustris.domain.MediaRequestRole
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit

/** Shared credential-free loader for public media; account identity is kept in cache keys. */
class MediaImageLoader private constructor(context: Context) {
    val imageLoader: ImageLoader = ImageLoader.Builder(context)
        .memoryCache {
            coil.memory.MemoryCache.Builder(context)
                .maxSizePercent(0.20)
                .build()
        }
        .diskCache {
            coil.disk.DiskCache.Builder()
                .directory(File(context.cacheDir, "media-images"))
                .maxSizeBytes(64L * 1024L * 1024L)
                .build()
        }
        .okHttpClient {
            OkHttpClient.Builder()
                .followRedirects(true)
                .followSslRedirects(true)
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .writeTimeout(20, TimeUnit.SECONDS)
                .callTimeout(30, TimeUnit.SECONDS)
                .build()
        }
        .components {
            add(AvifDecoder.Factory())
        }
        .build()

    fun request(
        context: Context,
        decision: MediaRequestDecision.Request,
        accountIdentity: String,
        postIdentity: String,
        attachment: Attachment,
        attachmentIndex: Int,
        decodeWidthPx: Int,
        decodeHeightPx: Int,
    ): ImageRequest = ImageRequest.Builder(context)
        .data(decision.url)
        .memoryCacheKey(cacheKey(accountIdentity, postIdentity, attachment, attachmentIndex, decision.role, decodeWidthPx, decodeHeightPx))
        .diskCacheKey(cacheKey(accountIdentity, postIdentity, attachment, attachmentIndex, decision.role, decodeWidthPx, decodeHeightPx))
        .size(decodeWidthPx.coerceAtLeast(1), decodeHeightPx.coerceAtLeast(1))
        .crossfade(false)
        .build()

    /** Credential-free, constrained custom-emoji requests keyed by canonical URL and size. */
    fun emojiRequest(
        context: Context,
        request: EmojiImageRequest,
        decodeSizePx: Int,
    ): ImageRequest = ImageRequest.Builder(context)
        .data(request.url.value)
        .memoryCacheKey(emojiCacheKey(request.url.value, decodeSizePx))
        .diskCacheKey(emojiCacheKey(request.url.value, decodeSizePx))
        .size(decodeSizePx.coerceAtLeast(1), decodeSizePx.coerceAtLeast(1))
        .crossfade(false)
        .build()

    companion object {
        @Volatile private var instance: MediaImageLoader? = null

        fun get(context: Context): MediaImageLoader = instance ?: synchronized(this) {
            instance ?: MediaImageLoader(context.applicationContext).also { instance = it }
        }

        fun cacheKey(
            accountIdentity: String,
            postIdentity: String,
            attachment: Attachment,
            attachmentIndex: Int,
            role: MediaRequestRole,
            decodeWidthPx: Int,
            decodeHeightPx: Int,
        ): String = listOf(
            "media-v1",
            accountIdentity,
            postIdentity,
            attachment.id ?: "position-$attachmentIndex",
            attachment.url.orEmpty(),
            attachment.previewUrl.orEmpty(),
            role.name,
            "${decodeWidthPx.coerceAtLeast(1)}x${decodeHeightPx.coerceAtLeast(1)}",
        ).joinToString("\u0000")

        fun emojiCacheKey(
            url: String,
            decodeSizePx: Int,
        ): String = listOf(
            "emoji-v2",
            canonicalEmojiUrl(url),
            decodeSizePx.coerceAtLeast(1).toString(),
        ).joinToString("\u0000")

        fun canonicalEmojiUrl(url: String): String = url.toHttpUrl()
            .newBuilder()
            .username("")
            .password("")
            .fragment(null)
            .build()
            .toString()
    }
}
