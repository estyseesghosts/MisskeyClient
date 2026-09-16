package me.foxtails.palustris.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import me.foxtails.palustris.R
import me.foxtails.palustris.domain.SourceError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Feature identifiers resolve to human labels, never raw protocol codes. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SourceErrorMessageTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val generic = context.getString(R.string.error_feature_generic)

    @Test
    fun timelineIdentifiersUseTheTimelineLabel() {
        val label = featureLabel(context, "timeline:Home")

        assertTrue(label.isNotBlank())
        assertTrue(label != "timeline:Home")
        assertTrue(label != generic)
    }

    @Test
    fun audienceIdentifiersUseTheAudienceLabel() {
        assertEquals(context.getString(R.string.audience_everyone), featureLabel(context, "audience:Public"))
        assertEquals(context.getString(R.string.audience_direct), featureLabel(context, "audience:Direct"))
    }

    @Test
    fun unknownIdentifiersFallBackToTheGenericPhrase() {
        assertEquals(generic, featureLabel(context, "profile.timeline"))
        assertEquals(generic, featureLabel(context, "requested feature"))
        assertEquals(generic, featureLabel(context, "timeline:NotATimeline"))
    }

    @Test
    fun blankServerDetailUsesTheGenericServerMessage() {
        assertEquals(
            context.getString(R.string.error_source_server),
            sourceErrorMessage(context, SourceError.ServerError("")),
        )
        assertEquals(
            "server said no",
            sourceErrorMessage(context, SourceError.ServerError("server said no")),
        )
    }
}
