package me.foxtails.palustris

import me.foxtails.palustris.data.auth.AccountRef
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.Audience
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.EmojiCapabilities
import me.foxtails.palustris.domain.EntityId
import me.foxtails.palustris.domain.Notification
import me.foxtails.palustris.domain.NotificationQuery
import me.foxtails.palustris.domain.OwnedPost
import me.foxtails.palustris.domain.Post
import me.foxtails.palustris.domain.Protocol
import me.foxtails.palustris.ui.NotificationsUiState
import me.foxtails.palustris.ui.emoji.EmojiCatalogState
import me.foxtails.palustris.ui.shell.AccountSwitcher
import me.foxtails.palustris.ui.shell.EmojiPresentation
import me.foxtails.palustris.ui.shell.NotificationsContract

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
}
