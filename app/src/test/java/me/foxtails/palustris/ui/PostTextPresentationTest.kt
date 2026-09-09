package me.foxtails.palustris.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class PostTextPresentationTest {
    @Test fun textWithoutHashtagsRemainsUnchanged() {
        assertPresentation("A post about #photography today.", "A post about #photography today.")
    }

    @Test fun inlineHashtagRemainsInVisibleText() {
        assertPresentation("A #useful inline tag remains in this sentence", "A #useful inline tag remains in this sentence")
    }

    @Test fun oneTerminalHashtagIsExtracted() {
        assertPresentation("A post #photography", "A post", "#photography")
    }

    @Test fun severalTerminalHashtagsPreserveOrderSpellingAndDuplicates() {
        assertPresentation(
            "A post #Photo #sunset #Photo #東京2026",
            "A post",
            "#Photo", "#sunset", "#Photo", "#東京2026",
        )
    }

    @Test fun terminalHashtagsMayBeSeparatedByCrLf() {
        assertPresentation("A post\r\n#one\r\n#two", "A post", "#one", "#two")
    }

    @Test fun detachedBlocksMayUseCrLfWithoutDamagingProseBreaks() {
        assertPresentation("Before\r\n#one ✨ #two\r\nAfter", "Before\r\nAfter", "#one", "#two")
    }

    @Test fun hashtagOnlyTextProducesAnEmptyBody() {
        assertPresentation("#onlytag #two", "", "#onlytag", "#two")
    }

    @Test fun trailingWhitespaceAndRelayMarkersAreNotIncludedInVisibleText() {
        assertPresentation("A post #tag  \n\uFFFC\u200B\uFEFF\u00A0", "A post", "#tag")
    }

    @Test fun unicodeNumbersUnderscoresAndCombiningMarksAreValidIdentifiers() {
        assertPresentation("A post #2026_release #Cafe\u0301 #日本語", "A post", "#2026_release", "#Cafe\u0301", "#日本語")
    }

    @Test fun emojiSeparatedBlockAtEndIsRemovedAsOneBlock() {
        val text = "A quiet walk.\n#Scape ✨ #ForestFriday ✨ …"
        assertPresentation(text, "A quiet walk.", "#Scape", "#ForestFriday")
    }

    @Test fun suppliedRelayedPostTextKeepsProseAndRemovesDecorativeTagLine() {
        val text = "A quiet walk through the woods.\n\n#Scape ✨ #ForestFriday ✨ …\n\nThe light was beautiful."
        assertPresentation(text, "A quiet walk through the woods.\n\nThe light was beautiful.", "#Scape", "#ForestFriday")
    }

    @Test fun multipleSeparatedHashtagBlocksAreRemovedInOriginalOrder() {
        val text = "First paragraph.\n#one • #two\nSecond paragraph.\n#three · #four\nLast paragraph."
        assertPresentation(
            text,
            "First paragraph.\nSecond paragraph.\nLast paragraph.",
            "#one", "#two", "#three", "#four",
        )
    }

    @Test fun aDetachedBlockWithOrdinaryWordsIsPreserved() {
        val text = "A sentence\n#one ✨ #two with words\nMore prose"
        assertPresentation(text, text)
    }

    @Test fun proseFollowedByTagsOnTheSameLineUsesTerminalRule() {
        assertPresentation("A post about the view #one #two", "A post about the view", "#one", "#two")
    }

    @Test fun inlineTagsInSentencesRemainVisibleWhenOtherBlocksAreRemoved() {
        val text = "A #keep tag in a sentence.\n#remove ✨ #these\nAnother #visible tag."
        assertPresentation(text, "A #keep tag in a sentence.\nAnother #visible tag.", "#remove", "#these")
    }

    @Test fun urlFragmentsEmailFragmentsAndCodeLikeTokensAreNotHashtags() {
        val text = "URL https://site.example/#fragment email name#fragment@example.com code `#tag` [#other]"
        assertPresentation(text, text)
    }

    @Test fun punctuationAttachedToTagsDisqualifiesTheTag() {
        val text = "A sentence #photo! and #other, plus #third."
        assertPresentation(text, text)
    }

    @Test fun unknownReplacementCharacterIsVisibleAndDisqualifiesBlock() {
        val text = "#one ✨ #two �"
        assertPresentation(text, text)
    }

    @Test fun nonBreakingSpacesSeparateAndTerminateTags() {
        assertPresentation("A post\u00A0#one\u00A0#two\u00A0", "A post", "#one", "#two")
    }

    @Test fun filteredRangesCoverDetectedBlocksInSourceOrder() {
        val text = "Intro\n#one ✨ #two\nOutro #three #four"
        val presentation = parseHashtagBlocks(text)

        assertEquals("Intro\nOutro", presentation.visibleText)
        assertEquals(listOf("#one", "#two", "#three", "#four"), presentation.filteredHashtags)
        assertEquals(
            listOf("#one ✨ #two\n", " #three #four"),
            presentation.filteredRanges.map { text.substring(it) },
        )
    }

    @Test fun commaSeparatedTerminalBlockIncludesLinkedHashtag() {
        val text = "A photo.\n#analog, #film, [#minimal](https://pixel.example/tags/minimal)"
        assertPresentation(text, "A photo.", "#analog", "#film", "#minimal")
    }

    @Test fun commaSeparatedTagsInsideProseRemainVisible() {
        val text = "These are #one, #two, and #three in a sentence."
        assertPresentation(text, text)
    }

    @Test fun ordinaryMarkdownLinkRemainsVisibleForClickableRendering() {
        assertPresentation("Read [the full guide](https://example.org/guide) today.", "Read [the full guide](https://example.org/guide) today.")
    }

    @Test fun instanceTagSearchMarkdownLinkIsNormalizedToAnInlineHashtag() {
        assertPresentation(
            "Meet at [#Zurich](<https://pixelfed.social/discover/tags/Zurich?src=hash>) today.",
            "Meet at #Zurich today.",
        )
    }

    @Test fun hiddenEntityTextDoesNotCountTowardPostLimit() {
        assertEquals(7, postBodyCharacterCount("https://example.org/a-very-long-path"))
        assertEquals(7, postBodyCharacterCount("@handle@mastodon.social"))
        assertEquals(9, postBodyCharacterCount("[Read this](https://example.org/a-very-long-path)"))
    }

    @Test fun truncationKeepsACompleteUrlEntityInsteadOfSplittingItsSourceSyntax() {
        val url = "https://example.org/a-very-long-path"
        val text = "x".repeat(340) + " " + url + " tail"

        assertEquals("x".repeat(340) + " " + url + " t…", truncatedPostBody(text))
    }

    @Test fun blankLinesCreatedByRemovedBlocksCollapseWithoutChangingParagraphSpacing() {
        val text = "Before\n\n#one ✨ #two\n\nAfter"
        assertPresentation(text, "Before\n\nAfter", "#one", "#two")
    }

    private fun assertPresentation(text: String, visibleText: String, vararg hashtags: String) {
        val actual = parseHashtagBlocks(text)
        assertEquals(visibleText, actual.visibleText)
        assertEquals(hashtags.toList(), actual.filteredHashtags)
    }
}
