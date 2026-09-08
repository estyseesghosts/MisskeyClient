package me.foxtails.palustris

import me.foxtails.palustris.data.notifications.push.MisskeyPushPayload
import me.foxtails.palustris.data.notifications.push.MisskeyPushPayloadParser
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MisskeyPushPayloadParserTest {
    @Test
    fun parsesNotificationBody() {
        val result = MisskeyPushPayloadParser.parse(
            """{"type":"notification","body":{"id":"n1","type":"mention"}}""".toByteArray(),
        )
        assertTrue(result is MisskeyPushPayload.Notification)
        assertEquals("n1", (result as MisskeyPushPayload.Notification).body.getString("id"))
    }

    @Test
    fun parsesReadAllAndChatEvents() {
        assertEquals(
            MisskeyPushPayload.ReadAllNotifications,
            MisskeyPushPayloadParser.parse("""{"type":"readAllNotifications"}""".toByteArray()),
        )
        assertTrue(
            MisskeyPushPayloadParser.parse(
                """{"type":"newChatMessage","body":{"id":"m1"}}""".toByteArray(),
            ) is MisskeyPushPayload.NewChatMessage,
        )
    }

    @Test
    fun normalizesJsonStringBodyAndNumericDateTime() {
        val result = MisskeyPushPayloadParser.parse(
            """{"type":"notification","body":"{\"id\":\"n1\",\"dateTime\":\"1234\"}"}""".toByteArray(),
        ) as MisskeyPushPayload.Notification
        assertEquals(1234L, result.body.getLong("dateTime"))
    }

    @Test
    fun unknownMalformedEmptyOversizedAndDeepPayloadsRefresh() {
        assertEquals(
            MisskeyPushPayload.Refresh,
            MisskeyPushPayloadParser.parse("{}".toByteArray()),
        )
        assertEquals(
            MisskeyPushPayload.Refresh,
            MisskeyPushPayloadParser.parse("not-json".toByteArray()),
        )
        assertEquals(
            MisskeyPushPayload.Refresh,
            MisskeyPushPayloadParser.parse(ByteArray(64 * 1024 + 1) { 'x'.code.toByte() }),
        )
        var nested = JSONObject().put("value", true)
        repeat(10) { nested = JSONObject().put("next", nested) }
        assertEquals(
            MisskeyPushPayload.Refresh,
            MisskeyPushPayloadParser.parse(JSONObject().put("type", "notification").put("body", nested).toString().toByteArray()),
        )
    }
}
