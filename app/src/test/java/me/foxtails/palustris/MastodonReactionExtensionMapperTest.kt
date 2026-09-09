package me.foxtails.palustris

import me.foxtails.palustris.data.mastodon.MastodonReactionExtensionMapper
import me.foxtails.palustris.domain.CustomEmoji
import me.foxtails.palustris.domain.ValidatedUrl
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MastodonReactionExtensionMapperTest {
    private val statusEmoji = mapOf(
        "akkoma_blob" to CustomEmoji(
            shortcode = "akkoma_blob",
            animatedUrl = ValidatedUrl.https("https://cdn.example/akkoma_blob.png"),
            staticUrl = ValidatedUrl.https("https://cdn.example/akkoma_blob_static.png"),
            submissionValue = ":akkoma_blob:",
        ),
        ":akkoma_blob:" to CustomEmoji(
            shortcode = "akkoma_blob",
            animatedUrl = ValidatedUrl.https("https://cdn.example/akkoma_blob.png"),
            staticUrl = ValidatedUrl.https("https://cdn.example/akkoma_blob_static.png"),
            submissionValue = ":akkoma_blob:",
        ),
    )

    private fun statusWith(reactions: JSONArray?) = JSONObject().apply {
        reactions?.let { put("emoji_reactions", it) }
    }

    @Test
    fun absentExtensionReturnsNullWithoutConvertingStandardEmojis() {
        assertNull(MastodonReactionExtensionMapper.reactions(JSONObject(), statusEmoji))
        val onlyStandard = JSONObject().put(
            "emojis",
            JSONArray().put(JSONObject().put("shortcode", "blob").put("url", "https://cdn.example/blob.png")),
        )
        assertNull(MastodonReactionExtensionMapper.reactions(onlyStandard, statusEmoji))
        assertNull(MastodonReactionExtensionMapper.selectedChoices(onlyStandard, statusEmoji, true))
    }

    @Test
    fun malformedEntriesAreSkippedIndependently() {
        val reactions = JSONArray()
            .put(JSONObject().put("name", ":ok:").put("count", 2).put("me", false))
            .put(JSONObject().put("name", "").put("count", 4))
            .put(JSONObject().put("count", 3).put("me", true))
            .put(JSONObject().put("name", ":neg:").put("count", -5).put("me", false))
        val mapped = MastodonReactionExtensionMapper.reactions(statusWith(reactions), statusEmoji)

        assertEquals(listOf(":ok:", ":neg:"), mapped?.map { it.emoji })
        assertEquals(2, mapped?.first()?.count)
        assertEquals(0, mapped?.last()?.count)
        assertTrue(mapped!!.none { it.count < 0 })
    }

    @Test
    fun rawNamesStayOpaqueAndSelectedMultiplicityIsPreserved() {
        val reactions = JSONArray()
            .put(JSONObject().put("name", ":bare_name_without_colons:").put("count", 1).put("me", true))
            .put(JSONObject().put("name", "👍").put("count", 1).put("me", true))
            .put(JSONObject().put("name", ":akkoma_blob:").put("count", 2).put("me", false))
        val json = statusWith(reactions)

        val mapped = MastodonReactionExtensionMapper.reactions(json, statusEmoji).orEmpty()
        assertEquals(listOf(":bare_name_without_colons:", "👍", ":akkoma_blob:"), mapped.map { it.emoji })

        val choices = MastodonReactionExtensionMapper.selectedChoices(json, statusEmoji, independentSelection = true).orEmpty()
        assertEquals(listOf(":bare_name_without_colons:", "👍"), choices.map { it.submissionValue })

        val collapsed = MastodonReactionExtensionMapper.selectedChoices(json, statusEmoji, independentSelection = false).orEmpty()
        assertEquals(listOf("👍"), collapsed.map { it.submissionValue })
    }

    @Test
    fun imagesResolveFromEntryUrlOrStatusMetadataOnly() {
        val withUrl = statusWith(JSONArray().put(JSONObject()
            .put("name", ":direct:")
            .put("count", 1)
            .put("me", false)
            .put("url", "https://cdn.example/direct.png")))
        val withMetadata = statusWith(JSONArray().put(JSONObject()
            .put("name", ":akkoma_blob:")
            .put("count", 1)
            .put("me", false)))
        val unresolved = statusWith(JSONArray().put(JSONObject()
            .put("name", ":mystery:")
            .put("count", 1)
            .put("me", false)))

        val direct = MastodonReactionExtensionMapper.reactions(withUrl, statusEmoji).orEmpty().single()
        assertEquals("https://cdn.example/direct.png", direct.emojiMetadata?.staticUrl?.value)
        assertEquals(":direct:", direct.emojiMetadata?.submissionValue)

        val resolved = MastodonReactionExtensionMapper.reactions(withMetadata, statusEmoji).orEmpty().single()
        assertNotNull(resolved.emojiMetadata)
        assertEquals("https://cdn.example/akkoma_blob_static.png", resolved.emojiMetadata?.staticUrl?.value)
        assertEquals(":akkoma_blob:", resolved.emojiMetadata?.submissionValue)

        val mystery = MastodonReactionExtensionMapper.reactions(unresolved, statusEmoji).orEmpty().single()
        assertNull(mystery.emojiMetadata)
    }

    @Test
    fun compatibilityNestingUnderPleromaIsSupported() {
        val nested = JSONObject().put("pleroma", JSONObject().put("emoji_reactions", JSONArray()
            .put(JSONObject().put("name", ":nested:").put("count", 1).put("me", true))))
        val mapped = MastodonReactionExtensionMapper.reactions(nested, statusEmoji).orEmpty()
        assertEquals(listOf(":nested:"), mapped.map { it.emoji })
        assertEquals(
            listOf(":nested:"),
            MastodonReactionExtensionMapper.selectedChoices(nested, statusEmoji, true).orEmpty()
                .map { it.submissionValue },
        )
    }
}
