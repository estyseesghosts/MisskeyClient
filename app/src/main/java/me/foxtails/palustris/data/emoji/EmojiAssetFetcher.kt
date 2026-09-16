package me.foxtails.palustris.data.emoji

import coil.ImageLoader
import coil.decode.DataSource
import coil.decode.ImageSource
import coil.fetch.Fetcher
import coil.fetch.FetchResult
import coil.fetch.SourceResult
import coil.request.Options
import me.foxtails.palustris.data.media.AvifDecoder
import okio.Path.Companion.toOkioPath

data class EmojiAssetRequest(
    val url: String,
)

class EmojiAssetFetcher(
    private val data: EmojiAssetRequest,
    private val store: EmojiAssetStore,
) : Fetcher {
    override suspend fun fetch(): FetchResult {
        // Coil closes the image source on decode success, failure, or
        // cancellation, and that closes the lease so eviction can reclaim it.
        val lease = store.acquire(data.url)
        return SourceResult(
            source = ImageSource(file = lease.file.toOkioPath(), closeable = lease),
            mimeType = lease.mimeType,
            dataSource = DataSource.DISK,
        )
    }

    class Factory(
        private val store: EmojiAssetStore,
    ) : Fetcher.Factory<EmojiAssetRequest> {
        override fun create(
            data: EmojiAssetRequest,
            options: Options,
            imageLoader: ImageLoader,
        ): Fetcher = EmojiAssetFetcher(data, store)
    }
}
