package me.foxtails.palustris

import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.Protocol
import me.foxtails.palustris.ui.settings.ModerationKind
import me.foxtails.palustris.ui.settings.SettingsRoute
import me.foxtails.palustris.ui.settings.decodeSettingsRoute
import me.foxtails.palustris.ui.settings.encodeSettingsRoute
import me.foxtails.palustris.ui.settings.restoreSettingsRoute
import me.foxtails.palustris.ui.settings.safeParent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SettingsRouteRestorationTest {
    private val account = AccountId(Connection("https://example.org", Protocol.MISSKEY), "person")

    @Test
    fun everyObjectRouteRoundTrips() {
        listOf(
            SettingsRoute.Main,
            SettingsRoute.Display,
            SettingsRoute.Notifications,
            SettingsRoute.Privacy,
            SettingsRoute.Language,
            SettingsRoute.ContentWarnings,
            SettingsRoute.Posting,
            SettingsRoute.PrivacyAccounts,
        ).forEach { route ->
            assertEquals(route, decodeSettingsRoute(encodeSettingsRoute(route)))
        }
    }

    @Test
    fun accountRoutesPreserveOriginProtocolAndLocalId() {
        val route = SettingsRoute.NotificationAccount(account)
        val decoded = decodeSettingsRoute(encodeSettingsRoute(route)) as SettingsRoute.NotificationAccount
        assertEquals("https://example.org", decoded.accountId.connection.origin)
        assertEquals(Protocol.MISSKEY, decoded.accountId.connection.protocol)
        assertEquals("person", decoded.accountId.localId)

        val moderation = SettingsRoute.Moderation(account, ModerationKind.Muted)
        assertEquals(moderation, decodeSettingsRoute(encodeSettingsRoute(moderation)))
    }

    @Test
    fun malformedAndUnknownValuesAreRejected() {
        assertNull(decodeSettingsRoute(emptyList<String>()))
        assertNull(decodeSettingsRoute(listOf("Unknown")))
        assertNull(decodeSettingsRoute(listOf("NotificationAccount", "not-a-url", "MISSKEY", "person")))
        assertNull(decodeSettingsRoute(listOf("NotificationAccount", "https://example.org", "BOGUS", "person")))
        assertNull(decodeSettingsRoute(listOf("NotificationAccount", "https://example.org", "MISSKEY")))
        assertNull(decodeSettingsRoute(listOf("Moderation", "https://example.org", "MISSKEY", "person", "Nope")))
    }

    @Test
    fun saverRestoresMalformedValuesToMain() {
        assertEquals(SettingsRoute.Main, restoreSettingsRoute(listOf("Unknown")))
        assertEquals(SettingsRoute.Main, restoreSettingsRoute(emptyList<String>()))
        assertEquals(
            SettingsRoute.Notifications,
            restoreSettingsRoute(encodeSettingsRoute(SettingsRoute.Notifications)),
        )
    }

    @Test
    fun accountRoutesFallBackToTheirSafeParent() {
        assertEquals(
            SettingsRoute.Notifications,
            SettingsRoute.NotificationAccount(account).safeParent(),
        )
        assertEquals(
            SettingsRoute.PrivacyAccounts,
            SettingsRoute.Moderation(account, ModerationKind.Blocked).safeParent(),
        )
        assertEquals(SettingsRoute.Main, SettingsRoute.Display.safeParent())
    }
}
