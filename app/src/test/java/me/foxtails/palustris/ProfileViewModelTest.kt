package me.foxtails.palustris

import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.Audience
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.CreatePostRequest
import me.foxtails.palustris.domain.EntityId
import me.foxtails.palustris.domain.Page
import me.foxtails.palustris.domain.Post
import me.foxtails.palustris.domain.ProfileRelationship
import me.foxtails.palustris.domain.ProfileTimelineQuery
import me.foxtails.palustris.domain.ProfileTimelineTab
import me.foxtails.palustris.domain.ServerCapabilities
import me.foxtails.palustris.domain.SocialSource
import me.foxtails.palustris.domain.SourceError
import me.foxtails.palustris.domain.Timeline
import me.foxtails.palustris.domain.UpdateProfileRequest
import me.foxtails.palustris.ui.profile.ProfileCategory
import me.foxtails.palustris.ui.profile.ProfileViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ProfileViewModelTest {
    private val origin = "https://example.org"
    private val self = account("self", "Self")
    private val remote = account("remote", "Remote")

    @Test fun openPublishesSeedThenAuthoritativeDetailsAndOwnedRows() = runProfileTest {
        val source = FakeSource().apply {
            detailResults[remote.id] = remote.copy(displayName = "Remote fresh")
            pinnedResults[remote.id] = listOf(post("pinned", remote))
        }
        val model = model(source)

        model.open(remote)
        assertEquals(remote, model.state.value.seedAccount)
        assertEquals(remote, model.state.value.account)

        advanceUntilIdle()

        assertEquals("Remote fresh", model.state.value.account?.displayName)
        assertEquals(listOf(remote.id), source.relationshipCalls)
        assertEquals(self.id, model.state.value.pages.getValue(ProfileTimelineTab.Posts).posts.single().fetchedBy)
        assertEquals(listOf("pinned"), model.state.value.pinnedPosts.map { it.post.id.value })
        assertFalse(model.state.value.detailLoading)
    }

    @Test fun reopeningSameTargetPreservesCategoryAndRefreshesThatCategory() = runProfileTest {
        val source = FakeSource().apply {
            timelineResults[ProfileTimelineTab.Media] = mutableListOf(Page(listOf(post("media-1", remote)), null))
        }
        val model = model(source)

        model.open(remote)
        advanceUntilIdle()
        model.selectCategory(ProfileCategory.Media)
        advanceUntilIdle()
        val callsBeforeReopen = source.timelineCalls.size

        model.open(remote)
        advanceUntilIdle()

        assertEquals(ProfileCategory.Media, model.state.value.selectedTab)
        assertEquals(callsBeforeReopen + 1, source.timelineCalls.size)
        assertEquals(ProfileTimelineTab.Media, source.timelineCalls.last().first.tab)
    }

    @Test fun oldTargetCannotPublishAfterOpeningAnotherTarget() = runProfileTest {
        val alice = account("alice", "Alice")
        val bob = account("bob", "Bob")
        val aliceGate = CompletableDeferred<Unit>()
        val bobGate = CompletableDeferred<Unit>()
        val source = FakeSource().apply {
            profileGates[alice.id] = aliceGate
            profileGates[bob.id] = bobGate
            detailResults[alice.id] = alice.copy(displayName = "Alice stale response")
            detailResults[bob.id] = bob.copy(displayName = "Bob authoritative")
        }
        val model = model(source)

        model.open(alice)
        runCurrent()
        model.open(bob)
        runCurrent()

        aliceGate.complete(Unit)
        runCurrent()
        assertEquals(bob.id, model.state.value.targetId)
        assertEquals("Bob", model.state.value.account?.displayName)

        bobGate.complete(Unit)
        advanceUntilIdle()
        assertEquals("Bob authoritative", model.state.value.account?.displayName)
        assertEquals(bob.id, model.state.value.account?.id)
    }

    @Test fun refreshFailureRetainsRowsCursorAndExposesSignInRecovery() = runProfileTest {
        val source = FakeSource().apply {
            timelineResults[ProfileTimelineTab.Posts] = mutableListOf(
                Page(listOf(post("first", remote)), "cursor-a"),
            )
        }
        val model = model(source)
        model.open(remote)
        advanceUntilIdle()
        source.timelineError = SourceError.Unauthorized

        model.refreshSelected()
        advanceUntilIdle()

        val page = model.state.value.pages.getValue(ProfileTimelineTab.Posts)
        assertEquals(listOf("first"), page.posts.map { it.post.id.value })
        assertEquals("cursor-a", page.nextCursor)
        assertTrue(page.needsSignIn)
        assertNotNull(page.error)
        assertFalse(page.refreshing)
    }

    @Test fun pagingDeduplicatesRowsAndSuppressesRepeatedCursor() = runProfileTest {
        val source = FakeSource().apply {
            timelineResults[ProfileTimelineTab.Posts] = mutableListOf(
                Page(listOf(post("one", remote)), "cursor-a"),
                Page(listOf(post("one", remote), post("two", remote)), "cursor-a"),
            )
        }
        val model = model(source)
        model.open(remote)
        advanceUntilIdle()

        model.loadMoreSelected()
        advanceUntilIdle()

        val page = model.state.value.pages.getValue(ProfileTimelineTab.Posts)
        assertEquals(listOf("one", "two"), page.posts.map { it.post.id.value })
        assertNull(page.nextCursor)
        assertTrue(page.terminal)
    }

    @Test fun emptyFilteredPagesKeepCursorForManualContinuation() = runProfileTest {
        val source = FakeSource().apply {
            timelineResults[ProfileTimelineTab.Posts] = mutableListOf(
                Page(emptyList(), "cursor-a"),
                Page(emptyList(), "cursor-b"),
                Page(listOf(post("eventually-visible", remote)), null),
            )
        }
        val model = model(source)
        model.open(remote)
        advanceUntilIdle()
        assertEquals(1, model.state.value.pages.getValue(ProfileTimelineTab.Posts).consecutiveEmptyPages)

        model.loadMoreSelected()
        advanceUntilIdle()
        assertEquals("cursor-b", model.state.value.pages.getValue(ProfileTimelineTab.Posts).nextCursor)
        assertEquals(2, model.state.value.pages.getValue(ProfileTimelineTab.Posts).consecutiveEmptyPages)

        model.loadMoreSelected()
        advanceUntilIdle()
        assertEquals(listOf("eventually-visible"), model.state.value.pages.getValue(ProfileTimelineTab.Posts).posts.map { it.post.id.value })
    }

    @Test fun selfSkipsRelationshipAndOwnsProfileEditingState() = runProfileTest {
        val source = FakeSource().apply {
            updateResult = self.copy(displayName = "Updated Self")
        }
        val model = model(source)
        var callbackAccount: Account? = null

        model.open(self)
        advanceUntilIdle()
        model.updateSelf(UpdateProfileRequest("Updated Self", "New bio")) { callbackAccount = it }
        advanceUntilIdle()

        assertTrue(source.relationshipCalls.isEmpty())
        assertEquals("Updated Self", model.state.value.account?.displayName)
        assertEquals("Updated Self", callbackAccount?.displayName)
        assertFalse(model.state.value.savingProfile)
        assertNull(model.state.value.editError)
    }

    @Test fun relationshipMutationsUseSourceStateAndUnsupportedHidesActions() = runProfileTest {
        val source = FakeSource()
        val model = model(source)
        model.open(remote)
        advanceUntilIdle()

        model.follow()
        advanceUntilIdle()
        assertTrue(source.followCalls.contains(remote.id))
        assertTrue(model.state.value.relationship?.following == true)

        model.unfollow()
        advanceUntilIdle()
        assertTrue(source.unfollowCalls.contains(remote.id))
        assertFalse(model.state.value.relationship?.following == true)

        val unsupported = FakeSource(ServerCapabilities(profile = me.foxtails.palustris.domain.ProfileCapabilities(
            relationships = me.foxtails.palustris.domain.CapabilityStatus.Unsupported,
        )))
        val unsupportedModel = model(unsupported)
        unsupportedModel.open(remote)
        advanceUntilIdle()
        assertEquals(false, unsupportedModel.state.value.relationshipSupported)
        assertTrue(unsupported.relationshipCalls.isEmpty())
    }

    @Test fun stopPreventsLateDetailPublicationAndIsIdempotent() = runProfileTest {
        val gate = CompletableDeferred<Unit>()
        val source = FakeSource().apply {
            profileGates[remote.id] = gate
            detailResults[remote.id] = remote.copy(displayName = "Late detail")
        }
        val model = model(source)
        model.open(remote)
        runCurrent()
        model.stop()
        model.stop()
        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(remote, model.state.value.account)
        assertTrue(model.state.value.detailLoading)
    }

    private fun model(source: FakeSource): ProfileViewModel = ProfileViewModel(self.id, source)

    private fun runProfileTest(block: suspend TestScope.() -> Unit) = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            block()
        } finally {
            Dispatchers.resetMain()
        }
    }

    private fun account(id: String, name: String): Account = Account(
        id = AccountId(Connection(origin, me.foxtails.palustris.domain.Protocol.MASTODON), id),
        displayName = name,
        handle = "@$name@example.org",
    )

    private fun post(id: String, author: Account): Post = Post(
        id = EntityId(origin, id),
        author = author,
        text = id,
        publishedAtEpochMillis = 0,
        audience = Audience.Public,
    )

    private class FakeSource(
        override val capabilities: ServerCapabilities = ServerCapabilities(),
    ) : SocialSource {
        val detailCalls = mutableListOf<AccountId>()
        val relationshipCalls = mutableListOf<AccountId>()
        val timelineCalls = mutableListOf<Pair<ProfileTimelineQuery, String?>>()
        val followCalls = mutableListOf<AccountId>()
        val unfollowCalls = mutableListOf<AccountId>()
        val profileGates = mutableMapOf<AccountId, CompletableDeferred<Unit>>()
        val detailResults = mutableMapOf<AccountId, Account>()
        val pinnedResults = mutableMapOf<AccountId, List<Post>>()
        val timelineResults = mutableMapOf<ProfileTimelineTab, MutableList<Page<Post>>>()
        private val timelineIndexes = mutableMapOf<ProfileTimelineTab, AtomicInteger>()
        var timelineError: Exception? = null
        var relationshipError: Exception? = null
        var updateError: Exception? = null
        var updateResult: Account? = null

        override suspend fun timeline(timeline: Timeline, cursor: String?): Page<Post> = Page(emptyList())

        override suspend fun profile(id: AccountId): Account {
            detailCalls += id
            profileGates[id]?.let { gate -> withContext(NonCancellable) { gate.await() } }
            detailResults[id]?.let { return it }
            return Account(id, "${id.localId} authoritative", "@${id.localId}@example.org")
        }

        override suspend fun profileTimeline(query: ProfileTimelineQuery, cursor: String?): Page<Post> {
            timelineCalls += query to cursor
            timelineError?.let { throw it }
            val pages = timelineResults[query.tab].orEmpty()
            if (pages.isEmpty()) return Page(listOf(Post(
                EntityId(query.profileId.connection.origin, "default"),
                Account(query.profileId, "Remote", "@remote@example.org"),
                "default",
                0,
                Audience.Public,
            )), "next")
            val index = timelineIndexes.getOrPut(query.tab) { AtomicInteger() }.getAndIncrement()
            return pages.getOrElse(index) { pages.last() }
        }

        override suspend fun profileRelationship(id: AccountId): ProfileRelationship {
            relationshipCalls += id
            relationshipError?.let { throw it }
            return ProfileRelationship(id)
        }

        override suspend fun followProfile(id: AccountId): ProfileRelationship {
            followCalls += id
            return ProfileRelationship(id, following = true)
        }

        override suspend fun unfollowProfile(id: AccountId): ProfileRelationship {
            unfollowCalls += id
            return ProfileRelationship(id)
        }

        override suspend fun pinnedPosts(id: AccountId): List<Post> = pinnedResults[id].orEmpty()

        override suspend fun updateProfile(request: UpdateProfileRequest): Account {
            updateError?.let { throw it }
            return updateResult ?: Account(
                AccountId(Connection("https://example.org", me.foxtails.palustris.domain.Protocol.MASTODON), "self"),
                request.displayName,
                "@self@example.org",
                biography = request.biography,
            )
        }

        override suspend fun create(post: CreatePostRequest): Post = error("not used")
    }
}
