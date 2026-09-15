package me.foxtails.palustris

import me.foxtails.palustris.data.auth.AccountRef
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.Protocol
import me.foxtails.palustris.ui.settings.ModerationKind
import me.foxtails.palustris.ui.settings.SettingsRoute
import me.foxtails.palustris.ui.settings.decodeSettingsRoute
import me.foxtails.palustris.ui.settings.encodeSettingsRoute
import me.foxtails.palustris.ui.settings.moderationRouteFor
import me.foxtails.palustris.ui.settings.notificationAccountFor
import me.foxtails.palustris.ui.settings.resolveAccountRoute
import me.foxtails.palustris.ui.settings.restoreSettingsRoute
import me.foxtails.palustris.ui.settings.safeParent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SettingsRouteRestorationTest {
    private val account = AccountId(Connection("https://example.org", Protocol.MISSKEY), "person")
    private val other = AccountId(Connection("https://example.org", Protocol.MISSKEY), "other")
    private fun ref(id: AccountId) = AccountRef(id, "@${id.localId}", null, id.localId)

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

    @Test
    fun pendingRouteSurvivesWhileTheAccountIndexLoads() {
        val route = SettingsRoute.NotificationAccount(account)
        assertEquals(route, resolveAccountRoute(route, emptyList(), accountsReady = false))
        assertNull(notificationAccountFor(route, emptyList(), accountsReady = false))

        val moderation = SettingsRoute.Moderation(account, ModerationKind.Muted)
        assertEquals(moderation, resolveAccountRoute(moderation, emptyList(), accountsReady = false))
        assertNull(moderationRouteFor(moderation, emptyList(), accountsReady = false))
    }

    @Test
    fun presentAccountRouteStaysAndConstructsItsModel() {
        val accounts = listOf(ref(account), ref(other))
        val route = SettingsRoute.NotificationAccount(account)
        assertEquals(route, resolveAccountRoute(route, accounts, accountsReady = true))
        assertEquals(account, notificationAccountFor(route, accounts, accountsReady = true))

        val moderation = SettingsRoute.Moderation(account, ModerationKind.Blocked)
        assertEquals(moderation, resolveAccountRoute(moderation, accounts, accountsReady = true))
        assertEquals(moderation, moderationRouteFor(moderation, accounts, accountsReady = true))
    }

    @Test
    fun removedAccountRouteFallsBackWithoutMappingToTheActiveAccount() {
        val accounts = listOf(ref(other))
        val route = SettingsRoute.NotificationAccount(account)
        assertEquals(SettingsRoute.Notifications, resolveAccountRoute(route, accounts, accountsReady = true))
        assertNull(notificationAccountFor(route, accounts, accountsReady = true))

        val moderation = SettingsRoute.Moderation(account, ModerationKind.Muted)
        assertEquals(
            SettingsRoute.PrivacyAccounts,
            resolveAccountRoute(moderation, accounts, accountsReady = true),
        )
        assertNull(moderationRouteFor(moderation, accounts, accountsReady = true))
    }

    @Test
    fun nonAccountRoutesNeverRemap() {
        val accounts = listOf(ref(other))
        assertEquals(
            SettingsRoute.Language,
            resolveAccountRoute(SettingsRoute.Language, accounts, accountsReady = true),
        )
        assertEquals(
            SettingsRoute.Language,
            resolveAccountRoute(SettingsRoute.Language, emptyList(), accountsReady = false),
        )
    }
}
