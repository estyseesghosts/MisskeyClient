package me.foxtails.palustris

import me.foxtails.palustris.data.auth.AccountRef
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.Audience
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.EmojiCapabilities
import me.foxtails.palustris.domain.EmojiChoice
import me.foxtails.palustris.domain.EditableProfilePatch
import me.foxtails.palustris.domain.EntityId
import me.foxtails.palustris.domain.Notification
import me.foxtails.palustris.domain.NotificationQuery
import me.foxtails.palustris.domain.OwnedPost
import me.foxtails.palustris.domain.Post
import me.foxtails.palustris.domain.PostDraft
import me.foxtails.palustris.domain.Protocol
import me.foxtails.palustris.domain.Timeline
import me.foxtails.palustris.data.auth.DraftStore
import me.foxtails.palustris.data.auth.InMemoryDraftStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.foxtails.palustris.ui.FeedState
import me.foxtails.palustris.ui.NotificationsUiState
import me.foxtails.palustris.ui.emoji.EmojiCatalogState
import me.foxtails.palustris.ui.profile.ProfileCategory
import me.foxtails.palustris.ui.profile.ProfileUiState
import me.foxtails.palustris.ui.shell.AccountSwitcher
import me.foxtails.palustris.ui.shell.ComposerContract
import me.foxtails.palustris.ui.shell.EmojiPresentation
import me.foxtails.palustris.ui.shell.DraftsContract
import me.foxtails.palustris.ui.shell.HomeContract
import me.foxtails.palustris.ui.shell.HomeFeedUiState
import me.foxtails.palustris.ui.shell.NotificationsContract
import me.foxtails.palustris.ui.shell.PostInteractions
import me.foxtails.palustris.ui.shell.ProfileContract
import me.foxtails.palustris.ui.shell.SearchContract
import me.foxtails.palustris.ui.shell.ThreadContract
import me.foxtails.palustris.ui.thread.PostThreadUiState

/**
 * Explicit shell identities for presentation tests.
 *
 * Every fixture names its own connection, account, and entity origin. This keeps shell tests
 * independent from the production account graph and from other fixtures.
 */
internal object AppShellFixtures {
    val connection = Connection("https://fixture.example", Protocol.MASTODON)

    fun account(
        localId: String = "fixture",
        displayName: String = "Fixture $localId",
        biography: String = "Fixture biography",
    ): Account = Account(
        id = AccountId(connection, localId),
        displayName = displayName,
        handle = "@$localId@fixture.example",
        biography = biography,
    )

    fun post(
        id: String,
        author: Account,
        text: String = "Fixture post",
        actionTargetId: EntityId? = null,
    ): Post = Post(
        id = EntityId(connection.origin, id),
        author = author,
        text = text,
        publishedAtEpochMillis = 0,
        audience = Audience.Public,
        actionTargetId = actionTargetId,
    )

    fun owned(account: Account, post: Post, sessionRevision: Long = 0L): OwnedPost =
        OwnedPost(account.id, post, sessionRevision)

    /** Test-only account-switcher actions recorder. */
    fun switcher(
        accounts: List<AccountRef> = emptyList(),
        onSwitch: (AccountId) -> Unit = {},
        onAdd: () -> Unit = {},
        onSettings: () -> Unit = {},
        onSignOut: () -> Unit = {},
    ): AccountSwitcher = AccountSwitcher(
        accounts = accounts,
        actions = object : AccountSwitcher.Actions {
            override fun switchTo(accountId: AccountId) = onSwitch(accountId)
            override fun addAccount() = onAdd()
            override fun openSettings() = onSettings()
            override fun signOut() = onSignOut()
        },
    )

    /** Test-only emoji presentation with inert picker-preference actions. */
    fun emoji(
        catalog: EmojiCatalogState = EmojiCatalogState(),
        capabilities: EmojiCapabilities = EmojiCapabilities(),
    ): EmojiPresentation = EmojiPresentation(
        catalog = catalog,
        capabilities = capabilities,
        actions = object : EmojiPresentation.Actions {
            override fun loadCatalog() = Unit
            override fun retryCatalog() = Unit
            override fun toggleGroupCollapsed(groupId: String) = Unit
            override fun toggleGroupPinned(groupId: String) = Unit
            override fun togglePinnedEmoji(identity: String) = Unit
        },
    )

    /** Test-only notification inbox with inert actions. */
    fun notifications(
        state: NotificationsUiState = NotificationsUiState(),
    ): NotificationsContract = NotificationsContract(
        state = state,
        actions = object : NotificationsContract.Actions {
            override fun refresh() = Unit
            override fun loadMore() = Unit
            override fun markAllRead() = Unit
            override fun markSeen(notification: Notification?) = Unit
            override fun dismiss(notification: Notification) = Unit
            override fun respondToFollowRequest(notification: Notification, accept: Boolean) = Unit
            override fun selectQuery(query: NotificationQuery) = Unit
        },
    )

    /** Test-only thread presentation with inert actions. */
    fun thread(state: PostThreadUiState? = null): ThreadContract = ThreadContract(
        state = state,
        actions = object : ThreadContract.Actions {
            override fun activate(post: OwnedPost?, enabled: Boolean) = Unit
            override fun deactivate() = Unit
            override fun refresh() = Unit
            override fun continueAcquisition() = Unit
            override fun favorite(post: OwnedPost) = Unit
            override fun repost(post: OwnedPost) = Unit
            override fun bookmark(post: OwnedPost) = Unit
            override fun react(post: OwnedPost, choice: EmojiChoice) = Unit
        },
    )

