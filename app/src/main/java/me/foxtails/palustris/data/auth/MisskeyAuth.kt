package me.foxtails.palustris.data.auth

import me.foxtails.palustris.data.AppMessages
import me.foxtails.palustris.data.misskey.HttpClientPool
import me.foxtails.palustris.data.misskey.MisskeyApi
import me.foxtails.palustris.data.misskey.MisskeyErrorMapper
import me.foxtails.palustris.data.misskey.MisskeyMapper
import me.foxtails.palustris.data.misskey.ServerAddress
import me.foxtails.palustris.data.misskey.nullableString
import me.foxtails.palustris.ProductIdentity
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

class MisskeyAuth(
    private val apiFor: (String) -> MisskeyApi,
    private val appMessages: AppMessages = AppMessages.Default,
) : AuthGateway {
    constructor(api: MisskeyApi, appMessages: AppMessages = AppMessages.Default) : this({ api }, appMessages)
    constructor(clientPool: HttpClientPool, appMessages: AppMessages = AppMessages.Default) : this({ origin ->
        MisskeyApi(clientPool.clientFor(Connection(origin, Protocol.MISSKEY)), appMessages = appMessages)
    }, appMessages)

    override suspend fun prepare(input: String): PendingLogin = try {
        val origin = ServerAddress.normalize(input, appMessages)
        val meta = JSONObject(apiFor(origin).post(origin, "meta", JSONObject().put("detail", false)).body)
        require(!meta.nullableString("version").isNullOrBlank()) { appMessages.misskeyServerIncompatible() }
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
        .addQueryParameter("name", ProductIdentity.name)
        .addQueryParameter("callback", "palustris://auth/misskey")
        .addQueryParameter("permission", MISSKEY_PERMISSIONS).build().toString()

    override suspend fun complete(pending: PendingLogin): LoginSession = try {
        require(pending.isFresh(System.currentTimeMillis())) { appMessages.signInExpired() }
        val result = JSONObject(apiFor(pending.origin).post(pending.origin, "miauth/${pending.id}/check").body)
        if (!result.optBoolean("ok")) throw IllegalArgumentException(appMessages.signInNotApproved())
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
        const val MISSKEY_PERMISSIONS = "read:account,write:account,write:notes,read:notifications,write:notifications,write:following,read:reactions,write:reactions,read:favorites,write:favorites,read:blocks,write:blocks,read:mutes,write:mutes"
        val REQUESTED_ACCESS = setOf(
            AccessScope.NotificationsRead,
            AccessScope.NotificationsWrite,
            AccessScope.FollowRequests,
            AccessScope.PrimaryFavouriteWrite,
            AccessScope.SavedPostsRead,
            AccessScope.SavedPostsWrite,
            AccessScope.LikedPostsRead,
            AccessScope.ModerationRead,
            AccessScope.ModerationWrite,
        )
    }
}
