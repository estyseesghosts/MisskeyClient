package me.foxtails.palustris

import org.junit.Assert.assertEquals
import org.junit.Test

class ProductIdentityTest {
    @Test
    fun generatedIdentityUsesBeelineVersion() {
        assertEquals("Beeline", BuildConfig.PRODUCT_NAME)
        assertEquals("0.2.1", BuildConfig.PRODUCT_VERSION)
        assertEquals("Beeline/0.2.1 (Android)", ProductIdentity.userAgent)
    }
}
