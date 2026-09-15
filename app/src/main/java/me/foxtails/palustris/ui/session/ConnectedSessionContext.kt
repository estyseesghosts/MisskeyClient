package me.foxtails.palustris.ui.session

import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.NotificationSyncToken
import me.foxtails.palustris.domain.SocialSource

/**
 * One accepted connected account/session lifetime.
 *
 * The context joins the visible account, the durable session revision, the runtime presentation
 * generation, and the registered source for the same session. Source-backed hosts read [source]
 * internally. Presentation consumers receive only the account, the durable revision, and the
 * presentation generation.
 *
 * The durable revision, the presentation generation, and [registryToken] are different identities.
 * They protect different operations. Do not treat them as interchangeable numbers.
 */
class ConnectedSessionContext internal constructor(
    val account: Account,
    val sessionRevision: Long,
    val presentationGeneration: Long,
    internal val source: SocialSource,
    internal val registryToken: NotificationSyncToken,
    internal val directMessageGeneration: Long,
) {
    val accountId: AccountId get() = account.id

    override fun toString(): String =
        "ConnectedSessionContext(accountId=$accountId, " +
            "sessionRevision=$sessionRevision, presentationGeneration=$presentationGeneration)"
}
