package me.foxtails.palustris.data.auth

import me.foxtails.palustris.data.misskey.ServerAddress
import me.foxtails.palustris.domain.Protocol

/** Chooses the protocol once, before either protocol-specific sign-in flow starts. */
class DetectingAuthGateway(
    private val misskey: AuthGateway,
    private val mastodon: AuthGateway,
    private val detectsMisskey: suspend (String) -> Boolean,
) : AuthGateway {
    override suspend fun prepare(input: String): PendingLogin {
        val origin = ServerAddress.normalize(input)
        val isMisskey = runCatching { detectsMisskey(origin) }.getOrDefault(false)
        return if (isMisskey) misskey.prepare(input) else mastodon.prepare(input)
    }

    override fun browserUrl(pending: PendingLogin): String = gatewayFor(pending).browserUrl(pending)

    override suspend fun complete(pending: PendingLogin): LoginSession = gatewayFor(pending).complete(pending)

    private fun gatewayFor(pending: PendingLogin): AuthGateway = when (pending.protocol) {
        Protocol.MISSKEY -> misskey
        Protocol.MASTODON -> mastodon
    }
}
