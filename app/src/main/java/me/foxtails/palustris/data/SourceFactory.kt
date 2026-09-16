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
    private val appMessages: AppMessages = AppMessages.Default,
) {
    fun create(session: Session): SocialSource = when (session.accountId.connection.protocol) {
        Protocol.MISSKEY -> MisskeySource(
            origin = session.accountId.connection.origin,
            token = session.token,
            api = MisskeyApi(clientPool.clientFor(session.accountId.connection), appMessages = appMessages),
            accountId = session.accountId,
            initialCapabilities = session.capabilities,
            capabilityCache = capabilityCache,
            sessionRevision = session.sessionRevision,
            onCapabilitiesUpdated = { capabilities ->
                // Persist only when the stored session still matches the source revision.
                sessionStore?.updateCapabilities(session.accountId, session.sessionRevision) { capabilities }
            },
            appMessages = appMessages,
        )
        Protocol.MASTODON -> {
            val api = MisskeyApi(clientPool.clientFor(session.accountId.connection), appMessages = appMessages)
            MastodonSource(
                origin = session.accountId.connection.origin,
                token = session.token,
                api = api,
                accountId = session.accountId,
                initialCapabilities = session.capabilities,
                capabilityProbe = MastodonCapabilityProbe(api),
                sessionRevision = session.sessionRevision,
                onCapabilitiesUpdated = { capabilities ->
                    // Persist only when the stored session still matches the source revision.
                    sessionStore?.updateCapabilities(session.accountId, session.sessionRevision) { capabilities }
                },
            )
        }
    }
}
