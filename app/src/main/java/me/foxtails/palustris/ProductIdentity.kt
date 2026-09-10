package me.foxtails.palustris

/** Public product metadata shared by authentication, transport, and presentation. */
object ProductIdentity {
    val name: String = BuildConfig.PRODUCT_NAME
    val version: String = BuildConfig.PRODUCT_VERSION
    val userAgent: String = "$name/$version (Android)"
}
