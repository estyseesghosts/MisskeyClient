package me.foxtails.palustris

import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.Audience
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.EntityId
import me.foxtails.palustris.domain.OwnedPost
import me.foxtails.palustris.domain.Post
import me.foxtails.palustris.domain.Protocol

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
}
