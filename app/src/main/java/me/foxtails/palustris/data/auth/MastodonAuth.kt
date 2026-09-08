package me.foxtails.palustris.data.auth

import me.foxtails.palustris.data.misskey.HttpClientPool
import me.foxtails.palustris.data.misskey.MisskeyApi
import me.foxtails.palustris.data.misskey.ServerAddress
import me.foxtails.palustris.data.mastodon.MastodonErrorMapper
import me.foxtails.palustris.domain.Connection
import me.foxtails.palustris.domain.AccessGrant
import me.foxtails.palustris.domain.AccessScope
import me.foxtails.palustris.domain.AccessStatus
import me.foxtails.palustris.domain.Protocol
import kotlinx.coroutines.CancellationException
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONObject
import java.net.URI
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID

class MastodonAuth(
    private val apiFor: (String) -> MisskeyApi,
    private val appRegistrationCache: AppRegistrationCache = AppRegistrationCache(),
) : AuthGateway {
    constructor(api: MisskeyApi, appRegistrationCache: AppRegistrationCache = AppRegistrationCache()) :
        this({ api }, appRegistrationCache)

    constructor(clientPool: HttpClientPool, appRegistrationCache: AppRegistrationCache = AppRegistrationCache()) :
        this({ origin -> MisskeyApi(clientPool.clientFor(Connection(origin, Protocol.MASTODON))) }, appRegistrationCache)

    override suspend fun prepare(input: String): PendingLogin = try {
        val origin = ServerAddress.normalize(input)
        val api = apiFor(origin)
        val registration = appRegistrationCache.getOrPut(origin, REQUIRED_APP_SCOPES) {
            registerApp(origin, api, REQUIRED_APP_SCOPES)
        }
        val supportsPkce = detectPkceSupport(origin, api)
        val verifier = if (supportsPkce) generateCodeVerifier() else null
        val challenge = verifier?.let(::codeChallenge)
        PendingLogin(
            origin = origin,
            id = UUID.randomUUID().toString(),
            createdAt = System.currentTimeMillis(),
            protocol = Protocol.MASTODON,
            clientId = registration.clientId,
            clientSecret = registration.clientSecret,
            codeVerifier = verifier,
            codeChallenge = challenge,
            scope = MASTODON_SCOPE,
            requestedAccess = REQUESTED_ACCESS,
        )
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        throw MastodonErrorMapper.map(e)
    }

    override fun browserUrl(pending: PendingLogin): String {
        require(pending.protocol == Protocol.MASTODON && !pending.clientId.isNullOrBlank())
        return pending.origin.toHttpUrl().newBuilder()
            .addPathSegment("oauth").addPathSegment("authorize")
            .addQueryParameter("client_id", pending.clientId)
            .addQueryParameter("redirect_uri", REDIRECT_URI)
            .addQueryParameter("response_type", "code")
            .addQueryParameter("scope", pending.scope)
            .addQueryParameter("state", pending.id)
            .apply {
                pending.codeChallenge?.let {
                    addQueryParameter("code_challenge", it)
                    addQueryParameter("code_challenge_method", "S256")
                }
            }
            .build().toString()
    }

    override suspend fun complete(pending: PendingLogin): LoginSession = try {
        require(pending.protocol == Protocol.MASTODON && pending.isFresh(System.currentTimeMillis())) {
            "This sign-in has expired. Choose your instance again."
        }
        val clientId = requireNotNull(pending.clientId)
        val clientSecret = requireNotNull(pending.clientSecret)
        val code = requireNotNull(pending.authorizationCode) {
            "Authorization was not returned. Finish signing in in your browser, then try again."
        }
        val tokenResponse = apiFor(pending.origin).postForm(pending.origin, "oauth/token", mapOf(
            "client_id" to clientId,
            "client_secret" to clientSecret,
            "grant_type" to "authorization_code",
            "redirect_uri" to REDIRECT_URI,
            "code" to code,
        ) + (pending.codeVerifier?.let { mapOf("code_verifier" to it) } ?: emptyMap()))
        val tokenJson = JSONObject(tokenResponse.body)
        val token = tokenJson.getString("access_token")
        require(token.isNotBlank())
        val user = JSONObject(apiFor(pending.origin)
            .get(pending.origin, "v1/accounts/verify_credentials", token).body)
        LoginSession(
            origin = pending.origin,
            token = token,
            user = user,
            protocol = Protocol.MASTODON,
            canPublish = true,
            access = AccessGrant(
                requested = pending.requestedAccess,
                known = tokenJson.optString("scope").takeIf { it.isNotBlank() }
                    ?.let(::accessFromScope).orEmpty(),
            ),
        )
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        throw MastodonErrorMapper.map(e)
    }

    private suspend fun registerApp(origin: String, api: MisskeyApi, scopes: Set<String>): AppRegistration {
        val response = api.postForm(origin, "api/v1/apps", mapOf(
            "client_name" to "Palustris",
            "redirect_uris" to REDIRECT_URI,
            "scopes" to scopes.joinToString(" "),
        ))
        val json = JSONObject(response.body)
        val scopeValues = json.optJSONArray("scopes")?.let { values ->
            (0 until values.length()).mapNotNull { values.optString(it).takeIf(String::isNotBlank) }.toSet()
        }.orEmpty()
        return AppRegistration(
            clientId = json.getString("client_id"),
            clientSecret = json.getString("client_secret"),
            scopes = scopeValues,
            scopesKnown = json.has("scopes"),
        )
    }

    private fun accessFromScope(scope: String): Map<AccessScope, AccessStatus> {
        val scopes = scope.split(Regex("\\s+")).filter(String::isNotBlank).toSet()
        return mapOf(
            AccessScope.NotificationsRead to if ("read" in scopes) AccessStatus.Granted else AccessStatus.Denied,
            AccessScope.NotificationsWrite to if ("write" in scopes) AccessStatus.Granted else AccessStatus.Denied,
            AccessScope.FollowRequests to if ("read" in scopes && "write" in scopes) AccessStatus.Granted else AccessStatus.Denied,
            AccessScope.Push to if ("push" in scopes) AccessStatus.Granted else AccessStatus.Denied,
            AccessScope.PrimaryFavouriteWrite to if ("write" in scopes || "write:favourites" in scopes) AccessStatus.Granted else AccessStatus.Denied,
            AccessScope.SavedPostsRead to if ("read" in scopes || "read:bookmarks" in scopes) AccessStatus.Granted else AccessStatus.Denied,
            AccessScope.SavedPostsWrite to if ("write" in scopes || "write:bookmarks" in scopes) AccessStatus.Granted else AccessStatus.Denied,
        )
    }

    private suspend fun detectPkceSupport(origin: String, api: MisskeyApi): Boolean {
        val version = runCatching {
            JSONObject(api.get(origin, "v2/instance").body).optString("version")
        }.getOrDefault("")
        return versionAtLeast(version, 4, 3, 0)
    }

    private fun versionAtLeast(value: String, major: Int, minor: Int, patch: Int): Boolean {
        val numbers = Regex("\\d+").findAll(value).map { it.value.toInt() }.toList()
        val current = listOf(numbers.getOrElse(0) { 0 }, numbers.getOrElse(1) { 0 }, numbers.getOrElse(2) { 0 })
        val target = listOf(major, minor, patch)
        return current.zip(target).firstOrNull { (left, right) -> left != right }
            ?.let { (left, right) -> left > right } ?: true
    }

    private fun generateCodeVerifier(): String {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private fun codeChallenge(verifier: String): String = Base64.getUrlEncoder().withoutPadding()
        .encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)))

    private companion object {
        const val MASTODON_SCOPE = "read write push"
        val REQUIRED_APP_SCOPES = setOf("read", "write", "push")
        val REQUESTED_ACCESS = setOf(
            AccessScope.NotificationsRead,
            AccessScope.NotificationsWrite,
            AccessScope.FollowRequests,
            AccessScope.Push,
            AccessScope.PrimaryFavouriteWrite,
            AccessScope.SavedPostsRead,
            AccessScope.SavedPostsWrite,
        )
        const val REDIRECT_URI = "palustris://auth/mastodon"
    }
}

object AuthCallback {
    fun matches(value: String, pending: PendingLogin, now: Long): Boolean = runCatching {
        val uri = URI(value)
        val validUri = uri.scheme == "palustris" && uri.host == "auth" && uri.userInfo == null &&
            uri.port == -1 && uri.fragment == null && pending.isFresh(now)
        if (!validUri) return@runCatching false
        when (pending.protocol) {
            Protocol.MISSKEY -> uri.path == "/misskey" && queryValues(uri, "session") == listOf(pending.id)
            Protocol.MASTODON -> uri.path == "/mastodon" && queryValues(uri, "state") == listOf(pending.id) &&
                queryValues(uri, "code").singleOrNull()?.isNotBlank() == true
        }
    }.getOrDefault(false)

    fun authorizationCode(value: String): String? = runCatching {
        queryValues(URI(value), "code").singleOrNull()
    }.getOrNull()

    private fun queryValues(uri: URI, key: String): List<String> = uri.rawQuery.orEmpty().split('&')
        .filter { it.substringBefore('=') == key }
        .map { java.net.URLDecoder.decode(it.substringAfter('='), Charsets.UTF_8.name()) }
}
