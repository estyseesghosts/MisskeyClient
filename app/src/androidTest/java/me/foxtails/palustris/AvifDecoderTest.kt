package me.foxtails.palustris

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import coil.decode.DataSource
import coil.request.ErrorResult
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.runBlocking
import me.foxtails.palustris.data.media.MediaImageLoader
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.nio.ByteBuffer

@RunWith(AndroidJUnit4::class)
class AvifDecoderTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val targetContext = instrumentation.targetContext
    private val testContext = instrumentation.context
    private val loader = MediaImageLoader.get(targetContext).imageLoader

    @Test
    fun baselineAvifUsesTheAppDecoderAndHonorsSampledSize() {
        val result = load("baseline-8bit.avif", width = 96, height = 72)
        val bitmap = result.bitmap()

        assertTrue(bitmap.width <= 96)
        assertTrue(bitmap.height <= 72)
        assertTrue(bitmap.width > 0 && bitmap.height > 0)
        assertHasPixelVariation(bitmap)
    }

    @Test
    fun alphaHighBitDepthAndIccAvifFilesDecode() {
        listOf("alpha.avif", "10bit.avif", "icc-profile.avif").forEach { asset ->
            val bitmap = load(asset, width = 128, height = 128).bitmap()
            assertTrue("$asset should have pixels", bitmap.width > 0 && bitmap.height > 0)
            assertHasPixelVariation(bitmap)
        }
    }

    @Test
    fun largeAvifUsesTheRequestedDecodeSize() {
        val bitmap = load("large.avif", width = 160, height = 120).bitmap()

        assertTrue(bitmap.width <= 160)
        assertTrue(bitmap.height <= 120)
    }

    @Test
    fun normalJpegFallsThroughToCoilDecoder() {
        val result = load("normal.jpg", width = 96, height = 96)

        assertTrue(result.bitmap().width > 0)
    }

    @Test
    fun repeatedAvifLoadUsesTheCache() {
        val bytes = asset("baseline-8bit.avif")
        val request = ImageRequest.Builder(targetContext)
            .data(bytes)
            .memoryCacheKey("avif-cache-test")
            .size(96, 96)
            .allowHardware(false)
            .build()

        val first = runBlocking { loader.execute(request) }
        val second = runBlocking { loader.execute(request) }

        assertTrue(first is SuccessResult)
        assertTrue(second is SuccessResult)
        assertTrue((second as SuccessResult).dataSource == DataSource.MEMORY_CACHE)
    }

    @Test
    fun corruptAvifReturnsAnErrorResult() {
        val corrupt = ByteBuffer.allocate(16)
            .putInt(16)
            .put("ftyp".toByteArray())
            .put("avif".toByteArray())
            .putInt(0)
            .array()
        val request = ImageRequest.Builder(targetContext)
            .data(corrupt)
            .size(96, 96)
            .allowHardware(false)
            .build()

        val result = runBlocking { loader.execute(request) }

        assertTrue(result is ErrorResult)
    }

    private fun load(name: String, width: Int, height: Int): SuccessResult {
        val request = ImageRequest.Builder(targetContext)
            .data(asset(name))
            .size(width, height)
            .allowHardware(false)
            .build()
        val result = runBlocking { loader.execute(request) }
        assertTrue("$name should decode", result is SuccessResult)
        return result as SuccessResult
    }

    private fun asset(name: String): ByteArray = testContext.assets.open(name).use { it.readBytes() }

    private fun SuccessResult.bitmap(): Bitmap = (drawable as BitmapDrawable).bitmap

    private fun assertHasPixelVariation(bitmap: Bitmap) {
        val first = bitmap.getPixel(0, 0)
        val other = bitmap.getPixel(bitmap.width / 2, bitmap.height / 2)
        assertNotEquals("decoded pixels should not be uniform", first, other)
    }
}
