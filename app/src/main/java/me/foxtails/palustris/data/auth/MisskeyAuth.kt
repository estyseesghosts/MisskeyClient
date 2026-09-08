package me.foxtails.palustris.data.auth

import me.foxtails.palustris.data.misskey.*
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.AccessGrant
import me.foxtails.palustris.domain.AccessScope
import me.foxtails.palustris.domain.AccessStatus
import me.foxtails.palustris.domain.Protocol
import kotlinx.coroutines.CancellationException
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONObject
import java.util.UUID

interface AuthGateway {
    suspend fun prepare(input: String): PendingLogin
    fun browserUrl(pending: PendingLogin): String
    suspend fun complete(pending: PendingLogin): LoginSession
}

class MisskeyAuth(private val apiFor: (String) -> MisskeyApi) : AuthGateway {
    constructor(api: MisskeyApi) : this({ api })
    constructor(clientPool: HttpClientPool) : this({ origin ->
        MisskeyApi(clientPool.clientFor(Connection(origin, Protocol.MISSKEY)))
    })

    override suspend fun prepare(input: String): PendingLogin = try {
        val origin = ServerAddress.normalize(input)
        val meta = JSONObject(apiFor(origin).post(origin, "meta", JSONObject().put("detail", false)).body)
        require(!meta.nullableString("version").isNullOrBlank()) { "This server did not return Misskey-compatible information." }
        PendingLogin(
            origin = origin,
            id = UUID.randomUUID().toString(),
            createdAt = System.currentTimeMillis(),
            requestedAccess = REQUESTED_ACCESS,
        )
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        throw MisskeyErrorMapper.map(e)
    }
    override fun browserUrl(pending: PendingLogin): String = pending.origin.toHttpUrl().newBuilder()
        .addPathSegment("miauth").addPathSegment(pending.id)
        .addQueryParameter("name", "Palustris")
        .addQueryParameter("callback", "palustris://auth/misskey")
        .addQueryParameter("permission", MISSKEY_PERMISSIONS).build().toString()

    override suspend fun complete(pending: PendingLogin): LoginSession = try {
        require(pending.isFresh(System.currentTimeMillis())) { "This sign-in has expired. Choose your instance again." }
        val result = JSONObject(apiFor(pending.origin).post(pending.origin, "miauth/${pending.id}/check").body)
        if (!result.optBoolean("ok")) throw IllegalArgumentException("Access has not been approved yet. Finish signing in in your browser, then try again.")
        val token = result.getString("token")
        require(token.isNotBlank())
        // The check response includes the authenticated user. Persist immediately: MiAuth
        // checks may be single-use, so a second network request could lose a valid token.
        val user = result.getJSONObject("user")
        MisskeyMapper.account(user, pending.origin)
        LoginSession(
            origin = pending.origin,
            token = token,
            user = user,
            canPublish = true,
            access = AccessGrant(
                requested = pending.requestedAccess,
                known = pending.requestedAccess.associateWith { AccessStatus.Granted },
            ),
        )
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        throw MisskeyErrorMapper.map(e)
    }

    private companion object {
        const val MISSKEY_PERMISSIONS = "read:account,write:account,write:notes,read:notifications,write:notifications,write:following,write:reactions,read:favorites,write:favorites"
        val REQUESTED_ACCESS = setOf(
            AccessScope.NotificationsRead,
            AccessScope.NotificationsWrite,
            AccessScope.FollowRequests,
            AccessScope.PrimaryFavouriteWrite,
            AccessScope.SavedPostsRead,
            AccessScope.SavedPostsWrite,
        )
    }
}
