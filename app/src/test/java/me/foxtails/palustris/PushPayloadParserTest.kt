package me.foxtails.palustris

import java.nio.charset.StandardCharsets
import me.foxtails.palustris.data.notifications.push.PushPayloadHint
import me.foxtails.palustris.data.notifications.push.PushPayloadParser
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PushPayloadParserTest {
    @Test
    fun classifiesNotificationHintsWithoutRetainingPayload() {
        assertEquals(
            PushPayloadHint.Notification,
            PushPayloadParser.classify("{\"notification_id\":\"opaque\",\"type\":\"mention\"}".toByteArray()),
        )
    }

    @Test
    fun rejectsCredentialBearingPayloads() {
        assertEquals(
            PushPayloadHint.RejectedSensitive,
            PushPayloadParser.classify("{\"notification_id\":\"opaque\",\"access_token\":\"redacted\"}".toByteArray()),
        )
    }

    @Test
    fun malformedAndOversizedPayloadsBecomeRefreshHints() {
        assertEquals(PushPayloadHint.Refresh, PushPayloadParser.classify("not-json".toByteArray()))
        assertEquals(
            PushPayloadHint.Refresh,
            PushPayloadParser.classify(ByteArray(64 * 1024 + 1) { 'x'.code.toByte() }),
        )
    }

    @Test
    fun emptyObjectDoesNotStartNetworkWork() {
        assertEquals(PushPayloadHint.Ignored, PushPayloadParser.classify("{}".toByteArray(StandardCharsets.UTF_8)))
    }
}