    /** Test-only Home timeline state derived from a fixture feed. */
    fun homeFeed(feed: FeedState): HomeFeedUiState = HomeFeedUiState(
        ownedPosts = feed.ownedPosts,
        posts = feed.posts,
        loading = feed.loading,
        loadingMore = feed.loadingMore,
        nextCursor = feed.nextCursor,
        error = feed.error,
        needsSignIn = feed.needsSignIn,
        selectedTimeline = feed.timeline,
        availableTimelines = feed.timelines,
    )

    /** Test-only Home contract derived from a fixture feed. */
    fun home(
        feed: FeedState,
        onRefresh: (Timeline) -> Unit = {},
        onLoadMore: (Timeline) -> Unit = {},
    ): HomeContract = HomeContract(
        state = homeFeed(feed),
        actions = object : HomeContract.Actions {
            override fun refresh(timeline: Timeline) = onRefresh(timeline)
            override fun loadMore(timeline: Timeline) = onLoadMore(timeline)
        },
    )

    /** Test-only search contract derived from a fixture feed. */
    fun search(
        feed: FeedState,
        onSearch: (String) -> Unit = {},
        onLoadMore: () -> Unit = {},
    ): SearchContract = SearchContract(
        state = feed.accountSearch,
        actions = object : SearchContract.Actions {
            override fun search(query: String) = onSearch(query)
            override fun loadMore() = onLoadMore()
        },
    )

    /** Test-only post interactions derived from a fixture feed. */
    fun interactions(
        feed: FeedState,
        onFavorite: (OwnedPost) -> Unit = {},
        onRepost: (OwnedPost) -> Unit = {},
        onBookmark: (OwnedPost) -> Unit = {},
        onReact: (OwnedPost, EmojiChoice) -> Unit = { _, _ -> },
    ): PostInteractions = PostInteractions(
        availableActions = feed.actions,
        quoteEnabled = feed.quoteStatus == me.foxtails.palustris.domain.CapabilityStatus.Supported,
        actions = object : PostInteractions.Actions {
            override fun favorite(post: OwnedPost) = onFavorite(post)
            override fun repost(post: OwnedPost) = onRepost(post)
            override fun bookmark(post: OwnedPost) = onBookmark(post)
            override fun react(post: OwnedPost, choice: EmojiChoice) = onReact(post, choice)
        },
    )

    /** Test-only composer contract derived from a fixture feed. */
    fun composer(
        feed: FeedState,
        postPreferences: me.foxtails.palustris.domain.PostPreferences = me.foxtails.palustris.domain.PostPreferences(),
        onPublish: (me.foxtails.palustris.domain.CreatePostRequest, (OwnedPost) -> Unit) -> Unit = { _, _ -> },
    ): ComposerContract = ComposerContract(
        postPreferences = postPreferences,
        availableAudiences = feed.audiences,
        canPublish = feed.canPublish,
        publishing = feed.publishing,
        error = feed.error,
        actions = object : ComposerContract.Actions {
            override fun publish(request: me.foxtails.palustris.domain.CreatePostRequest, onAccepted: (OwnedPost) -> Unit) =
                onPublish(request, onAccepted)
        },
    )

    /** Test-only draft persistence backed by an explicit store. */
    fun drafts(store: DraftStore = InMemoryDraftStore()): DraftsContract {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        return DraftsContract(
            actions = object : DraftsContract.Actions {
                override fun load(accountId: AccountId?, onResult: (List<PostDraft>) -> Unit) {
                    scope.launch { onResult(store.list(accountId)) }
                }

                override fun save(draft: PostDraft, onResult: (PostDraft) -> Unit, onError: () -> Unit) {
                    scope.launch {
                        runCatching { store.save(draft) }
                            .onSuccess { onResult(draft) }
                            .onFailure { onError() }
                    }
                }

                override fun delete(accountId: AccountId?, draftId: String, onDone: () -> Unit) {
                    scope.launch {
                        runCatching { store.delete(accountId, draftId) }
                        onDone()
                    }
                }
            },
        )
    }

    /** Test-only profile presentation with recorder hooks. */
    fun profile(
        state: ProfileUiState = ProfileUiState(),
        onOpen: (Account) -> Unit = {},
        onSelectCategory: (ProfileCategory) -> Unit = {},
        onRefresh: () -> Unit = {},
        onLoadMore: () -> Unit = {},
        onFollow: () -> Unit = {},
        onUnfollow: () -> Unit = {},
        onReact: (OwnedPost, EmojiChoice) -> Unit = { _, _ -> },
        onSaveEditor: (EditableProfilePatch, () -> Unit) -> Unit = { _, onSuccess -> onSuccess() },
        onOpenEditor: () -> Unit = {},
        onCloseEditor: () -> Unit = {},
    ): ProfileContract = ProfileContract(
        state = state,
        actions = object : ProfileContract.Actions {
            override fun open(account: Account) = onOpen(account)
            override fun selectCategory(category: ProfileCategory) = onSelectCategory(category)
            override fun refresh() = onRefresh()
            override fun loadMore() = onLoadMore()
            override fun follow() = onFollow()
            override fun unfollow() = onUnfollow()
            override fun react(post: OwnedPost, choice: EmojiChoice) = onReact(post, choice)
            override fun saveEditor(patch: EditableProfilePatch, onSuccess: () -> Unit) = onSaveEditor(patch, onSuccess)
            override fun openEditor() = onOpenEditor()
            override fun closeEditor() = onCloseEditor()
        },
    )
}
