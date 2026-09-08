package me.foxtails.palustris

import androidx.lifecycle.ViewModelStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import me.foxtails.palustris.data.auth.*
import me.foxtails.palustris.data.misskey.ApiFailure
import me.foxtails.palustris.data.misskey.MisskeyErrorMapper
import me.foxtails.palustris.domain.*
import me.foxtails.palustris.ui.AccountManager
import me.foxtails.palustris.ui.AccountSyncCoordinator
import me.foxtails.palustris.ui.FeedViewModel
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SessionViewModelTest {
    private val login get() = LoginSession("https://example.org", "test-token", JSONObject("""{"id":"a","username":"alice"}"""))
    private class MemoryStore(
        initialSession: Session? = null,
        initialAccount: Account? = null,
    ) : SessionStore {
        val sessions = mutableMapOf<AccountId, Session>().apply {
            initialSession?.let { put(it.accountId, it) }
        }
        var storedSession: Session?
            get() = sessions.values.firstOrNull()
            set(value) {
                sessions.clear()
                value?.let { sessions[it.accountId] = it }
            }
        var pending: PendingLogin? = null
        val readAccountIds = mutableListOf<AccountId>()
        val writtenAccountIds = mutableListOf<AccountId>()
        var index = initialAccount?.let { account ->
            AccountIndex(accounts = listOf(AccountRef(account.id, account.handle, account.avatarUrl, account.displayName)), activeAccountId = account.id)
        } ?: AccountIndex()

        override fun read(accountId: AccountId): Session? {
            readAccountIds += accountId
            return sessions[accountId]
        }
        override fun write(accountId: AccountId, session: Session) {
            writtenAccountIds += accountId
            sessions[accountId] = session
        }
        override fun delete(accountId: AccountId) { sessions.remove(accountId) }
        override fun readIndex() = index
        override fun writeIndex(index: AccountIndex) { this.index = index }
        override fun readPending() = pending
        override fun writePending(pending: PendingLogin) { this.pending = pending }
        override fun clearPending() { pending = null }
        override fun clear() { sessions.clear(); pending = null; index = AccountIndex() }
    }
    private class Source(
        override val capabilities: ServerCapabilities = ServerCapabilities(timelines = setOf(Timeline.Home)),
    ) : SocialSource {
        var error: Exception? = null
        var createError: Exception? = null
        var failingTimeline: Timeline? = null
        var createGate: CompletableDeferred<Unit>? = null
        val timelineCalls = mutableListOf<Pair<Timeline, String?>>()
        override suspend fun timeline(timeline: Timeline, cursor: String?): Page<Post> {
            timelineCalls += timeline to cursor
            if (timeline == failingTimeline) throw IOException("timeline unavailable")
            error?.let { throw MisskeyErrorMapper.map(it) }
            val account = Account(AccountId(Connection("https://example.org", Protocol.MISSKEY), "a"), "Alice", "@alice")
            fun post(id: String) = Post(EntityId("example", id), account, "Text", 0, Audience.Public)
            return if (cursor == null) Page(listOf(post("2")), "2") else Page(listOf(post("2"), post("1")), null)
        }
        override suspend fun create(post: CreatePostRequest): Post {
            createError?.let { throw MisskeyErrorMapper.map(it) }
            createGate?.await()
            val account = Account(AccountId(Connection("https://example.org", Protocol.MISSKEY), "a"), "Alice", "@alice")
            return Post(EntityId("example", "created"), account, post.text, 0, Audience.Public)
        }
    }

    private class AccountFeedSource(
        private val author: Account,
        override val capabilities: ServerCapabilities,
    ) : SocialSource {
        override suspend fun timeline(timeline: Timeline, cursor: String?): Page<Post> = Page(
            listOf(
                Post(
                    EntityId(author.id.connection.origin, "post-${author.id.localId}"),
                    author,
                    "${author.displayName} post",
                    0,
                    Audience.Public,
                ),
            ),
        )
    }

    private class ActionSource : SocialSource {
        override val capabilities = ServerCapabilities(
            timelines = setOf(Timeline.Home),
            actions = setOf(PostAction.Favorite, PostAction.Reshare, PostAction.React),
        )
        val favoriteIds = mutableListOf<EntityId>()
        val reshareIds = mutableListOf<EntityId>()
        val reactionIds = mutableListOf<EntityId>()
        var actionError: Exception? = null
        private val author = Account(
            AccountId(Connection("https://example.org", Protocol.MISSKEY), "author"),
            "Author",
            "@author@example.org",
        )

        override suspend fun timeline(timeline: Timeline, cursor: String?): Page<Post> = Page(
            listOf(Post(EntityId("https://example.org", "post"), author, "Post", 0, Audience.Public)),
        )

        override suspend fun favorite(id: EntityId) {
            actionError?.let { throw it }
            favoriteIds += id
        }

        override suspend fun renote(id: EntityId) {
            actionError?.let { throw it }
            reshareIds += id
        }

        override suspend fun react(id: EntityId, emoji: String) {
            actionError?.let { throw it }
            reactionIds += id
        }
    }
    private fun auth(result: LoginSession) = object : AuthGateway {
        override suspend fun prepare(input: String) = PendingLogin(input, "session-id", System.currentTimeMillis())
        override fun browserUrl(pending: PendingLogin) = "${pending.origin}/miauth/${pending.id}"
        override suspend fun complete(pending: PendingLogin) = result
    }

    @Test fun restorePageDeduplicateRetryAndSignOut() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val owner = ViewModelStore()
        try {
            val store = MemoryStore(Session(login.account.id, login.token, ServerCapabilities()), login.account)
            val source = Source()
            val accountManager = AccountManager(store, auth(login), StandardTestDispatcher(testScheduler))
            val feedModel = FeedViewModel(login.account.id, source, AccountSyncCoordinator())
            owner.put("account", accountManager)
            owner.put("feed", feedModel)
            advanceUntilIdle()
            assertEquals("@alice@example.org", accountManager.session.value.account?.handle)
            assertEquals(1, feedModel.feed.value.posts.size)
            assertEquals(login.account.id, feedModel.feed.value.ownedPosts.single().fetchedBy)
            feedModel.loadMore(); advanceUntilIdle()
            assertEquals(listOf("2", "1"), feedModel.feed.value.posts.map { it.id.value })
            source.error = IOException()
            feedModel.refresh(); advanceUntilIdle()
            assertEquals(2, feedModel.feed.value.posts.size)
            assertNotNull(feedModel.feed.value.error)
            source.error = null
            feedModel.refresh(); advanceUntilIdle()
            assertNull(feedModel.feed.value.error)
            source.error = ApiFailure(401)
            feedModel.refresh(); advanceUntilIdle()
            assertTrue(feedModel.feed.value.needsSignIn)
            accountManager.signOut(); advanceUntilIdle()
            assertNull(store.storedSession)
            assertNull(accountManager.session.value.account)
        } finally { owner.clear(); Dispatchers.resetMain() }
    }

    @Test fun pendingAuthSurvivesRestartAndInvalidCallbackDoesNotConnect() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val owner = ViewModelStore()
        try {
            val store = MemoryStore()
            val result = login
            val dispatcher = StandardTestDispatcher(testScheduler)
            val first = AccountManager(store, auth(result), dispatcher)
            owner.put("first", first)
            advanceUntilIdle()
            first.signIn("https://example.org"); advanceUntilIdle()
            assertNotNull(store.pending)
            val restored = AccountManager(store, auth(result), dispatcher)
            owner.put("restored", restored)
            advanceUntilIdle()
            assertTrue(restored.session.value.pending)
            restored.callback("palustris://auth/misskey?session=attacker")
            advanceUntilIdle()
            assertNull(restored.session.value.account)
            restored.callback("palustris://auth/misskey?session=session-id")
            advanceUntilIdle()
            assertNotNull(restored.session.value.account)
            assertNull(store.pending)
            assertNotNull(store.storedSession)
        } finally { owner.clear(); Dispatchers.resetMain() }
    }

    @Test fun failedPublishKeepsStateAndSuccessInvokesCompletion() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val owner = ViewModelStore()
        try {
            val store = MemoryStore(Session(login.account.id, login.token, ServerCapabilities()), login.account)
            val source = Source()
            val model = FeedViewModel(login.account.id, source, AccountSyncCoordinator())
            owner.put("feed", model)
            advanceUntilIdle()

            source.createError = IOException()
            var completed = false
            model.create(CreatePostRequest("Draft text")) { completed = true }
            advanceUntilIdle()
            assertFalse(completed)
            assertFalse(model.feed.value.publishing)
            assertNotNull(model.feed.value.error)

            source.createError = null
            model.create(CreatePostRequest("Draft text")) { completed = true }
            advanceUntilIdle()
            assertTrue(completed)
            assertFalse(model.feed.value.publishing)
        } finally { owner.clear(); Dispatchers.resetMain() }
    }

    @Test fun refreshPreservesPublishingPermissionActionsAndInFlightPublishing() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val owner = ViewModelStore()
        try {
            val source = Source(
                ServerCapabilities(
                    timelines = setOf(Timeline.Home, Timeline.Local),
                    actions = setOf(PostAction.Favorite, PostAction.Reply),
                    canPublish = true,
                ),
            )
            val model = FeedViewModel(login.account.id, source, AccountSyncCoordinator())
            owner.put("feed", model)
            advanceUntilIdle()

            assertEquals(Timeline.Home, model.feed.value.timeline)
            assertTrue(model.feed.value.canPublish)
            assertEquals(setOf(PostAction.Favorite), model.feed.value.actions)

            source.createGate = CompletableDeferred()
            model.create(CreatePostRequest("In-flight draft"))
            runCurrent()
            assertTrue(model.feed.value.publishing)

            model.refresh(Timeline.Local)
            advanceUntilIdle()
            assertEquals(Timeline.Local, model.feed.value.timeline)
            assertTrue(model.feed.value.canPublish)
            assertEquals(setOf(PostAction.Favorite), model.feed.value.actions)
            assertTrue(model.feed.value.publishing)

            source.createGate?.complete(Unit)
            advanceUntilIdle()
            assertFalse(model.feed.value.publishing)
        } finally { owner.clear(); Dispatchers.resetMain() }
    }

    @Test fun failedTimelineChangeRestoresPreviousFeedAndDisplayedTimeline() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val owner = ViewModelStore()
        try {
            val source = Source(
                ServerCapabilities(
                    timelines = setOf(Timeline.Home, Timeline.Local),
                    actions = setOf(PostAction.Favorite),
                    canPublish = true,
                ),
            )
            val model = FeedViewModel(login.account.id, source, AccountSyncCoordinator())
            owner.put("feed", model)
            advanceUntilIdle()
            val previous = model.feed.value

            source.failingTimeline = Timeline.Local
            model.refresh(Timeline.Local)
            advanceUntilIdle()

            assertEquals(previous.posts, model.feed.value.posts)
            assertEquals(previous.ownedPosts, model.feed.value.ownedPosts)
            assertEquals(Timeline.Home, model.feed.value.timeline)
            assertEquals(previous.timelines, model.feed.value.timelines)
            assertEquals(previous.actions, model.feed.value.actions)
            assertTrue(model.feed.value.canPublish)
            assertNotNull(model.feed.value.error)
        } finally { owner.clear(); Dispatchers.resetMain() }
    }

    @Test fun actionsUseFetchedAccountFilterClientReadinessAndRejectDuplicates() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val owner = ViewModelStore()
        val coordinator = AccountSyncCoordinator()
        try {
            val source = ActionSource()
            val model = FeedViewModel(login.account.id, source, coordinator)
            owner.put("feed", model)
            advanceUntilIdle()

            assertEquals(setOf(PostAction.Favorite, PostAction.Reshare), model.feed.value.actions)
            val ownedPost = model.feed.value.ownedPosts.single()
            model.favorite(ownedPost)
            model.favorite(ownedPost)
            advanceUntilIdle()
            assertEquals(listOf(ownedPost.post.id), source.favoriteIds)

            model.react(ownedPost, "🎉")
            advanceUntilIdle()
            assertTrue(source.reactionIds.isEmpty())

            val otherAccount = AccountId(Connection("https://example.org", Protocol.MISSKEY), "other")
            model.reshare(OwnedPost(otherAccount, ownedPost.post))
            advanceUntilIdle()
            assertTrue(source.reshareIds.isEmpty())

            source.actionError = IOException()
            model.reshare(ownedPost)
            advanceUntilIdle()
            assertNotNull(model.feed.value.error)
            source.actionError = null
            model.reshare(ownedPost)
            advanceUntilIdle()
            assertEquals(listOf(ownedPost.post.id), source.reshareIds)
        } finally {
            owner.clear()
            coordinator.close()
            Dispatchers.resetMain()
        }
    }

    @Test fun switchingAccountsRebindsFeedTimelineActionsAndOwnership() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val owner = ViewModelStore()
        val coordinator = AccountSyncCoordinator()
        try {
            val secondLogin = LoginSession(
                "https://other.example",
                "second-token",
                JSONObject("""{"id":"b","username":"bob"}"""),
            )
            val secondSession = Session(secondLogin.account.id, secondLogin.token, ServerCapabilities())
            val store = MemoryStore(Session(login.account.id, login.token, ServerCapabilities()), login.account)
            store.sessions[secondSession.accountId] = secondSession
            store.index = AccountIndex(
                accounts = listOf(
                    AccountRef(login.account.id, login.account.handle, null, login.account.displayName),
                    AccountRef(secondLogin.account.id, secondLogin.account.handle, null, secondLogin.account.displayName),
                ),
                activeAccountId = login.account.id,
            )
            val accountManager = AccountManager(store, auth(login), StandardTestDispatcher(testScheduler))
            owner.put("accounts", accountManager)

            val firstSource = AccountFeedSource(
                login.account,
                ServerCapabilities(
                    timelines = setOf(Timeline.Home, Timeline.Local),
                    actions = setOf(PostAction.Favorite),
                    canPublish = true,
                ),
            )
            val firstFeed = FeedViewModel(login.account.id, firstSource, coordinator)
            owner.put("first-feed", firstFeed)
            advanceUntilIdle()
            assertEquals(login.account.id, firstFeed.feed.value.ownedPosts.single().fetchedBy)
            assertEquals(setOf(Timeline.Home, Timeline.Local), firstFeed.feed.value.timelines)
            assertEquals(setOf(PostAction.Favorite), firstFeed.feed.value.actions)
            assertTrue(firstFeed.feed.value.canPublish)

            accountManager.switchAccount(secondLogin.account.id)
            advanceUntilIdle()
            assertEquals(secondLogin.account.id, accountManager.session.value.account?.id)

            firstFeed.stop()
            val secondSource = AccountFeedSource(
                secondLogin.account,
                ServerCapabilities(
                    timelines = setOf(Timeline.Home, Timeline.Federated),
                    actions = setOf(PostAction.Reshare),
                ),
            )
            val secondFeed = FeedViewModel(secondLogin.account.id, secondSource, coordinator)
            owner.put("second-feed", secondFeed)
            advanceUntilIdle()

            assertEquals(secondLogin.account.id, secondFeed.feed.value.ownedPosts.single().fetchedBy)
            assertEquals(secondLogin.account.id, secondFeed.feed.value.posts.single().author.id)
            assertEquals(setOf(Timeline.Home, Timeline.Federated), secondFeed.feed.value.timelines)
            assertEquals(setOf(PostAction.Reshare), secondFeed.feed.value.actions)
            assertFalse(secondFeed.feed.value.canPublish)
        } finally {
            owner.clear()
            coordinator.close()
            Dispatchers.resetMain()
        }
    }

    @Test fun stoppingFeedDoesNotStopAccountNotificationPolling() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val owner = ViewModelStore()
        val coordinator = AccountSyncCoordinator()
        try {
            val store = MemoryStore(Session(login.account.id, login.token, ServerCapabilities()), login.account)
            val source = Source()
            coordinator.register(login.account.id, source)
            val model = FeedViewModel(login.account.id, source, coordinator)
            owner.put("feed", model)
            advanceUntilIdle()
            assertTrue(model.sync.value.isActive)

            model.stop()

            assertTrue(model.sync.value.isActive)
        } finally {
            owner.clear()
            coordinator.close()
            Dispatchers.resetMain()
        }
    }

    @Test fun selectedTimelineOwnsPaginationAndPublishRefresh() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val owner = ViewModelStore()
        try {
            val store = MemoryStore(Session(login.account.id, login.token, ServerCapabilities()), login.account)
            val source = Source()
            val model = FeedViewModel(login.account.id, source, AccountSyncCoordinator())
            owner.put("feed", model)
            advanceUntilIdle()
            source.timelineCalls.clear()

            model.refresh(Timeline.Local)
            advanceUntilIdle()
            assertEquals(Timeline.Local, model.feed.value.timeline)
            assertEquals(listOf(Timeline.Local to null), source.timelineCalls)

            model.loadMore(Timeline.Home)
            advanceUntilIdle()
            assertEquals(listOf(Timeline.Local to null), source.timelineCalls)

            model.loadMore()
            advanceUntilIdle()
            assertEquals(listOf(Timeline.Local to null, Timeline.Local to "2"), source.timelineCalls)

            model.create(CreatePostRequest("Local post"))
            advanceUntilIdle()
            assertEquals(Timeline.Local, source.timelineCalls.last().first)
            assertEquals(Timeline.Local, model.feed.value.timeline)
        } finally { owner.clear(); Dispatchers.resetMain() }
    }

    @Test fun completingSignInLeavesOtherAccountSessionUntouched() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val owner = ViewModelStore()
        try {
            val existingLogin = LoginSession("https://existing.example", "existing-token", JSONObject("""{"id":"existing","username":"existing"}"""))
            val newLogin = LoginSession("https://new.example", "new-token", JSONObject("""{"id":"new","username":"new"}"""))
            val existingSession = Session(existingLogin.account.id, existingLogin.token, ServerCapabilities())
            val store = MemoryStore(existingSession, existingLogin.account)
            val model = AccountManager(store, auth(newLogin), StandardTestDispatcher(testScheduler))
            owner.put("isolated", model)
            advanceUntilIdle()

            model.signIn("https://new.example")
            advanceUntilIdle()
            model.callback("palustris://auth/misskey?session=session-id")
            advanceUntilIdle()

            assertEquals(existingSession, store.sessions[existingSession.accountId])
            assertEquals(newLogin.token, store.sessions[newLogin.account.id]?.token)
            assertEquals(setOf(existingLogin.account.id, newLogin.account.id), store.index.accounts.map { it.accountId }.toSet())
            assertEquals(listOf(existingSession.accountId), store.readAccountIds)
            assertEquals(listOf(newLogin.account.id), store.writtenAccountIds)
        } finally { owner.clear(); Dispatchers.resetMain() }
    }

    @Test fun reauthenticationReplacesSameAccountTokenAndSessionGeneration() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val owner = ViewModelStore()
        try {
            val replacement = LoginSession(
                "https://example.org",
                "replacement-token",
                JSONObject("""{"id":"a","username":"alice"}"""),
            )
            val store = MemoryStore(Session(login.account.id, login.token, ServerCapabilities()), login.account)
            val model = AccountManager(store, auth(replacement), StandardTestDispatcher(testScheduler))
            owner.put("account", model)
            advanceUntilIdle()
            val initialGeneration = model.session.value.sessionGeneration

            model.signIn("https://example.org")
            advanceUntilIdle()
            model.callback("palustris://auth/misskey?session=session-id")
            advanceUntilIdle()

            assertEquals("replacement-token", store.storedSession?.token)
            assertEquals("replacement-token", model.activeSession.value?.token)
            assertTrue(model.session.value.sessionGeneration > initialGeneration)
        } finally { owner.clear(); Dispatchers.resetMain() }
    }

    @Test fun permissionUpgradeDoesNotReplaceAccountWhenReturnedIdentityDiffers() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val owner = ViewModelStore()
        try {
            val existingSession = Session(login.account.id, login.token, ServerCapabilities())
            val mismatched = LoginSession(
                "https://example.org",
                "wrong-account-token",
                JSONObject("""{"id":"other","username":"other"}"""),
            )
            val store = MemoryStore(existingSession, login.account)
            val model = AccountManager(store, auth(mismatched), StandardTestDispatcher(testScheduler))
            owner.put("upgrade", model)
            advanceUntilIdle()

            model.upgradePermissions(login.account.id)
            advanceUntilIdle()
            model.callback("palustris://auth/misskey?session=session-id")
            advanceUntilIdle()

            assertEquals(existingSession, store.storedSession)
            assertEquals(existingSession, model.activeSession.value)
            assertTrue(model.session.value.error.orEmpty().contains("match"))
        } finally { owner.clear(); Dispatchers.resetMain() }
    }

    @Test fun addingAccountKeepsActiveSessionUntilCanceled() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val owner = ViewModelStore()
        try {
            val existingSession = Session(login.account.id, login.token, ServerCapabilities())
            val store = MemoryStore(existingSession, login.account)
            val model = AccountManager(store, auth(login), StandardTestDispatcher(testScheduler))
            owner.put("add", model)
            advanceUntilIdle()

            model.beginAddAccount()
            assertTrue(model.session.value.addingAccount)
            assertEquals(login.account.id, model.session.value.account?.id)
            model.signIn("https://new.example")
            advanceUntilIdle()

            assertTrue(model.session.value.pending)
            assertTrue(model.session.value.addingAccount)
            assertEquals(existingSession, model.activeSession.value)
            assertEquals(existingSession, store.sessions[existingSession.accountId])

            model.cancelSignIn()
            advanceUntilIdle()
            assertFalse(model.session.value.addingAccount)
            assertFalse(model.session.value.pending)
            assertEquals(login.account.id, model.session.value.account?.id)
            assertEquals(existingSession, model.activeSession.value)
            assertNull(store.pending)
        } finally { owner.clear(); Dispatchers.resetMain() }
    }
}
