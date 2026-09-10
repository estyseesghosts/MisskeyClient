package me.foxtails.palustris

import android.graphics.Bitmap
import coil.request.Options
import com.radzivon.bartoshyk.avif.coder.PreferredColorConfig
import me.foxtails.palustris.data.media.AvifDecoder
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class AvifColorConfigCompatibilityTest {
    @Test
    @Config(sdk = [29])
    fun api29UsesTheSupportedEightBitPath() {
        val options = Options(
            context = RuntimeEnvironment.getApplication(),
            config = Bitmap.Config.ARGB_8888,
        )

        assertEquals(PreferredColorConfig.RGBA_8888, preferredColorConfig(options))
    }

    @Test
    @Config(sdk = [35])
    fun api33AndLaterKeepsTheTenBitPath() {
        val tenBitConfig = Bitmap.Config::class.java
            .getField("RGBA_1010102")
            .get(null) as Bitmap.Config
        val options = Options(
            context = RuntimeEnvironment.getApplication(),
            config = tenBitConfig,
        )

        assertEquals(PreferredColorConfig.RGBA_1010102, preferredColorConfig(options))
    }

    private fun preferredColorConfig(options: Options): PreferredColorConfig {
        val decoder = decoderWithoutReadingMedia()
        val method = AvifDecoder::class.java.getDeclaredMethod(
            "preferredColorConfig",
            Options::class.java,
        )
        method.isAccessible = true
        return method.invoke(decoder, options) as PreferredColorConfig
    }

    private fun decoderWithoutReadingMedia(): AvifDecoder {
        // Configuration selection does not need a source; avoid initializing native media state in this unit test.
        val unsafeClass = Class.forName("sun.misc.Unsafe")
        val unsafeField = unsafeClass.getDeclaredField("theUnsafe")
        unsafeField.isAccessible = true
        val unsafe = unsafeField.get(null)
        return unsafeClass.getMethod("allocateInstance", Class::class.java)
            .invoke(unsafe, AvifDecoder::class.java) as AvifDecoder
    }
}
