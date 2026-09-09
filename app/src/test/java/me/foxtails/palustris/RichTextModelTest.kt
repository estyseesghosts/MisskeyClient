package me.foxtails.palustris

import me.foxtails.palustris.domain.Audience
import me.foxtails.palustris.domain.CustomEmoji
import me.foxtails.palustris.domain.EmojiChoice
import me.foxtails.palustris.domain.EntityId
import me.foxtails.palustris.domain.MediaRequestPolicy
import me.foxtails.palustris.domain.PostReactionReducer
import me.foxtails.palustris.domain.Reaction
import me.foxtails.palustris.domain.ReactionSelectionMode
import me.foxtails.palustris.domain.ValidatedUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** Entity locality, URL validation, fallback, and model copying for the emoji model. */
class RichTextModelTest {
    private val origin = "https://example.org"

    private fun post(
        id: String = "post",
        emoji: Map<String, CustomEmoji> = emptyMap(),
        reactions: List<Reaction> = emptyList(),
        selectedReactions: List<EmojiChoice> = emptyList(),
        myReaction: String? = null,
    ) = me.foxtails.palustris.domain.Post(
        id = EntityId(origin, id),
        author = me.foxtails.palustris.domain.Account(
            me.foxtails.palustris.domain.AccountId(
                me.foxtails.palustris.domain.Connection(origin, me.foxtails.palustris.domain.Protocol.MASTODON),
                "author",
            ),
            "Author",
            "@author@example.org",
        ),
        text = "text",
        publishedAtEpochMillis = 0,
        audience = Audience.Public,
        emoji = emoji,
        reactions = reactions,
        selectedReactions = selectedReactions,
        myReaction = myReaction,
    )

    private val blob = CustomEmoji(
        shortcode = "blob",
        animatedUrl = ValidatedUrl.https("https://cdn.example/blob.gif"),
        staticUrl = ValidatedUrl.https("https://cdn.example/blob.png"),
        submissionValue = ":blob:",
    )

    @Test fun emojiModelCopiesWithoutSharingNestedMaps() {
        val withEmoji = post(emoji = mapOf("blob" to blob))
        val copy = withEmoji.copy()
        assertEquals(withEmoji, copy)
        assertEquals(withEmoji.emoji, copy.emoji)
    }

    @Test fun emojiTokenIsTheOriginalShortcodeForFallbackAndAccessibility() {
        assertEquals(":blob:", blob.token)
    }

    @Test fun validatedEmojiUrlsRejectCredentialsAndNonHttps() {
        assertNull(MediaRequestPolicy.validatedWebUrl("http://cdn.example/x.png"))
        assertNull(MediaRequestPolicy.validatedWebUrl("https://user:pass@cdn.example/x.png"))
        assertNull(MediaRequestPolicy.validatedWebUrl("javascript:alert(1)"))
        assertNull(MediaRequestPolicy.validatedWebUrl(null))
        assertEquals(
            "https://cdn.example/x.png",
            MediaRequestPolicy.validatedWebUrl("https://cdn.example/x.png")?.value,
        )
    }

    @Test fun relativeEmojiUrlsResolveAgainstTheOwningOrigin() {
        assertEquals(
            "https://example.org/emoji/x.png",
            MediaRequestPolicy.validatedWebUrl("/emoji/x.png", "https://example.org")?.value,
        )
    }

    @Test fun emojiImagePrefersStaticUrl() {
        val request = MediaRequestPolicy.emojiImage(blob)
        assertEquals("https://cdn.example/blob.png", request?.url?.value)
        assertTrue(request?.static == true)
        val animatedOnly = blob.copy(staticUrl = null)
        assertEquals(
            "https://cdn.example/blob.gif",
            MediaRequestPolicy.emojiImage(animatedOnly)?.url?.value,
        )
        assertNull(MediaRequestPolicy.emojiImage(blob.copy(animatedUrl = null, staticUrl = null)))
    }

    @Test fun reducerPreservesSurvivingReactionMetadata() {
        val existing = Reaction(":blob:", 4, selected = true, emojiMetadata = blob)
        val other = Reaction(":wave:", 2, selected = false)
        val before = post(
            reactions = listOf(existing, other),
            selectedReactions = listOf(EmojiChoice(":blob:", ":blob:", blob)),
            myReaction = ":blob:",
        )
        val result = PostReactionReducer.apply(
            before,
            EmojiChoice(":wave:", ":wave:", null),
            selected = true,
            selectionMode = ReactionSelectionMode.Single,
        )
        assertEquals(listOf(":blob:", ":wave:"), result.reactions.map { it.emoji })
        assertEquals(3, result.reactions.first { it.emoji == ":blob:" }.count)
        assertEquals(3, result.reactions.first { it.emoji == ":wave:" }.count)
        assertSame(blob, result.reactions.first { it.emoji == ":blob:" }.emojiMetadata)
        assertEquals(listOf(":wave:"), result.selectedReactions.map { it.submissionValue })
        assertEquals(":wave:", result.myReaction)
    }

    @Test fun reducerNeverCountsBelowZero() {
        val before = post(reactions = listOf(Reaction(":one:", 1, selected = true)), myReaction = ":one:")
        val result = PostReactionReducer.apply(
            before,
            EmojiChoice(":one:", ":one:", null),
            selected = false,
            selectionMode = ReactionSelectionMode.Single,
        )
        assertTrue(result.reactions.isEmpty())
        assertTrue(result.selectedReactions.isEmpty())
        assertNull(result.myReaction)
    }

    @Test fun reducerIndependentSelectionKeepsMultipleSelections() {
        val first = EmojiChoice(":blob:", ":blob:", blob)
        val before = post(
            reactions = listOf(Reaction(":blob:", 1, selected = true, emojiMetadata = blob)),
            selectedReactions = listOf(first),
            myReaction = ":blob:",
        )
        val result = PostReactionReducer.apply(
            before,
            EmojiChoice("👍", "👍", null),
            selected = true,
            selectionMode = ReactionSelectionMode.Independent,
        )
        assertEquals(setOf(":blob:", "👍"), result.selectedReactions.map { it.submissionValue }.toSet())
        assertEquals(1, result.reactions.first { it.emoji == ":blob:" }.count)
        assertEquals(1, result.reactions.first { it.emoji == "👍" }.count)
    }

    @Test fun reducerDrivesPrimaryFavouriteWhenRequested() {
        val before = post()
        val result = PostReactionReducer.apply(
            before,
            EmojiChoice("❤️", "❤️", null),
            selected = true,
            selectionMode = ReactionSelectionMode.Single,
            primaryFavouriteEmoji = "❤️",
        )
        assertTrue(result.favourited)
        assertEquals("❤️", result.myReaction)
    }
}
