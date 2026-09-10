package me.foxtails.palustris.data.media

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import androidx.annotation.RequiresApi
import coil.ImageLoader
import coil.decode.DecodeResult
import coil.decode.Decoder
import coil.fetch.SourceResult
import coil.request.Options
import coil.size.Scale
import coil.size.Size
import coil.size.pxOrElse
import com.radzivon.bartoshyk.avif.coder.HeifCoder
import com.radzivon.bartoshyk.avif.coder.PreferredColorConfig
import com.radzivon.bartoshyk.avif.coder.ScaleMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runInterruptible

internal class AvifDecoder(
    private val source: SourceResult,
    private val options: Options,
) : Decoder {
    override suspend fun decode(): DecodeResult = runInterruptible {
        try {
            decodeImage()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Exception) {
            throw IllegalArgumentException("Unable to decode AVIF image", failure)
        }
    }

    private fun decodeImage(): DecodeResult {
        val encoded = source.source.source().readByteArray()
        val coder = HeifCoder()
        val dimensions = coder.getSize(encoded)
            ?: error("AVIF dimensions are unavailable")
        requireReasonableDimensions(dimensions.width, dimensions.height)
        val colorConfig = preferredColorConfig(options)
        val bitmap = if (options.size == Size.ORIGINAL) {
            coder.decode(encoded, colorConfig)
        } else {
            coder.decodeSampled(
                byteArray = encoded,
                scaledWidth = options.size.width.pxOrElse { dimensions.width },
                scaledHeight = options.size.height.pxOrElse { dimensions.height },
                preferredColorConfig = colorConfig,
                scaleMode = when (options.scale) {
                    Scale.FIT -> ScaleMode.FIT
                    Scale.FILL -> ScaleMode.FILL
                },
            )
        }
        return DecodeResult(
            drawable = BitmapDrawable(options.context.resources, bitmap),
            isSampled = options.size != Size.ORIGINAL,
        )
    }

    private fun preferredColorConfig(options: Options): PreferredColorConfig {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            preferredTenBitConfig(options.config)?.let {
                return it
            }
        }

        return when (options.config) {
            Bitmap.Config.ALPHA_8,
            Bitmap.Config.ARGB_8888,
            -> PreferredColorConfig.RGBA_8888

            Bitmap.Config.RGB_565 -> {
                if (options.allowRgb565) {
                    PreferredColorConfig.RGB_565
                } else {
                    PreferredColorConfig.DEFAULT
                }
            }

            Bitmap.Config.RGBA_F16 ->
                PreferredColorConfig.RGBA_F16

            Bitmap.Config.HARDWARE ->
                PreferredColorConfig.HARDWARE

            else ->
                PreferredColorConfig.DEFAULT
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun preferredTenBitConfig(
        config: Bitmap.Config,
    ): PreferredColorConfig? {
        return if (config == Bitmap.Config.RGBA_1010102) {
            PreferredColorConfig.RGBA_1010102
        } else {
            null
        }
    }

    private fun requireReasonableDimensions(width: Int, height: Int) {
        require(width in 1..MAX_DIMENSION && height in 1..MAX_DIMENSION) {
            "AVIF dimensions are unreasonable"
        }
        require(width.toLong() * height <= MAX_PIXELS) {
            "AVIF pixel count is unreasonable"
        }
    }

    class Factory : Decoder.Factory {
        override fun create(result: SourceResult, options: Options, imageLoader: ImageLoader): Decoder? {
            return if (AvifFormat.isAvif(result.mimeType, result.source.source())) {
                AvifDecoder(result, options)
            } else {
                null
            }
        }

        override fun equals(other: Any?): Boolean = other is Factory

        override fun hashCode(): Int = Factory::class.java.hashCode()
    }

    private companion object {
        const val MAX_DIMENSION = 32_768
        const val MAX_PIXELS = 268_435_456L
    }
}
