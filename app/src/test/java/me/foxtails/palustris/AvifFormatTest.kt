package me.foxtails.palustris

import me.foxtails.palustris.data.media.AvifFormat
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer

class AvifFormatTest {
    @Test
    fun recognizesMajorAvifBrand() {
        assertTrue(AvifFormat.hasAvifBrand(ftyp("avif")))
    }

    @Test
    fun recognizesAnimatedAvifBrand() {
        assertTrue(AvifFormat.hasAvifBrand(ftyp("avis")))
    }

    @Test
    fun recognizesCompatibleAvifBrand() {
        assertTrue(AvifFormat.hasAvifBrand(ftyp("mif1", "avif")))
    }

    @Test
    fun rejectsJpegBytesEvenWhenMimeTypeClaimsAvif() {
        val bytes = ftyp("mif1")
        assertFalse(AvifFormat.isAvif("image/avif", Buffer().write(bytes)))
    }

    @Test
    fun ignoresUrlAndUsesAvifMimeTypeAsAByteInspectionCandidate() {
        assertTrue(AvifFormat.isAvif("IMAGE/AVIF; charset=binary", Buffer().write(ftyp("avif"))))
        assertTrue(AvifFormat.isAvif("application/octet-stream", Buffer().write(ftyp("avif"))))
        assertFalse(AvifFormat.isAvif("image/jpeg", Buffer().write(ftyp("mif1"))))
    }

    @Test
    fun doesNotConsumeSourceDuringDetection() {
        val bytes = ftyp("avif") + byteArrayOf(1, 2, 3)
        val source = Buffer().write(bytes)

        assertTrue(AvifFormat.hasAvifBrand(source))
        assertEquals(bytes.size.toLong(), source.size)
        assertEquals(bytes.toList(), source.readByteArray().toList())
    }

    @Test
    fun rejectsTruncatedAndMalformedBoxes() {
        val truncated = ftyp("avif").copyOf(15)
        val malformed = ftyp("avif").also { ByteBuffer.wrap(it).putInt(0, 64) }

        assertFalse(AvifFormat.hasAvifBrand(truncated))
        assertFalse(AvifFormat.hasAvifBrand(malformed))
    }

    private fun ftyp(majorBrand: String, vararg compatibleBrands: String): ByteArray {
        require(majorBrand.length == 4)
        require(compatibleBrands.all { it.length == 4 })
        return ByteBuffer.allocate(16 + compatibleBrands.size * 4)
            .putInt(16 + compatibleBrands.size * 4)
            .put("ftyp".toByteArray())
            .put(majorBrand.toByteArray())
            .putInt(0)
            .apply { compatibleBrands.forEach { put(it.toByteArray()) } }
            .array()
    }
}
