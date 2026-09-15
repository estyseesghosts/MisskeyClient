package me.foxtails.palustris

import me.foxtails.palustris.data.notifications.NotificationRepositoryState
import me.foxtails.palustris.data.notifications.hasReceivingAccount
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.EntityId
import me.foxtails.palustris.domain.Notification
import me.foxtails.palustris.domain.NotificationActivity
import me.foxtails.palustris.domain.NotificationCheckpoint
import me.foxtails.palustris.domain.NotificationDeliveryRecord
import me.foxtails.palustris.domain.NotificationGroup
import me.foxtails.palustris.domain.NotificationGroupId
import me.foxtails.palustris.domain.NotificationQuery
import me.foxtails.palustris.domain.Protocol
import me.foxtails.palustris.domain.PushRegistration
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Only receiving-account fields participate in ownership. A remote actor must not reject a
 * state that the receiving account owns.
 */
class NotificationStateOwnershipTest {
    private val receiver = AccountId(Connection("https://misskey.example", Protocol.MISSKEY), "receiver")
    private val other = AccountId(Connection("https://misskey.example", Protocol.MISSKEY), "other")
    private val remoteActor = AccountId(Connection("https://remote.example", Protocol.MASTODON), "actor")

    private fun notification(owner: AccountId, groupOwner: AccountId? = null) = Notification(
        id = EntityId("https://misskey.example", "n1"),
        accountId = owner,
        createdAtEpochMillis = 1,
        activity = NotificationActivity.Mention,
        actors = listOf(Account(id = remoteActor, displayName = "Remote", handle = "@remote@remote.example")),
        rawType = "mention",
        group = groupOwner?.let { NotificationGroup(NotificationGroupId(it, "g1")) },
    )

    @Test
    fun stateForTheReceivingAccountIsAccepted() {
        val state = NotificationRepositoryState(items = listOf(notification(receiver)))
        assertTrue(state.hasReceivingAccount(receiver))
    }

    @Test
    fun remoteActorDoesNotRejectTheState() {
        val state = NotificationRepositoryState(items = listOf(notification(receiver)))
        assertTrue(state.hasReceivingAccount(receiver))
    }

    @Test
    fun foreignNotificationOwnerIsRejected() {
        val state = NotificationRepositoryState(items = listOf(notification(other)))
        assertFalse(state.hasReceivingAccount(receiver))
    }

    @Test
    fun foreignGroupOwnerIsRejected() {
        val state = NotificationRepositoryState(items = listOf(notification(receiver, groupOwner = other)))
        assertFalse(state.hasReceivingAccount(receiver))
    }

    @Test
    fun foreignDeliveryOwnerIsRejected() {
        val record = NotificationDeliveryRecord(
            accountId = other,
            notificationId = EntityId("https://misskey.example", "n1"),
            androidTag = "tag",
            androidId = 1,
        )
        val state = NotificationRepositoryState(deliveries = mapOf(record.notificationId to record))
        assertFalse(state.hasReceivingAccount(receiver))
    }

    @Test
    fun foreignCheckpointOwnerIsRejected() {
        val singular = NotificationRepositoryState(checkpoint = NotificationCheckpoint(other, NotificationQuery()))
        assertFalse(singular.hasReceivingAccount(receiver))

        val keyed = NotificationRepositoryState(
            checkpoints = mapOf("All|30|false" to NotificationCheckpoint(other, NotificationQuery())),
        )
        assertFalse(keyed.hasReceivingAccount(receiver))
    }

    @Test
    fun foreignPushOwnerIsRejected() {
        val push = PushRegistration(accountId = other, generation = 1, instanceName = "push-instance")
        val state = NotificationRepositoryState(pushRegistration = push)
        assertFalse(state.hasReceivingAccount(receiver))
    }

    @Test
    fun foreignDismissalOriginIsRejected() {
        val state = NotificationRepositoryState(dismissedIds = setOf(EntityId("https://other.example", "d1")))
        assertFalse(state.hasReceivingAccount(receiver))
    }
}
