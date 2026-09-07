package me.foxtails.palustris.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class PostTextPresentationTest {
    @Test fun textWithoutHashtagsRemainsUnchanged() {
        assertEquals(
            PostTextPresentation("A post about #photography today.", emptyList()),
            splitTrailingHashtags("A post about #photography today."),
        )
    }

    @Test fun inlineHashtagRemainsInVisibleText() {
        assertEquals(
            PostTextPresentation("A #useful inline tag remains in this sentence", emptyList()),
            splitTrailingHashtags("A #useful inline tag remains in this sentence"),
        )
    }

    @Test fun oneTerminalHashtagIsExtracted() {
        assertEquals(
            PostTextPresentation("A post", listOf("#photography")),
            splitTrailingHashtags("A post #photography"),
        )
    }

    @Test fun severalTerminalHashtagsPreserveOrderAndSpelling() {
        assertEquals(
            PostTextPresentation("A post", listOf("#Photo", "#sunset", "#東京2026")),
            splitTrailingHashtags("A post #Photo #sunset #東京2026"),
        )
    }

    @Test fun terminalHashtagsMayBeSeparatedByNewlines() {
        assertEquals(
            PostTextPresentation("A post", listOf("#one", "#two")),
            splitTrailingHashtags("A post\n#one\n#two"),
        )
    }

    @Test fun hashtagOnlyTextProducesAnEmptyBody() {
        assertEquals(
            PostTextPresentation("", listOf("#onlytag", "#two")),
            splitTrailingHashtags("#onlytag #two"),
        )
    }

    @Test fun trailingWhitespaceIsNotIncludedInVisibleText() {
        assertEquals(
            PostTextPresentation("A post", listOf("#tag")),
            splitTrailingHashtags("A post #tag  \n"),
        )
    }

    @Test fun unicodeNumbersAndUnderscoresAreValidIdentifiers() {
        assertEquals(
            PostTextPresentation("A post", listOf("#2026_release", "#日本語")),
            splitTrailingHashtags("A post #2026_release #日本語"),
        )
    }

    @Test fun malformedHashTokensRemainInText() {
        val text = "A post # #tag!"
        assertEquals(PostTextPresentation(text, emptyList()), splitTrailingHashtags(text))
    }

    @Test fun urlFragmentsAreNotHashtags() {
        val text = "URL https://site.example/#fragment"
        assertEquals(PostTextPresentation(text, emptyList()), splitTrailingHashtags(text))
    }

    @Test fun tagsWithTerminalPunctuationRemainInText() {
        val text = "Nice picture #photo!"
        assertEquals(PostTextPresentation(text, emptyList()), splitTrailingHashtags(text))
    }

    @Test fun sourceTextIsNotMutated() {
        val text = "A post #one #two"
        splitTrailingHashtags(text)
        assertEquals("A post #one #two", text)
    }
}
