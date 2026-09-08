package me.foxtails.palustris

import me.foxtails.palustris.data.notifications.AndroidNotificationIds
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.EntityId
import me.foxtails.palustris.domain.Protocol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidNotificationIdsTest {
    private val first = AccountId(Connection("https://example.org", Protocol.MISSKEY), "first")
    private val second = first.copy(localId = "second")

    @Test
    fun idsAreStableAndAccountScoped() {
        val event = EntityId(first.connection.origin, "event")
        assertEquals(AndroidNotificationIds.id(event), AndroidNotificationIds.id(event))
        assertNotEquals(AndroidNotificationIds.tag(first), AndroidNotificationIds.tag(second))
        assertNotEquals(AndroidNotificationIds.group(first), AndroidNotificationIds.group(second))
        assertTrue(AndroidNotificationIds.id(event) > 0)
    }
}
