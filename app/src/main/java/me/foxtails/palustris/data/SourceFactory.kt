package me.foxtails.palustris.data

import javax.inject.Inject
import me.foxtails.palustris.data.misskey.HttpClientPool
import me.foxtails.palustris.data.misskey.MisskeyApi
import me.foxtails.palustris.data.misskey.MisskeySource
import me.foxtails.palustris.data.misskey.CapabilityCache
import me.foxtails.palustris.data.mastodon.MastodonSource
import me.foxtails.palustris.data.mastodon.MastodonCapabilityProbe
import me.foxtails.palustris.domain.Protocol
import me.foxtails.palustris.domain.Session
import me.foxtails.palustris.domain.SocialSource
import me.foxtails.palustris.data.auth.SessionStore

class SocialSourceFactory @Inject constructor(
    private val clientPool: HttpClientPool,
    private val sessionStore: SessionStore? = null,
    private val capabilityCache: CapabilityCache = CapabilityCache(),
) {
    fun create(session: Session): SocialSource = when (session.accountId.connection.protocol) {
        Protocol.MISSKEY -> MisskeySource(
            origin = session.accountId.connection.origin,
            token = session.token,
            api = MisskeyApi(clientPool.clientFor(session.accountId.connection)),
            accountId = session.accountId,
            initialCapabilities = session.capabilities,
            capabilityCache = capabilityCache,
            sessionRevision = session.sessionRevision,
            onCapabilitiesUpdated = { capabilities ->
                sessionStore?.updateCapabilities(session.accountId) { capabilities }
            },
        )
        Protocol.MASTODON -> {
            val api = MisskeyApi(clientPool.clientFor(session.accountId.connection))
            MastodonSource(
                origin = session.accountId.connection.origin,
                token = session.token,
                api = api,
                accountId = session.accountId,
                initialCapabilities = session.capabilities,
                capabilityProbe = MastodonCapabilityProbe(api),
                sessionRevision = session.sessionRevision,
            )
        }
    }
}
