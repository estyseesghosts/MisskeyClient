package me.foxtails.palustris

import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Fixtures parse, identify their software/version, and contain no secrets. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ProtocolFixtureValidationTest {
    private fun load(path: String): String = checkNotNull(
        javaClass.classLoader.getResource(path),
    ) { "missing fixture $path" }.readText()

    private fun json(path: String): JSONObject = JSONObject(load(path))

    private val secretPatterns = listOf(
        Regex("(?i)access[_-]?token"),
        Regex("(?i)bearer\\s+[A-Za-z0-9._-]+"),
        Regex("(?i)client[_-]?secret"),
        Regex("(?i)session[_-]?(token|id)"),
        Regex("(?i)password"),
    )

    private fun assertNoSecrets(path: String) {
        val body = load(path)
        secretPatterns.forEach { pattern ->
            assertFalse("fixture $path contains a secret pattern", pattern.containsMatchIn(body))
        }
    }

    private fun assertVersionIdentified(path: String, software: String) {
        val body = load(path)
        val normalized = body.lowercase()
        assertTrue("fixture $path must identify $software", software in normalized)
        assertTrue("fixture $path must be version-pinned", Regex("\\d+\\.\\d+").containsMatchIn(body))
    }

    @Test fun allFixturesParseAndContainNoSecrets() {
        val fixtures = listOf(
            "fixtures/mastodon/standard-account-custom-emojis.json",
            "fixtures/mastodon/standard-status-custom-emojis.json",
            "fixtures/mastodon/nested-reblog-and-quote-custom-emojis.json",
            "fixtures/misskey/user-and-note-custom-emojis.json",
            "fixtures/misskey/note-reactions.json",
            "fixtures/misskey/reaction-notifications.json",
            "fixtures/akkoma/instance.json",
            "fixtures/akkoma/status-emoji-reactions.json",
            "fixtures/akkoma/emoji-reaction-notification.json",
        )
        fixtures.forEach { path ->
            val parsed = JSONTokener(load(path)).nextValue()
            assertTrue("fixture $path must contain a JSON object or array", parsed is JSONObject || parsed is JSONArray)
            assertNoSecrets(path)
        }
    }

    @Test fun akkomaInstanceIdentifiesSoftwareAndVersion() {
        val instance = json("fixtures/akkoma/instance.json")
        assertEquals("akkoma", instance.getJSONObject("software").getString("name"))
        assertVersionIdentified("fixtures/akkoma/instance.json", "akkoma")
    }

    @Test fun akkomaStatusNestsTopLevelEmojiReactionsWithMultipleSelections() {
        val status = json("fixtures/akkoma/status-emoji-reactions.json")
        val reactions = status.getJSONArray("emoji_reactions")
        assertEquals(4, reactions.length())
        val selected = (0 until reactions.length()).count { reactions.getJSONObject(it).optBoolean("me") }
        assertEquals(2, selected)
        assertEquals(":akkoma_blob:", reactions.getJSONObject(0).getString("name"))
    }

    @Test fun akkomaNotificationUsesTheConfirmedExtensionType() {
        val notification = json("fixtures/akkoma/emoji-reaction-notification.json")
        assertEquals("pleroma:emoji_reaction", notification.getString("type"))
        assertTrue(notification.has("emoji") && notification.has("emoji_url"))
        assertTrue(notification.getJSONObject("status").has("emojis"))
    }

    @Test fun misskeyReactionNotificationEstablishesGroupedNestingAndType() {
        val notifications = JSONArray(load("fixtures/misskey/reaction-notifications.json"))
        assertEquals(2, notifications.length())
        assertEquals("reaction:grouped", notifications.getJSONObject(0).getString("type"))
        assertTrue(notifications.getJSONObject(0).has("reactions"))
        assertTrue(notifications.getJSONObject(0).getJSONObject("note").has("reactionEmojis"))
        assertEquals("reaction", notifications.getJSONObject(1).getString("type"))
    }
}
