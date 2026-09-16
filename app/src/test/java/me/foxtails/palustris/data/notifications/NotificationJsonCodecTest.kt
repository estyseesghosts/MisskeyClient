package me.foxtails.palustris.data.notifications

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.io.File
import me.foxtails.palustris.data.notifications.FileNotificationStore
import me.foxtails.palustris.data.notifications.NotificationRepositoryState
import me.foxtails.palustris.data.notifications.NotificationStoreRead
import me.foxtails.palustris.data.notifications.decode
import me.foxtails.palustris.data.notifications.encode
import me.foxtails.palustris.data.notifications.isFutureNotificationStateVersion
import me.foxtails.palustris.data.notifications.stableFileName
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.Audience
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.CustomEmoji
import me.foxtails.palustris.domain.EntityId
import me.foxtails.palustris.domain.Notification
import me.foxtails.palustris.domain.NotificationActivity
import me.foxtails.palustris.domain.NotificationCategory
import me.foxtails.palustris.domain.NotificationCursor
import me.foxtails.palustris.domain.NotificationDeliveryState
import me.foxtails.palustris.domain.NotificationDestination
import me.foxtails.palustris.domain.NotificationGroup
import me.foxtails.palustris.domain.NotificationGroupId
import me.foxtails.palustris.domain.NotificationLabel
import me.foxtails.palustris.domain.NotificationLabelCode
import me.foxtails.palustris.domain.NotificationPushRegistrationState
import me.foxtails.palustris.domain.NotificationReaction
import me.foxtails.palustris.domain.NotificationReadStatus
import me.foxtails.palustris.domain.NotificationSettings
import me.foxtails.palustris.domain.NotificationSyncCompleteness
import me.foxtails.palustris.domain.NotificationTarget
import me.foxtails.palustris.domain.NotificationUnreadState
import me.foxtails.palustris.domain.PollOption
import me.foxtails.palustris.domain.PostAction
import me.foxtails.palustris.domain.PostContentVisibility
import me.foxtails.palustris.domain.ProfileField
import me.foxtails.palustris.domain.Protocol
import me.foxtails.palustris.domain.PushRegistrationFailureReason
import me.foxtails.palustris.domain.PushRegistrationFailureStage
import me.foxtails.palustris.domain.ValidatedUrl
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
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
        assertEquals(misskeyReceiver, second.accountId)
        assertEquals(Protocol.MISSKEY, second.accountId.connection.protocol)
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
    fun onlyVersionsNewerThanCurrentAreFuture() {
        assertFalse(JSONObject("{}").isFutureNotificationStateVersion())
        assertFalse(JSONObject("""{"version":0}""").isFutureNotificationStateVersion())
        assertFalse(JSONObject("""{"version":1}""").isFutureNotificationStateVersion())
        assertFalse(JSONObject("""{"version":2}""").isFutureNotificationStateVersion())
        assertTrue(JSONObject("""{"version":3}""").isFutureNotificationStateVersion())
        assertTrue(JSONObject("""{"version":99}""").isFutureNotificationStateVersion())
    }

    @Test
    fun fileStoreRefusesFutureFormatAndKeepsTheBytes() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = FileNotificationStore(context)
        val directory = File(context.noBackupFilesDir, "notifications")
        directory.mkdirs()
        val file = File(directory, "${misskeyReceiver.stableFileName()}.json")
        val bytes = """{"version":3,"items":[]}"""
        file.writeText(bytes)

        assertEquals(NotificationStoreRead.Unsupported, store.read(misskeyReceiver))
        assertEquals(bytes, file.readText())
    }

    @Test
    fun fileStoreReadsLegacyAndCurrentFormats() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = FileNotificationStore(context)
        val directory = File(context.noBackupFilesDir, "notifications")
        directory.mkdirs()
        val file = File(directory, "${misskeyReceiver.stableFileName()}.json")

        file.writeText("""{"items":[]}""")
        assertTrue(store.read(misskeyReceiver) is NotificationStoreRead.Readable)

        store.write(misskeyReceiver, NotificationRepositoryState())
        assertTrue(store.read(misskeyReceiver) is NotificationStoreRead.Readable)
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
        assertEquals(NotificationLabel.Plain("blobcat"), reaction.reaction.fallbackText)
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

        val state = (store.read(accountId) as NotificationStoreRead.Readable).state

        assertEquals(2, state.items.size)
        assertEquals(NotificationUnreadState.AtLeast(2), state.unreadState)
        assertEquals(NotificationPushRegistrationState.Connected, state.pushRegistration?.state)
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
                    fallbackText = NotificationLabel.Plain("blobcat"),
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
            NotificationActivity.System.Moderation(NotificationLabel.Plain("Moderation"), "A post was removed"),
            items.getValue("a-system-moderation").activity,
        )
        assertEquals(
            NotificationActivity.System.RelationshipChange(NotificationLabel.Plain("Follow changed"), "A user followed you"),
            items.getValue("a-system-relationship").activity,
        )
        assertEquals(
            NotificationActivity.System.RoleOrAchievement(NotificationLabel.Plain("New role"), "You earned a role"),
            items.getValue("a-system-role").activity,
        )
        assertEquals(
            NotificationActivity.System.AppEvent(NotificationLabel.Plain("Update"), "A new version is available"),
            items.getValue("a-system-app").activity,
        )
        assertEquals(
            NotificationActivity.Unknown(
                NotificationLabel.Plain("New thing"),
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

    @Test
    fun postsAndAccountsDecodeRecursiveQuotesAndIdentity() {
        val post = decode(fixture("posts_and_accounts.json")).items.single().post!!

        assertEquals(EntityId("https://misskey.example", "post-1"), post.id)
        assertEquals("Actor One", post.author.displayName)
        assertEquals(ProfileField("Site", "https://actor.example"), post.author.profileFields.single())
        assertEquals(5L, post.author.followersCount)
        assertEquals("blobcat", post.author.emoji.getValue("blobcat").shortcode)
        assertEquals(
            AccountId(Connection("https://misskey.example", Protocol.MISSKEY), "actor-2"),
            post.author.movedTo?.id,
        )
        val attachment = post.attachments.single()
        assertEquals("https://misskey.example/file.png", attachment.url)
        assertEquals("A cat", attachment.description)
        assertEquals("LEHV6nWB2yk8pyo0adR*", attachment.blurhash)
        assertEquals("cw", post.contentWarning)
        assertEquals(EntityId("https://misskey.example", "post-0"), post.replyTo)
        assertEquals(2, post.reactions.single().count)
        assertEquals(setOf(PostAction.Reply, PostAction.React, PostAction.Bookmark), post.availableActions)
        assertEquals(1, post.interactionCounts.favouriteCount)
        assertEquals(4, post.interactionCounts.quoteRepostCount)
        assertEquals(PollOption("Cats", 3), post.pollOptions.first())
        assertEquals(":blobcat:", post.myReaction)
        assertEquals(EntityId("https://misskey.example", "repost-1"), post.ownRepostId)
        assertEquals(EntityId("https://misskey.example", "target-1"), post.actionTargetId)

        val quote = post.quote!!
        assertEquals(EntityId("https://misskey.example", "post-2"), quote.id)
        assertEquals(Audience.Unlisted, quote.audience)
        assertEquals(EntityId("https://misskey.example", "post-3"), quote.quote?.id)
        assertEquals("Deep quote", quote.quote?.text)
    }

    @Test
    fun postsAndAccountsLegacyPostsWithoutVisibilityDecodeHidden() {
        // The frozen fixture predates visibility persistence. Old blobs cannot prove
        // their visibility, so every post and nested quote fails closed to Hidden until
        // an authenticated refresh replaces the cached body.
        val post = decode(fixture("posts_and_accounts.json")).items.single().post!!

        assertEquals(PostContentVisibility.Hidden, post.contentVisibility)
        assertEquals(PostContentVisibility.Hidden, post.quote?.contentVisibility)
        assertEquals(PostContentVisibility.Hidden, post.quote?.quote?.contentVisibility)
    }

    @Test
    fun postVisibilityDecodesEveryVariantIncludingNestedQuotes() {
        val items = decode(fixture("post_visibility.json")).items.associateBy { it.id.value }

        assertEquals(PostContentVisibility.Visible, items.getValue("v-visible").post?.contentVisibility)
        assertEquals(PostContentVisibility.Visible, items.getValue("v-visible").post?.quote?.contentVisibility)
        assertEquals(PostContentVisibility.Hidden, items.getValue("v-hidden").post?.contentVisibility)
        assertEquals(PostContentVisibility.Filtered, items.getValue("v-filtered").post?.contentVisibility)
        assertEquals(PostContentVisibility.Visible, items.getValue("v-nested-hidden-quote").post?.contentVisibility)
        assertEquals(
            PostContentVisibility.Hidden,
            items.getValue("v-nested-hidden-quote").post?.quote?.contentVisibility,
        )
    }

    @Test
    fun postVisibilityEncodesToTheFrozenFixture() {
        val fixture = fixture("post_visibility.json")

        assertJsonEquals(fixture, encode(decode(fixture)))
    }

    @Test
    fun missingAndUnknownVisibilityFailClosedToHidden() {
        val missing = decode(JSONObject("""{"version":2,"items":[]}"""))
        assertEquals(NotificationRepositoryState(), missing)

        val withoutField = decode(JSONObject(
            """{"version":2,"items":[{""" +
                """"id":{"connection":"https://misskey.example","value":"n1"},""" +
                """"accountId":{"origin":"https://misskey.example","protocol":"MISSKEY","localId":"receiver"},""" +
                """"activity":{"kind":"mention"},""" +
                """"post":{""" +
                """"id":{"connection":"https://misskey.example","value":"p1"},""" +
                """"author":{"id":{"origin":"https://misskey.example","protocol":"MISSKEY","localId":"actor"}},""" +
                """"text":"legacy body","publishedAt":1,"audience":"Public"},""" +
                """"rawType":"mention"}]}""",
        ))
        assertEquals(PostContentVisibility.Hidden, withoutField.items.single().post?.contentVisibility)

        val unknown = decode(JSONObject(
            """{"version":2,"items":[{""" +
                """"id":{"connection":"https://misskey.example","value":"n2"},""" +
                """"accountId":{"origin":"https://misskey.example","protocol":"MISSKEY","localId":"receiver"},""" +
                """"activity":{"kind":"mention"},""" +
                """"post":{""" +
                """"id":{"connection":"https://misskey.example","value":"p2"},""" +
                """"author":{"id":{"origin":"https://misskey.example","protocol":"MISSKEY","localId":"actor"}},""" +
                """"text":"future body","publishedAt":1,"audience":"Public","contentVisibility":"Quarantined"},""" +
                """"rawType":"mention"}]}""",
        ))
        assertEquals(PostContentVisibility.Hidden, unknown.items.single().post?.contentVisibility)
    }

    @Test
    fun interactionCountsDecodeSupportedAndUnsupportedValues() {
        val items = decode(fixture("interaction_counts.json")).items.associateBy { it.id.value }

        val known = items.getValue("counts-known").post!!.interactionCounts
        assertEquals(0, known.favouriteCount)
        assertEquals(12, known.reactionCount)
        assertEquals(3, known.repostCount)
        assertNull(known.quoteRepostCount)
        assertNull(known.replyCount)

        val unsupported = items.getValue("counts-unsupported").post!!.interactionCounts
        assertNull(unsupported.favouriteCount)
        assertNull(unsupported.reactionCount)
        assertNull(unsupported.repostCount)
        assertNull(unsupported.quoteRepostCount)
        assertNull(unsupported.replyCount)
    }

    @Test
    fun interactionCountsEncodeOmitsUnsupportedValues() {
        val encodedItems = encode(decode(fixture("interaction_counts.json"))).getJSONArray("items")
        val posts = (0 until encodedItems.length()).map { encodedItems.getJSONObject(it).getJSONObject("post") }
        val unsupported = posts.single { it.getJSONObject("id").getString("value") == "post-unsupported" }

        assertFalse(unsupported.has("favouriteCount"))
        assertFalse(unsupported.has("reactionCount"))
        assertFalse(unsupported.has("reshareCount"))
        assertFalse(unsupported.has("quoteRepostCount"))
        assertFalse(unsupported.has("replyCount"))
    }

    @Test
    fun unreadStatesDecodeEveryKind() {
        val cases = fixture("unread_states.json")

        assertEquals(NotificationUnreadState.Exact(4), decode(cases.getJSONObject("exact")).unreadState)
        assertEquals(NotificationUnreadState.AtLeast(2), decode(cases.getJSONObject("at_least")).unreadState)
        assertEquals(NotificationUnreadState.Present, decode(cases.getJSONObject("present")).unreadState)
        assertEquals(NotificationUnreadState.None, decode(cases.getJSONObject("none")).unreadState)
        assertEquals(NotificationUnreadState.Unknown, decode(cases.getJSONObject("unknown")).unreadState)
        assertEquals(NotificationUnreadState.Unknown, decode(cases.getJSONObject("missing")).unreadState)
        assertEquals(NotificationUnreadState.Exact(0), decode(cases.getJSONObject("negative_count")).unreadState)
        assertEquals(NotificationUnreadState.Unknown, decode(cases.getJSONObject("future_kind")).unreadState)
    }

    @Test
    fun settingsStatesDecodeDefaultsAndValidation() {
        val cases = fixture("settings_states.json")

        assertEquals(NotificationSettings(), decode(cases.getJSONObject("absent")).settings)
        assertEquals(NotificationSettings(), decode(cases.getJSONObject("empty_object")).settings)
        assertEquals(
            NotificationSettings(
                alertsEnabled = true,
                categories = emptySet(),
                showPreviews = true,
                periodicFallbackEnabled = true,
            ),
            decode(cases.getJSONObject("empty_categories")).settings,
        )
        assertEquals(
            NotificationSettings(categories = setOf(NotificationCategory.Mentions)),
            decode(cases.getJSONObject("unknown_categories")).settings,
        )
        assertEquals(
            NotificationSettings(
                categories = setOf(NotificationCategory.All),
                quietHoursStartMinutes = 1320,
                quietHoursEndMinutes = 420,
            ),
            decode(cases.getJSONObject("valid_quiet_hours")).settings,
        )
        assertEquals(
            NotificationSettings(
                categories = setOf(NotificationCategory.All),
                selectedDistributor = "org.example.distributor",
            ),
            decode(cases.getJSONObject("distributor")).settings,
        )
        assertThrows(IllegalArgumentException::class.java) { decode(cases.getJSONObject("out_of_range_quiet_start")) }
        assertThrows(IllegalArgumentException::class.java) { decode(cases.getJSONObject("out_of_range_quiet_end")) }
    }

    @Test
    fun pushStatesDecodeFieldsAndLegacyDefaults() {
        val cases = fixture("push_states.json")

        val full = decode(cases.getJSONObject("full")).pushRegistration!!
        assertEquals(NotificationPushRegistrationState.Connected, full.state)
        assertEquals("remote-1", full.serverRemoteId)
        assertEquals(3L, full.sessionRevision)
        assertEquals(2L, full.confirmedEndpointGeneration)
        assertEquals(PushRegistrationFailureStage.ServerSubscription, full.failureStage)
        assertEquals(PushRegistrationFailureReason.Network, full.failureReason)
        assertEquals("timeout", full.lastErrorDetail)
        assertEquals(9000L, full.nextRetryAtEpochMillis)

        val legacy = decode(cases.getJSONObject("legacy_connected")).pushRegistration!!
        assertEquals(NotificationPushRegistrationState.Connected, legacy.state)
        assertEquals(legacy.endpoint, legacy.serverEndpoint)
        assertEquals(1L, legacy.sessionRevision)

        val revisionDefault = decode(cases.getJSONObject("revision_default")).pushRegistration!!
        assertEquals(1L, revisionDefault.sessionRevision)

        val unavailable = decode(cases.getJSONObject("temporarily_unavailable")).pushRegistration!!
        assertEquals(NotificationPushRegistrationState.TemporarilyUnavailable, unavailable.state)
        assertEquals(4, unavailable.retryCount)
        assertEquals(PushRegistrationFailureStage.ServerSubscription, unavailable.failureStage)
        assertEquals(PushRegistrationFailureReason.Network, unavailable.failureReason)
    }

    @Test
    fun checkpointsDecodeAllCursorFieldsAndKeyedForms() {
        val state = decode(fixture("checkpoints.json"))
        val checkpoint = state.checkpoint!!

        assertEquals(setOf(NotificationCategory.Mentions, NotificationCategory.Replies), checkpoint.query.categories)
        assertEquals(30, checkpoint.query.limit)
        assertEquals("cursor-newest", checkpoint.newest?.value)
        assertEquals("cursor-oldest", checkpoint.oldest?.value)
        assertEquals("cursor-newer", checkpoint.newerContinuation?.value)
        assertEquals("cursor-older", checkpoint.olderContinuation?.value)
        assertEquals(NotificationSyncCompleteness.Gap, checkpoint.completeness)
        assertTrue(checkpoint.baselineEstablished)
        assertEquals(4000L, checkpoint.capturedAtEpochMillis)

        val keyed = state.checkpoints.getValue("Social|50|true")
        assertEquals("keyed-newest", keyed.newest?.value)
        assertEquals("keyed-oldest", keyed.oldest?.value)
        assertEquals("keyed-newer", keyed.newerContinuation?.value)
        assertEquals("keyed-older", keyed.olderContinuation?.value)
        assertEquals(NotificationSyncCompleteness.Complete, keyed.completeness)
        assertEquals(50, keyed.query.limit)
        assertTrue(keyed.query.grouped)
        assertEquals(4500L, keyed.capturedAtEpochMillis)
    }

    @Test
    fun checkpointsEncodeToTheFrozenFixture() {
        val fixture = fixture("checkpoints.json")

        assertJsonEquals(fixture, encode(decode(fixture)))
    }

    @Test
    fun checkpointFallbackDecodesDefaults() {
        val state = decode(fixture("checkpoint_fallback.json"))
        val checkpoint = state.checkpoint!!

        assertEquals(NotificationSyncCompleteness.Unknown, checkpoint.completeness)
        assertEquals(0L, checkpoint.capturedAtEpochMillis)
        assertFalse(checkpoint.baselineEstablished)
        assertNull(checkpoint.newest)
        assertEquals(30, checkpoint.query.limit)

        val keyed = state.checkpoints.getValue("fallback|30|false")
        assertEquals(NotificationSyncCompleteness.Unknown, keyed.completeness)
        assertTrue(keyed.baselineEstablished)
    }

    @Test
    fun malformedRootShapeDecodesToEmptyState() {
        val state = decode(fixture("malformed_root_shape.json"))

        assertTrue(state.items.isEmpty())
        assertEquals(NotificationUnreadState.Unknown, state.unreadState)
        assertNull(state.checkpoint)
        assertTrue(state.checkpoints.isEmpty())
        assertTrue(state.dismissedIds.isEmpty())
        assertTrue(state.deliveries.isEmpty())
        assertEquals(NotificationSettings(), state.settings)
        assertNull(state.pushRegistration)
    }

    @Test
    fun malformedEntriesDropOnlyTheBrokenEntry() {
        val state = decode(fixture("malformed_entries.json"))

        assertEquals(listOf(EntityId("https://misskey.example", "ok-1")), state.items.map { it.id })
        assertEquals(setOf(EntityId("https://misskey.example", "dismiss-ok")), state.dismissedIds)
        assertEquals(1, state.deliveries.size)
        assertEquals("tag", state.deliveries.values.single().androidTag)
    }

    @Test
    fun malformedSingularCheckpointFailsTheDecode() {
        assertThrows(JSONException::class.java) { decode(fixture("malformed_checkpoint.json")) }
    }

    @Test
    fun malformedPushRegistrationFailsTheDecode() {
        assertThrows(JSONException::class.java) { decode(fixture("malformed_push.json")) }
    }

    @Test
    fun brokenJsonTextIsNotParseable() {
        assertThrows(JSONException::class.java) { JSONObject(fixtureText("malformed_broken.json")) }
    }

    @Test
    fun fileStoreReportsBrokenJsonAsCorrupt() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = FileNotificationStore(context)
        val directory = File(context.noBackupFilesDir, "notifications")
        directory.mkdirs()
        File(directory, "${misskeyReceiver.stableFileName()}.json").writeText(fixtureText("malformed_broken.json"))

        assertEquals(NotificationStoreRead.Corrupt, store.read(misskeyReceiver))
    }

    @Test
    fun fileStoreReportsAbsentStateForMissingFile() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = FileNotificationStore(context)
        File(File(context.noBackupFilesDir, "notifications"), "${misskeyReceiver.stableFileName()}.json").delete()

        assertEquals(NotificationStoreRead.Absent, store.read(misskeyReceiver))
    }

    @Test
    fun fileStoreReportsInvalidValuesAsCorrupt() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = FileNotificationStore(context)
        val directory = File(context.noBackupFilesDir, "notifications")
        directory.mkdirs()
        File(directory, "${misskeyReceiver.stableFileName()}.json")
            .writeText("""{"version":2,"settings":{"quietStart":2000}}""")

        assertEquals(NotificationStoreRead.Corrupt, store.read(misskeyReceiver))
    }

    @Test
    fun fileStoreRejectsStateOwnedByAnotherAccount() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = FileNotificationStore(context)
        val directory = File(context.noBackupFilesDir, "notifications")
        directory.mkdirs()
        File(directory, "${misskeyReceiver.stableFileName()}.json").writeText(
            """{"version":2,"items":[{"id":{"connection":"https://misskey.example","value":"n1"},""" +
                """"accountId":{"origin":"https://misskey.example","protocol":"MISSKEY","localId":"other"},""" +
                """"activity":{"kind":"mention"},"rawType":"mention"}]}""",
        )

        assertEquals(NotificationStoreRead.Corrupt, store.read(misskeyReceiver))
    }

    @Test
    fun fileStoreReadsNestedQuotesAndInteractionCounts() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = FileNotificationStore(context)
        val directory = File(context.noBackupFilesDir, "notifications")
        directory.mkdirs()
        File(directory, "${misskeyReceiver.stableFileName()}.json").writeText(fixtureText("posts_and_accounts.json"))

        val post = (store.read(misskeyReceiver) as NotificationStoreRead.Readable).state.items.single().post

        assertNotNull(post)
        assertEquals(4, post?.interactionCounts?.quoteRepostCount)
        assertEquals(EntityId("https://misskey.example", "post-2"), post?.quote?.id)
    }

    @Test
    fun fileStorePersistsPostVisibilityAcrossRestart() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = FileNotificationStore(context)
        val directory = File(context.noBackupFilesDir, "notifications")
        directory.mkdirs()
        File(directory, "${misskeyReceiver.stableFileName()}.json").writeText(fixtureText("post_visibility.json"))

        val items = (store.read(misskeyReceiver) as NotificationStoreRead.Readable)
            .state.items.associateBy { it.id.value }

        assertEquals(PostContentVisibility.Visible, items.getValue("v-visible").post?.contentVisibility)
        assertEquals(PostContentVisibility.Hidden, items.getValue("v-hidden").post?.contentVisibility)
        assertEquals(PostContentVisibility.Filtered, items.getValue("v-filtered").post?.contentVisibility)
        assertEquals(
            PostContentVisibility.Hidden,
            items.getValue("v-nested-hidden-quote").post?.quote?.contentVisibility,
        )
    }

    @Test
    fun fileStoreReadsLegacyPostsWithoutVisibilityAsHidden() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = FileNotificationStore(context)
        val directory = File(context.noBackupFilesDir, "notifications")
        directory.mkdirs()
        File(directory, "${misskeyReceiver.stableFileName()}.json").writeText(fixtureText("posts_and_accounts.json"))

        val post = (store.read(misskeyReceiver) as NotificationStoreRead.Readable).state.items.single().post

        assertEquals(PostContentVisibility.Hidden, post?.contentVisibility)
        assertEquals(PostContentVisibility.Hidden, post?.quote?.contentVisibility)
    }

    @Test
    fun fileStorePreservesNestedUnknownServerDestination() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = FileNotificationStore(context)
        val directory = File(context.noBackupFilesDir, "notifications")
        directory.mkdirs()
        File(directory, "${misskeyReceiver.stableFileName()}.json").writeText(fixtureText("activity_variants.json"))

        val activity = (store.read(misskeyReceiver) as NotificationStoreRead.Readable).state.items
            .first { it.id.value == "a-unknown" }.activity as NotificationActivity.Unknown

        assertEquals(
            NotificationDestination.Server(ValidatedUrl.https("https://misskey.example/notice/9")!!),
            activity.validatedDestination,
        )
    }

    @Test
    fun groupContinuationAndUnknownDestinationAreNotRoundTripComplete() {
        // Post visibility is persisted since 03-I. The frozen omission item already
        // carries an explicit Hidden value, so it decodes Hidden and round-trips.
        val items = decode(fixture("known_omissions.json")).items.associateBy { it.id.value }

        assertEquals(PostContentVisibility.Hidden, items.getValue("omit-visibility").post?.contentVisibility)
        assertEquals(
            PostContentVisibility.Hidden,
            decode(encode(decode(fixture("known_omissions.json"))))
                .items.associateBy { it.id.value }.getValue("omit-visibility").post?.contentVisibility,
        )
        assertNull(items.getValue("omit-continuation").group?.actorContinuation)
    }

    @Test
    fun encodedGroupContinuationAndInAppUnknownDestinationAreLost() {
        val groupItem = Notification(
            id = EntityId("https://misskey.example", "omit-continuation"),
            accountId = misskeyReceiver,
            createdAtEpochMillis = 1,
            activity = NotificationActivity.Mention,
            rawType = "mention",
            group = NotificationGroup(
                id = NotificationGroupId(misskeyReceiver, "group-1"),
                totalCount = 2,
                actorContinuation = NotificationCursor("continuation-1"),
            ),
        )
        val unknownItem = Notification(
            id = EntityId("https://misskey.example", "omit-unknown"),
            accountId = misskeyReceiver,
            createdAtEpochMillis = 1,
            activity = NotificationActivity.Unknown(
                fallbackText = NotificationLabel.Plain("New thing"),
                validatedDestination = NotificationDestination.InApp(
                    NotificationTarget.Post(EntityId("https://misskey.example", "post-1")),
                ),
            ),
            rawType = "thing",
        )
        val decoded = decode(encode(NotificationRepositoryState(items = listOf(groupItem, unknownItem))))
            .items.associateBy { it.id.value }

        assertNull(decoded.getValue("omit-continuation").group?.actorContinuation)
        val unknown = decoded.getValue("omit-unknown").activity as NotificationActivity.Unknown
        assertNull(unknown.validatedDestination)
    }

    @Test
    fun codedLabelsRoundTripWithoutLosingTheirCode() {
        val codedItem = Notification(
            id = EntityId("https://misskey.example", "coded-label"),
            accountId = misskeyReceiver,
            createdAtEpochMillis = 1,
            activity = NotificationActivity.System.AppEvent(
                NotificationLabel.Coded(NotificationLabelCode.ScheduledPostFailed),
            ),
            rawType = "scheduledNotePostFailed",
        )
        val decoded = decode(encode(NotificationRepositoryState(items = listOf(codedItem)))).items.single().activity

        assertEquals(
            NotificationActivity.System.AppEvent(NotificationLabel.Coded(NotificationLabelCode.ScheduledPostFailed)),
            decoded,
        )
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
