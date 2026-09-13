package me.foxtails.palustris

import me.foxtails.palustris.domain.ContentWarningDecision
import me.foxtails.palustris.domain.ContentWarningPolicy
import me.foxtails.palustris.domain.ContentWarningRules
import me.foxtails.palustris.ui.postHashtags
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContentWarningPolicyTest {
    @Test
    fun bodyTextAndInlineHashtagsParticipateInWarningRules() {
        val rules = ContentWarningRules(
            hideKeywords = listOf("unsafe"),
            hideHashtags = listOf("blocked"),
        )

        assertEquals(
            ContentWarningDecision.Hidden,
            ContentWarningPolicy.decide("spoiler", listOf("#blocked"), rules, bodyText = "safe text"),
        )
        assertEquals(
            ContentWarningDecision.Hidden,
            ContentWarningPolicy.decide("spoiler", emptyList(), rules, bodyText = "unsafe text"),
        )
    }

    @Test
    fun semanticHashtagExtractionKeepsInlineTagsForRules() {
        assertEquals(listOf("#inline", "#terminal"), postHashtags("Text #inline\n#terminal"))
        assertTrue(ContentWarningPolicy.matchesHashtagMute(listOf("#inline"), listOf("inline")))
    }
}
