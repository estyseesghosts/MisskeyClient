package me.foxtails.palustris.domain

import me.foxtails.palustris.domain.TrackingParameterCleaner
import org.junit.Assert.assertEquals
import org.junit.Test

class TrackingParameterCleanerTest {
    @Test
    fun removesKnownAttributionParametersWithoutRewritingOtherQueryValues() {
        assertEquals(
            "https://example.org/post?q=a%20value&keep=1",
            TrackingParameterCleaner.clean("https://example.org/post?utm_source=app&q=a%20value&keep=1", true),
        )
    }

    @Test
    fun preservesSignedUrlsAndSupportsExplicitComposerCleanup() {
        val signed = "https://example.org/file?X-Amz-Signature=abc&utm_source=app"
        assertEquals(signed, TrackingParameterCleaner.clean(signed, true))
        assertEquals(
            "See https://example.org/post?keep=1.",
            TrackingParameterCleaner.cleanText("See https://example.org/post?utm_campaign=x&keep=1.") ,
        )
    }
}
