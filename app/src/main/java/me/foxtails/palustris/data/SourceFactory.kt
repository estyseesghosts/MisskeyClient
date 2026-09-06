package me.foxtails.palustris.data

import javax.inject.Inject
import me.foxtails.palustris.data.misskey.HttpClientPool
import me.foxtails.palustris.data.misskey.MisskeyApi
import me.foxtails.palustris.data.misskey.MisskeySource
import me.foxtails.palustris.domain.Protocol
import me.foxtails.palustris.domain.Session
import me.foxtails.palustris.domain.SocialSource
import me.foxtails.palustris.domain.SourceError

class SocialSourceFactory @Inject constructor(private val clientPool: HttpClientPool) {
    fun create(session: Session): SocialSource = when (session.accountId.connection.protocol) {
        Protocol.MISSKEY -> MisskeySource(
            origin = session.accountId.connection.origin,
            token = session.token,
            api = MisskeyApi(clientPool.clientFor(session.accountId.connection)),
            accountId = session.accountId,
        )
        Protocol.MASTODON -> throw SourceError.Unsupported("Mastodon source")
    }
}
