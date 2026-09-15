package me.foxtails.palustris

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.io.File
import me.foxtails.palustris.data.notifications.FileNotificationStore
import me.foxtails.palustris.data.notifications.NotificationRepositoryState
import me.foxtails.palustris.data.notifications.decode
import me.foxtails.palustris.data.notifications.encode
import me.foxtails.palustris.data.notifications.stableFileName
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.CustomEmoji
import me.foxtails.palustris.domain.EntityId
import me.foxtails.palustris.domain.NotificationActivity
import me.foxtails.palustris.domain.NotificationCategory
import me.foxtails.palustris.domain.NotificationDeliveryState
import me.foxtails.palustris.domain.NotificationDestination
import me.foxtails.palustris.domain.NotificationPushRegistrationState
import me.foxtails.palustris.domain.NotificationReaction
import me.foxtails.palustris.domain.NotificationReadStatus
import me.foxtails.palustris.domain.NotificationSettings
import me.foxtails.palustris.domain.NotificationSyncCompleteness
import me.foxtails.palustris.domain.NotificationTarget
import me.foxtails.palustris.domain.NotificationUnreadState
import me.foxtails.palustris.domain.Protocol
import me.foxtails.palustris.domain.ValidatedUrl
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Frozen decoder and encoder contract for the notification stored state.
 *
 * The fixtures are literal JSON. The tests never generate the expected input with the
 * encoder under test. See `app/src/test/resources/notifications/PROVENANCE.md`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class NotificationJsonCodecTest {
    private val misskeyReceiver = AccountId(Connection("https://misskey.example", Protocol.MISSKEY), "receiver")
    private val mastodonReceiver = AccountId(Connection("https://mastodon.example", Protocol.MASTODON), "receiver")

    @Test
    fun completeCurrentStateDecodesEveryTopLevelKey() {
        val state = decode(fixture("complete_current_state.json"))

        assertEquals(2, state.items.size)
        val first = state.items[0]
        assertEquals(EntityId("https://misskey.example", "notif-1"), first.id)
        assertEquals(misskeyReceiver, first.accountId)
        assertEquals(NotificationActivity.Mention, first.activity)
        assertEquals(
            NotificationDestination.InApp(NotificationTarget.Post(EntityId("https://misskey.example", "post-1"))),
            first.destination,
        )
        assertEquals(3, first.group?.totalCount)
        assertEquals(NotificationReadStatus.Unread, first.readState.status)
        assertTrue(first.readState.locallySeen)
        val second = state.items[1]
        assertEquals(mastodonReceiver, second.accountId)
        assertEquals(Protocol.MASTODON, second.accountId.connection.protocol)
        assertTrue(second.readState.androidDismissed)
        assertTrue(second.readState.serverAcknowledged)

        assertEquals(NotificationUnreadState.AtLeast(2), state.unreadState)
        assertEquals(setOf(NotificationCategory.Mentions), state.checkpoint?.query?.categories)
        assertEquals("cursor-newest", state.checkpoint?.newest?.value)
        assertEquals("cursor-older", state.checkpoint?.olderContinuation?.value)
        assertEquals(NotificationSyncCompleteness.Complete, state.checkpoint?.completeness)
        assertTrue(state.checkpoint?.baselineEstablished == true)
        assertEquals(5000L, state.lastSyncedAtEpochMillis)
        assertEquals(setOf(EntityId("https://misskey.example", "dismissed-1")), state.dismissedIds)
        assertEquals(setOf("Social|50|true"), state.checkpoints.keys)
        assertEquals(NotificationSyncCompleteness.Incomplete, state.checkpoints.getValue("Social|50|true").completeness)

        val delivery = state.deliveries.getValue(EntityId("https://misskey.example", "notif-1"))
        assertEquals(NotificationDeliveryState.Presented, delivery.state)
        assertEquals("claim-1", delivery.claimId)
        assertEquals(42, delivery.androidId)
        assertEquals(3, delivery.attemptCount)

        assertEquals(
            NotificationSettings(
                alertsEnabled = true,
                categories = setOf(NotificationCategory.Mentions, NotificationCategory.Replies),
                showPreviews = false,
                quietHoursStartMinutes = 1320,
                quietHoursEndMinutes = 420,
                periodicFallbackEnabled = true,
                selectedDistributor = "org.example.distributor",
            ),
            state.settings,
        )
        assertEquals(NotificationPushRegistrationState.Connected, state.pushRegistration?.state)
        assertEquals("https://push.example/server-endpoint", state.pushRegistration?.serverEndpoint?.value)
        assertEquals("remote-1", state.pushRegistration?.serverRemoteId)
        assertEquals(3L, state.pushRegistration?.sessionRevision)
    }

    @Test
    fun completeCurrentStateEncodesToTheFrozenFixture() {
        val fixture = fixture("complete_current_state.json")

        assertJsonEquals(fixture, encode(decode(fixture)))
    }

    @Test
    fun completeCurrentStateRoundTripsAtTheStateBoundary() {
        val decoded = decode(fixture("complete_current_state.json"))

        assertEquals(decoded, decode(encode(decoded)))
    }

    @Test
    fun encoderWritesVersionTwo() {
        assertEquals(2, encode(NotificationRepositoryState()).getInt("version"))
    }

    @Test
    fun decoderIgnoresUnknownVersion() {
        assertEquals(NotificationRepositoryState(), decode(JSONObject("""{"version":99}""")))
    }

    @Test
    fun legacyMinimalStateDecodesWithDocumentedDefaults() {
        val state = decode(fixture("legacy_minimal_state.json"))

        assertEquals(1, state.items.size)
        assertEquals(NotificationUnreadState.Unknown, state.unreadState)
        assertNull(state.checkpoint)
        assertEquals(0L, state.lastSyncedAtEpochMillis)
        assertTrue(state.dismissedIds.isEmpty())
        assertTrue(state.checkpoints.isEmpty())
        assertTrue(state.deliveries.isEmpty())
        assertEquals(NotificationSettings(), state.settings)
        assertNull(state.pushRegistration)

        val item = state.items.single()
        assertEquals(NotificationReadStatus.Unread, item.readState.status)
        assertFalse(item.readState.locallySeen)
        val actor = item.actors.single()
        assertEquals("", actor.biography)
        assertNull(actor.avatarUrl)
        assertNull(actor.followersCount)
        assertTrue(actor.emoji.isEmpty())
        assertNull(actor.movedTo)
    }

    @Test
    fun legacyTargetOnlyNavigationBecomesInAppDestination() {
        val item = decode(fixture("legacy_minimal_state.json")).items.single()

        assertEquals(
            NotificationDestination.InApp(NotificationTarget.Post(EntityId("https://misskey.example", "post-9"))),
            item.destination,
        )
    }

    @Test
    fun legacyReactionImageUrlUpgradesToCustomEmoji() {
        val item = decode(fixture("legacy_minimal_state.json")).items.single()
        val reaction = item.activity as NotificationActivity.EmojiReaction

        assertEquals(":blobcat:", reaction.reaction.identity)
        assertEquals("blobcat", reaction.reaction.fallbackText)
        val emoji = reaction.reaction.emoji
        assertNotNull(emoji)
        assertEquals("blobcat", emoji?.shortcode)
        assertEquals("https://misskey.example/blobcat.png", emoji?.animatedUrl?.value)
        assertEquals("https://misskey.example/blobcat.png", emoji?.staticUrl?.value)
        assertEquals(":blobcat:", emoji?.submissionValue)
        assertFalse(emoji?.visibleInPicker ?: true)
    }

    @Test
    fun fileStoreReadsFixedJsonFromDisk() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = FileNotificationStore(context)
        val accountId = misskeyReceiver
        val directory = File(context.noBackupFilesDir, "notifications")
        directory.mkdirs()
        File(directory, "${accountId.stableFileName()}.json").writeText(fixtureText("complete_current_state.json"))

        val state = store.read(accountId)

        assertNotNull(state)
        assertEquals(2, state?.items?.size)
        assertEquals(NotificationUnreadState.AtLeast(2), state?.unreadState)
        assertEquals(NotificationPushRegistrationState.Connected, state?.pushRegistration?.state)
    }

    @Test
    fun fileStoreWriteMatchesTheEncoderContract() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = FileNotificationStore(context)
        val fixture = fixture("complete_current_state.json")

        store.write(misskeyReceiver, decode(fixture))

        val written = File(
            File(context.noBackupFilesDir, "notifications"),
            "${misskeyReceiver.stableFileName()}.json",
        ).readText()
        assertJsonEquals(fixture, JSONObject(written))
    }

    @Test
    fun activityVariantsDecodeEveryDiscriminant() {
        val items = decode(fixture("activity_variants.json")).items.associateBy { it.id.value }

        assertEquals(19, items.size)
        assertEquals(NotificationActivity.Mention, items.getValue("a-mention").activity)
        assertEquals(NotificationActivity.Reply, items.getValue("a-reply").activity)
        assertEquals(NotificationActivity.Reshare, items.getValue("a-reshare").activity)
        assertEquals(NotificationActivity.Quote, items.getValue("a-quote").activity)
        assertEquals(NotificationActivity.Favourite, items.getValue("a-favourite").activity)
        assertEquals(
            NotificationActivity.EmojiReaction(
                NotificationReaction(
                    identity = ":blobcat:",
                    fallbackText = "blobcat",
                    emoji = CustomEmoji(
                        shortcode = "blobcat",
                        animatedUrl = ValidatedUrl.https("https://misskey.example/blobcat.gif"),
                        staticUrl = ValidatedUrl.https("https://misskey.example/blobcat.png"),
                        category = "cats",
                        aliases = listOf("bc"),
                        visibleInPicker = true,
                        submissionValue = ":blobcat:",
                    ),
                ),
            ),
            items.getValue("a-reaction").activity,
        )
        assertEquals(NotificationActivity.Follow, items.getValue("a-follow").activity)
        assertEquals(NotificationActivity.FollowRequest, items.getValue("a-follow-request").activity)
        assertEquals(NotificationActivity.AcceptedRequest, items.getValue("a-accepted").activity)
        assertEquals(NotificationActivity.SubscribedPost, items.getValue("a-subscribed").activity)
        assertEquals(NotificationActivity.PollResult("Cats"), items.getValue("a-poll").activity)
        assertEquals(NotificationActivity.PostUpdate, items.getValue("a-post-update").activity)
        assertEquals(NotificationActivity.QuotedPostUpdate, items.getValue("a-quoted-update").activity)
        assertEquals(NotificationActivity.DirectMessage, items.getValue("a-direct").activity)
        assertEquals(
            NotificationActivity.System.Moderation("Moderation", "A post was removed"),
            items.getValue("a-system-moderation").activity,
        )
        assertEquals(
            NotificationActivity.System.RelationshipChange("Follow changed", "A user followed you"),
            items.getValue("a-system-relationship").activity,
        )
        assertEquals(
            NotificationActivity.System.RoleOrAchievement("New role", "You earned a role"),
            items.getValue("a-system-role").activity,
        )
        assertEquals(
            NotificationActivity.System.AppEvent("Update", "A new version is available"),
            items.getValue("a-system-app").activity,
        )
        assertEquals(
            NotificationActivity.Unknown(
                "New thing",
                NotificationDestination.Server(ValidatedUrl.https("https://misskey.example/notice/9")!!),
            ),
            items.getValue("a-unknown").activity,
        )
    }

    @Test
    fun activityVariantsEncodeToTheFrozenFixture() {
        val fixture = fixture("activity_variants.json")

        assertJsonEquals(fixture, encode(decode(fixture)))
    }

    @Test
    fun navigationVariantsDecodeEveryTargetAndDestination() {
        val items = decode(fixture("navigation_variants.json")).items.associateBy { it.id.value }

        assertEquals(
            NotificationDestination.InApp(NotificationTarget.Post(EntityId("https://misskey.example", "post-1"))),
            items.getValue("n-post").destination,
        )
        assertEquals(
            NotificationDestination.InApp(
                NotificationTarget.Profile(AccountId(Connection("https://misskey.example", Protocol.MISSKEY), "actor-1")),
            ),
            items.getValue("n-profile").destination,
        )
        assertEquals(
            NotificationDestination.InApp(NotificationTarget.Poll(EntityId("https://misskey.example", "poll-1"))),
            items.getValue("n-poll").destination,
        )
        assertEquals(
            NotificationDestination.InApp(
                NotificationTarget.Conversation(EntityId("https://misskey.example", "conversation-1")),
            ),
            items.getValue("n-conversation").destination,
        )
        assertEquals(
            NotificationDestination.Server(ValidatedUrl.https("https://mastodon.example/@actor/1")!!),
            items.getValue("n-server").destination,
        )
    }

    @Test
    fun navigationVariantsEncodeToTheFrozenFixture() {
        val fixture = fixture("navigation_variants.json")

        assertJsonEquals(fixture, encode(decode(fixture)))
    }

    @Test
    fun malformedServerDestinationFallsBackOrDrops() {
        val items = decode(fixture("navigation_malformed.json")).items.associateBy { it.id.value }

        // An unsafe URL with a target falls back to the target's in-app destination.
        assertEquals(
            NotificationDestination.InApp(NotificationTarget.Post(EntityId("https://misskey.example", "post-1"))),
            items.getValue("m-unsafe").destination,
        )
        // An invalid URL with no target drops the destination.
        assertNull(items.getValue("m-invalid").destination)
    }

    @Test
    fun readStatesDecodeIndependently() {
        val items = decode(fixture("read_states.json")).items.associateBy { it.id.value }

        val read = items.getValue("r-read").readState
        assertEquals(NotificationReadStatus.Read, read.status)
        assertTrue(read.locallySeen && read.serverAcknowledged && read.androidPresented && read.androidDismissed)
        val unread = items.getValue("r-unread").readState
        assertEquals(NotificationReadStatus.Unread, unread.status)
        assertFalse(unread.locallySeen || unread.serverAcknowledged || unread.androidPresented || unread.androidDismissed)
        val unknown = items.getValue("r-unknown").readState
        assertEquals(NotificationReadStatus.Unknown, unknown.status)
        assertTrue(unknown.locallySeen)
        assertFalse(unknown.serverAcknowledged)
        assertTrue(unknown.androidPresented)
        assertFalse(unknown.androidDismissed)
    }

    @Test
    fun readStatesEncodeToTheFrozenFixture() {
        val fixture = fixture("read_states.json")

        assertJsonEquals(fixture, encode(decode(fixture)))
    }

    @Test
    fun deliveryVariantsDecodeEveryStateAndClaim() {
        val deliveries = decode(fixture("delivery_variants.json")).deliveries

        assertEquals(5, deliveries.size)
        assertEquals(
            NotificationDeliveryState.Pending,
            deliveries.getValue(EntityId("https://misskey.example", "d-pending")).state,
        )
        assertEquals(
            NotificationDeliveryState.Posting,
            deliveries.getValue(EntityId("https://misskey.example", "d-posting")).state,
        )
        assertEquals(
            NotificationDeliveryState.Presented,
            deliveries.getValue(EntityId("https://misskey.example", "d-presented")).state,
        )
        assertEquals(
            NotificationDeliveryState.Suppressed,
            deliveries.getValue(EntityId("https://misskey.example", "d-suppressed")).state,
        )
        val posting = deliveries.getValue(EntityId("https://misskey.example", "d-posting"))
        assertEquals("claim-1", posting.claimId)
        assertEquals(200L, posting.claimExpiresAtEpochMillis)
        val failed = deliveries.getValue(EntityId("https://misskey.example", "d-failed"))
        assertEquals(NotificationDeliveryState.Failed, failed.state)
        assertEquals("network", failed.lastErrorCategory)
        assertEquals(4, failed.attemptCount)
    }

    @Test
    fun deliveryVariantsEncodeToTheFrozenFixture() {
        val fixture = fixture("delivery_variants.json")

        assertJsonEquals(fixture, encode(decode(fixture)))
    }

    @Test
    fun duplicateDeliveryIdsKeepTheLastRecord() {
        val deliveries = decode(fixture("delivery_duplicate_ids.json")).deliveries

        assertEquals(1, deliveries.size)
        val record = deliveries.getValue(EntityId("https://misskey.example", "d-duplicate"))
        assertEquals(NotificationDeliveryState.Presented, record.state)
        assertEquals("tag-last", record.androidTag)
    }

    private fun fixture(name: String): JSONObject = JSONObject(fixtureText(name))

    private fun fixtureText(name: String): String {
        val stream = javaClass.getResourceAsStream("/notifications/$name")
            ?: error("Missing notification fixture: $name")
        return stream.use { String(it.readBytes(), Charsets.UTF_8) }
    }

    private fun assertJsonEquals(expected: Any?, actual: Any?, path: String = "$") {
        when {
            expected is JSONObject && actual is JSONObject -> {
                assertEquals("keys at $path", expected.keys().asSequence().toSet(), actual.keys().asSequence().toSet())
                expected.keys().forEach { key -> assertJsonEquals(expected.get(key), actual.get(key), "$path.$key") }
            }
            expected is JSONArray && actual is JSONArray -> {
                assertEquals("length at $path", expected.length(), actual.length())
                for (index in 0 until expected.length()) {
                    assertJsonEquals(expected.get(index), actual.get(index), "$path[$index]")
                }
            }
            expected is Number && actual is Number -> {
                assertTrue("number at $path", expected.toDouble() == actual.toDouble())
            }
            else -> assertEquals("value at $path", expected, actual)
        }
    }
}
