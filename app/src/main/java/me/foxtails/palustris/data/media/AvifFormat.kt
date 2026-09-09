package me.foxtails.palustris.data.media

import okio.BufferedSource

internal object AvifFormat {
    const val MIME_TYPE = "image/avif"

    private const val FTYP_TYPE_OFFSET = 4
    private const val FTYP_HEADER_SIZE = 16
    private const val EXTENDED_FTYP_HEADER_SIZE = 24
    private const val MAX_FTYP_BOX_SIZE = 4 * 1024
    private val FTYP = "ftyp".encodeToByteArray()
    private val AVIF_BRANDS = setOf(
        "avif".encodeToByteArray().toList(),
        "avis".encodeToByteArray().toList(),
    )

    fun isAvifMimeType(mimeType: String?): Boolean = mimeType
        ?.substringBefore(';')
        ?.trim()
        ?.equals(MIME_TYPE, ignoreCase = true) == true

    /** MIME type identifies a candidate; the ftyp box remains authoritative. */
    fun isAvif(mimeType: String?, source: BufferedSource): Boolean {
        val mimeCandidate = isAvifMimeType(mimeType)
        val byteCandidate = hasAvifBrand(source)
        return when {
            mimeCandidate -> byteCandidate
            byteCandidate -> true
            else -> false
        }
    }

    fun hasAvifBrand(source: BufferedSource): Boolean {
        val peek = source.peek()
        if (!peek.request(8)) return false
        val prefix = peek.readByteArray(8)
        if (!prefix.copyOfRange(FTYP_TYPE_OFFSET, 8).contentEquals(FTYP)) return false

        val declaredSize = readUInt32(prefix, 0)
        val header = if (declaredSize == 1L) {
            if (!peek.request(8)) return false
            prefix + peek.readByteArray(8)
        } else {
            prefix
        }
        val boxSize = if (declaredSize == 1L) readUInt64(header, 8) else declaredSize
        if (boxSize < FTYP_HEADER_SIZE || boxSize > MAX_FTYP_BOX_SIZE) return false
        val remaining = boxSize - header.size
        if (!peek.request(remaining)) return false
        return hasAvifBrand(header + peek.readByteArray(remaining))
    }

    fun hasAvifBrand(bytes: ByteArray): Boolean {
        if (bytes.size < FTYP_HEADER_SIZE) return false
        if (!bytes.copyOfRange(FTYP_TYPE_OFFSET, 8).contentEquals(FTYP)) return false

        val declaredSize = readUInt32(bytes, 0)
        val brandOffset = if (declaredSize == 1L) EXTENDED_FTYP_HEADER_SIZE else 8
        val boxSize = if (declaredSize == 0L) bytes.size.toLong() else declaredSize
        if (boxSize < FTYP_HEADER_SIZE || boxSize > bytes.size || brandOffset > boxSize) return false

        var offset = brandOffset
        while (offset + 4 <= boxSize) {
            val intOffset = offset.toInt()
            val brand = bytes.copyOfRange(intOffset, intOffset + 4).toList()
            if (brand in AVIF_BRANDS) return true
            offset += 4
        }
        return false
    }

    private fun readUInt32(bytes: ByteArray, offset: Int): Long =
        ((bytes[offset].toLong() and 0xff) shl 24) or
            ((bytes[offset + 1].toLong() and 0xff) shl 16) or
            ((bytes[offset + 2].toLong() and 0xff) shl 8) or
            (bytes[offset + 3].toLong() and 0xff)

    private fun readUInt64(bytes: ByteArray, offset: Int): Long =
        ((bytes[offset].toLong() and 0xff) shl 56) or
            ((bytes[offset + 1].toLong() and 0xff) shl 48) or
            ((bytes[offset + 2].toLong() and 0xff) shl 40) or
            ((bytes[offset + 3].toLong() and 0xff) shl 32) or
            ((bytes[offset + 4].toLong() and 0xff) shl 24) or
            ((bytes[offset + 5].toLong() and 0xff) shl 16) or
            ((bytes[offset + 6].toLong() and 0xff) shl 8) or
            (bytes[offset + 7].toLong() and 0xff)
}
