package me.foxtails.palustris

import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.EntityId
import me.foxtails.palustris.domain.Notification
import me.foxtails.palustris.domain.NotificationActivity
import me.foxtails.palustris.domain.NotificationDestination
import me.foxtails.palustris.domain.NotificationTarget
import me.foxtails.palustris.domain.Protocol
import me.foxtails.palustris.ui.navigation.AppRoute
import me.foxtails.palustris.ui.notifications.NotificationRouteResolver
import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationRouteResolverTest {
    private val account = AccountId(Connection("https://example.org", Protocol.MASTODON), "receiver")

    @Test
    fun postTargetKeepsReceivingAccountAndCanonicalTarget() {
        val postId = EntityId(account.connection.origin, "status")
        val notification = Notification(
            id = EntityId(account.connection.origin, "notification"),
            accountId = account,
            createdAtEpochMillis = 1L,
            activity = NotificationActivity.Mention,
            target = NotificationTarget.Post(postId),
            destination = NotificationDestination.InApp(NotificationTarget.Post(postId)),
            rawType = "mention",
        )

        assertEquals(AppRoute.Post(account, postId), NotificationRouteResolver.resolve(notification))
    }

    @Test
    fun invalidLaunchTargetCannotCrossConnectionBoundary() {
        val foreignId = EntityId("https://other.example", "notification")

        assertEquals(
            AppRoute.AccountUnavailable(account, foreignId),
            NotificationRouteResolver.detail(account, foreignId),
        )
    }
}
