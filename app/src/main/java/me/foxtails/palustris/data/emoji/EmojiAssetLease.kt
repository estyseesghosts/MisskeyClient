package me.foxtails.palustris.data.emoji

import java.io.Closeable
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * A held reference to one stored emoji asset file.
 *
 * The store keeps the content alive while a lease is open, so eviction cannot
 * delete a file that a decoder still reads. Coil closes the lease through the
 * [coil.decode.ImageSource] closeable hook on decode success, failure, or
 * cancellation. Closing more than once is safe.
 */
class EmojiAssetLease internal constructor(
    val file: File,
    val mimeType: String?,
    private val onClose: () -> Unit,
) : Closeable {
    private val closed = AtomicBoolean(false)

    override fun close() {
        if (closed.compareAndSet(false, true)) onClose()
    }
}
