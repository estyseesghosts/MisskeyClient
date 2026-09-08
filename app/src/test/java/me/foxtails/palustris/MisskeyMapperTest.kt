package me.foxtails.palustris

import me.foxtails.palustris.data.misskey.MisskeyMapper
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MisskeyMapperTest {
    @Test
    fun pureRenoteKeepsOuterRowIdButUsesDisplayedNoteForActions() {
        val user = JSONObject()
            .put("id", "user-a")
            .put("username", "alice")
            .put("name", "Alice")
            .put("host", JSONObject.NULL)
        val original = JSONObject()
            .put("id", "original-id")
            .put("createdAt", "2026-09-06T10:00:00Z")
            .put("user", user)
            .put("text", "Original")
        val renote = JSONObject()
            .put("id", "outer-id")
            .put("createdAt", "2026-09-06T11:00:00Z")
            .put("user", user)
            .put("text", JSONObject.NULL)
            .put("renote", original)

        val post = MisskeyMapper.post(renote, "https://example.org")

        assertEquals("outer-id", post.id.value)
        assertEquals("original-id", post.actionTargetId?.value)
    }
}
