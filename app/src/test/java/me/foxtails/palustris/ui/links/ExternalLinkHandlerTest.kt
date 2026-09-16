package me.foxtails.palustris.ui.links

import android.app.Application
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import me.foxtails.palustris.R
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ExternalLinkHandlerTest {
    private val application = ApplicationProvider.getApplicationContext<Application>()

    /** Records each launched Intent. It can also simulate a device with no browser. */
    private class RecordingContext(base: Context) : ContextWrapper(base) {
        val started = mutableListOf<Intent>()
        var refuseToOpen = false

        override fun startActivity(intent: Intent) {
            if (refuseToOpen) throw ActivityNotFoundException()
            started += intent
        }
    }

    @Before
    fun resetTrackingPreference() {
        ExternalLinkHandler.cleanTrackingParameters = false
    }

    @After
    fun restoreTrackingPreference() {
        ExternalLinkHandler.cleanTrackingParameters = false
    }

    @Test
    fun nullOrBlankUrlDoesNotStartAnActivity() {
        val context = RecordingContext(application)

        ExternalLinkHandler.open(context, null)
        ExternalLinkHandler.open(context, "")
        ExternalLinkHandler.open(context, "   ")

        assertTrue(context.started.isEmpty())
    }

    @Test
    fun nonHttpSchemeOrBlankHostDoesNotStartAnActivity() {
        val context = RecordingContext(application)

        ExternalLinkHandler.open(context, "palustris://notification/event")
        ExternalLinkHandler.open(context, "mailto:owner@example.org")
        ExternalLinkHandler.open(context, "https://")

        assertTrue(context.started.isEmpty())
    }

    @Test
    fun webUrlStartsExactlyOneViewIntent() {
        val context = RecordingContext(application)

        ExternalLinkHandler.open(context, "https://example.org/post?id=1")

        val intent = context.started.single()
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals("https://example.org/post?id=1", intent.data.toString())
    }

    @Test
    fun trackingParametersAreRemovedWhenThePreferenceIsEnabled() {
        val context = RecordingContext(application)
        ExternalLinkHandler.cleanTrackingParameters = true

        ExternalLinkHandler.open(context, "https://example.org/post?utm_source=app&keep=1")

        assertEquals("https://example.org/post?keep=1", context.started.single().data.toString())
    }

    @Test
    fun prepareRespectsTheTrackingPreference() {
        val url = "https://example.org/post?utm_source=app"
        assertEquals(url, ExternalLinkHandler.prepare(url))

        ExternalLinkHandler.cleanTrackingParameters = true
        assertEquals("https://example.org/post", ExternalLinkHandler.prepare(url))
    }

    @Test
    fun missingBrowserShowsTheStandardErrorToast() {
        val context = RecordingContext(application).apply { refuseToOpen = true }

        ExternalLinkHandler.open(context, "https://example.org/post")

        assertTrue(context.started.isEmpty())
        assertEquals(application.getString(R.string.error_no_app_open_link), ShadowToast.getTextOfLatestToast())
    }
}
